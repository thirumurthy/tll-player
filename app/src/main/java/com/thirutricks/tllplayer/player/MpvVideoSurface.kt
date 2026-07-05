package com.thirutricks.tllplayer.player

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.util.Log
import android.graphics.Bitmap
import androidx.webkit.WebViewAssetLoader

private class MpvSurfaceView(context: Context, private val player: OwnTVPlayer) :
    SurfaceView(context), SurfaceHolder.Callback {

    private var pendingFps = 0f

    init {
        holder.addCallback(this)
        isFocusable = false
        isFocusableInTouchMode = false
    }

    /** Ask the display to switch to a refresh rate matching the video (TVs that support it drop the
     *  3:2-pulldown judder of 24fps content on a fixed 60Hz panel). Re-applied on each fps change and
     *  on surface (re)create. No-op below Android 11, or where the panel can't switch (harmless). */
    fun applyVideoFrameRate(fps: Float) {
        pendingFps = fps
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return
        val surface = holder.surface ?: return
        if (!surface.isValid || fps <= 0f) return
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, Surface.CHANGE_FRAME_RATE_ALWAYS)
            } else {
                @Suppress("DEPRECATION")
                surface.setFrameRate(fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
            }
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        player.attachSurface(holder.surface)
        if (pendingFps > 0f) applyVideoFrameRate(pendingFps) // re-assert after a surface recreate
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        player.setSurfaceSize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        player.detachSurface()
    }
}

/**
 * Hosts the mpv video output (a [SurfaceView]) in Compose.
 *
 * In direct render mode the decoder fills the surface edge-to-edge (no GL scaling), so zoom/aspect is
 * done by **sizing the view itself**: the surface is scaled/cropped/letterboxed by laying it out at the
 * target geometry inside a clipped black box (the same approach ExoPlayer/YouTube use). In GL mode mpv
 * scales internally (via [OwnTVPlayer.setZoomMode]'s properties) and the view just fills the slot.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MpvVideoSurface(player: OwnTVPlayer, modifier: Modifier = Modifier) {
    val direct by player.directRender.collectAsStateWithLifecycle()
    val aspect by player.videoAspect.collectAsStateWithLifecycle()
    val videoSize by player.videoSize.collectAsStateWithLifecycle()
    val zoom by player.zoomMode.collectAsStateWithLifecycle()
    val fps by player.videoFps.collectAsStateWithLifecycle()
    val playbackMode by player.playbackMode.collectAsStateWithLifecycle()
    val legacyWebUrl by player.legacyWebUrl.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    BoxWithConstraints(modifier.background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
        when (playbackMode) {
            OwnTVPlayer.PlaybackMode.LEGACY_WEB -> {
                val url = legacyWebUrl
                if (!url.isNullOrEmpty()) {
                    LegacyWebViewPlayer(url = url, modifier = Modifier.fillMaxSize())
                }
            }
            OwnTVPlayer.PlaybackMode.LEGACY_EXO -> {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        android.view.SurfaceView(ctx).apply {
                            isFocusable = false
                            isFocusableInTouchMode = false
                            holder.addCallback(object : android.view.SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                    player.attachSurface(holder.surface)
                                }
                                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                                    player.setSurfaceSize(width, height)
                                }
                                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                                    player.detachSurface()
                                }
                            })
                        }
                    }
                )
            }
            OwnTVPlayer.PlaybackMode.MPV -> {
                val a = aspect
                val viewModifier = if (!direct || a == null || a <= 0f) {
                    // GL mode letterboxes internally; or no video dimensions yet — just fill the slot.
                    Modifier.fillMaxSize()
                } else {
                    val cw = maxWidth
                    val ch = maxHeight
                    val containerAspect = cw.value / ch.value
                    when (zoom) {
                        ZoomMode.STRETCH -> Modifier.fillMaxSize()
                        ZoomMode.ORIGINAL -> {
                            val vs = videoSize
                            if (vs != null && vs.first > 0 && vs.second > 0) {
                                with(density) { Modifier.size(vs.first.toDp(), vs.second.toDp()) }
                            } else {
                                Modifier.aspectRatio(a)
                            }
                        }
                        else -> {
                            // Target box aspect for the view; the surface stretches to fill it.
                            val targetAspect = when (zoom) {
                                ZoomMode.FORCE_16_9 -> 16f / 9f
                                ZoomMode.FORCE_4_3 -> 4f / 3f
                                else -> a // FIT, FILL keep the video aspect
                            }
                            val cover = zoom == ZoomMode.FILL // cover the container (crop) vs contain (fit)
                            // contain: largest box of targetAspect fitting inside; cover: smallest covering it.
                            val widthDriven = if (cover) targetAspect < containerAspect else targetAspect >= containerAspect
                            if (widthDriven) {
                                Modifier.width(cw).height((cw.value / targetAspect).dp)
                            } else {
                                Modifier.height(ch).width((ch.value * targetAspect).dp)
                            }
                        }
                    }
                }
                // key(surfaceResetToken): when the player bumps the token, this whole AndroidView is disposed and
                // recreated — destroying the old Surface and making a FRESH one. The Realtek decoder needs a clean
                // Surface for a back-to-back 4K-class session (reusing the dirty one throws 0x80001000).
                val surfaceResetToken by player.surfaceResetToken.collectAsStateWithLifecycle()
                androidx.compose.runtime.key(surfaceResetToken) {
                    AndroidView(
                        modifier = viewModifier,
                        factory = { ctx -> MpvSurfaceView(ctx, player) },
                        // Match the display refresh rate to the video FPS once known (kills 24fps-on-60Hz judder).
                        update = { it.applyVideoFrameRate(fps ?: 0f) },
                    )
                }
                // Image-subtitle (PGS/VOBSUB/DVB) overlay for the ExoPlayer handoff. Mounted ONLY while ExoPlayer
                // owns playback — putting ANY view over the SurfaceView (even an empty one) knocks it off the
                // hardware-overlay / direct scan-out path, which stutters 4K to a ~2 fps slideshow under GPU
                // composition. During normal mpv playback this isn't composed, so the surface scans out directly.
                val exoActive by player.exoActiveState.collectAsStateWithLifecycle()
                val cues by player.exoCues.collectAsStateWithLifecycle()
                if (exoActive) {
                    AndroidView(
                        modifier = viewModifier,
                        factory = { ctx -> androidx.media3.ui.SubtitleView(ctx) },
                        update = { it.setCues(cues) },
                    )
                }
                // Freeze-frame: the last mpv frame, shown over the surface during the mpv→ExoPlayer swap so the
                // decoder switch doesn't flash black. Same geometry as the surface; cleared on Exo's first frame.
                val freeze by player.freezeFrame.collectAsStateWithLifecycle()
                freeze?.let { bmp ->
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = viewModifier,
                        contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                    )
                }
            }
        }
    }
}

/**
 * Hosts the [LivePreviewEngine]'s ExoPlayer video (a [SurfaceView]) for the Live preview pane. A plain
 * fill — the pane is a fixed 16:9 box and ExoPlayer letterboxes within it. The surface is handed to the
 * engine on create and released on destroy. [keepAwake] holds the screen on (TV screensaver off) while
 * actively watching full-screen/PiP — mpv playback is covered separately by the activity.
 */
@Composable
fun ExoPreviewSurface(engine: LivePreviewEngine, modifier: Modifier = Modifier, keepAwake: Boolean = false) {
    androidx.compose.foundation.layout.Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) = engine.setSurface(holder.surface)
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                        override fun surfaceDestroyed(holder: SurfaceHolder) = engine.setSurface(null)
                    })
                }
            },
            update = { it.keepScreenOn = keepAwake },
        )
        // Subtitle overlay — mounted ONLY while subs are on, so 4K live keeps its direct hardware-overlay path.
        val subOn by engine.subtitleOn.collectAsStateWithLifecycle()
        val cues by engine.cues.collectAsStateWithLifecycle()
        if (subOn) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx -> androidx.media3.ui.SubtitleView(ctx) },
                update = { it.setCues(cues) },
            )
        }
    }
}

/**
 * Converts a YouTube watch or short URL to an embed URL that auto-plays inside the WebView.
 * The embed player is simpler (no sign-in prompts, no ads, no fullscreen-in-fullscreen issues)
 * and plays immediately within the already-fullscreen player surface.
 *
 * Supported forms:
 *   https://www.youtube.com/watch?v=VIDEO_ID
 *   https://youtu.be/VIDEO_ID
 *   https://youtube.com/live/VIDEO_ID  (live stream short link)
 *   https://www.youtube.com/shorts/VIDEO_ID
 * Returns the original URL unchanged if it's already an embed or doesn't match.
 */
private fun convertToYoutubeEmbed(url: String): String {
    val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { return url }
    val host = uri.host?.lowercase() ?: return url
    if (!host.contains("youtube") && !host.contains("youtu.be")) return url
    // Already an embed — leave it alone
    if (uri.path?.startsWith("/embed/") == true || uri.path?.startsWith("/v/") == true) return url

    val videoId: String? = when {
        host == "youtu.be" -> uri.path?.trimStart('/')
        uri.path?.startsWith("/live/") == true -> uri.path?.removePrefix("/live/")
        uri.path?.startsWith("/shorts/") == true -> uri.path?.removePrefix("/shorts/")
        else -> uri.getQueryParameter("v")
    }
    return if (!videoId.isNullOrBlank()) {
        // Use youtube-nocookie.com (no tracking cookies) with autoplay, show controls, no related videos.
        // enablejsapi=1 allows JS interaction; playsinline=1 prevents a native fullscreen pop-up.
        "https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&controls=1&rel=0&enablejsapi=1&playsinline=1"
    } else url
}

private fun getYoutubeVideoId(url: String): String? {
    val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { null } ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (!host.contains("youtube") && !host.contains("youtu.be")) return null
    val path = uri.path ?: ""
    return when {
        host == "youtu.be" -> path.trimStart('/')
        path.startsWith("/live/") -> path.removePrefix("/live/")
        path.startsWith("/shorts/") -> path.removePrefix("/shorts/")
        path.startsWith("/embed/") -> path.removePrefix("/embed/")
        path.startsWith("/v/") -> path.removePrefix("/v/")
        else -> uri.getQueryParameter("v")
    }
}

private class AndroidTouchBridge(private val webView: android.webkit.WebView) {
    @android.webkit.JavascriptInterface
    fun dispatchClick(left: Float, top: Float, width: Float, height: Float) {
        webView.post {
            val density = webView.context.resources.displayMetrics.density
            val x = (left + width / 2f) * density
            val y = (top + height / 2f) * density
            android.util.Log.d("LegacyWebViewPlayer", "AndroidBridge.dispatchClick: CSS bounds=[L:$left, T:$top, W:$width, H:$height] -> Physical=[X:$x, Y:$y]")
            
            val downTime = android.os.SystemClock.uptimeMillis()
            val downEvent = android.view.MotionEvent.obtain(
                downTime, downTime,
                android.view.MotionEvent.ACTION_DOWN, x, y, 0
            )
            val upEvent = android.view.MotionEvent.obtain(
                downTime, downTime + 50,
                android.view.MotionEvent.ACTION_UP, x, y, 0
            )
            webView.dispatchTouchEvent(downEvent)
            webView.dispatchTouchEvent(upEvent)
            downEvent.recycle()
            upEvent.recycle()
            android.util.Log.d("LegacyWebViewPlayer", "Bridge Touch event dispatched successfully")
        }
    }
}


@Composable
fun LegacyWebViewPlayer(url: String, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Track a fullscreen custom-view so we can clean it up if the composable is released
    // while the web player is still in fullscreen mode.
    val customViewContainerRef = remember { androidx.compose.runtime.mutableStateOf<android.widget.FrameLayout?>(null) }
    val customViewRef = remember { androidx.compose.runtime.mutableStateOf<android.view.View?>(null) }
    val customViewCallbackRef = remember { androidx.compose.runtime.mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }

    fun showFullscreenView(view: android.view.View, callback: android.webkit.WebChromeClient.CustomViewCallback) {
        val activity = context as? android.app.Activity ?: return
        val decor = activity.window.decorView as android.widget.FrameLayout
        android.util.Log.d("LegacyWebViewPlayer", "showFullscreenView: HTML5 fullscreen requested, creating FrameLayout container")
        
        val container = android.widget.FrameLayout(context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        view.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        container.addView(view)
        decor.addView(container)
        
        customViewContainerRef.value = container
        customViewRef.value = view
        customViewCallbackRef.value = callback
        
        // Apply system UI flags for clean immersive fullscreen
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        android.util.Log.d("LegacyWebViewPlayer", "showFullscreenView: custom view successfully displayed full-screen")
    }

    fun hideFullscreenView() {
        val activity = context as? android.app.Activity ?: return
        val decor = activity.window.decorView as android.widget.FrameLayout
        android.util.Log.d("LegacyWebViewPlayer", "hideFullscreenView: exiting HTML5 fullscreen")
        
        customViewContainerRef.value?.let { container ->
            container.removeAllViews()
            decor.removeView(container)
        }
        
        customViewCallbackRef.value?.onCustomViewHidden()
        
        customViewContainerRef.value = null
        customViewRef.value = null
        customViewCallbackRef.value = null
        
        // Restore standard system UI visibility
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        android.util.Log.d("LegacyWebViewPlayer", "hideFullscreenView: fullscreen clean-up done")
    }

    val isYoutube = url.contains("youtube.com", ignoreCase = true) || 
                    url.contains("youtube-nocookie.com", ignoreCase = true) || 
                    url.contains("youtu.be", ignoreCase = true)

    val currentHtmlRef = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val originRefererHost = androidx.compose.runtime.remember(url) {
        val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { null }
        val referer = uri?.getQueryParameter("origin_referer")
        val host = referer?.let { try { android.net.Uri.parse(it).host } catch (e: Exception) { null } }
        if (!host.isNullOrEmpty()) host else "appassets.androidplatform.net"
    }
    val assetLoaderRef = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<WebViewAssetLoader?>(null) }
    val assetLoader = androidx.compose.runtime.remember(originRefererHost) {
        val loader = WebViewAssetLoader.Builder()
            .setDomain(originRefererHost)
            .addPathHandler("/youtube/", WebViewAssetLoader.PathHandler { path ->
                val htmlContent = currentHtmlRef.value
                android.util.Log.d("LegacyWebViewPlayer", "AssetLoader: handling request for host=$originRefererHost path=$path, html length=${htmlContent.length}")
                if (htmlContent.isNotEmpty()) {
                    val stream = java.io.ByteArrayInputStream(htmlContent.toByteArray(Charsets.UTF_8))
                    android.webkit.WebResourceResponse("text/html", "utf-8", stream)
                } else {
                    null
                }
            })
            .build()
        assetLoaderRef.value = loader
        loader
    }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                android.webkit.WebView.setWebContentsDebuggingEnabled(true)
            }
            android.webkit.WebView(ctx).apply {
                setBackgroundColor(android.graphics.Color.BLACK)

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    settings.allowUniversalAccessFromFileURLs = true
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // Register AndroidTouchBridge JavascriptInterface and log WebView setup
                addJavascriptInterface(AndroidTouchBridge(this), "AndroidBridge")
                android.util.Log.d("LegacyWebViewPlayer", "WebView initialized. mediaPlaybackRequiresUserGesture set to false, AndroidBridge registered")

                // YouTube embed / any video player needs focus to receive D-pad/remote events
                isClickable = isYoutube
                isFocusable = isYoutube
                isFocusableInTouchMode = isYoutube
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true

                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun getDefaultVideoPoster(): android.graphics.Bitmap {
                        return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                    }

                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        android.util.Log.d("LegacyWebViewPlayer", "Console: ${consoleMessage?.message()} at ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}")
                        return super.onConsoleMessage(consoleMessage)
                    }

                    override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                        super.onShowCustomView(view, callback)
                        showFullscreenView(view, callback)
                    }

                    override fun onHideCustomView() {
                        super.onHideCustomView()
                        hideFullscreenView()
                    }
                }

                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onReceivedError(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        android.util.Log.e("LegacyWebViewPlayer", "Resource error: ${error?.description} for ${request?.url}")
                    }

                    override fun onReceivedHttpError(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?,
                        errorResponse: android.webkit.WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        android.util.Log.e("LegacyWebViewPlayer", "HTTP error: ${errorResponse?.statusCode} for ${request?.url}")
                    }

                    override fun onReceivedSslError(
                        view: android.webkit.WebView?,
                        handler: android.webkit.SslErrorHandler,
                        error: android.net.http.SslError?
                    ) {
                        handler.proceed()
                    }

                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView?,
                        request: android.webkit.WebResourceRequest?
                    ): android.webkit.WebResourceResponse? {
                        val uri = request?.url ?: return null
                        
                        // 1. Try WebViewAssetLoader interception first
                        val loader = assetLoaderRef.value
                        if (loader != null) {
                            val assetResponse = loader.shouldInterceptRequest(uri)
                            if (assetResponse != null) {
                                android.util.Log.d("LegacyWebViewPlayer", "AssetLoader successfully intercepted local URL: $uri")
                                return assetResponse
                            }
                        }

                        val host = uri.host?.lowercase() ?: ""
                        if (host.contains("youtube") || host.contains("youtu.be")
                            || host.contains("ytimg.com") || host.contains("googlevideo.com")
                            || host.contains("googleusercontent.com") || host.contains("gstatic.com")) {
                            android.util.Log.d("LegacyWebViewPlayer", "Passthrough matched for trusted domain: $host (url: $uri)")
                            return null
                        }
                        if (uri?.host == "www.nmtv.cn" && uri.path?.endsWith(".css") == true) return null
                        if (uri?.host == "cdnjs.cloudflare.com" && uri.path?.endsWith("controls.min.css") == true) return null
                        if ((uri?.host == "www.btzx.com.cn" || uri?.host == "g.cbg.cn" || uri?.host == "www.ahtv.cn") && uri.path?.endsWith(".css") == true) return null
                        if (uri?.host == "www.yupptv.com" && uri.path?.endsWith("jioAds.js") == true) return null
                        if (uri?.path?.endsWith("gpt.js") == true) return null

                        if (request?.isForMainFrame == false && (uri?.path?.endsWith(".jpg") == true || uri?.path?.endsWith(".png") == true || uri?.path?.endsWith(".gif") == true || uri?.path?.endsWith(".css") == true)) {
                            return android.webkit.WebResourceResponse("text/plain", "utf-8", null)
                        }
                        if (uri?.host?.endsWith("cctvpic.com") == true && uri.path?.endsWith(".css") == true) {
                            return android.webkit.WebResourceResponse("text/plain", "utf-8", null)
                        }
                        if (uri?.host == "pagead2.googlesyndication.com" || uri?.host == "www.googletagmanager.com" || uri?.host == "jouwaikekaivep.net" || uri?.host == "instant.page" || uri?.path?.endsWith("adsbygoogle.js") == true || uri?.path?.endsWith("anti_copy.js") == true) {
                            return android.webkit.WebResourceResponse("text/plain", "utf-8", null)
                        }
                        return null
                    }

                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val uri = try { android.net.Uri.parse(url) } catch(e: Exception) { null } ?: return
                        val host = uri.host ?: return

                        val currentTag = view?.tag as? String
                        val isLocalHtmlPlayer = currentTag?.startsWith("youtube_") == true
                        android.util.Log.d("LegacyWebViewPlayer", "onPageFinished: host=$host url=$url isLocalHtmlPlayer=$isLocalHtmlPlayer tag=$currentTag")

                        if (isLocalHtmlPlayer) {
                            android.util.Log.d("LegacyWebViewPlayer", "Local HTML player detected, skipping third-party page JS injections")
                            return
                        }

                        android.util.Log.d("LegacyWebViewPlayer", "onPageFinished: host=$host url=$url")

                        // Override Element.prototype.click to intercept DOM click calls and route them via AndroidBridge
                        val overrideClickJs = """
                            (function() {
                                if (window.AndroidBridge && !Element.prototype._originalClick) {
                                    console.log("Overriding Element.prototype.click inside WebView to intercept programmatic clicks");
                                    Element.prototype._originalClick = Element.prototype.click;
                                    Element.prototype.click = function() {
                                        try {
                                            var rect = this.getBoundingClientRect();
                                            if (rect.width > 0 && rect.height > 0) {
                                                console.log("Element.click() intercepted for: " + this.tagName + " at CSS bounds: [L:" + rect.left + ", T:" + rect.top + ", W:" + rect.width + ", H:" + rect.height + "], passing to AndroidBridge");
                                                window.AndroidBridge.dispatchClick(rect.left, rect.top, rect.width, rect.height);
                                            } else {
                                                console.log("Element.click() tag: " + this.tagName + " has no size, using original click");
                                                this._originalClick();
                                            }
                                        } catch(e) {
                                            console.error("Intercepted click error", e);
                                            this._originalClick();
                                        }
                                    };
                                }
                            })()
                        """.trimIndent()
                        view?.evaluateJavascript(overrideClickJs, null)

                        val rawRes = when {
                            host == "tamilseithigal.in" || host == "www.tamilseithigal.in" -> com.thirutricks.tllplayer.R.raw.tamilseithigal
                            host.endsWith("youtube.com") || host.endsWith("youtube-nocookie.com") || host.endsWith("youtu.be") -> com.thirutricks.tllplayer.R.raw.ytp
                            host in listOf("tv.cctv.com", "www.gdtv.cn", "www.sztv.com.cn", "news.hbtv.com.cn", "www.nxtv.com.cn", "tv.gxtv.cn", "live.fjtv.net", "tc.hnntv.cn", "www.hebtv.com", "live.mgtv.com", "www.hnntv.cn", "cricktv.site", "www.gzstv.com", "www.cztv.com", "www.jlntv.cn", "www.qhbtv.com", "www.qhtb.cn", "www.hljtv.com", "static.hntv.tv", "www.btzx.com.cn", "live.snrtv.com", "www.yb983.com") -> com.thirutricks.tllplayer.R.raw.ahtv
                            host == "www.tvmalaysia.live" -> com.thirutricks.tllplayer.R.raw.tvmalaysia
                            host == "tllapp.dpdns.org" -> com.thirutricks.tllplayer.R.raw.snx
                            host == "www.setv.sh.cn" || host == "tapmadtv.live" -> com.thirutricks.tllplayer.R.raw.gdtv
                            host == "www.yangshipin.cn" -> com.thirutricks.tllplayer.R.raw.ysp
                            host == "l455o.com" || host == "filemoon.nl" || host == "filemoon.sx" -> com.thirutricks.tllplayer.R.raw.moon
                            host == "www.nmtv.cn" -> com.thirutricks.tllplayer.R.raw.nmgtv
                            host == "www.yntv.cn" -> com.thirutricks.tllplayer.R.raw.yntv
                            host == "www.xjtvs.com.cn" -> com.thirutricks.tllplayer.R.raw.xjtv
                            host == "www.sxrtv.com" -> com.thirutricks.tllplayer.R.raw.sxrtv
                            host == "www.cbg.cn" -> com.thirutricks.tllplayer.R.raw.cqtv
                            host == "live.kankanews.com" -> com.thirutricks.tllplayer.R.raw.shtv
                            else -> null
                        }

                        if (rawRes != null) {
                            try {
                                android.util.Log.d("LegacyWebViewPlayer", "Injecting JS rawRes=$rawRes for host=$host")
                                val js = context.resources.openRawResource(rawRes).bufferedReader().use { it.readText() }
                                evaluateJavascript(js, null)
                            } catch (e: Exception) {
                                Log.e("LegacyWebViewPlayer", "Failed to inject JS for $host", e)
                            }
                        }

                        if (host == "www.yupptv.com") {
                            loadUrl(
                                "javascript:(function() { " +
                                        "const divElement = document.createElement('div'); " +
                                        "divElement.id = 'overlayDiv'; " +
                                        "divElement.style.position = 'fixed'; " +
                                        "divElement.style.top = '0'; " +
                                        "divElement.style.left = '0'; " +
                                        "divElement.style.width = '100%'; " +
                                        "divElement.style.height = '100%'; " +
                                        "divElement.style.backgroundColor = '#000'; " +
                                        "divElement.style.zIndex = '99998'; " +
                                        "document.body.appendChild(divElement); " +
                                        "})()"
                            )
                            postDelayed({
                                try {
                                    val js = context.resources.openRawResource(com.thirutricks.tllplayer.R.raw.yupp).bufferedReader().use { it.readText() }
                                    evaluateJavascript(js, null)
                                } catch (e: Exception) {
                                    Log.e("LegacyWebViewPlayer", "Failed to inject Yupp JS", e)
                                }
                            }, 1000)
                        }
                    }
                }
            }
        },
        update = { webView ->
            // For third-party pages that embed YouTube (e.g. tamilseithigal.in), load the real
            // URL as-is and let the injected JS (tamilseithigal.js) handle fullscreen/autoplay.
            // Only use the local HTML YouTube player for direct youtube.com / youtu.be URLs.
            val isDirectYoutubeUrl = run {
                val h = try { android.net.Uri.parse(url).host?.lowercase() } catch (e: Exception) { null } ?: ""
                h.contains("youtube") || h.contains("youtu.be")
            }
            val videoId = if (isDirectYoutubeUrl) getYoutubeVideoId(url) else null
            if (videoId != null) {
                val targetKey = "youtube_$videoId"
                val currentTag = webView.tag as? String
                if (currentTag != targetKey) {
                    webView.tag = targetKey
                    webView.settings.userAgentString = null
                    
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                            <style>
                                body, html {
                                    margin: 0;
                                    padding: 0;
                                    width: 100% !important;
                                    height: 100% !important;
                                    background-color: transparent !important;
                                    overflow: hidden !important;
                                }
                                #player {
                                    width: 100% !important;
                                    height: 100% !important;
                                    position: absolute !important;
                                    top: 0 !important;
                                    left: 0 !important;
                                    border: none !important;
                                }
                            </style>
                        </head>
                        <body>
                            <div id="player"></div>
                            <script>
                                var tag = document.createElement('script');
                                tag.src = "https://www.youtube.com/iframe_api";
                                var firstScriptTag = document.getElementsByTagName('script')[0];
                                firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                                var player;
                                function onYouTubeIframeAPIReady() {
                                    console.log("YouTube API Ready, initializing videoId: " + "$videoId");
                                    player = new YT.Player('player', {
                                        width: '100%',
                                        height: '100%',
                                        videoId: '$videoId',
                                        playerVars: {
                                            'autoplay': 1,
                                            'mute': 0,
                                            'controls': 1,
                                            'rel': 0,
                                            'enablejsapi': 1,
                                            'playsinline': 1,
                                            'fs': 1
                                        },
                                        events: {
                                            'onReady': onPlayerReady,
                                            'onStateChange': onPlayerStateChange,
                                            'onError': onPlayerError
                                        }
                                    });
                                }

                                function onPlayerReady(event) {
                                    console.log("onPlayerReady: playing video");
                                    event.target.playVideo();
                                }

                                function onPlayerStateChange(event) {
                                    console.log("onPlayerStateChange: " + event.data);
                                    if (event.data === -1) { // UNSTARTED
                                        event.target.playVideo();
                                    }
                                }

                                function onPlayerError(event) {
                                    console.error("onPlayerError: " + event.data);
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    // Update dynamic PathHandler HTML content
                    currentHtmlRef.value = html

                    val loaderUrl = "https://$originRefererHost/youtube/index.html"
                    android.util.Log.d("LegacyWebViewPlayer", "Loading local HTML for YouTube video ID: $videoId via WebViewAssetLoader at URL: $loaderUrl")
                    webView.loadUrl(loaderUrl)
                }
            } else {
                val target = convertToYoutubeEmbed(url)
                val currentTag = webView.tag as? String
                if (currentTag != target) {
                    webView.tag = target
                    webView.settings.userAgentString = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
                    
                    android.util.Log.d("LegacyWebViewPlayer", "Loading non-YouTube target URL: $target")
                    val uri = try { android.net.Uri.parse(url) } catch (e: Exception) { null }
                    if (uri?.host == "tv.cctv.com") {
                        webView.evaluateJavascript("localStorage.setItem('cctv_live_resolution', '720');", null)
                    }
                    webView.loadUrl(target)
                }
            }
        },
        onRelease = { webView ->
            hideFullscreenView()
            webView.loadUrl("about:blank")
        }
    )
}
