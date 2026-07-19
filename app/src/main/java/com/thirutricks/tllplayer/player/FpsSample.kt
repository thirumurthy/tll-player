package com.thirutricks.tllplayer.player

import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

/**
 * Give each independently-timed poller its own instance to avoid corruption.
 */
class FpsSample {
    var renderedCount = 0
    var atMs = 0L
    var lastFps: Float? = null

    /** True once a reading has actually matched a standard rate — a window that caught a brief stall
     *  produces an off-rate value instead, which callers can use as a signal to sample again. */
    var confident = false
        private set

    /** Takes one sample via [p]'s decoder counters and immediately publishes it to [lastFps]. */
    fun sample(p: ExoPlayer): Float? {
        peek(p)?.let { lastFps = it }
        return lastFps
    }

    /** Like [sample], but returns the fresh reading (if any) without publishing it to [lastFps] — for a
     *  caller that wants to judge [confident] before deciding whether to [publish] it. */
    fun peek(p: ExoPlayer): Float? {
        val counters = p.videoDecoderCounters ?: return null
        counters.ensureUpdated()
        val rendered = counters.renderedOutputBufferCount
        if (rendered == 0) return null
        val now = SystemClock.elapsedRealtime()
        var fresh: Float? = null
        if (atMs > 0) {
            val dFrames = rendered - renderedCount
            val dSecs = (now - atMs) / 1000f
            if (dFrames > 0 && dSecs > 0) {
                val raw = dFrames / dSecs
                fresh = snapToStandardRate(raw)
                confident = fresh != raw
            }
        }
        renderedCount = rendered
        atMs = now
        return fresh
    }

    fun publish(fps: Float) {
        lastFps = fps
    }

    /** Fresh window on the next measurement; keeps [lastFps] so a display doesn't blank mid-remeasure. */
    fun resetWindow() {
        atMs = 0L
    }

    /** Full reset for a genuine channel/file change. */
    fun resetAll() {
        renderedCount = 0
        atMs = 0L
        lastFps = null
        confident = false
    }

    private companion object {
        val STANDARD_FRAME_RATES = floatArrayOf(24f, 25f, 30f, 50f, 60f, 90f, 120f)
        fun snapToStandardRate(fps: Float): Float {
            val nearest = STANDARD_FRAME_RATES.minByOrNull { abs(it - fps) } ?: return fps
            return if (abs(nearest - fps) <= nearest * 0.05f) nearest else fps
        }
    }
}
