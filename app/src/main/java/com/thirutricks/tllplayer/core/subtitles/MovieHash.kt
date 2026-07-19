package com.thirutricks.tllplayer.core.subtitles

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The OpenSubtitles "moviehash" (plan §3.3): file size + the first and last 64 KiB summed as
 * little-endian unsigned 64-bit words. Only computable when the COMPLETE media file is local —
 * OwnTV uses it solely for downloaded movies/episodes, as an optional search-quality enhancer.
 * Reads 128 KiB total, so it's fast; callers still guard it with a timeout and never let a
 * failure block the metadata search.
 */
object MovieHash {

    private const val CHUNK = 64 * 1024

    /** Hex hash of [file], or null when the file is too small/unreadable (caller just omits it). */
    fun compute(file: File): String? = runCatching {
        val size = file.length()
        if (size < CHUNK) return null
        RandomAccessFile(file, "r").use { raf ->
            var hash = size
            hash += sumChunk(raf, 0)
            hash += sumChunk(raf, size - CHUNK)
            String.format("%016x", hash)
        }
    }.getOrNull()

    private fun sumChunk(raf: RandomAccessFile, offset: Long): Long {
        val bytes = ByteArray(CHUNK)
        raf.seek(offset)
        raf.readFully(bytes)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0L
        repeat(CHUNK / 8) { sum += buf.long }
        return sum
    }
}
