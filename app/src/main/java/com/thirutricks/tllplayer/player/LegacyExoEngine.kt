package com.thirutricks.tllplayer.player

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import com.thirutricks.tllplayer.legacy.SP

@androidx.media3.common.util.UnstableApi
class LegacyExoEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onPlayingChanged(playing: Boolean)
        fun onBuffering(buffering: Boolean)
        fun onVideoSize(width: Int, height: Int)
        fun onPositionDuration(positionMs: Long, durationMs: Long)
        fun onFirstFrame()
        fun onError(message: String)
    }

    private var player: ExoPlayer? = null
    private var surface: Surface? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            callbacks.onPlayingChanged(isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            callbacks.onBuffering(playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_READY) emitPositionDuration()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                callbacks.onVideoSize(videoSize.width, videoSize.height)
            }
        }

        override fun onRenderedFirstFrame() {
            callbacks.onFirstFrame()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Legacy ExoPlayer error: ${error.message}", error)
            callbacks.onError(error.message ?: "Playback error")
        }
    }

    fun start(url: String, startPositionMs: Long, surface: Surface?) {
        this.surface = surface

        var videoUrl = url
        var drmConfig: DrmConfig? = null
        val requestHeaders = mutableMapOf<String, String>().apply {
            put("Cache-Control", "no-cache, no-store, must-revalidate")
            put("Pragma", "no-cache")
            put("Expires", "0")
        }
        var userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        // Some sources delimit the URL from its control params with a literal `?|` / `?%7C` (a pipe that
        // avoids clashing with a real query string); others use a normal `?`/`&` query. Detect either so
        // drmScheme/drmLicense (ClearKey) and the header overrides work in both forms.
        val regex = "(?i)(\\?\\|)|(\\?%7C)".toRegex()
        val matchResult = regex.find(url)
        val params: List<String> = if (matchResult != null) {
            // Pipe-delimited control params — strip them from the playback URL
            val splitIndex = matchResult.range.first
            videoUrl = url.substring(0, splitIndex)
            url.substring(matchResult.range.last + 1).split("&")
        } else if (videoUrl.contains("?")) {
            // Normal query string: KEEP the full URL including query params for playback (streams need
            // their real query params: mac=, stream=, play_token=, etc.). Only scan the params to extract
            // DRM and header overrides — do NOT strip the query from videoUrl.
            val q = videoUrl.indexOf('?')
            val qString = videoUrl.substring(q + 1)
            // videoUrl stays unchanged (full URL with query string preserved)
            qString.split("&")
        } else {
            emptyList()
        }

        for (param in params) {
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()

                when (key.lowercase()) {
                    "user-agent" -> userAgent = value
                    "cookie" -> requestHeaders["Cookie"] = value
                    "referer" -> requestHeaders["Referer"] = value
                    "origin" -> requestHeaders["Origin"] = value
                    "x-forwarded-for" -> requestHeaders["X-Forwarded-For"] = value
                }
            }
        }

        val queryParams = params.associate {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
        }

        val schemeKey = queryParams.keys.find { it.equals("drmScheme", ignoreCase = true) }
        val licenseKey = queryParams.keys.find { it.equals("drmLicense", ignoreCase = true) }

        if (schemeKey != null && queryParams[schemeKey]?.lowercase() == "clearkey") {
            val drmLicense = queryParams[licenseKey]
            if (drmLicense != null) {
                drmConfig = DrmConfig("clearkey", drmLicense)
            }
        }

        // Add a dynamic cache-busting timestamp to force the CDN/proxy/server to deliver fresh live-edge
        // chunks on every reconnect. SKIP for URLs with authentication tokens (play_token, token=, auth=,
        // secret=) — appending extra params changes the URL the server validates and causes 401/403 rejections
        // on Stalker portals and other token-gated streams.
        val hasAuthToken = videoUrl.contains("play_token=", ignoreCase = true)
            || videoUrl.contains("&token=", ignoreCase = true)
            || videoUrl.contains("?token=", ignoreCase = true)
            || videoUrl.contains("auth=", ignoreCase = true)
            || videoUrl.contains("secret=", ignoreCase = true)
        if (!hasAuthToken && (videoUrl.startsWith("http://", ignoreCase = true) || videoUrl.startsWith("https://", ignoreCase = true))) {
            val timestamp = System.currentTimeMillis()
            videoUrl = if (videoUrl.contains("?")) {
                "$videoUrl&_t=$timestamp"
            } else {
                "$videoUrl?_t=$timestamp"
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000, // MIN_BUFFER_MS
                60000, // MAX_BUFFER_MS
                4000,  // Buffer for playback
                8000   // Buffer after rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(requestHeaders)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        if (drmConfig != null && drmConfig.scheme == "clearkey") {
            if (drmConfig.license.startsWith("http://", ignoreCase = true) || drmConfig.license.startsWith("https://", ignoreCase = true)) {
                val drmCallback = HttpMediaDrmCallback(drmConfig.license, dataSourceFactory)
                val drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(drmCallback)
                mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
            } else {
                try {
                    val drmCallback = LocalMediaDrmCallback(createClearKeyJson(drmConfig.license).toByteArray())
                    val drmSessionManager = DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(drmCallback)
                    mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to configure ClearKey DRM with local key: ${drmConfig.license}", e)
                }
            }
        }

        val trackSelector = DefaultTrackSelector(context)
        if (!SP.forceHighQuality) {
            trackSelector.parameters = trackSelector.parameters.buildUpon()
                .setMaxVideoSizeSd()
                .build()
        }

        val p = ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context))
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()

        p.addListener(listener)
        if (surface != null) p.setVideoSurface(surface)
        
        val mediaItemBuilder = MediaItem.Builder().setUri(videoUrl)
        if (drmConfig != null && drmConfig.scheme == "clearkey") {
            val drmBuilder = MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
            if (drmConfig.license.startsWith("http://", ignoreCase = true) || drmConfig.license.startsWith("https://", ignoreCase = true)) {
                drmBuilder.setLicenseUri(Uri.parse(drmConfig.license))
            }
            mediaItemBuilder.setDrmConfiguration(drmBuilder.build())
        }
        p.setMediaItem(mediaItemBuilder.build())
        p.prepare()
        if (startPositionMs > 0) p.seekTo(startPositionMs)
        p.playWhenReady = true
        player = p
    }

    fun setSurface(surface: Surface?) {
        this.surface = surface
        if (surface != null) player?.setVideoSurface(surface) else player?.clearVideoSurface()
    }

    fun play() { player?.play() }
    fun pause() { player?.pause() }
    fun togglePlayPause() { player?.let { if (it.isPlaying) it.pause() else it.play() } }

    fun seekTo(positionMs: Long) { player?.seekTo(positionMs.coerceAtLeast(0)); emitPositionDuration() }
    fun seekBy(deltaMs: Long) {
        val p = player ?: return
        p.seekTo((p.currentPosition + deltaMs).coerceAtLeast(0))
        emitPositionDuration()
    }

    fun setVolume(percent: Int) { player?.volume = (percent / 100f).coerceIn(0f, 1f) }

    fun emitPositionDuration() {
        val p = player ?: return
        val dur = p.duration.let { if (it == C.TIME_UNSET) 0L else it }
        callbacks.onPositionDuration(p.currentPosition.coerceAtLeast(0), dur.coerceAtLeast(0))
    }

    fun stop() {
        player?.let { p ->
            p.removeListener(listener)
            p.clearVideoSurface()
            p.release()
        }
        player = null
    }

    private data class DrmConfig(val scheme: String, val license: String)

    private fun createClearKeyJson(license: String): String {
        val parts = license.split(":")
        if (parts.size < 2) {
            Log.e(TAG, "Invalid ClearKey license format (expected keyId:key): $license")
            return ""
        }
        val keyIdHex = parts[0]
        val keyHex = parts[1]

        val keyIdBase64 = hexToBase64Url(keyIdHex)
        val keyBase64 = hexToBase64Url(keyHex)

        val keyObject = JSONObject().apply {
            put("kty", "oct")
            put("k", keyBase64)
            put("kid", keyIdBase64)
        }

        val keysArray = JSONArray().apply {
            put(keyObject)
        }

        return JSONObject().apply {
            put("keys", keysArray)
            put("type", "temporary")
        }.toString()
    }

    private fun hexToBase64Url(hex: String): String {
        return try {
            val cleaned = hex.trim()
            val bytes = ByteArray(cleaned.length / 2)
            for (i in bytes.indices) {
                val index = i * 2
                val j = Integer.parseInt(cleaned.substring(index, index + 2), 16)
                bytes[i] = j.toByte()
            }
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "hexToBase64Url failed for input: $hex", e)
            ""
        }
    }

    companion object {
        private const val TAG = "LegacyExoEngine"
    }
}
