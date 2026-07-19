package com.thirutricks.tllplayer.core.sync.work

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import com.thirutricks.tllplayer.core.epg.EpgSourceStore
import com.thirutricks.tllplayer.core.network.ConnectivityObserver
import com.thirutricks.tllplayer.core.repository.EpgRepository
import com.thirutricks.tllplayer.core.sync.EpgActivityTracker
import com.thirutricks.tllplayer.core.util.friendlySyncError
import com.thirutricks.tllplayer.core.util.isTransientSyncError

class EpgSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val epgRepository: EpgRepository,
    private val store: EpgSourceStore,
    private val connectivity: ConnectivityObserver,
    private val activityTracker: EpgActivityTracker,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(KEY_SOURCE_ID, Long.MIN_VALUE)
        val reason = inputData.getString(KEY_REASON) ?: "unknown"
        if (sourceId == Long.MIN_VALUE) return Result.failure()

        val source = store.getAll().firstOrNull { it.id == sourceId } ?: run {
            Log.i(TAG, "Skipping stale EPG sync sourceId=$sourceId reason=$reason")
            return Result.success()
        }

        val baseProgrammes = inputData.getInt(KEY_BASE_PROGRAMMES, 0)
        val progress = ProgressPublisher(baseProgrammes) { channels, programmes ->
            activityTracker.progress(source.id, channels, programmes)
        }
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "Starting EPG sync sourceId=${source.id} reason=$reason")
        activityTracker.started(source.id, source.name)

        try {
            val programmes = epgRepository.refreshUrl(source.id, source.url, source.userAgent) { channels, count ->
                progress.publish(channels, count)
            }
            progress.flush()
            store.setSynced(source.id, System.currentTimeMillis(), null)
            Log.i(
                TAG,
                "EPG sync finished sourceId=${source.id} reason=$reason programmes=$programmes ms=${SystemClock.elapsedRealtime() - startedAt}",
            )
            return Result.success()
        } catch (c: CancellationException) {
            Log.i(TAG, "EPG sync cancelled sourceId=${source.id} reason=$reason")
            throw c
        } catch (e: Exception) {
            val online = connectivity.isOnlineNow()
            val message = friendlySyncError(e.message, online)
            // Record the failure WITHOUT updating lastSyncAt — staleness-based auto-refresh treats
            // lastSyncAt as the last *successful* sync, so a failed attempt must leave it untouched
            // (otherwise a flaky network would falsely reset the threshold and stop retries).
            store.markError(source.id, message)
            // Transient network trouble → WorkManager retry with backoff instead of staying stale
            // until the next scheduled window. Permanent errors (bad URL, malformed XML) stay terminal.
            return if ((e is java.io.IOException || isTransientSyncError(e.message, online)) && runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Log.w(TAG, "EPG sync failed transiently sourceId=${source.id} reason=$reason attempt=$runAttemptCount — will retry", e)
                Result.retry()
            } else {
                Log.w(TAG, "EPG sync failed sourceId=${source.id} reason=$reason", e)
                Result.failure()
            }
        } finally {
            // Always clear the pill entry — success, failure, retry, or cancellation — so it never sticks.
            activityTracker.finished(source.id)
        }
    }

    private inner class ProgressPublisher(
        private val baseProgrammes: Int,
        private val onEmit: (channels: Int, programmes: Int) -> Unit,
    ) {
        private var lastEmitAtMs = 0L
        private var lastChannels = -1
        private var lastProgrammes = -1
        private var pendingChannels = 0
        private var pendingProgrammes = 0
        private var hasPending = false

        fun publish(channels: Int, programmes: Int) {
            pendingChannels = channels
            pendingProgrammes = programmes
            hasPending = true
            val now = SystemClock.elapsedRealtime()
            if (lastEmitAtMs == 0L || (now - lastEmitAtMs >= PROGRESS_MIN_INTERVAL_MS && shouldEmit())) {
                emit(now)
            }
        }

        fun flush() {
            if (hasPending) emit(SystemClock.elapsedRealtime())
        }

        private fun shouldEmit(): Boolean =
            pendingChannels != lastChannels || pendingProgrammes != lastProgrammes

        private fun emit(now: Long) {
            if (!hasPending) return
            val channels = pendingChannels
            val programmes = pendingProgrammes
            if (channels == lastChannels && programmes == lastProgrammes && lastEmitAtMs != 0L) {
                hasPending = false
                return
            }
            setProgressAsync(
                workDataOf(
                    KEY_PROGRESS_CHANNELS to channels,
                    KEY_PROGRESS_PROGRAMMES to programmes,
                    KEY_BASE_PROGRAMMES to baseProgrammes,
                ),
            )
            // Also push the counts to the shell status pill (independent of WorkManager progress).
            onEmit(channels, programmes)
            lastEmitAtMs = now
            lastChannels = channels
            lastProgrammes = programmes
            hasPending = false
        }
    }

    companion object {
        const val TAG = "EpgSyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val PROGRESS_MIN_INTERVAL_MS = 750L
        const val KEY_SOURCE_ID = "sourceId"
        const val KEY_REASON = "reason"
        const val KEY_PROGRESS_CHANNELS = "channels"
        const val KEY_PROGRESS_PROGRAMMES = "programmes"
        const val KEY_BASE_PROGRAMMES = "baseProgrammes"
    }
}
