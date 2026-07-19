package com.thirutricks.tllplayer.core.sync

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import com.thirutricks.tllplayer.core.database.BulkInsertHelper
import com.thirutricks.tllplayer.core.database.dao.CategoryDao
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.MovieDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.CategoryEntity
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpisodeEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.SeasonEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.core.network.HttpClient
import com.thirutricks.tllplayer.core.parser.M3uParser

/**
 * The M3U import flow (split out of SyncManager, Phase 0 of the Stalker plan). M3U uses
 * clear-then-insert because playlists do not provide stable item ids — but the clears are deferred
 * per type, so a failed download never leaves the source empty and a live-only playlist never
 * touches previously-imported VOD rows (and vice versa).
 */
internal class M3uSyncer(
    private val context: android.content.Context,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val m3u: M3uParser,
    private val http: HttpClient,
    private val bulkInsertHelper: BulkInsertHelper,
) {
    suspend fun sync(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector) {
        val channelsStart = System.currentTimeMillis()
        val elapsedStart = SystemClock.elapsedRealtime()
        val ctx = currentCoroutineContext()
        val freshSource = s.lastSyncAt == null
        val chunkSize = if (freshSource) BulkInsertHelper.CHUNK_FRESH else BulkInsertHelper.CHUNK
        val reportBytes = SyncSupport.IgnoreByteProgress
        // A locally-picked playlist file (in-app StorageBrowser gives an absolute path; also tolerate
        // file://content:// URIs) is read straight from the device; a normal URL is downloaded. Same parser.
        val isLocal = s.url.startsWith("/") || s.url.startsWith("file://") || s.url.startsWith("content://")
        val localPlaylist = if (isLocal) openLocalPlaylist(s.url) else null
        Log.i(TAG, "M3U phase start sourceId=${s.id} local=$isLocal bytesTotal=${localPlaylist?.second ?: -1}")
        progress.update(SyncPhase.LIVE, 0)

        var processed = 0
        var moviesProcessed = 0
        var seriesProcessed = 0
        val header = bulkInsertHelper.withOptimizedBulkInsert(
            "channels",
            "channels_fts",
            eligible = freshSource,
            ftsOnly = true,
        ) {
            // Deferred per-type clears — only wipe a type's old data once the first real row of that
            // type is about to be written, so a failed download never leaves the source empty and a
            // live-only playlist never touches previously-imported VOD rows (and vice versa).
            var channelsCleared = false
            var moviesCleared = false
            suspend fun ensureChannelsCleared() {
                if (channelsCleared) return
                channelsCleared = true
                val start = SystemClock.elapsedRealtime()
                channelDao.clearSource(s.id)
                categoryDao.clear(s.id, MediaType.LIVE)
                Log.d(TAG, "M3U clear channels+categories sourceId=${s.id} ms=${SystemClock.elapsedRealtime() - start}")
            }
            suspend fun ensureMoviesCleared() {
                if (moviesCleared) return
                moviesCleared = true
                val start = SystemClock.elapsedRealtime()
                movieDao.clearSource(s.id)
                categoryDao.clear(s.id, MediaType.MOVIE)
                Log.d(TAG, "M3U clear movies+categories sourceId=${s.id} ms=${SystemClock.elapsedRealtime() - start}")
            }
            var seriesCleared = false
            suspend fun ensureSeriesCleared() {
                if (seriesCleared) return
                seriesCleared = true
                val start = SystemClock.elapsedRealtime()
                seriesDao.clearSource(s.id) // seasons/episodes cascade
                categoryDao.clear(s.id, MediaType.SERIES)
                Log.d(TAG, "M3U clear series+categories sourceId=${s.id} ms=${SystemClock.elapsedRealtime() - start}")
            }

            // Categories are per-mediaType: the same group-title can exist for both live and VOD.
            val groupToCategoryId = HashMap<Pair<MediaType, String>, Long>()
            val pendingCategoryKeys = LinkedHashSet<Pair<MediaType, String>>()
            val pendingCategories = ArrayList<CategoryEntity>(chunkSize)
            val buffer = ArrayList<PendingM3uChannel>(chunkSize)
            val movieBuffer = ArrayList<PendingM3uChannel>(chunkSize)
            var order = 0 // playlist position — lets "Playlist order" sorting replay the file's order
            var categoryOrder = 0

            fun queueCategory(type: MediaType, group: String) {
                val key = type to group
                if (groupToCategoryId.containsKey(key) || !pendingCategoryKeys.add(key)) return
                pendingCategories.add(
                    CategoryEntity(
                        sourceId = s.id,
                        mediaType = type,
                        name = group,
                        remoteId = group,
                        sortOrder = categoryOrder++,
                    ),
                )
            }

            suspend fun flushCategories() {
                if (pendingCategories.isEmpty()) return
                ctx.ensureActive()
                // A type's deferred clear MUST run before that type's categories are inserted — a
                // later ensure*Cleared() would delete just-written category rows and leave content
                // pointing at dead ids (FOREIGN KEY constraint failed on the content insert).
                if (pendingCategories.any { it.mediaType == MediaType.LIVE }) ensureChannelsCleared()
                if (pendingCategories.any { it.mediaType == MediaType.MOVIE }) ensureMoviesCleared()
                if (pendingCategories.any { it.mediaType == MediaType.SERIES }) ensureSeriesCleared()
                val keys = pendingCategoryKeys.toList()
                val categories = pendingCategories.toList()
                val start = SystemClock.elapsedRealtime()
                val ids = categoryDao.upsertAll(categories)
                keys.forEachIndexed { index, key ->
                    ids.getOrNull(index)?.let { groupToCategoryId[key] = it }
                }
                Log.d(TAG, "M3U categories flush sourceId=${s.id} rows=${categories.size} mapped=${keys.size} ms=${SystemClock.elapsedRealtime() - start}")
                pendingCategoryKeys.clear()
                pendingCategories.clear()
            }

            suspend fun flushChannels() {
                if (buffer.isEmpty()) return
                ensureChannelsCleared()
                flushCategories()
                ctx.ensureActive()
                val channels = buffer.map { item ->
                    val entry = item.entry
                    ChannelEntity(
                        sourceId = s.id,
                        categoryId = entry.groupTitle?.let { groupToCategoryId[MediaType.LIVE to it] },
                        name = entry.name,
                        logoUrl = entry.logo,
                        streamUrl = entry.streamUrl,
                        epgChannelId = entry.tvgId,
                        number = entry.tvgChno,
                        remoteId = null, // M3U has no stable id; rely on clear-then-insert
                        sortOrder = item.order,
                        catchup = entry.catchup != null,
                        catchupDays = entry.catchupDays ?: 0,
                        catchupSource = entry.catchupSource,
                    )
                }
                val start = SystemClock.elapsedRealtime()
                channelDao.upsertAll(channels)
                processed += channels.size
                Log.d(TAG, "M3U channel flush sourceId=${s.id} rows=${channels.size} processed=$processed ms=${SystemClock.elapsedRealtime() - start}")
                buffer.clear()
                progress.update(SyncPhase.LIVE, processed)
            }

            suspend fun flushMovies() {
                if (movieBuffer.isEmpty()) return
                ensureMoviesCleared()
                flushCategories()
                ctx.ensureActive()
                val movies = movieBuffer.map { item ->
                    val entry = item.entry
                    MovieEntity(
                        sourceId = s.id,
                        categoryId = entry.groupTitle?.let { groupToCategoryId[MediaType.MOVIE to it] },
                        name = entry.name,
                        posterUrl = entry.logo,
                        streamUrl = entry.streamUrl,
                        remoteId = null, // M3U has no stable id; rely on clear-then-insert
                        sortOrder = item.order,
                    )
                }
                val start = SystemClock.elapsedRealtime()
                movieDao.upsertAll(movies)
                moviesProcessed += movies.size
                Log.d(TAG, "M3U movie flush sourceId=${s.id} rows=${movies.size} processed=$moviesProcessed ms=${SystemClock.elapsedRealtime() - start}")
                movieBuffer.clear()
                progress.update(SyncPhase.MOVIES, moviesProcessed)
            }

            // Series-tagged entries are per-EPISODE lines ("Show S01E05"); they're grouped by show
            // name into series → seasons → episodes and written once at the end of the parse (series
            // playlists are small — hundreds to a few thousand lines — so buffering them is cheap).
            val seriesAccumulator = LinkedHashMap<String, M3uShowAccumulator>()

            suspend fun flushSeries() {
                if (seriesAccumulator.isEmpty()) return
                ensureSeriesCleared()
                flushCategories()
                ctx.ensureActive()
                val start = SystemClock.elapsedRealtime()
                val shows = seriesAccumulator.values.toList()
                val seriesIds = seriesDao.upsertSeriesReturnIds(
                    shows.map { show ->
                        SeriesEntity(
                            sourceId = s.id,
                            categoryId = show.group?.let { groupToCategoryId[MediaType.SERIES to it] },
                            name = show.name,
                            posterUrl = show.logo,
                            remoteId = null, // M3U has no stable id; rely on clear-then-insert
                            sortOrder = show.order,
                        )
                    },
                )
                var episodesWritten = 0
                shows.forEachIndexed { index, show ->
                    val seriesId = seriesIds.getOrNull(index) ?: return@forEachIndexed
                    val seasonNumbers = show.episodes.map { it.season }.distinct().sorted()
                    val seasonIds = seriesDao.upsertSeasonsReturnIds(
                        seasonNumbers.map { n -> SeasonEntity(seriesId = seriesId, seasonNumber = n, name = "Season $n") },
                    )
                    val seasonIdByNumber = seasonNumbers.zip(seasonIds).toMap()
                    seriesDao.upsertEpisodes(
                        show.episodes.map { ep ->
                            EpisodeEntity(
                                seriesId = seriesId,
                                seasonId = seasonIdByNumber[ep.season],
                                seasonNumber = ep.season,
                                episodeNumber = ep.episode,
                                name = ep.title,
                                streamUrl = ep.streamUrl,
                            )
                        },
                    )
                    episodesWritten += show.episodes.size
                }
                seriesProcessed = shows.size
                Log.d(TAG, "M3U series flush sourceId=${s.id} shows=${shows.size} episodes=$episodesWritten ms=${SystemClock.elapsedRealtime() - start}")
                seriesAccumulator.clear()
                progress.update(SyncPhase.SERIES, seriesProcessed)
            }

            val onEntry: suspend (tv.own.owntv.core.parser.M3uEntry) -> Unit = { e ->
                when {
                    // type="series" / tvg-type="series" → grouped into the Series tab.
                    e.isSeries -> {
                        e.groupTitle?.let { queueCategory(MediaType.SERIES, it) }
                        val parsed = parseM3uEpisode(e.name)
                        val show = seriesAccumulator.getOrPut(parsed.show.lowercase()) {
                            M3uShowAccumulator(name = parsed.show, logo = e.logo, group = e.groupTitle, order = order++)
                        }
                        val episode = if (parsed.episode > 0) parsed.episode else show.episodes.count { it.season == parsed.season } + 1
                        show.episodes.add(
                            M3uEpisodeRow(
                                season = parsed.season,
                                episode = episode,
                                title = parsed.title ?: "Episode $episode",
                                streamUrl = e.streamUrl,
                            ),
                        )
                    }
                    // Other VOD tags (type="vod"/"movie", tvg-type="vod"/"movie") → the movie grid.
                    e.isVod -> {
                        e.groupTitle?.let { queueCategory(MediaType.MOVIE, it) }
                        movieBuffer.add(PendingM3uChannel(order = order++, entry = e))
                        if (movieBuffer.size >= chunkSize) {
                            flushMovies()
                        }
                    }
                    else -> {
                        e.groupTitle?.let { queueCategory(MediaType.LIVE, it) }
                        buffer.add(PendingM3uChannel(order = order++, entry = e))
                        if (buffer.size >= chunkSize) {
                            flushChannels()
                        }
                    }
                }
            }
            val header = if (isLocal) {
                localPlaylist!!.first.use { input -> m3u.parse(input, onEntry) }
            } else {
                http.get(s.url, s.userAgent, reportBytes) { input -> m3u.parse(input, onEntry) }
            }
            if (buffer.isNotEmpty()) {
                flushChannels()
            }
            if (movieBuffer.isNotEmpty()) {
                flushMovies()
            }
            flushSeries()
            header
        }
        // Persist the playlist's EPG url (url-tvg) for the EPG engine if the source didn't have one.
        if (!header.urlTvg.isNullOrBlank() && s.epgUrl.isNullOrBlank()) {
            sourceDao.update(s.copy(epgUrl = header.urlTvg))
        }
        progress.update(SyncPhase.LIVE, processed)
        if (moviesProcessed > 0) progress.update(SyncPhase.MOVIES, moviesProcessed)
        if (seriesProcessed > 0) progress.update(SyncPhase.SERIES, seriesProcessed)
        stats.phaseTiming["channels"] = System.currentTimeMillis() - channelsStart
        stats.processedCounts["channels"] = processed
        if (moviesProcessed > 0) stats.processedCounts["movies"] = moviesProcessed
        if (seriesProcessed > 0) stats.processedCounts["series"] = seriesProcessed
        Log.i(TAG, "M3U phase end sourceId=${s.id} processed=$processed movies=$moviesProcessed series=$seriesProcessed ms=${SystemClock.elapsedRealtime() - elapsedStart}")
    }

    private data class PendingM3uChannel(
        val order: Int,
        val entry: tv.own.owntv.core.parser.M3uEntry,
    )

    /** One M3U series-tagged show being accumulated during a playlist parse. */
    private class M3uShowAccumulator(
        val name: String,
        val logo: String?,
        val group: String?,
        val order: Int,
    ) {
        val episodes = ArrayList<M3uEpisodeRow>()
    }

    private data class M3uEpisodeRow(val season: Int, val episode: Int, val title: String, val streamUrl: String)

    private data class ParsedM3uEpisode(val show: String, val season: Int, val episode: Int, val title: String?)

    /**
     * Splits an M3U series entry title like "Stranger Things S01E05" / "Show 2x03 - Pilot" into
     * show + season + episode (+ optional episode title). Entries without a recognizable pattern
     * become season 1 with sequential episode numbers ("Tales From The Crypt (1989-90s)").
     */
    private fun parseM3uEpisode(rawName: String): ParsedM3uEpisode {
        val name = rawName.trim()
        M3U_EPISODE_SXXEYY.find(name)?.let { m ->
            val show = name.substring(0, m.range.first).trim(' ', '-', '.', '_', ':')
            val title = name.substring(m.range.last + 1).trim(' ', '-', '.', '_', ':').takeIf { it.isNotEmpty() }
            if (show.isNotEmpty()) {
                return ParsedM3uEpisode(show, m.groupValues[1].toInt(), m.groupValues[2].toInt(), title)
            }
        }
        M3U_EPISODE_NXN.find(name)?.let { m ->
            val show = name.substring(0, m.range.first).trim(' ', '-', '.', '_', ':')
            val title = name.substring(m.range.last + 1).trim(' ', '-', '.', '_', ':').takeIf { it.isNotEmpty() }
            if (show.isNotEmpty()) {
                return ParsedM3uEpisode(show, m.groupValues[1].toInt(), m.groupValues[2].toInt(), title)
            }
        }
        return ParsedM3uEpisode(show = name, season = 1, episode = 0, title = null) // episode 0 → sequential
    }

    private fun openLocalPlaylist(url: String): Pair<InputStream, Long?> = when {
        url.startsWith("/") -> {
            val file = File(url)
            file.inputStream() to file.length().takeIf { it >= 0 }
        }
        url.startsWith("file://") -> {
            val uri = Uri.parse(url)
            val file = File(uri.path ?: throw java.io.IOException("Couldn't open the playlist file. Re-pick it (it may have moved.)"))
            file.inputStream() to file.length().takeIf { it >= 0 }
        }
        url.startsWith("content://") -> {
            val uri = Uri.parse(url)
            val totalBytes = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    afd.length.takeIf { it >= 0 }
                }
            }.getOrNull()
            val input = context.contentResolver.openInputStream(uri)
                ?: throw java.io.IOException("Couldn't open the playlist file. Re-pick it (it may have moved.)")
            input to totalBytes
        }
        else -> throw java.io.IOException("Unsupported local playlist path")
    }

    companion object {
        private const val TAG = SyncSupport.TAG

        /** "S01E05" / "s1 e5" — the common episode marker in M3U series playlists. */
        private val M3U_EPISODE_SXXEYY = Regex("""(?i)\bS(\d{1,2})\s*[.\-_ ]?\s*E(\d{1,3})\b""")

        /** "1x05" alternative marker. */
        private val M3U_EPISODE_NXN = Regex("""(?i)\b(\d{1,2})x(\d{1,3})\b""")
    }
}
