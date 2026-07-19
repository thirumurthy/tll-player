package com.thirutricks.tllplayer.player

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import com.thirutricks.tllplayer.BuildConfig

/**
 * Bounded ring buffer + rolling file for Live TV (ExoPlayer) playback diagnostics. A hang can happen at any
 * time, and this device's vendor PQ/AI logging floods logcat fast enough that OwnTV's own lines don't survive
 * to a bugreport — so this keeps its own bounded record independent of Logcat.
 *
 * Enabled by default in debug builds, disabled by default in release. Callers must redact URLs/credentials
 * before passing text in — this class does not inspect or strip anything itself.
 */
object LiveDiagnosticsLog {
    const val TAG = "OwnTV-LivePreviewEngine"

    @Volatile var enabled: Boolean = BuildConfig.DEBUG

    private const val MAX_EVENTS = 1000
    private const val MAX_FILE_BYTES = 256 * 1024L
    private val ring = ArrayDeque<String>()
    private val lock = Any()
    private val timeFmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    /** Call once with an app [Context] so events can be flushed to [file]. Safe to call repeatedly. */
    fun init(context: Context) {
        if (logFile != null) return
        runCatching {
            val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
            logFile = File(dir, "live_diagnostics.log")
        }
    }

    /** Record one diagnostic line. Always kept in the in-memory ring; also written to Logcat + the rolling
     *  file when [enabled]. [message] must already be redacted of URLs/credentials by the caller. */
    fun event(message: String) {
        val line = "${timeFmt.format(Date())} $message"
        synchronized(lock) {
            ring.addLast(line)
            while (ring.size > MAX_EVENTS) ring.pollFirst()
        }
        if (enabled) {
            android.util.Log.i(TAG, message)
            writeLine(line)
        }
    }

    private fun writeLine(line: String) {
        val f = logFile ?: return
        runCatching {
            if (f.exists() && f.length() > MAX_FILE_BYTES) {
                val kept = f.readLines().takeLast(MAX_EVENTS / 2)
                f.writeText(kept.joinToString("\n") + "\n")
            }
            f.appendText(line + "\n")
        }
    }

    /** Oldest-first snapshot of the in-memory ring buffer (e.g. for an in-app export action). */
    fun snapshot(): String = synchronized(lock) { ring.joinToString("\n") }

    /** Path of the rolling diagnostic file on disk, once [init] has run. */
    fun file(): File? = logFile
}
