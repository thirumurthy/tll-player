package com.thirutricks.tllplayer.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide "an EPG sync is running" signal for the shell's status pill. Mirrors [SyncActivityTracker]:
 * [tv.own.owntv.core.sync.work.EpgSyncWorker] reports here (started / progress / finished), so the same
 * unobtrusive pill that tracks catalog syncs also reflects guide/EPG downloads. Purely observational —
 * nothing reads this to make decisions.
 */
class EpgActivityTracker {

    data class ActiveEpgSync(
        val sourceId: Long,
        val sourceName: String,
        val channels: Int = 0,
        val programmes: Int = 0,
    )

    private val _active = MutableStateFlow<Map<Long, ActiveEpgSync>>(emptyMap())

    /** All currently-running EPG syncs keyed by sourceId (usually 0 or 1 entries). */
    val active: StateFlow<Map<Long, ActiveEpgSync>> = _active.asStateFlow()

    fun started(sourceId: Long, sourceName: String) {
        _active.value = _active.value + (sourceId to ActiveEpgSync(sourceId, sourceName))
    }

    fun progress(sourceId: Long, channels: Int, programmes: Int) {
        val existing = _active.value[sourceId] ?: return
        _active.value = _active.value + (sourceId to existing.copy(channels = channels, programmes = programmes))
    }

    fun finished(sourceId: Long) {
        _active.value = _active.value - sourceId
    }
}
