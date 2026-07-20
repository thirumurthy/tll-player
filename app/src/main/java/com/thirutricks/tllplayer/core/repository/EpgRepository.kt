package com.thirutricks.tllplayer.core.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import com.thirutricks.tllplayer.core.customize.CustomizationStore
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.EpgDao
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.core.database.entity.EpgChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.model.SourceType
import com.thirutricks.tllplayer.core.network.HttpClient
import com.thirutricks.tllplayer.core.parser.XmltvParser
import com.thirutricks.tllplayer.core.parser.XtreamClient

/**
 * Fetches and stores the bulk XMLTV guide for a source (Xtream `xmltv.php`, or a M3U playlist's
 * `url-tvg`). Only programmes overlapping a rolling window are kept, and finished rows are pruned, so
 * the guide stays bounded. The grid reads from [EpgDao]; per-channel now/next still uses Xtream's
 * short-EPG separately.
 */
class EpgRepository(
    private val epgDao: EpgDao,
    private val http: HttpClient,
    private val xtream: XtreamClient,
    private val channelDao: ChannelDao,
    private val customize: CustomizationStore,
    private val settings: SettingsRepository,
    private val context: android.content.Context,
    private val db: com.thirutricks.tllplayer.core.database.OwnTVDatabase,
) {

    /** Where a source's downloaded XMLTV is cached, so a later smart-match can top up programmes from it
     *  without re-hitting the network (see [storeProgrammesForIdsFromCache]). */
    private fun cacheFile(storeId: Long) = java.io.File(context.cacheDir, "epg_$storeId.xmltv")

    /**
     * Ensure the `epg_programmes(sourceId, epgChannelId)` index exists — it turns the Guide's "which channels
     * have guide data" lookup from a multi-second full scan into a fast index scan. The index is **permanent**
     * and **auto-maintained** by SQLite on every future insert, so this builds it exactly once (a few seconds,
     * in the background, the first time) and is an instant no-op every call after. Run at startup (for users
     * who already have EPG) and after each EPG sync (for brand-new EPG).
     */
    suspend fun ensureEpgIndexes() = withContext(Dispatchers.IO) {
        runCatching {
            db.openHelper.writableDatabase.execSQL(
                "CREATE INDEX IF NOT EXISTS index_epg_programmes_sourceId_epgChannelId ON epg_programmes(sourceId, epgChannelId)",
            )
        }
    }

    /**
     * The set of normalised EPG ids the user's channels actually reference: every channel's tvg-id plus
     * any persisted smart-match overrides. Public XMLTV feeds carry thousands of channels the user doesn't
     * have, so the bulk sync filters programmes to this set instead of storing the whole feed. Returns null
     * when we can't determine any ids (no channels / no tvg-ids) — caller then stores everything (never
     * filter the guide down to empty).
     */
    private suspend fun neededEpgIds(): Set<String>? {
        val ids = HashSet<String>()
        runCatching { channelDao.allEpgChannelIds().forEach { ids.add(it) } } // already lower+trim from SQL
        runCatching {
            val pid = settings.activeProfileId.first()
            if (pid >= 0) customize.observe(pid, MediaType.LIVE).first().epgMatches.values
                .forEach { ids.add(it.trim().lowercase()) }
        }
        return ids.ifEmpty { null }
    }
    /** The guide URL for a source, or null if it has no EPG feed. A manual EPG URL always wins. */
    fun guideUrl(source: SourceEntity): String? = when (source.type) {
        SourceType.XTREAM -> source.epgUrl?.takeIf { it.isNotBlank() } ?: xtream.xmltvUrl(source)
        SourceType.M3U, SourceType.TLL -> source.epgUrl?.takeIf { it.isNotBlank() }
        SourceType.STALKER -> source.epgUrl?.takeIf { it.isNotBlank() }
        SourceType.LOCAL_BACKUP -> null
    }

    fun hasGuide(source: SourceEntity): Boolean = guideUrl(source) != null

    /**
     * Refresh one playlist source's guide (used by the one-time migration). Returns programmes stored.
     */
    suspend fun refresh(source: SourceEntity, onProgress: (Int) -> Unit = {}): Int {
        val url = guideUrl(source) ?: return 0
        return refreshUrl(source.id, url, source.userAgent, onProgress)
    }

    /**
     * Download [url] (XMLTV, gzip-aware) into the guide tables keyed by [storeId], keeping only the
     * rolling window. Used for standalone EPG sources (negative ids). Throws on network/parse failure.
     */
    suspend fun refreshUrl(storeId: Long, url: String, userAgent: String?, onProgress: (Int) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val from = now - WINDOW_BACK_MS
        val to = now + WINDOW_AHEAD_MS

        epgDao.clearSource(storeId) // clear-then-insert (no unique key on programmes to REPLACE on)

        // Filter programmes to the channels the user actually has (tvg-ids + persisted matches). Public
        // feeds carry far more channels than the user owns, so this is the single biggest sync speedup —
        // ~10–20× fewer rows. We still store ALL <channel> entries (cheap, needed as smart-match candidates).
        // Null = couldn't determine any ids → don't filter (never reduce the guide to empty).
        val needed = neededEpgIds()

        val channels = LinkedHashMap<String, EpgChannelEntity>()
        val buffer = ArrayList<EpgProgrammeEntity>(CHUNK)
        var stored = 0

        // Stream the feed: parse it as it downloads (no waiting for the whole file) while ALSO teeing the raw
        // bytes into a local cache file. The retained file lets a later smart-match top up just that channel's
        // programmes without another network round-trip — without slowing the live sync.
        val file = cacheFile(storeId)
        try {
        file.outputStream().use { out ->
        http.get(url, userAgent) { input ->
            XmltvParser.parse(
                TeeInputStream(input, out),
                onChannel = { id, name ->
                    // Ids are stored normalized (trim+lowercase) so guide lookups can use the
                    // (epgChannelId, startMs) index directly — XMLTV ids often differ from the
                    // panel's epg_channel_id only in case.
                    val key = id.trim().lowercase()
                    channels.getOrPut(key) { EpgChannelEntity(sourceId = storeId, epgChannelId = key, displayName = name) }
                },
                onProgramme = { channelId, startMs, stopMs, title, desc ->
                    val key = channelId.trim().lowercase()
                    if (stopMs > from && startMs < to && (needed == null || key in needed)) {
                        buffer.add(
                            EpgProgrammeEntity(
                                sourceId = storeId, epgChannelId = key,
                                startMs = startMs, stopMs = stopMs, title = title, description = desc,
                            ),
                        )
                        if (buffer.size >= CHUNK) {
                            runBlocking { epgDao.upsertProgrammes(buffer.toList()) }
                            stored += buffer.size
                            buffer.clear()
                            onProgress(stored)
                        }
                    }
                },
            )
        }
        }
        } catch (e: Exception) {
            // Network drop or an unrecoverable parse position. Keep whatever we already pulled — a partial
            // guide beats none — and only fail outright if we got nothing at all.
            if (stored == 0 && buffer.isEmpty() && channels.isEmpty()) throw e
            android.util.Log.w("EpgRepository", "EPG sync incomplete — keeping partial ($stored programmes)", e)
        }
        if (buffer.isNotEmpty()) { epgDao.upsertProgrammes(buffer.toList()); stored += buffer.size }
        if (channels.isNotEmpty()) epgDao.upsertChannels(channels.values.toList())
        epgDao.prune(now - WINDOW_BACK_MS)
        ensureEpgIndexes() // new/refreshed EPG → make sure the Guide read-index exists (no-op if already there)
        onProgress(stored)
        stored
    }

    /**
     * Top up programmes for newly-matched [epgIds] from the **cached** XMLTV file(s) — no network. Used right
     * after a smart-match so the channels' guide fills in instantly (the bulk sync filters to the channels you
     * have, so a freshly-matched id wasn't stored). Parses each cache file ONCE for the whole id set. Returns
     * false if there's no fresh (≤24h) cache to read — the caller then does a full network re-sync.
     */
    suspend fun storeProgrammesForIdsFromCache(epgIds: Set<String>): Boolean = withContext(Dispatchers.IO) {
        val keys = epgIds.map { it.trim().lowercase() }.filterTo(HashSet()) { it.isNotBlank() }
        if (keys.isEmpty()) return@withContext true
        val now = System.currentTimeMillis()
        val from = now - WINDOW_BACK_MS; val to = now + WINDOW_AHEAD_MS
        val files = context.cacheDir.listFiles { f -> f.name.startsWith("epg_") && f.name.endsWith(".xmltv") }
            ?.filter { it.length() > 0 && now - it.lastModified() <= CACHE_TTL_MS }
            ?: emptyList<java.io.File>()
        if (files.isEmpty()) return@withContext false // no fresh cache → caller falls back to a network re-sync

        keys.forEach { epgDao.clearChannel(it) } // avoid duplicates if matched more than once
        for (file in files) {
            val storeId = file.name.removePrefix("epg_").removeSuffix(".xmltv").toLongOrNull() ?: continue
            val buffer = ArrayList<EpgProgrammeEntity>(512)
            runCatching {
                file.inputStream().use { input ->
                    XmltvParser.parse(
                        input,
                        onChannel = { _, _ -> },
                        onProgramme = { channelId, startMs, stopMs, title, desc ->
                            val key = channelId.trim().lowercase()
                            if (key in keys && stopMs > from && startMs < to) {
                                buffer.add(EpgProgrammeEntity(sourceId = storeId, epgChannelId = key, startMs = startMs, stopMs = stopMs, title = title, description = desc))
                                if (buffer.size >= CHUNK) { runBlocking { epgDao.upsertProgrammes(buffer.toList()) }; buffer.clear() }
                            }
                        },
                    )
                }
            }
            if (buffer.isNotEmpty()) epgDao.upsertProgrammes(buffer)
        }
        true // had fresh cache and processed it (true even if an id wasn't present — re-syncing wouldn't help)
    }

    /** An InputStream that mirrors every byte it reads into [out] — lets us parse a download while caching it. */
    private class TeeInputStream(
        private val src: java.io.InputStream,
        private val out: java.io.OutputStream,
    ) : java.io.InputStream() {
        override fun read(): Int { val b = src.read(); if (b >= 0) out.write(b); return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = src.read(b, off, len); if (n > 0) out.write(b, off, n); return n
        }
        override fun close() = src.close() // the caller owns [out] (closed by its own use{})
    }

    /** Drop a removed EPG source's stored programmes. */
    suspend fun clear(storeId: Long) = withContext(Dispatchers.IO) { epgDao.clearSource(storeId) }

    companion object {
        // Keep up to ~7 days of just-aired programmes so the Guide can browse a long catch-up archive
        // (still bounded, and ultimately limited by how much past data the EPG feed actually provides —
        // many xmltv.php feeds only return 1–2 days of past programmes, so storage rarely reaches 7 days).
        private const val WINDOW_BACK_MS = 7L * 24 * 60 * 60 * 1000
        private const val WINDOW_AHEAD_MS = 48L * 60 * 60 * 1000 // and 48h ahead
        private const val CHUNK = 2_000 // larger batches = far fewer transaction commits during bulk insert
        private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000 // reuse a cached XMLTV for incremental matches up to 24h
    }
}
