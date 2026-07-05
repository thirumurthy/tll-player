package com.thirutricks.tllplayer.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

/**
 * Thin OkHttp wrapper for fetching M3U playlists and Xtream JSON. [get] streams the response body to
 * a block (so huge payloads are never fully buffered) and always closes the response. A per-source
 * custom User-Agent can be supplied (Phase 12 power feature).
 */
class HttpClient(private val client: OkHttpClient) {

    fun <T> get(url: String, userAgent: String? = null, block: (InputStream) -> T): T {
        // Many IPTV panels reject requests that don't look like a media player (or that use the
        // default OkHttp UA), so we send a player-style default unless the source overrides it
        // (custom User-Agent is a Phase 12 power feature).
        val ua = userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${redact(url)}")
            val body = response.body ?: throw IOException("Empty response body for ${redact(url)}")
            return block(body.byteStream())
        }
    }

    /** Mask credentials in a URL before it appears in an error/log — Xtream embeds user/pass in the query. */
    private fun redact(url: String): String =
        url.replace(Regex("(?i)(username|password|user|pass|token)=[^&]*"), "$1=***")

    /** Convenience for small responses (e.g. Xtream category lists). */
    fun getText(url: String, userAgent: String? = null): String =
        get(url, userAgent) { it.readBytes().decodeToString() }

    companion object {
        /** Player-style UA that IPTV panels broadly accept. Overridable per-source in Phase 12. */
        const val DEFAULT_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"

        /** Mask credentials for display (info overlay / logs): query params AND Xtream `/type/user/pass/`
         *  path segments, which is where live URLs embed them. */
        fun redactUrl(url: String): String = url
            .replace(Regex("(?i)(username|password|user|pass|token)=[^&]*"), "$1=***")
            .replace(Regex("(?i)(://[^/]+/(?:live|movie|series|vod)/)([^/]+)/([^/]+)/"), "$1•••/•••/")
    }
}

