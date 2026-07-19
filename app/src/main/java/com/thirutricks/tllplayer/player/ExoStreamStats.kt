package com.thirutricks.tllplayer.player

import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer

/** Live measured bitrate once available, else the declared value, else 0. */
fun bitrateRow(f: Format, throughputTracker: ThroughputTracker): Pair<String, String> {
    val bps = if (throughputTracker.hasMeasured) throughputTracker.bitsPerSecond else f.bitrate.takeIf { it > 0 }?.toLong() ?: 0L
    return "Bitrate" to "%.1f Mbps".format(bps / 1_000_000.0)
}

/** Buffered duration + dropped frames since [dropsBaseline]. We can't reset ExoPlayer's own drop
 *  counter, so callers snapshot it per item and we subtract instead. */
fun bufferRow(p: ExoPlayer, dropsBaseline: Int): Pair<String, String>? {
    val drops = p.videoDecoderCounters?.let { it.ensureUpdated(); (it.droppedBufferCount - dropsBaseline).coerceAtLeast(0) }
    val line = listOfNotNull(
        if (p.totalBufferedDuration > 0) "%.1f s".format(p.totalBufferedDuration / 1000.0) else null,
        drops?.let { "drops $it" },
    ).joinToString(" · ")
    return line.takeIf { it.isNotBlank() }?.let { "Buffer" to it }
}

/** Snapshot of the current drop count, for [bufferRow]'s baseline. */
fun currentDroppedFrames(p: ExoPlayer?): Int {
    val counters = p?.videoDecoderCounters ?: return 0
    counters.ensureUpdated()
    return counters.droppedBufferCount
}
