package com.thirutricks.tllplayer.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide "a catalog sync is running" signal for the shell's unobtrusive status pill. Every sync —
 * foreground add, backgrounded add, WorkManager remainder/refresh — funnels through
 * [SyncManager.sync], which reports here. Purely observational: nothing reads this to make decisions.
 */
class SyncActivityTracker {

    data class ActiveSync(val sourceId: Long, val sourceName: String, val stage: ImportStage? = null)

    private val _active = MutableStateFlow<Map<Long, ActiveSync>>(emptyMap())

    /** All currently-running syncs keyed by sourceId (usually 0 or 1 entries). */
    val active: StateFlow<Map<Long, ActiveSync>> = _active.asStateFlow()

    fun started(sourceId: Long, sourceName: String) {
        _active.value = _active.value + (sourceId to ActiveSync(sourceId, sourceName))
    }

    fun progress(sourceId: Long, stage: ImportStage) {
        val existing = _active.value[sourceId] ?: return
        _active.value = _active.value + (sourceId to existing.copy(stage = stage))
    }

    fun finished(sourceId: Long) {
        _active.value = _active.value - sourceId
    }
}
