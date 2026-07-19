package com.thirutricks.tllplayer.core.sync

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import com.thirutricks.tllplayer.core.customize.CustomizationStore
import com.thirutricks.tllplayer.core.customize.CustomizeKeys
import com.thirutricks.tllplayer.core.database.BulkInsertHelper
import com.thirutricks.tllplayer.core.database.dao.CategoryDao
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.MovieDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.CategoryEntity
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.ContentHashProjection
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.database.entity.computeContentHash
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import kotlin.coroutines.CoroutineContext

/**
 * Shared building blocks for the per-source-type syncers ([XtreamSyncer], [M3uSyncer], later
 * StalkerSyncer): the chunked streaming inserter with cross-pass dedupe, the hash-diffed stable
 * upsert / fresh insert pair, category refresh + stable upsert, and stale-row pruning. Extracted
 * from SyncManager so each syncer stays focused on its own protocol flow while any fix to the
 * shared machinery lands once.
 */
internal class SyncSupport(
    private val categoryDao: CategoryDao,
    channelDao: ChannelDao,
    movieDao: MovieDao,
    seriesDao: SeriesDao,
    private val sourceDao: SourceDao,
    private val customize: CustomizationStore,
    private val settings: SettingsRepository,
) {
    val channelAdapter = ContentAdapter<ChannelEntity>(
        remoteIdOf = { it.remoteId },
        hashOf = { it.computeContentHash() },
        copyWith = { row, id, hash -> if (id != null) row.copy(id = id, contentHash = hash) else row.copy(contentHash = hash) },
        updateAll = { channelDao.updateAll(it) },
        insertAll = { channelDao.insertAll(it) },
        remoteIdsForSource = { channelDao.remoteIdsForSource(it) },
        deleteByRemoteIds = { src, ids -> channelDao.deleteByRemoteIds(src, ids) },
        loadHashes = { channelDao.contentHashesForSource(it) },
    )

    val movieAdapter = ContentAdapter<MovieEntity>(
        remoteIdOf = { it.remoteId },
        hashOf = { it.computeContentHash() },
        copyWith = { row, id, hash -> if (id != null) row.copy(id = id, contentHash = hash) else row.copy(contentHash = hash) },
        updateAll = { movieDao.updateAll(it) },
        insertAll = { movieDao.insertAll(it) },
        remoteIdsForSource = { movieDao.remoteIdsForSource(it) },
        deleteByRemoteIds = { src, ids -> movieDao.deleteByRemoteIds(src, ids) },
        loadHashes = { movieDao.contentHashesForSource(it) },
        countsByCategory = { src -> movieDao.countsByCategoryOnce(src).associate { it.categoryId to it.itemCount } },
        remoteIdsForCategory = { src, cat -> movieDao.remoteIdsForCategory(src, cat) },
    )

    val seriesAdapter = ContentAdapter<SeriesEntity>(
        remoteIdOf = { it.remoteId },
        hashOf = { it.computeContentHash() },
        copyWith = { row, id, hash -> if (id != null) row.copy(id = id, contentHash = hash) else row.copy(contentHash = hash) },
        updateAll = { seriesDao.updateSeries(it) },
        insertAll = { seriesDao.insertSeries(it) },
        remoteIdsForSource = { seriesDao.remoteIdsForSource(it) },
        deleteByRemoteIds = { src, ids -> seriesDao.deleteByRemoteIds(src, ids) },
        loadHashes = { seriesDao.contentHashesForSource(it) },
        countsByCategory = { src -> seriesDao.countsByCategoryOnce(src).associate { it.categoryId to it.itemCount } },
        remoteIdsForCategory = { src, cat -> seriesDao.remoteIdsForCategory(src, cat) },
    )

    /** Hash-diffed stable upsert: unchanged rows are skipped, changed rows keep their local id. */
    suspend fun <T> upsertStable(
        rows: List<T>,
        hashDeferred: Deferred<Map<String, Pair<Long, Int>>>,
        adapter: ContentAdapter<T>,
    ): UpsertStats {
        val hashMap = hashDeferred.await()
        val inserts = ArrayList<T>()
        val updates = ArrayList<T>()
        var skipped = 0
        rows.forEach { row ->
            val existing = adapter.remoteIdOf(row)?.let { hashMap[it] }
            val hash = adapter.hashOf(row)
            when {
                existing == null -> inserts.add(adapter.copyWith(row, null, hash))
                hash != existing.second -> updates.add(adapter.copyWith(row, existing.first, hash))
                else -> skipped++
            }
        }
        if (updates.isNotEmpty()) adapter.updateAll(updates)
        if (inserts.isNotEmpty()) adapter.insertAll(inserts)
        return UpsertStats(inserted = inserts.size, updated = updates.size, skippedUnchanged = skipped)
    }

    /** First-ever import: no diffing, just hash + insert. */
    suspend fun <T> insertFresh(rows: List<T>, adapter: ContentAdapter<T>): UpsertStats {
        val hashed = rows.map { adapter.copyWith(it, null, adapter.hashOf(it)) }
        adapter.insertAll(hashed)
        return UpsertStats(inserted = hashed.size)
    }

    private fun List<ContentHashProjection>.toHashLookup(): Map<String, Pair<Long, Int>> =
        associateBy({ it.remoteId }, { it.id to it.contentHash })

    fun asyncHashLoad(
        scope: CoroutineScope,
        label: String,
        sourceId: Long,
        load: suspend () -> List<ContentHashProjection>,
    ): Deferred<Map<String, Pair<Long, Int>>> = scope.async {
        val start = SystemClock.elapsedRealtime()
        load().toHashLookup().also {
            Log.d(TAG, "$label hash map loaded sourceId=$sourceId size=${it.size} ms=${SystemClock.elapsedRealtime() - start}")
        }
    }

    suspend fun pruneCategories(sourceId: Long, type: MediaType, seenRemoteIds: Set<String>, label: String, stats: SyncStatsCollector) {
        val start = SystemClock.elapsedRealtime()
        val stale = categoryDao.remoteIdsForSource(sourceId, type).filterNot(seenRemoteIds::contains)
        stale.chunked(QUERY_CHUNK).forEach { categoryDao.deleteByRemoteIds(sourceId, type, it) }
        if (stale.isNotEmpty()) stats.processedCounts.merge(CATEGORIES_REMOVED_KEY, stale.size, Int::plus)
        Log.i(TAG, "$label category prune sourceId=$sourceId type=$type stale=${stale.size} ms=${SystemClock.elapsedRealtime() - start}")
    }

    suspend fun pruneRemoteIds(
        label: String,
        sourceId: Long,
        seenRemoteIds: Set<String>,
        loadExisting: suspend (Long) -> List<String>,
        deleteRemoteIds: suspend (Long, List<String>) -> Unit,
    ) {
        val start = SystemClock.elapsedRealtime()
        val stale = loadExisting(sourceId).filterNot(seenRemoteIds::contains)
        stale.chunked(QUERY_CHUNK).forEach { deleteRemoteIds(sourceId, it) }
        Log.i(TAG, "$label content prune sourceId=$sourceId stale=${stale.size} ms=${SystemClock.elapsedRealtime() - start}")
    }

    suspend fun refreshCategories(
        s: SourceEntity,
        type: MediaType,
        parsed: List<tv.own.owntv.core.parser.XtCategory>,
        stats: SyncStatsCollector,
    ): CategoryRefresh {
        val start = SystemClock.elapsedRealtime()
        // s.lastSyncAt never changes during a sync run (SyncManager stamps it only after the syncer
        // returns), so this is safe to derive here rather than threading a freshSource param through
        // every call site.
        val freshSource = s.lastSyncAt == null
        Log.d(TAG, "refreshCategories start sourceId=${s.id} type=$type count=${parsed.size}")
        val uniqueCategories = parsed.distinctBy { it.id }
        val existing = existingCategoriesByRemoteId(s.id, type, uniqueCategories.map { it.id })
        // sortOrder = provider index, so the rail follows the provider's category order.
        val entities = uniqueCategories.mapIndexed { i, c ->
            CategoryEntity(
                id = existing[c.id]?.id ?: 0,
                sourceId = s.id,
                mediaType = type,
                name = c.name,
                remoteId = c.id,
                sortOrder = i,
            )
        }
        val upsertStart = SystemClock.elapsedRealtime()
        val upsert = upsertCategoriesStable(s.id, type, entities, existing)
        Log.d(
            TAG,
            "refreshCategories upsert sourceId=${s.id} type=$type rows=${entities.size} " +
                "dbInserted=${upsert.stats.inserted} dbUpdated=${upsert.stats.updated} " +
                "dbSkipped=${upsert.stats.skippedUnchanged} ms=${SystemClock.elapsedRealtime() - upsertStart}",
        )
        // Everything is technically "new" on a fresh source's first sync — neither the hide-by-default
        // preference nor the sync summary is meaningful there, only on a genuine resync.
        if (!freshSource && upsert.newRows.isNotEmpty()) {
            stats.processedCounts.merge(CATEGORIES_ADDED_KEY, upsert.newRows.size, Int::plus)
            applyHideNewCategoriesDefault(s.id, type, upsert.newRows)
        }
        // C5: ids come straight from the upsert (existing rows + returned insert rowids) — the old
        // second existingCategoriesByRemoteId round-trip only re-fetched just-upserted rows.
        return CategoryRefresh(idsByRemoteId = upsert.idsByRemoteId, seenRemoteIds = uniqueCategories.mapTo(HashSet()) { it.id }).also {
            Log.d(TAG, "refreshCategories end sourceId=${s.id} type=$type mapped=${it.idsByRemoteId.size} totalMs=${SystemClock.elapsedRealtime() - start}")
        }
    }

    private suspend fun existingCategoriesByRemoteId(sourceId: Long, type: MediaType, remoteIds: List<String>): Map<String, CategoryEntity> =
        remoteIds.distinct().chunked(QUERY_CHUNK).flatMap { categoryDao.findByRemoteIds(sourceId, type, it) }
            .mapNotNull { category -> category.remoteId?.let { it to category } }
            .toMap()

    private class CategoryUpsert(val stats: UpsertStats, val idsByRemoteId: Map<String, Long>, val newRows: List<CategoryEntity>)

    private suspend fun upsertCategoriesStable(
        sourceId: Long,
        type: MediaType,
        rows: List<CategoryEntity>,
        existingByRemoteId: Map<String, CategoryEntity>,
    ): CategoryUpsert {
        val inserts = ArrayList<CategoryEntity>()
        val updates = ArrayList<CategoryEntity>()
        var skipped = 0
        val ids = HashMap<String, Long>()
        rows.forEach { row ->
            val current = row.remoteId?.let(existingByRemoteId::get)
            when {
                current == null -> inserts.add(row)
                row != current -> { updates.add(row); ids[row.remoteId] = current.id }
                else -> { skipped++; ids[row.remoteId] = current.id }
            }
        }
        if (updates.isNotEmpty()) categoryDao.updateAll(updates)
        if (inserts.isNotEmpty()) {
            val rowIds = categoryDao.insertAll(inserts)
            val missed = ArrayList<String>()
            inserts.forEachIndexed { i, row ->
                val rid = row.remoteId ?: return@forEachIndexed
                val id = rowIds.getOrNull(i) ?: -1L
                if (id > 0) ids[rid] = id else missed.add(rid)
            }
            // IGNOREd conflicts return −1 (shouldn't happen — inserts were pre-checked by remoteId);
            // heal by re-fetching just those rows rather than everything.
            if (missed.isNotEmpty()) {
                existingCategoriesByRemoteId(sourceId, type, missed).forEach { (rid, cat) -> ids[rid] = cat.id }
            }
        }
        return CategoryUpsert(
            stats = UpsertStats(inserted = inserts.size, updated = updates.size, skippedUnchanged = skipped),
            idsByRemoteId = ids,
            newRows = inserts,
        )
    }

    /** Applies each profile's own "hide new categories" preference (same across Live/Movies/Series) to
     *  categories just discovered on a resync — a source can be shared by several profiles. */
    private suspend fun applyHideNewCategoriesDefault(sourceId: Long, type: MediaType, newRows: List<CategoryEntity>) {
        val profileIds = sourceDao.profileIdsForSource(sourceId)
        if (profileIds.isEmpty()) return
        val keys = newRows.map { CustomizeKeys.category(it) }
        profileIds.forEach { profileId ->
            if (settings.hideNewCategoriesDefault(profileId).first()) {
                customize.setCategoriesHidden(profileId, type, keys, hidden = true)
            }
        }
    }

    /**
     * Drives a push-stream [producer] that feeds items into [add]; flushes to the DB via [insert] in
     * chunks of [BulkInsertHelper.CHUNK], reporting progress. Inserts are awaited to provide sequential back-pressure,
     * and cancellation is checked each chunk.
     */
    suspend fun <T, R> chunked(
        ctx: CoroutineContext,
        phase: SyncPhase,
        label: String,
        progress: SyncCounters,
        insert: suspend (List<T>) -> UpsertStats,
        total: IntArray, // shared [0] running unique count for the whole media type, so progress never resets
        seenKeys: MutableSet<String>? = null,
        uniqueKey: ((T) -> String?)? = null,
        chunkSize: Int = BulkInsertHelper.CHUNK,
        producer: suspend (add: suspend (T) -> Unit) -> R,
    ): R {
        val buffer = ArrayList<T>(chunkSize)
        var chunkIndex = 0
        var skippedDuplicates = 0
        val chunkRunStart = SystemClock.elapsedRealtime()
        suspend fun flush() {
            if (buffer.isEmpty()) return
            ctx.ensureActive()
            chunkIndex++
            val rawCount = buffer.size
            val flushStart = SystemClock.elapsedRealtime()
            val pendingKeys = ArrayList<String>()
            val rows = buffer.toList().filterNewItems(seenKeys, uniqueKey, pendingKeys)
            val filterMs = SystemClock.elapsedRealtime() - flushStart
            buffer.clear()
            val skipped = rawCount - rows.size
            skippedDuplicates += skipped
            if (rows.isEmpty()) {
                Log.d(
                    TAG,
                    "$label chunk skipped phase=${phase.label} chunk=$chunkIndex raw=$rawCount skipped=$skipped " +
                        "totalSkipped=$skippedDuplicates totalUnique=${total[0]} filterMs=$filterMs elapsedMs=${SystemClock.elapsedRealtime() - chunkRunStart}",
                )
                return
            }
            val insertStart = SystemClock.elapsedRealtime()
            val upsertStats = insert(rows)
            val insertMs = SystemClock.elapsedRealtime() - insertStart
            seenKeys?.addAll(pendingKeys)
            total[0] += rows.size
            if (shouldLogChunk(chunkIndex, insertMs, skipped)) {
                Log.d(
                    TAG,
                    "$label chunk applied phase=${phase.label} chunk=$chunkIndex raw=$rawCount accepted=${rows.size} " +
                        "dbInserted=${upsertStats.inserted} dbUpdated=${upsertStats.updated} dbSkipped=${upsertStats.skippedUnchanged} " +
                        "dedupeSkipped=$skipped totalDedupeSkipped=$skippedDuplicates totalUnique=${total[0]} " +
                        "filterMs=$filterMs applyMs=$insertMs elapsedMs=${SystemClock.elapsedRealtime() - chunkRunStart}",
                )
            }
            progress.update(phase, total[0])
        }
        val result = producer { item ->
            buffer.add(item)
            if (buffer.size >= chunkSize) flush()
        }
        flush()
        Log.i(
            TAG,
            "$label stream done phase=${phase.label} chunks=$chunkIndex totalUnique=${total[0]} " +
                "skippedDuplicates=$skippedDuplicates elapsedMs=${SystemClock.elapsedRealtime() - chunkRunStart}",
        )
        return result
    }

    private fun shouldLogChunk(chunkIndex: Int, insertMs: Long, skipped: Int): Boolean =
        chunkIndex <= 3 || chunkIndex % 20 == 0 || insertMs >= SLOW_INSERT_LOG_MS || skipped > 0

    private fun <T> List<T>.filterNewItems(
        seenKeys: MutableSet<String>?,
        uniqueKey: ((T) -> String?)?,
        pendingKeys: MutableList<String>,
    ): List<T> {
        if (seenKeys == null || uniqueKey == null) return this
        val rows = ArrayList<T>(size)
        val batchKeys = HashSet<String>()
        forEach { item ->
            val key = uniqueKey(item)
            if (key == null) {
                rows.add(item)
            } else if (!seenKeys.contains(key) && batchKeys.add(key)) {
                pendingKeys.add(key)
                rows.add(item)
            }
        }
        return rows
    }

    companion object {
        /** Shared log tag — kept as "SyncManager" across the split so existing logcat filters still work. */
        const val TAG = "SyncManager"
        const val QUERY_CHUNK = 500
        const val CATEGORY_REQUEST_DELAY_MS = 150L // pace per-category fallback requests (avoid HTTP 429)
        private const val SLOW_INSERT_LOG_MS = 250L
        val IgnoreByteProgress: (Long, Long?) -> Unit = { _, _ -> }

        /** [SyncStatsCollector.processedCounts] keys aggregating category changes across all phases,
         *  for the sync-complete summary — not shown at all when a fresh source makes every category "new". */
        const val CATEGORIES_ADDED_KEY = "categoriesAdded"
        const val CATEGORIES_REMOVED_KEY = "categoriesRemoved"
    }
}

/**
 * Per-entity DAO/mapping lambdas so ONE [SyncSupport.upsertStable]/[SyncSupport.insertFresh]/prune
 * implementation serves channels, movies and series (C5) — any fix to the hash-diff/prune logic now
 * lands once.
 */
internal class ContentAdapter<T>(
    val remoteIdOf: (T) -> String?,
    val hashOf: (T) -> Int,
    /** Copy with contentHash set; a non-null [id] rekeys the row to the existing local row. */
    val copyWith: (row: T, id: Long?, hash: Int) -> T,
    val updateAll: suspend (List<T>) -> Unit,
    val insertAll: suspend (List<T>) -> Unit,
    val remoteIdsForSource: suspend (Long) -> List<String>,
    val deleteByRemoteIds: suspend (Long, List<String>) -> Unit,
    val loadHashes: suspend (Long) -> List<ContentHashProjection>,
    /** Re-sync delta check (paged catalogs): current per-category item counts; null = not supported. */
    val countsByCategory: (suspend (sourceId: Long) -> Map<Long, Int>)? = null,
    /** RemoteIds of one category's existing rows — protects a delta-skipped category from pruning. */
    val remoteIdsForCategory: (suspend (sourceId: Long, categoryId: Long) -> List<String>)? = null,
)

internal data class UpsertStats(
    val inserted: Int = 0,
    val updated: Int = 0,
    val skippedUnchanged: Int = 0,
)

internal data class CategoryRefresh(
    val idsByRemoteId: Map<String, Long>,
    val seenRemoteIds: Set<String>,
)

internal class SyncCounters(
    contentTypes: SyncContentTypes,
    private val onProgress: (ImportStage) -> Unit,
) {
    private val lock = Any()
    private val liveActive = contentTypes.live
    private val moviesActive = contentTypes.movies
    private val seriesActive = contentTypes.series
    private var liveProcessed = 0
    private var moviesProcessed = 0
    private var seriesProcessed = 0

    fun update(phase: SyncPhase, count: Int): ImportStage {
        val snapshot = synchronized(lock) {
            when (phase) {
                SyncPhase.LIVE -> liveProcessed = count
                SyncPhase.MOVIES -> moviesProcessed = count
                SyncPhase.SERIES -> seriesProcessed = count
            }
            snapshotLocked()
        }
        onProgress(snapshot)
        return snapshot
    }

    fun completeAll(): ImportStage {
        val snapshot = synchronized(lock) { snapshotLocked() }
        onProgress(snapshot)
        return snapshot
    }

    private fun snapshotLocked() = ImportStage(
        liveProcessed = liveProcessed,
        moviesProcessed = moviesProcessed,
        seriesProcessed = seriesProcessed,
        liveActive = liveActive,
        moviesActive = moviesActive,
        seriesActive = seriesActive,
    )
}

internal class SyncStatsCollector(val sourceId: Long) {
    val startedAt = System.currentTimeMillis()
    val phaseTiming = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val processedCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    val phaseErrors = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile var usedFallback = false

    fun warnings() = phaseErrors.map { (phase, message) -> SyncWarning(phase, message) }

    fun build(result: SyncResult) = SyncRunStats(
        sourceId = sourceId,
        startedAt = startedAt,
        finishedAt = System.currentTimeMillis(),
        result = result,
        phaseTiming = phaseTiming.toMap(),
        processedCounts = processedCounts.toMap(),
        phaseErrors = phaseErrors.toMap(),
        usedFallback = usedFallback,
    )
}
