package com.thirutricks.tllplayer.player

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Small rolling on-disk history of playback failures (last [MAX] entries), so a user who dismissed
 * the error screen — or whose app restarted — can still read/report what happened from
 * Settings → "Playback error log". Most TV users can't pull logcat; this is their only record.
 *
 * Plain JSON file in filesDir; all IO runs on a single background thread so the player's error
 * path never blocks. Timestamps are wall-clock (for display), unlike the monotonic diagnostics.
 */
object PlaybackErrorLog {
    private const val MAX = 10
    private const val FILE_NAME = "playback_errors.json"

    data class Entry(
        val atMs: Long,
        val engine: String,
        val live: Boolean,
        val reason: String?,
        val spec: String?,
        val raw: String?,
        val model: String,
        val android: String,
    )

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "owntv-errorlog").apply { isDaemon = true } }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Append an error (fire-and-forget; trims to the newest [MAX]). */
    fun log(context: Context, engine: String, live: Boolean, info: ErrorInfo) {
        if (info.reason == null && info.raw == null) return // nothing useful to keep
        val appContext = context.applicationContext
        io.execute {
            runCatching {
                val entries = readSync(appContext).toMutableList()
                entries.add(
                    Entry(
                        atMs = System.currentTimeMillis(),
                        engine = engine,
                        live = live,
                        reason = info.reason,
                        spec = info.spec,
                        raw = info.raw,
                        model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                        android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    ),
                )
                writeSync(appContext, entries.takeLast(MAX))
            }
        }
    }

    /** Newest-first list for the Settings viewer. Safe to call from a coroutine on Dispatchers.IO. */
    fun read(context: Context): List<Entry> = runCatching { readSync(context).reversed() }.getOrDefault(emptyList())

    /** Synchronous (tiny file delete) so a viewer re-read right after can't race a queued delete. */
    fun clear(context: Context) {
        runCatching { file(context.applicationContext).delete() }
    }

    private fun readSync(context: Context): List<Entry> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Entry(
                atMs = o.optLong("atMs"),
                engine = o.optString("engine"),
                live = o.optBoolean("live"),
                reason = o.optString("reason").takeIf { it.isNotEmpty() },
                spec = o.optString("spec").takeIf { it.isNotEmpty() },
                raw = o.optString("raw").takeIf { it.isNotEmpty() },
                model = o.optString("model"),
                android = o.optString("android"),
            )
        }
    }

    private fun writeSync(context: Context, entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("atMs", e.atMs)
                    .put("engine", e.engine)
                    .put("live", e.live)
                    .put("reason", e.reason ?: "")
                    .put("spec", e.spec ?: "")
                    .put("raw", e.raw ?: "")
                    .put("model", e.model)
                    .put("android", e.android),
            )
        }
        file(context).writeText(arr.toString())
    }
}
