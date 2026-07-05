package com.thirutricks.tllplayer.player

import android.app.ActivityManager
import android.content.Context

/**
 * Device-scaled memory budget for the player. The old fixed 256 MiB demuxer cache was tuned on the
 * Android Studio emulator (which borrows the host PC's RAM) and OOM-killed real TVs: a budget
 * 4K Google TV gives a foreground app only a few hundred MB before the low-memory killer fires
 * (observed: PSS 389–620 MB at death on a TCL G10, all reason=LOW_MEMORY).
 */
data class PlayerBudget(
    /** mpv demuxer-max-bytes (forward read-ahead cache). */
    val demuxerMaxBytes: String,
    /** mpv demuxer-max-back-bytes (kept-behind cache for small back-seeks). */
    val demuxerBackBytes: String,
    /** mpv demuxer-readahead-secs. */
    val readaheadSecs: String,
    /** mpv cache-secs. */
    val cacheSecs: String,
    /** True on TV-class / low-RAM devices: also use cheaper rgba8 framebuffers (a 4K rgba16f
     *  intermediate is ~64 MB each; rgba8 halves that, invisible on 8-bit panels). */
    val lowSpec: Boolean,
) {
    companion object {
        /** Aggressive shrink target applied live when the OS signals critical memory pressure. */
        const val TRIM_DEMUXER_BYTES = "24MiB"

        fun of(context: Context): PlayerBudget {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
            return when {
                am.isLowRamDevice || totalGb < 3.0 -> PlayerBudget(
                    // TV-class (1.5–2.5 GB total): keep the whole player well under ~180 MB PSS.
                    demuxerMaxBytes = "48MiB", demuxerBackBytes = "16MiB",
                    readaheadSecs = "10", cacheSecs = "30", lowSpec = true,
                )
                totalGb < 6.0 -> PlayerBudget(
                    demuxerMaxBytes = "128MiB", demuxerBackBytes = "32MiB",
                    readaheadSecs = "30", cacheSecs = "60", lowSpec = false,
                )
                else -> PlayerBudget(
                    // RAM-rich devices (emulators, high-end boxes) keep the deep 4K/8K read-ahead.
                    demuxerMaxBytes = "256MiB", demuxerBackBytes = "64MiB",
                    readaheadSecs = "60", cacheSecs = "120", lowSpec = false,
                )
            }
        }
    }
}
