package com.thirutricks.tllplayer.core.sync.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class EpgSyncScheduler(private val context: Context) {

    fun enqueueSync(sourceId: Long, reason: String, baseProgrammes: Int = 0, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val request = OneTimeWorkRequestBuilder<EpgSyncWorker>()
            .setInputData(
                workDataOf(
                    EpgSyncWorker.KEY_SOURCE_ID to sourceId,
                    EpgSyncWorker.KEY_REASON to reason,
                    EpgSyncWorker.KEY_BASE_PROGRAMMES to baseProgrammes,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(TAG_EPG_SYNC)
            .addTag("epg-source-$sourceId")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(sourceId), policy, request)
    }

    fun cancelSync(sourceId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(sourceId))
    }

    fun observeSync(sourceId: Long): Flow<EpgSyncState> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(workName(sourceId))
            .map { infos ->
                val active = infos.firstOrNull { !it.state.isFinished }
                if (active == null) {
                    EpgSyncState.Idle
                } else {
                    EpgSyncState.Syncing(
                        channels = active.progress.getInt(EpgSyncWorker.KEY_PROGRESS_CHANNELS, 0),
                        programmes = active.progress.getInt(EpgSyncWorker.KEY_PROGRESS_PROGRAMMES, 0),
                        baseProgrammes = active.progress.getInt(EpgSyncWorker.KEY_BASE_PROGRAMMES, 0),
                    )
                }
            }
            .distinctUntilChanged()

    companion object {
        const val TAG_EPG_SYNC = "epg-sync"

        fun workName(sourceId: Long) = "epg-sync-source-$sourceId"
    }
}

sealed interface EpgSyncState {
    data object Idle : EpgSyncState
    data class Syncing(val channels: Int = 0, val programmes: Int = 0, val baseProgrammes: Int = 0) : EpgSyncState

    val isActive: Boolean
        get() = this is Syncing
}
