package com.thirutricks.tllplayer.core.sync.work

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.launcher.LauncherIntegrationRepository
import com.thirutricks.tllplayer.core.model.SourceType
import com.thirutricks.tllplayer.core.network.ConnectivityObserver
import com.thirutricks.tllplayer.core.util.isTransientSyncError
import com.thirutricks.tllplayer.core.repository.SourceRepository
import com.thirutricks.tllplayer.core.sync.ImportFinalizer
import com.thirutricks.tllplayer.core.sync.ImportStage
import com.thirutricks.tllplayer.core.sync.SyncContentTypes
import com.thirutricks.tllplayer.core.sync.SyncResult

class CatalogSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val sourceRepository: SourceRepository,
    private val sourceDao: SourceDao,
    private val importFinalizer: ImportFinalizer,
    private val catalogSyncScheduler: CatalogSyncScheduler,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val connectivity: ConnectivityObserver,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, -1L)
        val reason = inputData.getString(KEY_REASON) ?: "unknown"
        val baseItemCount = inputData.getInt(KEY_BASE_ITEM_COUNT, 0)
        if (sourceId < 0) return Result.failure()

        val contentTypes = SyncContentTypes(
            live = inputData.getBoolean(KEY_LIVE, true),
            movies = inputData.getBoolean(KEY_MOVIES, true),
            series = inputData.getBoolean(KEY_SERIES, true),
        )
        // Set when this run is the background remainder of a staged (priority) initial sync: the
        // foreground pass plus this one cover all content types, so together they count as a full
        // sync and the source must get its lastSyncAt — otherwise every later sync would take the
        // fresh-import fast path forever (row-id churn, no stale-row pruning).
        val completesInitialSync = inputData.getBoolean(KEY_COMPLETES_INITIAL_SYNC, false)

        val source = sourceRepository.getById(sourceId) ?: run {
            Log.w(TAG, "Source $sourceId not found — skipping ($reason)")
            return Result.failure()
        }

        Log.i(TAG, "Starting sync for source ${source.id} (${source.name}) reason=$reason contentTypes=$contentTypes baseItemCount=$baseItemCount")
        val trackedContentTypes = when (source.type) {
            SourceType.XTREAM -> contentTypes
            SourceType.M3U, SourceType.LOCAL_BACKUP -> SyncContentTypes(live = true, movies = false, series = false)
            // Stalker (Phase F): full catalog like Xtream — live + VOD + series all sync (C-1/D-1),
            // and re-syncs are hash-diffed upserts (StalkerSyncer), so auto refresh is non-destructive.
            SourceType.STALKER -> contentTypes
        }
        val progressPublisher = ProgressPublisher(trackedContentTypes, baseItemCount)
        progressPublisher.publishStarting()

        val syncStartedAt = SystemClock.elapsedRealtime()
        val result = sourceRepository.sync(source, onProgress = { stage ->
            progressPublisher.publish(stage)
        }, contentTypes = contentTypes)
        progressPublisher.flush()
        Log.i(TAG, "SourceRepository.sync finished sourceId=${source.id} result=${result.name()} ms=${SystemClock.elapsedRealtime() - syncStartedAt}")

        return when (result) {
            is SyncResult.Success -> {
                val warningText = result.warnings.takeIf { it.isNotEmpty() }?.joinToString { it.label }
                Log.i(TAG, "Sync succeeded for source ${source.id} (${source.name}) warnings=$warningText")
                if (completesInitialSync) {
                    sourceDao.markSynced(source.id, System.currentTimeMillis())
                    Log.i(TAG, "Staged initial sync complete — markSynced sourceId=${source.id}")
                }
                val finalizeStartedAt = SystemClock.elapsedRealtime()
                val deferIndexes = source.lastSyncAt == null
                runCatching { importFinalizer.finalize(source, deferIndexes = deferIndexes) }
                    .onSuccess { Log.i(TAG, "Import finalizer sourceId=${source.id} counts=$it ms=${SystemClock.elapsedRealtime() - finalizeStartedAt}") }
                    .onFailure { Log.w(TAG, "Import finalizer failed sourceId=${source.id} ms=${SystemClock.elapsedRealtime() - finalizeStartedAt}", it) }
                if (deferIndexes) {
                    catalogSyncScheduler.enqueueContentIndexBuild(reason = "fresh_sync")
                }
                sourceDao.profileIdsForSource(source.id).forEach { profileId ->
                    val launcherStartedAt = SystemClock.elapsedRealtime()
                    runCatching { launcherIntegrationRepository.refreshProfile(profileId) }
                        .onSuccess { Log.i(TAG, "Launcher refresh profileId=$profileId sourceId=${source.id} ms=${SystemClock.elapsedRealtime() - launcherStartedAt}") }
                        .onFailure { Log.w(TAG, "Launcher refresh failed profileId=$profileId sourceId=${source.id} ms=${SystemClock.elapsedRealtime() - launcherStartedAt}", it) }
                }
                Result.success()
            }
            is SyncResult.Failed -> {
                // A network blip / server 5xx shouldn't leave the catalog stale until the next
                // scheduled window — ask WorkManager to retry with backoff. Auth/URL failures stay terminal.
                if (isTransientSyncError(result.message, connectivity.isOnlineNow()) && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                    Log.w(TAG, "Sync failed transiently for source ${source.id} attempt=$runAttemptCount — will retry: ${result.message}")
                    Result.retry()
                } else {
                    Log.w(TAG, "Sync failed for source ${source.id}: ${result.message}")
                    Result.failure()
                }
            }
            SyncResult.Cancelled -> Result.failure()
        }
    }

    private inner class ProgressPublisher(
        private val contentTypes: SyncContentTypes,
        private val baseItemCount: Int,
    ) {
        private var lastEmitAtMs = 0L
        private var emittedLiveCount = false
        private var emittedMoviesCount = false
        private var emittedSeriesCount = false
        private var lastLiveProcessed = 0
        private var lastMoviesProcessed = 0
        private var lastSeriesProcessed = 0
        private var lastTotalProcessed = 0
        private var pending: ImportStage? = null

        fun publishStarting() {
            val now = SystemClock.elapsedRealtime()
            setProgress(
                workDataOf(
                    KEY_BASE_ITEM_COUNT to baseItemCount,
                    KEY_PROGRESS_LIVE_PROCESSED to 0,
                    KEY_PROGRESS_MOVIES_PROCESSED to 0,
                    KEY_PROGRESS_SERIES_PROCESSED to 0,
                    KEY_PROGRESS_LIVE_ACTIVE to contentTypes.live,
                    KEY_PROGRESS_MOVIES_ACTIVE to contentTypes.movies,
                    KEY_PROGRESS_SERIES_ACTIVE to contentTypes.series,
                ),
            )
            lastEmitAtMs = now
            lastTotalProcessed = 0
        }

        fun publish(stage: ImportStage) {
            pending = stage
            val now = SystemClock.elapsedRealtime()
            if (shouldEmit(stage, now)) {
                emit(stage, now)
            }
        }

        fun flush() {
            pending?.takeUnless { it.matchesLastEmit() }?.let { emit(it, SystemClock.elapsedRealtime()) }
            pending = null
        }

        private fun emit(stage: ImportStage, now: Long) {
            Log.d(
                TAG,
                "Progress emit total=${stage.totalProcessed} live=${stage.liveProcessed} " +
                    "movies=${stage.moviesProcessed} series=${stage.seriesProcessed} " +
                    "sinceLastMs=${now - lastEmitAtMs}",
            )
            setProgress(stage.toWorkData(baseItemCount))
            lastEmitAtMs = now
            lastLiveProcessed = stage.liveProcessed
            lastMoviesProcessed = stage.moviesProcessed
            lastSeriesProcessed = stage.seriesProcessed
            lastTotalProcessed = stage.totalProcessed
            if (stage.liveProcessed > 0) emittedLiveCount = true
            if (stage.moviesProcessed > 0) emittedMoviesCount = true
            if (stage.seriesProcessed > 0) emittedSeriesCount = true
            pending = null
        }

        private fun shouldEmit(stage: ImportStage, now: Long): Boolean =
            (stage.liveProcessed > 0 && !emittedLiveCount) ||
                (stage.moviesProcessed > 0 && !emittedMoviesCount) ||
                (stage.seriesProcessed > 0 && !emittedSeriesCount) ||
                (now - lastEmitAtMs >= PROGRESS_MIN_INTERVAL_MS && stage.hasMeaningfulCountDelta())

        private fun ImportStage.hasMeaningfulCountDelta(): Boolean =
            totalProcessed - lastTotalProcessed >= PROGRESS_ITEM_STEP

        private fun ImportStage.matchesLastEmit(): Boolean =
            liveProcessed == lastLiveProcessed &&
                moviesProcessed == lastMoviesProcessed &&
                seriesProcessed == lastSeriesProcessed

        private fun setProgress(data: Data) {
            setProgressAsync(data)
        }
    }

    companion object {
        const val TAG = "CatalogSyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val PROGRESS_MIN_INTERVAL_MS = 750L
        private const val PROGRESS_ITEM_STEP = 1_000
        const val KEY_SOURCE_ID = "sourceId"
        const val KEY_REASON = "reason"
        const val KEY_BASE_ITEM_COUNT = "baseItemCount"
        const val KEY_LIVE = "live"
        const val KEY_MOVIES = "movies"
        const val KEY_SERIES = "series"
        const val KEY_COMPLETES_INITIAL_SYNC = "completesInitialSync"
        const val KEY_PROGRESS_LIVE_PROCESSED = "liveProcessed"
        const val KEY_PROGRESS_MOVIES_PROCESSED = "moviesProcessed"
        const val KEY_PROGRESS_SERIES_PROCESSED = "seriesProcessed"
        const val KEY_PROGRESS_LIVE_ACTIVE = "liveActive"
        const val KEY_PROGRESS_MOVIES_ACTIVE = "moviesActive"
        const val KEY_PROGRESS_SERIES_ACTIVE = "seriesActive"
    }
}

private fun SyncResult.name(): String = when (this) {
    is SyncResult.Success -> "Success"
    is SyncResult.Failed -> "Failed"
    SyncResult.Cancelled -> "Cancelled"
}

private fun ImportStage.toWorkData(baseItemCount: Int): Data =
    workDataOf(
        CatalogSyncWorker.KEY_BASE_ITEM_COUNT to baseItemCount,
        CatalogSyncWorker.KEY_PROGRESS_LIVE_PROCESSED to liveProcessed,
        CatalogSyncWorker.KEY_PROGRESS_MOVIES_PROCESSED to moviesProcessed,
        CatalogSyncWorker.KEY_PROGRESS_SERIES_PROCESSED to seriesProcessed,
        CatalogSyncWorker.KEY_PROGRESS_LIVE_ACTIVE to liveActive,
        CatalogSyncWorker.KEY_PROGRESS_MOVIES_ACTIVE to moviesActive,
        CatalogSyncWorker.KEY_PROGRESS_SERIES_ACTIVE to seriesActive,
    )
