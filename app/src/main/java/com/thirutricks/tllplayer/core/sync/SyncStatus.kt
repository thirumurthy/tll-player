package com.thirutricks.tllplayer.core.sync

/** Live progress of an import (e.g. "Channels", 128430, null → indeterminate total). */
data class ImportStage(val label: String, val processed: Int, val total: Int?) {
    val fraction: Float?
        get() = total?.takeIf { it > 0 }?.let { processed.toFloat() / it }
}

/** Terminal result of a sync run. */
sealed interface SyncResult {
    data object Success : SyncResult
    data object Cancelled : SyncResult
    data class Failed(val message: String) : SyncResult
}
