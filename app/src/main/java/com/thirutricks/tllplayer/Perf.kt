package com.thirutricks.tllplayer

import android.os.SystemClock
import android.util.Log

/**
 * Tiny startup/menu perf tracer. [begin] sets the zero point (called as the first thing in
 * [OwnTVApp.onCreate]); [stamp] logs the elapsed milliseconds since. Filter logcat with `-s OwnTVPerf` for a
 * clean timeline of what runs at cold start and where the milliseconds actually go — Koin init, DB open /
 * migration, shell render, the Home landing screen's data, the EPG pre-warm. This is what tells us *why* a
 * first launch (cold boot / after TV power-cycle) is slower than later ones: the OS + SQLite page caches are
 * cold, so the first DB reads come off slow eMMC instead of RAM.
 *
 * Overhead is negligible (one Log.d per phase) and it's safe to leave in; remove the call sites once startup
 * tuning is validated.
 */
object Perf {
    private const val TAG = "OwnTVPerf"

    @Volatile private var t0 = -1L

    fun begin() {
        t0 = SystemClock.elapsedRealtime()
        Log.d(TAG, "process-begin @0ms")
    }

    fun stamp(label: String) {
        val base = t0
        if (base < 0) return
        Log.d(TAG, "$label @${SystemClock.elapsedRealtime() - base}ms")
    }
}
