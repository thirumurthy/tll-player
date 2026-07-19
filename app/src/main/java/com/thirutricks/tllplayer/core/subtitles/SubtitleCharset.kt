package com.thirutricks.tllplayer.core.subtitles

import org.mozilla.universalchardet.UniversalDetector
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Charset normalisation for local subtitle files (subtitle plan §7.2 / review R6). Local .srt/.ass
 * files are frequently NOT UTF-8 — Windows-1256 (Arabic), Windows-1252 and ISO-8859 variants are
 * common — and both engines assume UTF-8, so a raw copy would render mojibake. Detection uses
 * juniversalchardet (Mozilla's universalchardet port).
 */
object SubtitleCharset {

    /**
     * Returns [bytes] as UTF-8 text bytes: already-UTF-8 (or plain ASCII) input is returned as-is
     * (minus a BOM); anything else is decoded with the detected charset and re-encoded. When
     * detection fails entirely, falls back to Windows-1252 — every byte maps, so worst case a few
     * wrong accents rather than replacement characters everywhere.
     */
    fun toUtf8(bytes: ByteArray): ByteArray {
        val detected = detect(bytes)
        val charset = when {
            detected == null -> if (isValidUtf8(bytes)) StandardCharsets.UTF_8 else charsetOr1252("")
            else -> charsetOr1252(detected)
        }
        val body = stripBom(bytes, charset)
        if (charset == StandardCharsets.UTF_8) return body
        return String(body, charset).toByteArray(StandardCharsets.UTF_8)
    }

    private fun detect(bytes: ByteArray): String? {
        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()
        return detector.detectedCharset
    }

    private fun charsetOr1252(name: String): Charset =
        runCatching { Charset.forName(name) }.getOrNull()
            ?: runCatching { Charset.forName("windows-1252") }.getOrDefault(StandardCharsets.ISO_8859_1)

    /** Drops a UTF-8/UTF-16 BOM if present — mpv copes, but Exo's parsers can choke on it mid-label. */
    private fun stripBom(bytes: ByteArray, charset: Charset): ByteArray = when {
        charset == StandardCharsets.UTF_8 && bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
            bytes.copyOfRange(3, bytes.size)
        else -> bytes
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean =
        runCatching {
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)); true
        }.getOrDefault(false)
}
