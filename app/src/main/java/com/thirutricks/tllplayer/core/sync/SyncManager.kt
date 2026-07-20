package com.thirutricks.tllplayer.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.thirutricks.tllplayer.core.database.dao.CategoryDao
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.MovieDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.CategoryEntity
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.core.model.SourceType
import com.thirutricks.tllplayer.core.network.HttpClient
import com.thirutricks.tllplayer.core.parser.M3uParser
import com.thirutricks.tllplayer.core.parser.XtCategory
import com.thirutricks.tllplayer.core.parser.XtLiveStream
import com.thirutricks.tllplayer.core.parser.XtSeries
import com.thirutricks.tllplayer.core.parser.XtVod
import com.thirutricks.tllplayer.core.parser.XtreamClient
import kotlin.coroutines.CoroutineContext

import com.thirutricks.tllplayer.legacy.models.TV

/**
 * Imports a source into the database. Uses a clear-then-insert refresh per source/type (avoids the
 * @Insert REPLACE cascade pitfall), batches inserts in chunks of [CHUNK], and reports progress via a
 * non-suspend [onProgress] callback (callers push it into a StateFlow for the UI).
 *
 * Series episodes are intentionally fetched lazily later (Phase 9), not during sync.
 */
class SyncManager(
    private val context: android.content.Context,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val xtream: XtreamClient,
    private val m3u: M3uParser,
    private val http: HttpClient,
) {
    suspend fun sync(source: SourceEntity, onProgress: (ImportStage) -> Unit): SyncResult =
        withContext(Dispatchers.IO) {
            try {
                when (source.type) {
                    SourceType.XTREAM -> syncXtream(source, onProgress)
                    SourceType.M3U -> syncM3u(source, onProgress)
                    SourceType.TLL -> syncTllPlaylist(source, onProgress)
                    SourceType.STALKER -> Unit // Stalker support not yet integrated
                    SourceType.LOCAL_BACKUP -> Unit
                }
                sourceDao.markSynced(source.id, System.currentTimeMillis())
                SyncResult.Success
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                SyncResult.Failed(e.message ?: "Sync failed")
            }
        }

    // ---------------- Xtream ----------------
    private suspend fun syncXtream(s: SourceEntity, onProgress: (ImportStage) -> Unit) {
        val ctx = currentCoroutineContext()

        // LIVE — sortOrder records the provider's order so "Playlist order" sorting can replay it.
        val liveCats = xtream.liveCategories(s)
        val liveMap = refreshCategories(s, MediaType.LIVE, liveCats)
        channelDao.clearSource(s.id)
        var liveOrder = 0
        val toChannel = { item: XtLiveStream ->
            ChannelEntity(
                sourceId = s.id, categoryId = liveMap[item.categoryId], name = item.name,
                logoUrl = item.icon, streamUrl = xtream.liveUrl(s, item.streamId),
                epgChannelId = item.epgChannelId, number = item.num, remoteId = item.streamId,
                sortOrder = liveOrder++,
                catchup = item.archive, catchupDays = item.archiveDays,
            )
        }
        val liveTotal = intArrayOf(0)
        val liveDone = bulkOrFallback("Channels") {
            chunked<ChannelEntity, Boolean>(ctx, "Channels", onProgress, { channelDao.upsertAll(it) }, liveTotal) { add ->
                xtream.streamLive(s) { add(toChannel(it)) }
            }
        }
        if (!liveDone) sliceByCategory(ctx, "Channels", onProgress, liveCats, { channelDao.upsertAll(it) }, liveTotal, liveTotal[0]) { cat, add ->
            xtream.streamLive(s, cat.id) { add(toChannel(it)) }
        }

        // MOVIES — non-fatal: a flaky VOD endpoint shouldn't abort the whole import (keep the channels).
        guardStep("Movies") {
            val vodCats = xtream.vodCategories(s)
            val vodMap = refreshCategories(s, MediaType.MOVIE, vodCats)
            movieDao.clearSource(s.id)
            var vodOrder = 0
            val toMovie = { item: XtVod ->
                MovieEntity(
                    sourceId = s.id, categoryId = vodMap[item.categoryId], name = item.name,
                    posterUrl = item.icon, rating = item.rating, plot = item.plot,
                    streamUrl = xtream.movieUrl(s, item.streamId, item.containerExt),
                    containerExt = item.containerExt, remoteId = item.streamId, addedAt = item.added,
                    sortOrder = vodOrder++,
                )
            }
            val vodTotal = intArrayOf(0)
            val vodDone = bulkOrFallback("Movies") {
                chunked<MovieEntity, Boolean>(ctx, "Movies", onProgress, { movieDao.upsertAll(it) }, vodTotal) { add ->
                    xtream.streamVod(s) { add(toMovie(it)) }
                }
            }
            if (!vodDone) sliceByCategory(ctx, "Movies", onProgress, vodCats, { movieDao.upsertAll(it) }, vodTotal, vodTotal[0]) { cat, add ->
                xtream.streamVod(s, cat.id) { add(toMovie(it)) }
            }
        }

        // SERIES (shows only; seasons/episodes fetched lazily later) — non-fatal too. Some providers
        // (e.g. peoplestv) return a non-standard HTTP 512 here that used to abort the entire import; now we
        // keep the channels + movies and just skip series rather than failing the whole sync.
        guardStep("Series") {
            val seriesCats = xtream.seriesCategories(s)
            val seriesMap = refreshCategories(s, MediaType.SERIES, seriesCats)
            seriesDao.clearSource(s.id)
            var seriesOrder = 0
            val toSeries = { item: XtSeries ->
                SeriesEntity(
                    sourceId = s.id, categoryId = seriesMap[item.categoryId], name = item.name,
                    posterUrl = item.cover, plot = item.plot, rating = item.rating,
                    year = item.year, remoteId = item.seriesId,
                    sortOrder = seriesOrder++,
                )
            }
            val seriesTotal = intArrayOf(0)
            val seriesDone = bulkOrFallback("Series") {
                chunked<SeriesEntity, Boolean>(ctx, "Series", onProgress, { seriesDao.upsertSeries(it) }, seriesTotal) { add ->
                    xtream.streamSeries(s) { add(toSeries(it)) }
                }
            }
            if (!seriesDone) sliceByCategory(ctx, "Series", onProgress, seriesCats, { seriesDao.upsertSeries(it) }, seriesTotal, seriesTotal[0]) { cat, add ->
                xtream.streamSeries(s, cat.id) { add(toSeries(it)) }
            }
        }
    }

    /** Run a content step (Movies / Series) without letting a provider-side failure abort the whole import —
     *  the channels (and any earlier step) are already saved, so a broken VOD/Series endpoint just means that
     *  section is missing, not a total failure. Cancellation still propagates. */
    private suspend inline fun guardStep(label: String, block: () -> Unit) {
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            android.util.Log.w("SyncManager", "$label import failed — keeping the rest of the import", e)
        }
    }

    /**
     * Run a bulk list fetch; if it ERRORS (not just truncates), return false so the caller drops to the
     * smaller per-category requests. Some panels (e.g. peoplestv) return a non-standard HTTP 512 on the giant
     * full `get_series` / `get_vod_streams` response but serve the per-category (`&category_id=X`) requests
     * fine — without this, the bulk error skipped straight past the per-category fallback.
     */
    private inline fun bulkOrFallback(label: String, bulk: () -> Boolean): Boolean =
        try {
            bulk()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            android.util.Log.w("SyncManager", "$label bulk fetch failed (${e.message}) — falling back to per-category requests", e)
            false
        }

    /**
     * Fallback for when a provider truncates the single bulk list (issue #15): re-fetch one category at
     * a time (`&category_id=X`, tiny payloads) and upsert progressively. The table is NOT cleared first,
     * so any items the partial bulk already inserted (incl. uncategorized ones missing from the category
     * list) survive — the unique `(sourceId, remoteId)` index dedupes the overlap. [total] is shared with
     * the bulk pass so progress keeps climbing instead of resetting per category.
     *
     * Some panels IGNORE `category_id` and return the whole (truncating) list for every category — that
     * would loop forever re-fetching the same data. If a single category returns ~the bulk's whole count
     * and still truncates (or several categories can't be served), we stop and keep what we have.
     */
    private fun <T> sliceByCategory(
        ctx: CoroutineContext,
        label: String,
        onProgress: (ImportStage) -> Unit,
        categories: List<XtCategory>,
        insert: suspend (List<T>) -> Unit,
        total: IntArray,
        bulkPartial: Int,
        stream: (cat: XtCategory, add: (T) -> Unit) -> Boolean,
    ) {
        var truncations = 0
        categories.forEachIndexed { index, cat ->
            ctx.ensureActive()
            // Gentle pacing between the many small requests so we don't trip a rate-limiter (HTTP 429)
            // while looping through every category.
            if (index > 0) try { Thread.sleep(CATEGORY_REQUEST_DELAY_MS) } catch (_: InterruptedException) {}
            val before = total[0]
            // A single failing category (e.g. it 512s/429s) is skipped — keep importing the other categories
            // rather than losing the whole section.
            val complete = try {
                chunked<T, Boolean>(ctx, label, onProgress, insert, total) { add -> stream(cat) { add(it) } }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                android.util.Log.w("SyncManager", "$label: category ${cat.id} failed (${e.message}) — skipping it", e)
                return@forEachIndexed
            }
            if (!complete) {
                truncations++
                val delta = total[0] - before
                if ((bulkPartial > 0 && delta >= bulkPartial) || truncations >= 3) {
                    android.util.Log.w(
                        "SyncManager",
                        "$label: per-category fetch still truncating (panel likely ignores category_id) — stopping fallback after ${total[0]} items",
                    )
                    return // stop the fallback entirely
                }
            }
        }
    }

    private suspend fun refreshCategories(
        s: SourceEntity,
        type: MediaType,
        parsed: List<com.thirutricks.tllplayer.core.parser.XtCategory>,
    ): Map<String, Long> {
        categoryDao.clear(s.id, type)
        // sortOrder = provider index, so the rail follows the provider's category order.
        val entities = parsed.mapIndexed { i, c -> CategoryEntity(sourceId = s.id, mediaType = type, name = c.name, remoteId = c.id, sortOrder = i) }
        val ids = categoryDao.upsertAll(entities)
        return parsed.mapIndexedNotNull { i, c -> ids.getOrNull(i)?.let { c.id to it } }.toMap()
    }

    // ---------------- M3U ----------------
    private suspend fun syncM3u(s: SourceEntity, onProgress: (ImportStage) -> Unit) {
        val ctx = currentCoroutineContext()
        categoryDao.clear(s.id, MediaType.LIVE)
        channelDao.clearSource(s.id)

        val groupToCategoryId = HashMap<String, Long>()
        val buffer = ArrayList<ChannelEntity>(CHUNK)
        var processed = 0
        var order = 0 // playlist position — lets "Playlist order" sorting replay the file's order

        val onEntry: (com.thirutricks.tllplayer.core.parser.M3uEntry) -> Unit = { e ->
            val categoryId = e.groupTitle?.let { group ->
                groupToCategoryId.getOrPut(group) {
                    runBlocking {
                        categoryDao.upsertAll(
                            // sortOrder = first-seen position of the group in the playlist
                            listOf(CategoryEntity(sourceId = s.id, mediaType = MediaType.LIVE, name = group, remoteId = group, sortOrder = groupToCategoryId.size)),
                        ).first()
                    }
                }
            }
            buffer.add(
                ChannelEntity(
                    sourceId = s.id, categoryId = categoryId, name = e.name, logoUrl = e.logo,
                    streamUrl = e.streamUrl, epgChannelId = e.tvgId, number = e.tvgChno,
                    remoteId = null, // M3U has no stable id; rely on clear-then-insert
                    sortOrder = order++,
                    catchup = e.catchup != null, catchupDays = e.catchupDays ?: 0, catchupSource = e.catchupSource,
                ),
            )
            if (buffer.size >= CHUNK) {
                ctx.ensureActive()
                runBlocking { channelDao.upsertAll(buffer.toList()) }
                processed += buffer.size
                buffer.clear()
                onProgress(ImportStage("Channels", processed, null))
            }
        }
        // A locally-picked playlist file (in-app StorageBrowser gives an absolute path; also tolerate
        // file://content:// URIs) is read straight from the device; a normal URL is downloaded. Same parser.
        val isLocal = s.url.startsWith("/") || s.url.startsWith("file://") || s.url.startsWith("content://")
        val header = if (isLocal) {
            val input = if (s.url.startsWith("/")) {
                java.io.File(s.url).inputStream()
            } else {
                context.contentResolver.openInputStream(android.net.Uri.parse(s.url))
                    ?: throw java.io.IOException("Couldn't open the playlist file. Re-pick it (it may have moved).")
            }
            input.use { m3u.parse(it, onEntry) }
        } else {
            http.get(s.url, s.userAgent) { input -> m3u.parse(input, onEntry) }
        }
        if (buffer.isNotEmpty()) {
            runBlocking { channelDao.upsertAll(buffer.toList()) }
            processed += buffer.size
            onProgress(ImportStage("Channels", processed, null))
        }

        // Persist the playlist's EPG url (url-tvg) for the EPG engine if the source didn't have one.
        if (!header.urlTvg.isNullOrBlank() && s.epgUrl.isNullOrBlank()) {
            sourceDao.update(s.copy(epgUrl = header.urlTvg))
        }
    }

    /**
     * Drives a push-stream [producer] that feeds items into [add]; flushes to the DB via [insert] in
     * chunks of [CHUNK], reporting progress. Inserts run blocking on the IO thread (we want
     * sequential back-pressure), and cancellation is checked each chunk.
     */
    private fun <T, R> chunked(
        ctx: CoroutineContext,
        label: String,
        onProgress: (ImportStage) -> Unit,
        insert: suspend (List<T>) -> Unit,
        total: IntArray, // shared [0] running count for the whole media type, so progress never resets
        producer: (add: (T) -> Unit) -> R,
    ): R {
        val buffer = ArrayList<T>(CHUNK)
        fun flush() {
            if (buffer.isEmpty()) return
            ctx.ensureActive()
            runBlocking { insert(buffer.toList()) }
            total[0] += buffer.size
            buffer.clear()
            onProgress(ImportStage(label, total[0], null))
        }
        val result = producer { item ->
            buffer.add(item)
            if (buffer.size >= CHUNK) flush()
        }
        flush()
        return result
    }

    private suspend fun syncTllPlaylist(s: SourceEntity, onProgress: (ImportStage) -> Unit) {
        val allChannels = mutableListOf<TV>()

        // 1. Try to read from SP.networkConfigs
        val configs = com.thirutricks.tllplayer.legacy.SP.networkConfigs
        if (configs.isNotEmpty()) {
            onProgress(ImportStage("Fetching network configurations", 0, null))
            configs.filter { it.isEnabled }.forEach { config ->
                try {
                    val channels = com.thirutricks.tllplayer.legacy.infrastructure.PlaylistFetchers.fetch(config)
                    allChannels.addAll(channels)
                } catch (e: Exception) {
                    android.util.Log.e("SyncManager", "Failed to fetch config ${config.url}", e)
                }
            }
        }

        // 2. If no channels were fetched from networkConfigs, fall back to R.raw.channels (channels.txt) or default url
        if (allChannels.isEmpty()) {
            onProgress(ImportStage("Loading default channels", 0, null))
            var jsonStr = ""
            try {
                val file = java.io.File(context.filesDir, com.thirutricks.tllplayer.legacy.models.TVList.FILE_NAME)
                if (file.exists()) {
                    jsonStr = file.readText()
                } else {
                    jsonStr = context.resources.openRawResource(com.thirutricks.tllplayer.R.raw.channels).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            } catch (e: Exception) {
                android.util.Log.e("SyncManager", "Failed to read default channels file", e)
            }

            if (jsonStr.isNotBlank()) {
                var decrypted = jsonStr.trim()
                val g = io.github.lizongying.Gua()
                if (g.verify(decrypted)) {
                    decrypted = g.decode(decrypted)
                }
                
                val startIndex = decrypted.indexOf('[')
                if (startIndex != -1) {
                    decrypted = decrypted.substring(startIndex)
                    try {
                        val type = object : com.google.gson.reflect.TypeToken<List<TV>>() {}.type
                        val list: List<TV> = com.google.gson.GsonBuilder().setLenient().create().fromJson(decrypted, type)
                        allChannels.addAll(list)
                    } catch (e: Exception) {
                        android.util.Log.e("SyncManager", "Failed to parse decrypted default channels", e)
                    }
                }
            }
        }

        // 3. Import the channels into CategoryDao and ChannelDao
        if (allChannels.isNotEmpty()) {
            onProgress(ImportStage("Importing channels", 0, null))
            importLegacyChannels(s.id, allChannels)
            onProgress(ImportStage("Channels", allChannels.size, null))
        }
    }

    private suspend fun importLegacyChannels(sId: Long, tvList: List<TV>) {
        categoryDao.clear(sId, MediaType.LIVE)
        channelDao.clearSource(sId)

        // Assign unique sequential IDs if the loaded list doesn't have unique non-zero IDs.
        // This prevents different channels from having the default id = 0, which would cause
        // database unique-constraint conflicts and lead to only one channel being loaded.
        val hasUniqueIds = tvList.isNotEmpty() && tvList.map { it.id }.distinct().let { it.size == tvList.size && 0 !in it }
        if (!hasUniqueIds) {
            tvList.forEachIndexed { index, tv ->
                tv.id = index
            }
        }

        val groupToChannels = tvList.groupBy { it.group.ifBlank { "All Channels" } }

        var order = 0
        var catOrder = 0
        groupToChannels.forEach { (groupName, channelsInGroup) ->
            val categoryEntity = CategoryEntity(
                sourceId = sId,
                mediaType = MediaType.LIVE,
                name = groupName,
                remoteId = groupName,
                sortOrder = catOrder++
            )
            val cId = categoryDao.upsertAll(listOf(categoryEntity)).first()

            val channelEntities = channelsInGroup.map { tv ->
                val joinedStreamUrl = tv.uris.joinToString("|||")
                ChannelEntity(
                    sourceId = sId,
                    categoryId = cId,
                    name = tv.title.ifBlank { tv.name },
                    logoUrl = tv.logo.ifBlank { tv.image ?: "" },
                    streamUrl = joinedStreamUrl,
                    epgChannelId = tv.tvgId,
                    number = null,
                    remoteId = tv.id.toString(),
                    sortOrder = order++,
                    catchup = tv.catchup != null,
                    catchupDays = tv.catchupDays?.toIntOrNull() ?: 0,
                    catchupSource = tv.catchupSource
                )
            }
            channelDao.upsertAll(channelEntities)
        }
    }

    companion object {
        const val CHUNK = 500
        private const val CATEGORY_REQUEST_DELAY_MS = 150L // pace per-category fallback requests (avoid HTTP 429)
    }
}
