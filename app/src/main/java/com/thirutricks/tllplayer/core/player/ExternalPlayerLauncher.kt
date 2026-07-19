package com.thirutricks.tllplayer.core.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

// Hands a stream URL (or a downloaded file path) to an external video player (VLC, MX Player, etc.)
// via ACTION_VIEW. When it fires, the in-app player is bypassed entirely (the fullscreen player
// never opens, because OwnTVPlayer is never told to play).
//
// Network URLs (http/https/rtsp/rtmp/udp/mms) are handed over verbatim. Local download paths are
// shared through the app FileProvider with a read permission grant.
//
// Limitations: no custom User-Agent / Referer / headers can be attached to an ACTION_VIEW intent,
// so streams needing per-source auth headers may fail; no resume position or prev/next queue.
class ExternalPlayerLauncher(private val context: Context) {

    // Open url externally. Returns true if an external app was actually launched.
    fun launch(url: String, title: String? = null): Boolean {
        val uri = uriFor(url)
        if (uri == null) {
            toast("Could not open this stream.")
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeFor(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val targets = context.packageManager.queryIntentActivities(intent, 0)
        return when {
            targets.isEmpty() -> {
                toast("No external player found - install VLC or MX Player.")
                false
            }
            targets.size == 1 -> startActivity(intent)
            else -> startActivity(
                Intent.createChooser(intent, title ?: "Play with")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // Whether any installed app can handle a video URL.
    fun isAvailable(): Boolean {
        val probe = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("https://example.com/video.mp4"), "video/mp4")
        return context.packageManager.queryIntentActivities(probe, 0).isNotEmpty()
    }

    private fun startActivity(intent: Intent): Boolean =
        runCatching { context.startActivity(intent) }
            .onFailure { toast("No external player found - install VLC or MX Player.") }
            .isSuccess

    // Network scheme: hand the URL over verbatim; otherwise treat as a local file path.
    private fun uriFor(url: String): Uri? {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme == "http" || scheme == "https" || scheme == "rtsp" ||
            scheme == "rtmp" || scheme == "udp" || scheme == "mms"
        ) {
            return Uri.parse(url)
        }
        val file = File(url)
        if (!file.exists()) return null
        val authority = context.packageName + ".fileprovider"
        return runCatching {
            FileProvider.getUriForFile(context, authority, file)
        }.getOrNull()
    }

    // A MIME type the common external players accept for IPTV URLs.
    private fun mimeFor(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/', "")
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "m3u8", "m3u" -> "application/x-mpegURL"
            "ts", "m2t", "mts" -> "video/mp2t"
            "mp4", "m4v", "mov", "3gp" -> "video/mp4"
            else -> "video/*"
        }
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
