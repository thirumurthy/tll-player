package com.thirutricks.tllplayer.player

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Tails the app's OWN logcat for the below-the-engine playback failures the player objects can't expose:
 * Android **MediaCodec** (e.g. `Codec reported err 0x80001000`) and **AudioTrack** errors. mpv/ExoPlayer sit
 * on top of MediaCodec, so a hardware decode/audio failure only names itself in the system log — and most
 * users can't run adb. Reading your *own* process's logs needs no permission.
 *
 * Best-effort: if logcat can't be spawned (rare/locked-down devices), it silently no-ops and the player
 * falls back to the engine's own error text. A single daemon reader thread, tag-filtered to stay cheap.
 */
class PlayerDiagnostics {
    private data class Entry(val atMs: Long, val tag: String, val text: String)

    private val ring = ConcurrentLinkedDeque<Entry>()
    @Volatile private var started = false
    @Volatile private var loadStartMs = 0L

    /** Start tailing (idempotent). Call once the player initialises. */
    fun start() {
        if (started) return
        started = true
        Thread({ readLoop() }, "owntv-logcat").apply { isDaemon = true; start() }
    }

    /** Mark the start of a new stream load, so [recentError] only reports failures from the CURRENT item. */
    fun markLoad() { loadStartMs = System.currentTimeMillis() }

    private fun readLoop() {
        runCatching {
            // `-T 1`: start near "now" (don't replay the whole backlog). Tag-filtered to codec/audio errors.
            val cmd = listOf(
                "logcat", "-v", "tag", "-T", "1",
                "MediaCodec:E", "ACodec:E", "OMXClient:E", "CCodec:E", "Codec2Client:E", "C2PlatformStore:E",
                "MediaCodecRenderer:E", "MediaCodecVideoRenderer:E", "MediaCodecAudioRenderer:E",
                "AudioTrack:W", "AudioSink:E", "AudioFlinger:E", "*:S",
            )
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines -> lines.forEach(::record) }
        }
    }

    private fun record(line: String) {
        // `-v tag` format: "E/MediaCodec: Codec reported err 0x80001000, actionCode 0, …"
        val slash = line.indexOf('/')
        if (slash != 1) return
        val colon = line.indexOf(':', slash)
        if (colon < 0) return
        val tag = line.substring(slash + 1, colon).trim()
        val text = line.substring(colon + 1).trim()
        if (text.isEmpty()) return
        ring.addLast(Entry(System.currentTimeMillis(), tag, text))
        while (ring.size > 80) ring.pollFirst()
    }

    /** The most recent codec/audio error from the current stream (≤15 s old), raw "tag: text"; null if none.
     *  [PlayerErrors.reasonFor] is applied by the consumer so all error sources share one humanization map. */
    fun recentError(): String? {
        val cutoff = maxOf(loadStartMs, System.currentTimeMillis() - 15_000)
        val e = ring.descendingIterator().asSequence().firstOrNull { it.atMs >= cutoff } ?: return null
        return "${e.tag}: ${e.text}"
    }
}

/** A playback failure broken into the three lines the error screen shows: a plain-English [reason], the
 *  media [spec] (codec • resolution • decoder), and the [raw] engine text. Any field may be null. */
data class ErrorInfo(val reason: String?, val spec: String?, val raw: String?)

/** Maps cryptic playback-failure strings (MediaCodec codes, HTTP/SSL, OOM) to a plain-English reason a user
 *  can act on. Applied to EVERY error source (logcat codec lines, mpv log lines, ExoPlayer codes) so one
 *  table covers them all. Reviewer-sourced cases (HTTP 509, ENOMEM, SSL, unsupported formats). */
object PlayerErrors {
    /** Plain-English reason for a raw error string, or null if we don't recognize it. */
    fun reasonFor(raw: String): String? {
        val l = raw.lowercase()
        return when {
            "0x80001000" in l -> "Hardware video decoder is busy or can't handle this stream"
            "0x80001001" in l -> "Hardware video decoder error — transient, try again"
            "0xfffffff3" in l || "0xffffffea" in l || "format_unsupported" in l || "omx_errorformat" in l ->
                "This device's decoder doesn't support this video format/profile (e.g. HEVC 10-bit)"
            "enomem" in l || "-12" in l || "out of memory" in l || "no memory" in l || "insufficient" in l ->
                "Device ran out of memory for the decoder — try closing other apps"
            "error_key" in l || "cryptoinfo" in l || "0x80001100" in l || ("drm" in l && "error" in l) ->
                "DRM / secure-decoder error"
            "509" in l && "http" in l -> "Provider blocked the connection — too many streams at once (HTTP 509)"
            "403" in l && "http" in l -> "Provider denied access (HTTP 403) — credentials, subscription, or IP block"
            "401" in l && "http" in l -> "Provider rejected your login (HTTP 401)"
            "404" in l && "http" in l -> "Stream not found on the provider (HTTP 404)"
            "400" in l && "http" in l -> "Provider rejected the request (HTTP 400) — bad or expired link"
            "certificate verify failed" in l || ("ssl" in l && "certif" in l) || "cert_" in l ->
                "Provider's SSL certificate is invalid or expired"
            "unrecognized file format" in l || "invalid data found" in l ->
                "Stream format not recognized (bad or partial stream)"
            "connection refused" in l || "connection reset" in l || "timed out" in l || "timeout" in l ->
                "Network problem reaching the provider"
            "audiotrack" in l || "audiosink" in l || "audioflinger" in l || "audio codec" in l ->
                "Audio output error — the device couldn't play this audio format"
            else -> null
        }
    }
}
