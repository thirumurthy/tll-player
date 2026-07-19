package com.thirutricks.tllplayer.core.companion

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/**
 * Helpers for the Remote (companion) flow: discovering the device's LAN address and rendering the
 * URL a phone opens as a scannable QR code.
 *
 * The QR encodes the **plain URL only** — never the PIN. The PIN is shown on the TV and typed on the
 * phone's gate page, so a photographed/forwarded QR alone can't push a source.
 */
object CompanionLink {

    /** Default listener port. High, unprivileged, and unlikely to collide with common services. */
    const val DEFAULT_PORT = 8089

    /**
     * `http://<lan-ip>:<port>/` the phone can open. Prefers an RFC1918 private address (the usual
     * home Wi-Fi range); falls back to any other non-loopback IPv4, then to 127.0.0.1 as a last resort.
     */
    fun lanUrl(port: Int): String {
        val host = lanHosts().firstOrNull(::isPrivateLan) ?: lanHosts().firstOrNull() ?: "127.0.0.1"
        return "http://$host:$port/"
    }

    /** The candidate URLs to show on the TV (preferred first). Public so callers can list alternates. */
    fun lanUrls(port: Int): List<String> {
        val hosts = lanHosts().sortedByDescending(::isPrivateLan).ifEmpty { listOf("127.0.0.1") }
        return hosts.map { "http://$it:$port/" }
    }

    /** Up, non-loopback, non-link-local IPv4 addresses of the device. Public for testing. */
    fun lanHosts(): List<String> {
        val out = ArrayList<String>()
        val interfaces = runCatching { Collections.list(NetworkInterface.getNetworkInterfaces()) }.getOrNull().orEmpty()
        interfaces.forEach { iface ->
            if (runCatching { !iface.isUp || iface.isLoopback || iface.isVirtual }.getOrDefault(true)) return@forEach
            Collections.list(iface.inetAddresses)
                .filterIsInstance<Inet4Address>()
                .forEach { address ->
                    if (address.isLoopbackAddress || address.isLinkLocalAddress) return@forEach
                    address.hostAddress?.takeIf { it.isNotBlank() }?.let(out::add)
                }
        }
        return out.distinct()
    }

    /**
     * Render [url] as a square QR [ImageBitmap] for display on the TV. Returns null if encoding fails
     * (the Remote screen then falls back to showing the URL text only).
     */
    fun renderQr(url: String, sizePx: Int = 512): ImageBitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                pixels[row + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }.getOrNull()

    /** RFC1918 private IPv4 ranges (10/8, 192.168/16, 172.16–31/12). Public for testing. */
    fun isPrivateLan(host: String): Boolean =
        host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            (host.startsWith("172.") && host.split('.').getOrNull(1)?.toIntOrNull()?.let { it in 16..31 } == true)
}
