package com.thirutricks.tllplayer.player

import android.content.Context
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thirutricks.tllplayer.core.network.HttpClient
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A selectable audio/subtitle track. [mpvId] is the mpv track id used for `aid`/`sid` (when ExoPlayer
 * owns playback its tracks reuse this field as an opaque ordinal). [image] flags an image-based subtitle
 * (PGS/VOBSUB/DVB) — selecting one on a VOD hands playback to ExoPlayer to render it. [typeIndex] is the
 * track's 0-based position among tracks of its own type, used to line a picked sub up with ExoPlayer's. */
data class TrackOption(
    val label: String,
    val mpvId: Int,
    val selected: Boolean,
    val image: Boolean = false,
    val codec: String? = null,
    val lang: String? = null,
    val typeIndex: Int = -1,
)

/** Image-based subtitle codecs. They carry no text, so the app-drawn (direct render) overlay can't show
 *  them on mpv's direct path. On VOD we hand playback to ExoPlayer, which renders them on its own layer. */
private val BITMAP_SUB_CODECS = setOf(
    "hdmv_pgs_subtitle", "pgssub", "dvd_subtitle", "dvdsub", "vobsub", "dvb_subtitle", "dvbsub", "xsub",
)

/** Audio codecs ExoPlayer can reliably decode (MediaCodec / built-in). If the active audio isn't one of
 *  these (e.g. DTS, TrueHD) we DON'T hand off — the handoff would just fail and bounce back to mpv. mpv
 *  decodes these in software via FFmpeg; ExoPlayer doesn't. Video is the same MediaCodec under both, so
 *  only audio gates the handoff. Matched against the mpv `codec` string with a prefix check. */
private val EXO_SAFE_AUDIO_CODECS = setOf(
    "aac", "ac3", "eac3", "mp3", "mp2", "opus", "vorbis", "flac", "pcm", "alac",
)

/** Metadata shown in the player HUD (breadcrumb path, year, channel logo). */
data class MediaMeta(
    val title: String? = null,
    val subtitle: String? = null,
    val year: String? = null,
    val logoUrl: String? = null,
)

/** An item in a play queue (e.g. a season's episodes), for prev/next. */
data class PlaylistItem(val url: String, val meta: MediaMeta = MediaMeta(), val sourceType: com.thirutricks.tllplayer.core.model.SourceType? = null)

/** Whether prev/next are available in the current queue. */
data class NavState(val hasPrev: Boolean, val hasNext: Boolean)

/** Video scaling modes exposed in the player's zoom menu. */
enum class ZoomMode(val label: String) {
    FIT("Fit Screen"), FILL("Fill / Crop"), STRETCH("Stretch"),
    ORIGINAL("Original (1:1)"), FORCE_16_9("Force 16:9"), FORCE_4_3("Force 4:3"),
}

/**
 * App-wide single libmpv player. mpv (FFmpeg) decodes virtually any codec/container and exposes every
 * audio/subtitle track — the right engine for IPTV (ExoPlayer only surfaced device-decodable tracks).
 * Also gives caching, playback speed, etc. State is published as StateFlows for the Compose HUD.
 *
 * For the one case mpv's direct path can't render — a VOD with an **image** subtitle (PGS/VOBSUB/DVB) —
 * it hands playback to [ExoSubtitleEngine] (ExoPlayer), which keeps video zero-copy AND draws the bitmap
 * sub on its own layer. The handoff is transparent: ExoPlayer's state is mirrored into these same flows.
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
class OwnTVPlayer(
    private val context: Context,
    private val settings: SettingsRepository,
    private val connectivity: com.thirutricks.tllplayer.core.network.ConnectivityObserver,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val diagnostics: PlayerDiagnostics,
) : MPVLib.EventObserver {

    private companion object {
        const val TAG = "OwnTVPlayer"
        const val MAX_AUTO_RETRIES = 3 // silent retries (backoff) before showing the error UI
        // Warn-level mpv lines worth keeping as the failure reason (HTTP codes, open/decode failures, …).
        val FAILURE_RX = Regex(
            "http|error|fail|refus|timed out|unrecogn|cannot|no such|invalid|denied|forbidden|not found|" +
                "unsupported|connection|reset|4\\d\\d|5\\d\\d",
            RegexOption.IGNORE_CASE,
        )
        // Generic "consequence" lines that shouldn't overwrite a more specific captured cause.
        val GENERIC_FAIL_RX = Regex(
            "failed to open|opening failed|could not open|loading failed|was aborted|finished playback",
            RegexOption.IGNORE_CASE,
        )
    }

    // The app renders with mpv's direct decoder-to-surface output (vo=mediacodec_embed) — the same
    // zero-copy pipeline YouTube/Netflix use, and the right one for TV hardware. mpv's GL renderer is
    // kept ONLY as the automatic software-decode rescue (hwdec=no): the direct surface can't display
    // software-decoded frames, so those go through vo=gpu. The Android emulator's *translated* GL is
    // broken and hard-crashes the process, so on emulators we never attempt the GL rescue — we show a
    // clean "can't decode" error instead. Real TVs (incl. Fire TV) run the GL rescue fine.
    private val glUnsupported: Boolean by lazy { isProbablyEmulator() }
    private fun isProbablyEmulator(): Boolean {
        val fp = android.os.Build.FINGERPRINT
        val model = android.os.Build.MODEL
        return fp.startsWith("generic") || fp.startsWith("unknown") ||
            fp.contains("emulator", true) || fp.contains("/sdk_") ||
            model.contains("google_sdk") || model.contains("Emulator") ||
            model.contains("Android SDK built for") ||
            android.os.Build.MANUFACTURER.contains("Genymotion") ||
            android.os.Build.HARDWARE.contains("goldfish") || android.os.Build.HARDWARE.contains("ranchu") ||
            android.os.Build.PRODUCT.contains("sdk") || android.os.Build.PRODUCT.contains("emulator") ||
            android.os.Build.PRODUCT.contains("simulator")
    }

    private var mpv: MPVLib? = null
    private var initialized = false
    private var pendingSeekMs = 0L
    @Volatile private var pendingStartPaused = false // load this item paused (restore a backgrounded VOD)
    private var currentUrl: String? = null
    private var expectingPlayback = false
    // Snapshot of a non-live item taken when the app backgrounds (screensaver / Home), so it can be restored
    // paused at its position on return — otherwise the stream is freed and Play does nothing until a reload.
    private data class BackgroundRestore(val url: String, val meta: MediaMeta, val positionMs: Long, val wasPlaying: Boolean)
    @Volatile private var backgroundRestore: BackgroundRestore? = null
    private var playlist: List<PlaylistItem> = emptyList()
    private var playlistIndex = 0
    // mpv's android video output needs a surface at loadfile time, or it deselects video (audio-only).
    // So when no surface is attached yet we defer the load until attachSurface().
    private var surfaceAttached = false
    private var pendingUrl: String? = null
    private var hdrHint = true
    private var playerBudget: PlayerBudget? = null

    // Decode watchdog state: if a >1080p video ends up on the software decoder, playback is aborted
    // with a friendly error — CPU-decoding 4K/8K on a TV chip stutters, overheats, and OOM-kills the
    // app (observed on a TCL G10). Both values arrive on mpv's event thread per loaded file.
    @Volatile private var currentHwdec: String? = null
    @Volatile private var currentHeightPx = 0
    @Volatile private var currentWidthPx = 0
    @Volatile private var decodeGuardTripped = false
    // Last successfully-decoded video height (persists across loads). Used to decide recovery when a load
    // fails before any frame: a stream we know is >1080p must NOT fall back to software decode (the guard
    // would just kill it) — we retry the hardware decoder instead, which is what a manual Retry does.
    @Volatile private var lastVideoHeightPx = 0

    // --- Render path -------------------------------------------------------------------------
    // Always direct (vo=mediacodec_embed + hwdec=mediacodec): zero CPU copies, no GL shader work, the
    // panel's own silicon renders HDR — the same pipeline YouTube/Netflix use, and the only one weak TV
    // SoCs play 4K smoothly on. The GL renderer (vo=gpu) is used ONLY when hardware decoding is off (the
    // user setting) or the per-item software rescue kicks in — the direct surface can't show SW frames.
    // Silent auto-retry budget for a load that fails to start (transient: cold-boot decoder-busy,
    // a provider 5xx, the surface-timing race). Reset per genuinely-new item; counts up across
    // retries with backoff, then the error UI + manual Retry takes over.
    @Volatile private var autoRetries = 0
    // A stream the hardware decoder can't start (weak TV decoders — e.g. a Fire TV Stick 3rd gen reject
    // some otherwise-fine channels/VOD with "unsupported format") is retried ONCE in pure software,
    // per item, before the error shows — so the user no longer has to flip the global hardware-decoding
    // setting off. Per-item only; never changes the user's setting. Reset on each genuinely-new item.
    @Volatile private var forceSoftwareThisLoad = false
    // A live Xtream stream that won't start on the default `.ts` (MPEG-TS) endpoint is retried once on
    // the provider's `.m3u8` (HLS) variant before erroring — covers the rare panel that only serves HLS.
    // Per-item; reset on each genuinely-new item.
    @Volatile private var triedAltFormat = false
    private val _directRender = MutableStateFlow(false)
    /** True while the direct (decoder-to-surface) output is in use — HUD hides zoom, app draws subs. */
    val directRender: StateFlow<Boolean> = _directRender.asStateFlow()

    /** Hardware decoding effectively in use right now — the global setting, minus a per-item override
     *  forced on after the hardware decoder failed to start a stream. */
    private fun hwDecodingActive(): Boolean = hwDecoding && !forceSoftwareThisLoad

    /** Silent-retry budget per content type: Live TV is worth retrying (cold-boot decoder lag, server
     *  hiccups). VOD gets 2 — most failures are bad links, but a back-to-back load (e.g. auto-play to the
     *  next episode) can hit a transient hardware-decoder error (Realtek 0x80001000) that a quick direct
     *  retry clears, exactly like a manual Retry. */
    private fun maxRetries(): Int = if (isLiveContent) MAX_AUTO_RETRIES else 2

    /** Exponential backoff between silent retries: 1s, 2s, 4s for attempts 1..3 — gives the cold-boot
     *  decoder a bit more breathing room each time. */
    private fun backoffMs(attempt: Int): Long = 1000L * (1L shl (attempt - 1).coerceIn(0, 5))

    // Image subtitles (PGS/VOBSUB/DVB) used to drop the whole player into GL compositing (vo=gpu +
    // hwdec=mediacodec-copy) to draw them — which copies every 4K HDR frame and made playback unwatchable
    // on TV-class hardware. That fallback is GONE: video now ALWAYS stays on the direct path, and image
    // subs on a VOD are handled by handing playback to ExoPlayer (see [handoffToExo]) instead.
    private fun targetHwdec(): String = if (hwDecodingActive()) "mediacodec" else "no"
    private fun targetVo(): String = if (hwDecodingActive()) "mediacodec_embed" else "gpu"

    /** Direct decoder-to-surface output. Non-direct = software decode only (hwdec off / per-item rescue),
     *  which the direct surface can't display, so it goes through the GL renderer. */
    private fun useDirect(): Boolean = targetVo() == "mediacodec_embed"

    /** Apply vo/hwdec for the current render path (also safe live — mpv reinits decoder/output). */
    private fun MPVLib.applyRenderConfig() {
        setPropertyString("hwdec", targetHwdec())
        if (surfaceAttached) setPropertyString("vo", targetVo())
        _directRender.value = targetVo() == "mediacodec_embed"
    }

    /** mpv `audio-channels`: surround on → multichannel LPCM where the sink **unambiguously** supports it
     *  (`auto-safe`), else a safe stereo downmix; surround off → force stereo. `auto-safe` (not `auto`)
     *  because some sinks falsely claim 5.1/7.1. If a sink claims support but actually mis-plays multichannel
     *  PCM (the "2× speed, no sound", #25), [surroundOutputBroken] is latched by the runaway detector and we
     *  force stereo for the rest of the session. Always decoded PCM, so the audio clock stays alive. */
    private fun audioChannelsValue(): String = if (surroundSound && !surroundOutputBroken) "auto-safe" else "stereo"
    // Latched when surround output is detected broken on this device (audio drains ~2× → runaway video).
    @Volatile private var surroundOutputBroken = false

    /**
     * Trim FFmpeg's stream probe for **live** sources so channels start faster (the default ~5 MB / 5 s
     * probe adds ~1 s of black before the first frame). VOD keeps the full probe so HDR colorspace and
     * all tracks are detected. If a trimmed live load returns no audio, [forceFullProbe] re-probes fully.
     */
    private fun MPVLib.applyProbeProfile(url: String) {
        val lower = url.lowercase()
        // Raw continuous MPEG-TS (Xtream live `…/id.ts`, catch-up timeshift `.ts`). These probe fast and
        // start mid-stream, so they're the streams fast-zap trimming was built for — and the proven-safe
        // case (Xtream live works). HLS (.m3u8) and other/extensionless live URLs are NOT trimmed: they need
        // the full probe (playlist + a segment) to open cleanly, and a trimmed probe handed mpv incomplete
        // info → the stream opened but the playloop wedged (regression: M3U HLS live hung after the decoder
        // inited, while v2.2.4 — which always full-probed — played). So trim ONLY raw TS, full-probe the rest.
        val rawTs = lower.contains(".ts") || lower.contains("/timeshift/")
        // Make FFmpeg RECONNECT when a live server closes the HTTP connection (some drop the socket every
        // few seconds; without this mpv hits EOF → the app reconnects → a black/decoder-churn loop). NOT for
        // VOD/catch-up (isLiveContent=false) — those have a real end and must be allowed to finish.
        val reconnect = "reconnect=1,reconnect_streamed=1,reconnect_delay_max=8,reconnect_on_http_error=5xx"
        // `reconnect_at_eof` keeps a CONTINUOUS stream going across mid-stream EOFs — but it also reconnects
        // on the EOF that ends a *finite* HTTP response (an HLS .m3u8 playlist, a redirect, …), looping
        // forever during OPEN so the stream never starts. So enable it only for raw MPEG-TS live.
        val eofReconnect = if (isLiveContent && lower.contains(".ts")) ",reconnect_at_eof=1" else ""
        setPropertyString("stream-lavf-o", "$reconnect$eofReconnect")
        val trim = rawTs && !forceFullProbe
        usedTrimmedProbe = trim
        if (!trim) {
            // Full probe (mpv default 0 → FFmpeg's 5 MB / 5 s) — needed for HDR, complete track lists, and
            // to open HLS/other live cleanly. Capping the analyze time (even to 2.5 s) wedges mpv's HLS open
            // on this hardware — it never reaches the decoder — so the full probe is required. This is the
            // ~3–5 s full-screen startup floor for HLS (vs the instant ExoPlayer preview).
            setPropertyString("demuxer-lavf-probesize", "0")
            setPropertyString("demuxer-lavf-analyzeduration", "0")
            setPropertyString("demuxer-lavf-o", "")
            return
        }
        setPropertyString("demuxer-lavf-probesize", "1000000")
        setPropertyString("demuxer-lavf-analyzeduration", "1.0") // ~1s keeps HDR/colorspace detection safe
        setPropertyString("demuxer-lavf-o", "fflags=+nobuffer+genpts")
    }

    /** Reload the current item at its position (used when a setting change needs the chain re-inited). */
    private fun reloadCurrentInPlace() {
        val url = currentUrl ?: return
        val gen = loadGeneration
        scope.launch {
            if (gen != loadGeneration) return@launch
            loadUrl(url, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, if (isLiveContent) 0L else _position.value, resetRetries = false)
        }
    }

    // Video Player Settings — cached so ensureInit can apply them as mpv options, and the observers
    // below apply changes live to a running player.
    private var hwDecoding = true
    private var surroundSound = false // off by default (opt-in); see SettingsRepository.surroundSound (#25)
    private var autoPlayNext = true
    private var subScale = 1.0
    private var audioDelaySec = 0.0
    private var baseAudioDelayMs = 0 // the Settings audio-delay; each new file resets the in-player nudge to it
    private val _audioDelayMs = MutableStateFlow(0)
    /** Effective audio delay in ms (Settings default + the in-player A/V-sync nudge). */
    val audioDelayMs: StateFlow<Int> = _audioDelayMs.asStateFlow()
    private var prefAudioLang = ""
    private var prefSubLang = ""
    private var defaultZoom = ZoomMode.FIT

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * All mpv commands/property writes run on this single worker thread, never on the UI thread.
     * libmpv calls are synchronous and can block for seconds while the core is stuck in a stalling
     * network read (flaky live streams) — issuing them from the main thread caused ANRs ("Input
     * dispatching timed out"). A single thread keeps the original call order.
     */
    private val mpvExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "mpv-cmd").apply { isDaemon = true }
    }

    private fun mpvAsync(block: MPVLib.() -> Unit) {
        val m = mpv ?: return
        mpvExecutor.execute { runCatching { m.block() } }
    }

    private fun markActiveFile(active: Boolean, reason: String) {
        mpvHasActiveFile.set(active)
    }

    private fun MPVLib.incrementPendingStopCounter(reason: String): Boolean {
        if (!mpvHasActiveFile.get()) return false
        pendingStopEndFiles.incrementAndGet()
        return true
    }

    private fun MPVLib.rollbackPendingStopCounter(reason: String) {
        while (true) {
            val current = pendingStopEndFiles.get()
            if (current <= 0) return
            if (pendingStopEndFiles.compareAndSet(current, current - 1)) {
                return
            }
        }
    }

    private fun MPVLib.loadfileWithStopClassification(url: String, reason: String) {
        val counted = incrementPendingStopCounter(reason)
        try {
            command(arrayOf("loadfile", url))
            markActiveFile(true, reason)
        } catch (t: Throwable) {
            if (counted) rollbackPendingStopCounter(reason)
            throw t
        }
    }

    private fun MPVLib.stopWithStopClassification(reason: String) {
        val counted = incrementPendingStopCounter(reason)
        try {
            command(arrayOf("stop"))
            markActiveFile(false, reason)
        } catch (t: Throwable) {
            if (counted) rollbackPendingStopCounter(reason)
            throw t
        }
    }

    private fun consumePendingStopEndFile(): Boolean {
        while (true) {
            val current = pendingStopEndFiles.get()
            if (current <= 0) return false
            if (pendingStopEndFiles.compareAndSet(current, current - 1)) {
                return true
            }
        }
    }

    init {
        // Track the HDR setting; apply it live and re-apply on each load via ensureInit.
        settings.hdrEnabled.onEach { enabled ->
            hdrHint = enabled
            if (initialized) mpvAsync { setPropertyString("target-colorspace-hint", if (enabled) "yes" else "no") }
        }.launchIn(scope)
        settings.hwDecoding.onEach { on ->
            hwDecoding = on
            if (initialized) mpvAsync { applyRenderConfig() }
        }.launchIn(scope)
        settings.surroundSound.onEach { on ->
            surroundSound = on
            if (on) surroundOutputBroken = false // re-enabling surround = a fresh attempt at multichannel
            // A reload re-inits the audio chain so the new channel layout takes effect on the playing stream.
            if (initialized) {
                mpvAsync {
                    val sur = surroundSound && !surroundOutputBroken
                    setPropertyString("audio-channels", audioChannelsValue())
                    setPropertyString("audio-format", if (sur) "s16" else "")
                    setPropertyString("audio-samplerate", if (sur) "48000" else "0")
                }
                reloadCurrentInPlace()
            }
        }.launchIn(scope)
        settings.autoPlayNext.onEach { autoPlayNext = it }.launchIn(scope)
        settings.subtitleScale.onEach { s ->
            subScale = s.toDouble()
            if (initialized) mpvAsync { setPropertyDouble("sub-scale", subScale) }
        }.launchIn(scope)
        settings.audioDelayMs.onEach { ms ->
            baseAudioDelayMs = ms // the Settings default each new file resets to
            applyAudioDelay(ms)
        }.launchIn(scope)
        settings.preferredAudioLang.onEach { lang ->
            prefAudioLang = lang
            if (initialized && lang.isNotBlank()) mpvAsync { setPropertyString("alang", lang) }
        }.launchIn(scope)
        settings.preferredSubLang.onEach { lang ->
            prefSubLang = lang
            if (initialized && lang.isNotBlank()) mpvAsync { setPropertyString("slang", lang) }
        }.launchIn(scope)
        settings.defaultZoom.onEach { name ->
            defaultZoom = runCatching { ZoomMode.valueOf(name) }.getOrDefault(ZoomMode.FIT)
        }.launchIn(scope)
        // Subtitle overlay is fed by OBSERVING "sub-text" (see eventProperty) — not polling. The old
        // 250 ms getPropertyString poll logged a "property unavailable" error 4×/sec whenever no line
        // was on screen, flooding logcat and burning a cross-thread call the whole time.
    }
    // Bumped on every load/stop so stale work can tell it's been superseded: the end-of-file error
    // check, and queued loadfile commands (fast preview scrolling queues a burst — only the newest
    // may run, or a slow provider makes the worker grind through dead loads). Volatile: written on
    // the main thread, read on the mpv-cmd worker.
    @Volatile private var loadGeneration = 0
    // App-issued loadfile/stop commands can leave a cleanup END_FILE behind. Track those separately
    // so mpv's event thread can classify them as STOP instead of startup failure or reconnect.
    private val pendingStopEndFiles = AtomicInteger(0)
    private val mpvHasActiveFile = AtomicBoolean(false)
    private var errorCheckJob: Job? = null
    private var videoCheckJob: Job? = null
    // Catch-up/VOD streams that start mid-GOP (no H.264 SPS/PPS yet) can play audio with a blank video.
    // We try a software-decode reload once before surfacing an error, tracked per item.
    @Volatile private var triedSoftwareForVideo = false
    // Fast-zap probe trimming (live only). usedTrimmedProbe = this load used a trimmed probe;
    // forceFullProbe = a trimmed load came back with no audio, so re-probe fully (the safety net).
    @Volatile private var usedTrimmedProbe = false
    @Volatile private var forceFullProbe = false

    private val _nav = MutableStateFlow(NavState(false, false))
    val nav: StateFlow<NavState> = _nav.asStateFlow()

    // Emitted when the LAST item of an episode queue finishes naturally and auto-play is on, so the
    // series ViewModel can continue into the next season (it has the full series; the player only has
    // the current season's queue). Within-season advance is handled by the player itself.
    private val _queueEnded = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val queueEnded: kotlinx.coroutines.flow.SharedFlow<Unit> = _queueEnded

    var currentTitle: String? = null
        private set
    var currentSubtitle: String? = null
        private set
    var currentYear: String? = null
        private set
    var currentLogoUrl: String? = null
        private set
    var isLiveContent: Boolean = false
        private set

    // Reactive copy of the current item's metadata so Compose recomposes the HUD's title / channel
    // card the instant a new stream loads — channel zapping changes the plain vars above, but a plain
    // var isn't observed, so the "now watching" card showed the previous channel's name.
    private val _currentMeta = MutableStateFlow(MediaMeta())
    val currentMeta: StateFlow<MediaMeta> = _currentMeta.asStateFlow()

    private var preMuteVolume = 100

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _errorInfo = MutableStateFlow<ErrorInfo?>(null)
    val errorInfo: StateFlow<ErrorInfo?> = _errorInfo.asStateFlow()
    @Volatile private var currentVideoCodec: String? = null // for the error screen's media spec line
    // Last warn/error-level mpv log line (e.g. "ffmpeg: http: HTTP error 400", "stream: Failed to open").
    // Snapshotted into [_errorInfo] when an error surfaces, so the HUD can show the real cause. Reset per load.
    @Volatile private var lastMpvError: String? = null

    // Captures mpv's own error output (msg-level=warn, set even in release) so the real failure reason is
    // available to show the user. Keeps error/fatal lines, plus warn lines that look like an actual failure
    // (HTTP codes, "failed", "unrecognized", etc.) — ignoring benign per-frame warnings.
    private val logObserver = object : MPVLib.LogObserver {
        override fun logMessage(prefix: String, level: Int, text: String) {
            val t = text.trim()
            if (t.isEmpty()) return
            val keep = level <= 20 /* error/fatal */ || (level <= 30 /* warn */ && FAILURE_RX.containsMatchIn(t))
            if (!keep) return
            // "Failed to open / loading failed" is the CONSEQUENCE — don't let it overwrite a more specific
            // cause already captured for this load (e.g. "HTTP error 400", an SSL error, a codec message).
            if (lastMpvError != null && GENERIC_FAIL_RX.containsMatchIn(t)) return
            lastMpvError = "${prefix.trim().trimEnd(':')}: $t"
        }
    }

    /** "HEVC 3840x1920 • hardware decoder" from the current stream, for the error screen's spec line. Null
     *  if nothing decoded yet (e.g. a stream that failed to open — then there's no codec/size to show). */
    private fun mediaSpec(): String? {
        val codec = currentVideoCodec?.uppercase()
        val res = if (currentWidthPx > 0 && currentHeightPx > 0) "${currentWidthPx}x$currentHeightPx" else null
        val decoder = currentHwdec?.let { if (it.contains("mediacodec")) "hardware decoder" else if (it == "no") "software decoder" else it }
        val head = listOfNotNull(codec, res).joinToString(" ").ifBlank { null }
        return listOfNotNull(head, decoder).joinToString(" • ").ifBlank { null }
    }
    private val _volume = MutableStateFlow(100)
    val volume: StateFlow<Int> = _volume.asStateFlow()
    private val _videoRes = MutableStateFlow<String?>(null)
    val videoRes: StateFlow<String?> = _videoRes.asStateFlow()

    /** The video's frame rate (e.g. 23.976) — used to ask the display to match it, killing the 3:2
     *  pulldown judder you get playing 24fps content on a fixed 60Hz panel. Null until known. */
    private val _videoFps = MutableStateFlow<Float?>(null)
    val videoFps: StateFlow<Float?> = _videoFps.asStateFlow()

    /** Video aspect ratio (w/h) — the surface view sizes itself with this in direct mode. */
    private val _videoAspect = MutableStateFlow<Float?>(null)
    val videoAspect: StateFlow<Float?> = _videoAspect.asStateFlow()

    /** Native video pixel size (w, h) — the surface view uses it for the Original (1:1) zoom mode. */
    private val _videoSize = MutableStateFlow<Pair<Int, Int>?>(null)
    val videoSize: StateFlow<Pair<Int, Int>?> = _videoSize.asStateFlow()

    /** Current subtitle line(s) for the Compose overlay (direct mode only; null = nothing showing). */
    private val _subText = MutableStateFlow<String?>(null)
    val subText: StateFlow<String?> = _subText.asStateFlow()
    private val _audioCount = MutableStateFlow(0)
    val audioCount: StateFlow<Int> = _audioCount.asStateFlow()
    private val _subCount = MutableStateFlow(0)
    val subCount: StateFlow<Int> = _subCount.asStateFlow()
    private val _zoomMode = MutableStateFlow(ZoomMode.FIT)
    val zoomMode: StateFlow<ZoomMode> = _zoomMode.asStateFlow()
    private val _speed = MutableStateFlow(1.0)
    val speed: StateFlow<Double> = _speed.asStateFlow()

    val currentMediaUrl: String? get() = currentUrl

    // --- ExoPlayer image-subtitle handoff -----------------------------------------------------
    // ExoPlayer takes over playback ONLY for a VOD with an image subtitle selected. mpv is stopped first
    // (so the provider sees one connection), and ExoPlayer's state is mirrored into the flows above so the
    // HUD is unchanged. All Exo access is on the main scope (its application thread).
    @Volatile private var attachedSurface: Surface? = null
    @Volatile private var surfaceW = 0
    @Volatile private var surfaceH = 0
    private var exoEngine: ExoSubtitleEngine? = null
    @Volatile private var exoActive = false
    private var exoTickJob: Job? = null
    private var pendingImageSub: TrackOption? = null
    // A text subtitle picked while an Exo handoff is active: applied after mpv reloads (FILE_LOADED).
    @Volatile private var pendingSelectSid: Int? = null
    private val freezeHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val _exoCues = MutableStateFlow<List<androidx.media3.common.text.Cue>>(emptyList())
    /** Bitmap/text subtitle cues from the ExoPlayer handoff — drawn by the SubtitleView overlay. */
    val exoCues: StateFlow<List<androidx.media3.common.text.Cue>> = _exoCues.asStateFlow()

    private val _freezeFrame = MutableStateFlow<android.graphics.Bitmap?>(null)
    /** A snapshot of the last mpv frame, shown over the surface during the mpv→ExoPlayer swap so the
     *  ~second-long decoder switch doesn't flash black. Cleared on ExoPlayer's first rendered frame. */
    val freezeFrame: StateFlow<android.graphics.Bitmap?> = _freezeFrame.asStateFlow()

    private val _surfaceResetToken = MutableStateFlow(0)
    /** Bumped to force the video SurfaceView to be recreated. The Realtek decoder throws 0x80001000 when a
     *  new 4K-class MediaCodec is bound to the SAME Surface a previous 4K-class session used (its VPU
     *  buffer queue stays dirty even after release) — so a back-to-back >1080p load gets a FRESH Surface. */
    val surfaceResetToken: StateFlow<Int> = _surfaceResetToken.asStateFlow()

    // --- Legacy TLL Playback Engine Integration ---
    /** Source type of the currently-loading item, so internal retries/reloads (mpv watchdog, alt-format
     *  fallback) preserve the original routing decision without every call site threading the type. */
    private var currentSourceType: com.thirutricks.tllplayer.core.model.SourceType? = null

    enum class PlaybackMode { MPV, LEGACY_EXO, LEGACY_WEB }

    private val _playbackMode = MutableStateFlow(PlaybackMode.MPV)
    val playbackMode: StateFlow<PlaybackMode> = _playbackMode.asStateFlow()

    private val _legacyWebUrl = MutableStateFlow<String?>(null)
    val legacyWebUrl: StateFlow<String?> = _legacyWebUrl.asStateFlow()

    private var currentTllUrls: List<String> = emptyList()
    private var currentTllUrlIndex = 0

    private val legacyExoCallbacks = object : LegacyExoEngine.Callbacks {
        override fun onPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onBuffering(buffering: Boolean) {
            _buffering.value = buffering
        }

        override fun onVideoSize(width: Int, height: Int) {
            currentWidthPx = width; currentHeightPx = height
            _videoSize.value = Pair(width, height)
            _videoAspect.value = width.toFloat() / height.toFloat()
            _videoRes.value = resolutionLabel(height)
        }

        override fun onPositionDuration(positionMs: Long, durationMs: Long) {
            _position.value = positionMs
            _duration.value = durationMs
        }

        override fun onFirstFrame() {
            _buffering.value = false
        }

        override fun onError(message: String) {
            _buffering.value = false
            if (currentTllUrlIndex < currentTllUrls.size - 1) {
                currentTllUrlIndex++
                android.util.Log.w(TAG, "Legacy ExoPlayer error, retrying alternate URL (${currentTllUrlIndex + 1}/${currentTllUrls.size})")
                loadTllUrl(currentTllUrls[currentTllUrlIndex], _currentMeta.value ?: MediaMeta(""), 0)
            } else {
                _error.value = message
            }
        }
    }

    private val legacyExoEngine by lazy { LegacyExoEngine(context, okHttpClient, legacyExoCallbacks) }

    private var legacyExoTickJob: Job? = null
    private fun startLegacyExoTick() {
        legacyExoTickJob?.cancel()
        legacyExoTickJob = scope.launch {
            while (playbackMode.value == PlaybackMode.LEGACY_EXO) {
                legacyExoEngine.emitPositionDuration()
                delay(500)
            }
        }
    }

    private fun deactivateLegacyExo() {
        legacyExoTickJob?.cancel()
        legacyExoEngine.stop()
    }

    /** True while ExoPlayer (not mpv) owns playback for an image-subtitle VOD. */
    val isExoActive: Boolean get() = exoActive

    private val _exoActiveState = MutableStateFlow(false)
    /** Reactive form of [isExoActive]. The UI mounts the SubtitleView overlay ONLY while this is true,
     *  so during normal mpv playback nothing is composited over the video SurfaceView — otherwise the
     *  SurfaceView loses its hardware-overlay / direct scan-out path and 4K stutters to a slideshow. */
    val exoActiveState: StateFlow<Boolean> = _exoActiveState.asStateFlow()

    private val exoCallbacks = object : ExoSubtitleEngine.Callbacks {
        override fun onPlayingChanged(playing: Boolean) { _isPlaying.value = playing }
        override fun onBuffering(buffering: Boolean) { _buffering.value = buffering }
        override fun onVideoSize(width: Int, height: Int) {
            currentWidthPx = width; currentHeightPx = height
            updateAspect()
            _videoRes.value = resolutionLabel(height)
        }
        override fun onPositionDuration(positionMs: Long, durationMs: Long) {
            _position.value = positionMs
            if (durationMs > 0) _duration.value = durationMs
        }
        override fun onFirstFrame() { _buffering.value = false; _freezeFrame.value = null }
        override fun onCues(cues: List<androidx.media3.common.text.Cue>) { _exoCues.value = cues }
        override fun onAudioTracks(tracks: List<TrackOption>) {
            _audioTrackList.value = tracks
            _audioCount.value = tracks.size
        }
        override fun onVideoFps(fps: Float) { _videoFps.value = fps }
        override fun onError(message: String) { scope.launch { revertToMpv(error = message) } }
    }

    /** ExoPlayer can decode this VOD's active audio? Always-safe codecs (AAC/AC3/…) pass immediately;
     *  for others (DTS/TrueHD) we check whether THIS device actually has a hardware/software decoder for
     *  it — many TVs do — and only block when it genuinely can't, so we don't fail+bounce. Unknown → try. */
    private fun audioCodecSafeForExo(): Boolean {
        val sel = _audioTrackList.value.firstOrNull { it.selected } ?: _audioTrackList.value.firstOrNull()
        val codec = sel?.codec?.lowercase() ?: return true
        if (EXO_SAFE_AUDIO_CODECS.any { codec.startsWith(it) }) {
            android.util.Log.i(TAG, "Exo codec gate: audio codec='$codec' → safe (allowlist)")
            return true
        }
        val mime = audioMimeFor(codec)
        val ok = mime != null && deviceHasAudioDecoder(mime)
        android.util.Log.i(TAG, "Exo codec gate: audio codec='$codec' mime=$mime deviceDecoder=$ok")
        return ok
    }

    /** Map an mpv/FFmpeg audio codec name to the Android MIME used to look up a device decoder. */
    private fun audioMimeFor(codec: String): String? = when {
        codec.startsWith("ac3") || codec.startsWith("ac-3") -> "audio/ac3"
        codec.startsWith("eac3") || codec.startsWith("e-ac-3") -> "audio/eac3"
        codec.startsWith("dts") -> "audio/vnd.dts"
        codec.startsWith("truehd") || codec.startsWith("mlp") -> "audio/true-hd"
        else -> null
    }

    /** Does this device expose a (hardware or software) MediaCodec decoder for [mime]? */
    private fun deviceHasAudioDecoder(mime: String): Boolean = runCatching {
        val list = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
        list.codecInfos.any { info -> !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
    }.getOrDefault(false)

    /** Hand playback from mpv to ExoPlayer to show an image subtitle (VOD only). */
    private fun handoffToExo(sub: TrackOption) {
        val surface = attachedSurface ?: return
        val url = currentUrl ?: return
        if (!audioCodecSafeForExo()) {
            toast("Image subtitles aren't available for this video's audio format.")
            _subTrackList.value = _subTrackList.value.map { it.copy(selected = false) }
            return
        }
        val pos = _position.value
        pendingImageSub = sub
        _subTrackList.value = _subTrackList.value.map { it.copy(selected = it.mpvId == sub.mpvId) }
        loadGeneration++ // supersede any mpv retry/watchdog work for this item
        errorCheckJob?.cancel(); videoCheckJob?.cancel()
        expectingPlayback = false
        _error.value = null
        _buffering.value = true
        // Capture the current frame to mask the decoder swap, THEN stop mpv + release its surface on the
        // worker (frees the connection + decoder), THEN start ExoPlayer on the main scope — ordering keeps
        // the surface single-owner at every step.
        captureFreezeThen {
            mpvAsync {
                stopWithStopClassification("handoff to exo")
                setPropertyString("vo", "null")
                runCatching { this.detachSurface() } // mpv's detachSurface (the receiver), not OwnTVPlayer's
                scope.launch { startExo(url, pos, surface, sub) }
            }
        }
    }

    private fun startExo(url: String, pos: Long, surface: Surface, sub: TrackOption) {
        exoActive = true
        _exoActiveState.value = true // mount the SubtitleView overlay now (only while Exo owns playback)
        _directRender.value = true // ExoPlayer also renders direct-to-surface → the view sizes for zoom
        _subText.value = null // mpv's text overlay is off during the handoff
        val budget = playerBudget ?: PlayerBudget.of(context).also { playerBudget = it }
        val engine = exoEngine ?: ExoSubtitleEngine(context, okHttpClient, budget, exoCallbacks).also { exoEngine = it }
        engine.start(url, pos, surface, sub.lang, sub.typeIndex)
        engine.setVolume(_volume.value) // carry the current HUD volume into ExoPlayer
        startExoTick()
    }

    /** PixelCopy the live surface into a bitmap (shown during the swap), then run [block]. Best-effort:
     *  on any failure or after a short timeout it proceeds with no freeze (no worse than a black flash). */
    private fun captureFreezeThen(block: () -> Unit) {
        val surface = attachedSurface
        val w = surfaceW; val h = surfaceH
        if (surface == null || w <= 0 || h <= 0 || android.os.Build.VERSION.SDK_INT < 24) {
            android.util.Log.w(TAG, "freeze-frame skipped: surface=${surface != null} size=${w}x$h sdk=${android.os.Build.VERSION.SDK_INT}")
            block(); return
        }
        val bmp = runCatching { android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888) }.getOrNull()
        if (bmp == null) { android.util.Log.w(TAG, "freeze-frame skipped: bitmap alloc failed ${w}x$h"); block(); return }
        var proceeded = false
        val proceed = { if (!proceeded) { proceeded = true; block() } }
        runCatching {
            android.view.PixelCopy.request(surface, bmp, { result ->
                android.util.Log.i(TAG, "freeze-frame PixelCopy result=$result (SUCCESS=${android.view.PixelCopy.SUCCESS})")
                if (result == android.view.PixelCopy.SUCCESS) _freezeFrame.value = bmp else bmp.recycle()
                proceed()
            }, freezeHandler)
        }.onFailure { runCatching { bmp.recycle() }; proceed() }
        // Safety net: never block the handoff if PixelCopy doesn't call back.
        freezeHandler.postDelayed({ proceed() }, 250)
    }

    private fun startExoTick() {
        exoTickJob?.cancel()
        exoTickJob = scope.launch {
            while (exoActive) { exoEngine?.emitPositionDuration(); delay(500) }
        }
    }

    /** Tear down the Exo handoff and give the surface back to mpv (does NOT reload — caller decides). */
    private fun deactivateExo() {
        if (!exoActive) return
        exoActive = false
        _exoActiveState.value = false // unmount the SubtitleView overlay → SurfaceView regains direct scan-out
        exoTickJob?.cancel()
        _exoCues.value = emptyList()
        _freezeFrame.value = null
        exoEngine?.stop()
        pendingImageSub = null
        reattachMpvSurface()
    }

    /** Hand playback back to mpv (image sub turned off, a text sub picked, or an Exo failure), resuming
     *  the same item at its current position with subtitles off (or [thenSelectSid] applied after load). */
    private fun revertToMpv(error: String? = null, thenSelectSid: Int? = null) {
        if (!exoActive) return
        val url = currentUrl ?: return
        val pos = _position.value
        deactivateExo()
        error?.let { toast(it) }
        pendingSelectSid = thenSelectSid
        _subTrackList.value = _subTrackList.value.map { it.copy(selected = thenSelectSid != null && it.mpvId == thenSelectSid) }
        loadUrl(url, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, pos, resetRetries = false)
    }

    private fun reattachMpvSurface() {
        val surface = attachedSurface ?: return
        mpvAsync {
            runCatching { this.attachSurface(surface) } // mpv's attachSurface (the receiver)
            setOptionString("force-window", "yes")
            setPropertyString("vo", targetVo())
        }
        _directRender.value = useDirect()
    }

    private fun toast(message: String) {
        scope.launch { android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show() }
    }

    private fun ensureInit() {
        if (initialized) return
        val budget = PlayerBudget.of(context)
        playerBudget = budget
        android.util.Log.i(TAG, "PlayerBudget: $budget")
        mpv = MPVLib.create(context)?.apply {
            setOptionString("vo", if (useDirect()) "mediacodec_embed" else "gpu")
            setOptionString("gpu-context", "android")
            setOptionString("hwdec", if (useDirect()) "mediacodec" else "no")
            setOptionString("ao", "audiotrack")
            // Surround sound (opt-in, default off): decode Dolby/DTS to MULTICHANNEL LPCM (5.1/7.1) over HDMI. The
            // AudioTrack stays a normal PCM track, so getTimestamp() keeps mpv's audio clock alive and the
            // zero-copy mediacodec_embed 4K-HDR video path renders smoothly. The sink picks the layout
            // (auto → stereo on a 2.0 TV, 5.1/7.1 on a capable receiver). Off → a plain stereo downmix.
            // (We never bitstream/spdif: on Realtek the passthrough AudioTrack reports no clock, which
            // stalls the direct VO into a ~2fps slideshow on Dolby/DTS content.)
            setOptionString("audio-channels", audioChannelsValue())
            // Compatibility for multichannel: some HALs choke on Float / 44.1 kHz 5.1 PCM (mis-sized buffer
            // → 2× drain, #25). Pin the universally-safe 16-bit/48 kHz output when surround is on.
            val sur = surroundSound && !surroundOutputBroken
            setOptionString("audio-format", if (sur) "s16" else "")
            setOptionString("audio-samplerate", if (sur) "48000" else "0")
            setOptionString("force-window", "no")
            setOptionString("idle", "yes")
            setOptionString("ytdl", "no") // IPTV URLs are direct; skip the youtube-dl hook
            // Closed captions (CEA-608/708): US premium channels (HBO/Showtime/Cinemax) and many movies carry
            // captions embedded in the video stream rather than as a subtitle track. mpv (FFmpeg) decodes them
            // into a selectable subtitle track — ExoPlayer doesn't surface undeclared CC, so this is the path
            // that actually shows them. Harmless when there are none (no track is created).
            setOptionString("sub-create-cc-track", "yes")
            // Allow volume boost above 100% (Kodi-style amplification) for quiet streams; mpv soft-limits.
            setOptionString("volume-max", "150")
            // A/V sync on hardware decode: a few movies (high bitrate / 50–60 fps) decode just behind
            // real time, so the picture drifts slightly behind the audio. mpv's default framedrop is "vo",
            // which is a no-op with the direct mediacodec surface (the decoder presents its own frames) — so
            // nothing drops the late frames. "decoder+vo" lets mpv skip decoding late frames at the
            // MediaCodec stage to catch the picture back up to the audio clock. It only drops when actually
            // behind, so content that decodes in time is untouched.
            setOptionString("framedrop", "decoder+vo")
            // Quiet logcat in release; debug builds keep decoder/video-out logs for diagnosing
            // hwdec behavior on real TVs (which decoder engaged, why fallbacks happened).
            setOptionString("msg-level", if (com.thirutricks.tllplayer.BuildConfig.DEBUG) "all=warn,vd=v,vo=v" else "all=warn")
            // Demuxer cache sized to the device (a fixed 256MiB OOM-killed real TVs — see PlayerBudget).
            setOptionString("cache", "yes")
            setOptionString("demuxer-max-bytes", budget.demuxerMaxBytes)
            setOptionString("demuxer-max-back-bytes", budget.demuxerBackBytes)
            setOptionString("demuxer-readahead-secs", budget.readaheadSecs)
            setOptionString("cache-secs", budget.cacheSecs)
            if (budget.lowSpec) {
                // GL diet for TV-class GPUs (e.g. PowerVR BXE on budget 4K panels): mpv's default
                // render path tone-maps 4K HDR in rgba16f with quality scalers — that alone drops
                // a TCL G10 to half-speed video. "fast" = bilinear scalers, no dither/deband.
                setOptionString("profile", "fast")
                setOptionString("fbo-format", "rgba8") // 4K rgba16f intermediates are ~64MB each
                setOptionString("tone-mapping", "clip") // cheapest HDR→SDR
            }
            setOptionString("network-timeout", "60")
            // Strict IPTV panels briefly answer 5xx (e.g. 509 connection-limit right after a channel
            // switch, while the old session still counts). Let FFmpeg retry those itself instead of
            // EOF-ing the stream — the demuxer cache rides over the gap with no visible interruption.
            setOptionString("stream-lavf-o", "reconnect=1,reconnect_streamed=1,reconnect_delay_max=8,reconnect_on_http_error=5xx")
            setOptionString("user-agent", HttpClient.DEFAULT_USER_AGENT)
            setOptionString("sub-scale-with-window", "yes")
            setOptionString("sub-scale", subScale.toString())
            setOptionString("audio-delay", audioDelaySec.toString())
            if (prefAudioLang.isNotBlank()) setOptionString("alang", prefAudioLang)
            if (prefSubLang.isNotBlank()) setOptionString("slang", prefSubLang)
            // HDR passthrough: signal the source colorspace (incl. HDR10/HLG) to the display surface.
            setOptionString("target-colorspace-hint", if (hdrHint) "yes" else "no")
            init()
            // Read back what mpv actually accepted — setOptionString failures are silent, and this
            // line also identifies the running build in logcat captures.
            _directRender.value = useDirect()
            android.util.Log.i(
                TAG,
                "mpv ready: lowSpec=${budget.lowSpec} direct=${useDirect()} hwdec=${getPropertyString("hwdec")} " +
                    "fbo=${getPropertyString("fbo-format")} cache=${getPropertyString("demuxer-max-bytes")}",
            )
            observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            observeProperty("width", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            observeProperty("height", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            observeProperty("speed", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            observeProperty("container-fps", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            // Decode watchdog input: which decoder is actually active ("mediacodec[-copy]" or "no").
            observeProperty("hwdec-current", MPVLib.MpvFormat.MPV_FORMAT_STRING)
            observeProperty("video-codec", MPVLib.MpvFormat.MPV_FORMAT_STRING) // for the error screen spec line
            // Current subtitle line for the app-drawn overlay (direct mode); fires only on change.
            observeProperty("sub-text", MPVLib.MpvFormat.MPV_FORMAT_STRING)
            addObserver(this@OwnTVPlayer)
            addLogObserver(logObserver) // capture mpv's error output for the on-screen "err: …" detail line
        }
        diagnostics.start() // tail logcat for MediaCodec/AudioTrack errors mpv can't surface
        // When a friendly error is surfaced, expose the real reason beneath it — prefer a system codec/audio
        // error (e.g. MediaCodec 0x80001000) from this stream, else mpv's own last log line.
        scope.launch {
            _error.collect {
                _errorInfo.value = if (it != null) {
                    val raw = diagnostics.recentError() ?: lastMpvError
                    ErrorInfo(reason = raw?.let(PlayerErrors::reasonFor), spec = mediaSpec(), raw = raw)
                } else null
            }
        }
        initialized = mpv != null
    }

    /** Play a single item (movie / live channel) — clears any queue. [muted] is used by the live preview.
     *  [sourceType] routes TLL channels through the legacy WebView/Exo engine (multi-URL retry + HEAD
     *  detection); other types play on mpv. Passing it explicitly is what makes the TLL engine work for
     *  any profile, instead of the old brittle `activeProfileId == 1L` assumption. */
    fun play(
        url: String,
        title: String? = null,
        subtitle: String? = null,
        year: String? = null,
        logoUrl: String? = null,
        isLive: Boolean = false,
        startPositionMs: Long = 0,
        muted: Boolean = false,
        preferSoftware: Boolean = false,
        startPaused: Boolean = false,
        sourceType: com.thirutricks.tllplayer.core.model.SourceType? = null,
    ) {
        playlist = emptyList()
        playlistIndex = 0
        updateNav()
        _zoomMode.value = defaultZoom // start new content at the user's default zoom
        loadUrl(url, MediaMeta(title, subtitle, year, logoUrl), isLive, startPositionMs, muted, preferSoftware = preferSoftware, startPaused = startPaused, sourceType = sourceType)
    }

    /** Play a queue (a season's episodes) starting at [startIndex] — enables prev/next. */
    fun playEpisodes(items: List<PlaylistItem>, startIndex: Int, startPositionMs: Long = 0) {
        playlist = items
        playlistIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val item = items.getOrNull(playlistIndex) ?: return
        _zoomMode.value = defaultZoom
        loadUrl(item.url, item.meta, isLive = false, startPositionMs, sourceType = item.sourceType)
        updateNav()
    }

    fun next() {
        if (playlistIndex < playlist.size - 1) {
            playlistIndex++
            playCurrent()
        }
    }

    fun previous() {
        if (playlistIndex > 0) {
            playlistIndex--
            playCurrent()
        }
    }

    private fun playCurrent() {
        val item = playlist.getOrNull(playlistIndex) ?: return
        loadUrl(item.url, item.meta, isLive = false, 0, sourceType = item.sourceType)
        updateNav()
    }

    private fun updateNav() {
        _nav.value = NavState(playlistIndex > 0, playlistIndex < playlist.size - 1)
    }

    private fun loadUrl(
        url: String,
        meta: MediaMeta,
        isLive: Boolean,
        startPositionMs: Long,
        muted: Boolean = false,
        resetRetries: Boolean = true,
        preferSoftware: Boolean = false,
        startPaused: Boolean = false,
        sourceType: com.thirutricks.tllplayer.core.model.SourceType? = null,
        isResolved: Boolean = false,
    ) {
        ensureInit()

        if (url.contains("tamilseithigal.in", ignoreCase = true)) {
            if (resetRetries) currentSourceType = sourceType
            isLiveContent = true
            playLegacyWeb(url, meta)
            return
        }

        // Remember the routing decision for this load so internal retries/reloads (mpv watchdog,
        // alt-format fallback, prev/next in a queue) preserve it without re-threading the type.
        if (resetRetries) currentSourceType = sourceType
        val effectiveSourceType = sourceType ?: currentSourceType
        // Route TLL channels through the legacy WebView/Exo engine keyed on SOURCE TYPE (not profile id).
        // The old `activeProfileId == 1L` only worked for the seeded default profile; source-type routing
        // makes TLL playback work for ANY profile that has a TLL source.
        isLiveContent = if (effectiveSourceType == com.thirutricks.tllplayer.core.model.SourceType.TLL) true else isLive
        val isYoutube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
        if (isYoutube) {
            isLiveContent = true
            playLegacyWeb(url, meta)
            return
        }
        if (effectiveSourceType == com.thirutricks.tllplayer.core.model.SourceType.TLL) {
            if (resetRetries) {
                currentTllUrls = url.split("|||")
                currentTllUrlIndex = 0
            }
            loadTllUrl(currentTllUrls[currentTllUrlIndex], meta, startPositionMs)
            return
        }
        pendingStartPaused = startPaused
        if (resetRetries) {
            deactivateExo()
            deactivateLegacyExo()
            _legacyWebUrl.value = null
            _playbackMode.value = PlaybackMode.MPV
            detachSurface()
        }
        currentTitle = meta.title
        currentSubtitle = meta.subtitle
        currentYear = meta.year
        currentLogoUrl = meta.logoUrl
        _currentMeta.value = meta // reactive — refreshes the HUD title / "now watching" card on every load
        isLiveContent = isLive
        currentUrl = url
        loadGeneration++
        errorCheckJob?.cancel()
        videoCheckJob?.cancel()
        _error.value = null
        lastMpvError = null // fresh item → drop the previous stream's captured error
        diagnostics.markLoad() // scope captured codec/audio errors to this stream
        _videoRes.value = null
        _videoFps.value = null
        expectingPlayback = true
        pendingSeekMs = startPositionMs
        applyAudioDelay(baseAudioDelayMs) // new item starts at the Settings default — drop any per-file nudge
        // A genuinely new item resets the failure budget; an auto-retry / software-fallback reload of
        // the SAME item passes resetRetries=false to keep that state.
        if (resetRetries) {
            autoRetries = 0
            triedAltFormat = false
            triedSoftwareForVideo = false
            forceFullProbe = false // a genuinely new item starts with the trimmed (fast-zap) probe again
            // Pick this item's decode path. Catch-up forces SOFTWARE: archive (timeshift) segments often
            // start mid-GOP, which the hardware MediaCodec decoder can't recover from (blank video, and
            // it can wedge/crash) — software decodes cleanly from the next keyframe. Everything else uses
            // the user's hardware-decoding setting. (Software renders via GL, which is broken on the
            // emulator, so skip the override there.)
            val wantSoftware = preferSoftware && !glUnsupported
            val needReconfig = forceSoftwareThisLoad != wantSoftware
            forceSoftwareThisLoad = wantSoftware
            if (needReconfig) mpvAsync { applyRenderConfig() }
        }
        // Reset the decode watchdog + per-file video state.
        currentHwdec = null
        currentVideoCodec = null
        currentHeightPx = 0
        currentWidthPx = 0
        decodeGuardTripped = false
        _videoAspect.value = null
        _videoSize.value = null
        _subText.value = null
        // Mute is a global mpv property, so set it now (applies whenever the file actually loads). The
        // live preview mutes; everything else plays with sound.
        mpvAsync { setPropertyBoolean("mute", muted) }
        // Defer the actual loadfile until a surface exists, otherwise mpv inits video output with no
        // surface and falls back to audio-only. attachSurface() flushes the pending load.
        // A back-to-back >1080p (4K-class) load on the SAME reused Surface throws Realtek 0x80001000 / a
        // frame-drop "slideshow" (the VPU buffer queue stays dirty after a heavy session) — so recreate the
        // SurfaceView first and let the fresh surface's attachSurface() flush this load. Covers BOTH VOD
        // auto-play AND live channel zapping (4K→next 4K via D-pad/CH±, which otherwise hangs until you back
        // out and re-enter — a manual surface recreate). Only when the PREVIOUS item was >1080p, so normal
        // playback and the first 4K load are untouched.
        if (lastVideoHeightPx > 1080 && surfaceAttached) {
            pendingUrl = url
            _surfaceResetToken.value++
        } else if (surfaceAttached) {
            startLoad(url)
        } else {
            pendingUrl = url
        }

        // Catch-up/VOD video watchdog: some archive (timeshift) segments start mid-GOP — audio plays
        // but no H.264 frame ever decodes ("non-existing PPS" → blank, no error). If we're clearly
        // playing (time advancing) yet have no video after a grace window, retry once in software
        // (which recovers at the next keyframe) and only then surface a clear error. Live is excluded:
        // it recovers on its own, and audio-only radio channels are legitimate.
        if (!isLive) {
            val gen = loadGeneration
            videoCheckJob = scope.launch {
                delay(7000)
                if (gen != loadGeneration || isLiveContent || currentHeightPx > 0 || _position.value <= 0L) return@launch
                // A stream we already know is >1080p must NOT go to software decode — the guard would just
                // kill it. This is the auto-play-to-next-episode case: a transient hardware-decoder error
                // (0x80001000) on the back-to-back load. Leave recovery to the direct-retry path (FILE_LOADED
                // decode check), which re-inits the hardware decoder — exactly what a manual Retry does.
                if (lastVideoHeightPx > 1080) {
                    android.util.Log.w(TAG, "no video frames on a >1080p stream — leaving recovery to the direct retry (no software)")
                    return@launch
                }
                if (!triedSoftwareForVideo && hwDecodingActive() && !glUnsupported) {
                    triedSoftwareForVideo = true
                    forceSoftwareThisLoad = true
                    android.util.Log.w(TAG, "no video frames (mid-GOP archive?) — retrying in software decode")
                    _buffering.value = true
                    mpvAsync { applyRenderConfig() }
                    delay(200)
                    if (gen == loadGeneration && currentUrl != null) {
                        loadUrl(currentUrl!!, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, _position.value, resetRetries = false)
                    }
                } else {
                    android.util.Log.w(TAG, "no video frames decoded — surfacing error")
                    _buffering.value = false
                    // Catch-up (preferSoftware) gets the archive wording; a movie/episode gets generic copy.
                    _error.value = if (preferSoftware)
                        "Couldn't play this recording. The archived segment may be incomplete or start mid-stream."
                    else
                        "Couldn't play this video — it may be unavailable or in a format this device can't decode."
                }
            }
        }
    }

    private fun isLegacyWebChannel(url: String): Boolean {
        if (url.isEmpty()) return false
        if (url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true) ||
            url.contains("yupptv.com") || url.contains("athavantv.com") || url.contains("ttn.tv")) {
            return true
        }
        // Stalker Portal / MAG stream URLs contain extension=ts (or m3u8/mpd/mp4) in the query string.
        // They MUST go to ExoPlayer, not WebView — even though the path ends in .php.
        val urlLower = url.lowercase()
        if (urlLower.contains("extension=ts") || urlLower.contains("extension=m3u8") ||
            urlLower.contains("extension=mpd") || urlLower.contains("extension=mp4")) {
            return false
        }
        if (url.endsWith(".html", ignoreCase = true) || url.endsWith(".htm", ignoreCase = true) || url.endsWith(".php", ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun playLegacyWeb(url: String, meta: MediaMeta) {
        deactivateExo()
        deactivateLegacyExo()
        mpvAsync { stopWithStopClassification("web_player") }
        _playbackMode.value = PlaybackMode.LEGACY_WEB
        _legacyWebUrl.value = url
        _isPlaying.value = true
        _buffering.value = false
        _error.value = null
        currentUrl = url
        currentTitle = meta.title
        currentLogoUrl = meta.logoUrl
        _currentMeta.value = meta
    }

    private fun playLegacyExo(url: String, meta: MediaMeta, startPositionMs: Long) {
        _legacyWebUrl.value = null
        deactivateExo()
        mpvAsync { stopWithStopClassification("legacy_exoplayer") }
        _playbackMode.value = PlaybackMode.LEGACY_EXO
        _isPlaying.value = true
        _buffering.value = true
        _error.value = null
        currentUrl = url
        currentTitle = meta.title
        currentLogoUrl = meta.logoUrl
        _currentMeta.value = meta
        
        legacyExoEngine.stop()
        val surface = attachedSurface
        if (surfaceAttached && surface != null) {
            legacyExoEngine.start(url, startPositionMs, surface)
            startLegacyExoTick()
        } else {
            pendingUrl = url
            pendingSeekMs = startPositionMs
        }
    }

    private fun loadTllUrl(url: String, meta: MediaMeta, startPositionMs: Long) {
        val gen = ++loadGeneration
        errorCheckJob?.cancel()
        videoCheckJob?.cancel()
        _error.value = null
        lastMpvError = null
        _videoRes.value = null
        _videoFps.value = null
        
        if (isLegacyWebChannel(url)) {
            playLegacyWeb(url, meta)
        } else {
            scope.launch(Dispatchers.IO) {
                var isWebPage = false
                try {
                    val request = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .head()
                        .build()
                    okHttpClient.newCall(request).execute().use { response ->
                        val contentType = response.header("Content-Type")?.lowercase()
                        if (contentType?.contains("text/html") == true) {
                            isWebPage = true
                        }
                    }
                } catch (e: Exception) {
                    try {
                        val request = okhttp3.Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("Range", "bytes=0-500")
                            .build()
                        okHttpClient.newCall(request).execute().use { response ->
                            val contentType = response.header("Content-Type")?.lowercase()
                            if (contentType?.contains("text/html") == true) {
                                isWebPage = true
                            }
                        }
                    } catch (ex: Exception) {
                        // ignore
                    }
                }
                
                if (gen != loadGeneration) return@launch
                withContext(Dispatchers.Main) {
                    if (isWebPage) {
                        playLegacyWeb(url, meta)
                    } else {
                        val hasDrm = url.contains("drmScheme", ignoreCase = true) || url.contains("drmLicense", ignoreCase = true)
                        if (hasDrm) {
                            playLegacyExo(url, meta, startPositionMs)
                        } else {
                            deactivateExo()
                            deactivateLegacyExo()
                            _legacyWebUrl.value = null
                            _playbackMode.value = PlaybackMode.MPV
                            currentTitle = meta.title
                            currentSubtitle = meta.subtitle
                            currentYear = meta.year
                            currentLogoUrl = meta.logoUrl
                            _currentMeta.value = meta
                            isLiveContent = true
                            currentUrl = url
                            errorCheckJob?.cancel()
                            videoCheckJob?.cancel()
                            _error.value = null
                            lastMpvError = null
                            diagnostics.markLoad()
                            _videoRes.value = null
                            _videoFps.value = null
                            expectingPlayback = true
                            pendingSeekMs = startPositionMs
                            
                            if (surfaceAttached) {
                                startLoad(url)
                            } else {
                                pendingUrl = url
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startLoad(url: String) {
        pendingUrl = null
        val gen = loadGeneration
        mpvAsync {
            // Superseded by a newer load or a stop while waiting in the queue? Skip the dead load —
            // this keeps fast preview-scrolling from grinding through every channel it passed.
            if (gen != loadGeneration) return@mpvAsync
            applyProbeProfile(url) // trim the demuxer probe for live (faster zap); full probe for VOD
            val isYoutube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
            setOptionString("ytdl", if (isYoutube) "yes" else "no")
            loadfileWithStopClassification(url, "replacement loadfile")
            setPropertyBoolean("pause", false)
        }
        // mpv only fires the "pause" observer on a *change*; at startup pause is already false, so seed
        // the playing state here, otherwise the HUD shows PLAY while the stream is actually running.
        _isPlaying.value = true
    }

    /** Mute/unmute without reloading — lets preview → fullscreen reuse the same stream connection. */
    fun setMuted(muted: Boolean) {
        if (initialized) mpvAsync { setPropertyBoolean("mute", muted) }
    }

    fun togglePlayPause() {
        if (playbackMode.value == PlaybackMode.LEGACY_EXO) { legacyExoEngine.togglePlayPause(); return }
        if (exoActive) { exoEngine?.togglePlayPause(); return }
        if (initialized) mpvAsync { command(arrayOf("cycle", "pause")) }
    }

    fun seekBy(deltaMs: Long) {
        if (playbackMode.value == PlaybackMode.LEGACY_EXO) { legacyExoEngine.seekBy(deltaMs); return }
        if (exoActive) { exoEngine?.seekBy(deltaMs); return }
        if (initialized) mpvAsync { command(arrayOf("seek", (deltaMs / 1000).toString(), "relative")) }
    }

    fun setSpeed(speed: Double) {
        if (playbackMode.value == PlaybackMode.LEGACY_EXO) { /* Not supported in legacy Exo */ }
        else if (exoActive) exoEngine?.setSpeed(speed) else if (initialized) mpvAsync { setPropertyDouble("speed", speed) }
        _speed.value = speed
    }

    private fun applyAudioDelay(ms: Int) {
        _audioDelayMs.value = ms
        audioDelaySec = ms / 1000.0
        if (initialized) mpvAsync { setPropertyDouble("audio-delay", audioDelaySec) }
    }

    /** In-player A/V-sync nudge for a badly-muxed file (positive = delay audio). Per-file: resets to the
     *  Settings default on the next item, so it never carries a wrong offset onto a good file. */
    fun adjustAudioDelay(deltaMs: Int) {
        applyAudioDelay((_audioDelayMs.value + deltaMs).coerceIn(-5_000, 5_000))
    }

    // --- Volume (mpv software volume, independent of the system/hardware volume) ---
    fun setVolume(percent: Int) {
        val v = percent.coerceIn(0, 150)
        if (playbackMode.value == PlaybackMode.LEGACY_EXO) { legacyExoEngine.setVolume(v) }
        else if (exoActive) exoEngine?.setVolume(v) else if (initialized) mpvAsync { setPropertyDouble("volume", v.toDouble()) }
        _volume.value = v
        if (v > 0) preMuteVolume = v
    }

    fun adjustVolume(delta: Int) = setVolume(_volume.value + delta)

    fun toggleMute() {
        if (_volume.value > 0) { preMuteVolume = _volume.value; setVolume(0) } else setVolume(preMuteVolume.coerceAtLeast(10))
    }

    // --- Zoom / aspect ---
    fun setZoomMode(mode: ZoomMode) {
        _zoomMode.value = mode
        // Direct mode: the decoder owns the surface, so mpv's GL scaling properties don't apply — the
        // surface VIEW resizes/crops itself per mode instead (see MpvVideoSurface, which observes
        // zoomMode). Nothing to do on mpv here. GL mode (software rescue) still uses the props below.
        if (_directRender.value) return
        mpvAsync {
            // Reset, then apply the chosen mode's overrides.
            setPropertyString("video-unscaled", "no")
            setPropertyDouble("panscan", 0.0)
            when (mode) {
                ZoomMode.FIT -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "no") }
                ZoomMode.FILL -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "no"); setPropertyDouble("panscan", 1.0) }
                ZoomMode.STRETCH -> { setPropertyString("keepaspect", "no"); setPropertyString("video-aspect-override", "no") }
                ZoomMode.ORIGINAL -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "no"); setPropertyString("video-unscaled", "yes") }
                ZoomMode.FORCE_16_9 -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "16:9") }
                ZoomMode.FORCE_4_3 -> { setPropertyString("keepaspect", "yes"); setPropertyString("video-aspect-override", "4:3") }
            }
        }
    }

    fun retry() {
        val url = currentUrl ?: return
        loadUrl(url, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, 0)
    }

    fun stop() {
        deactivateLegacyExo()
        _legacyWebUrl.value = null
        _playbackMode.value = PlaybackMode.MPV
        deactivateExo() // give the surface back to mpv before tearing down
        loadGeneration++ // cancels any queued-but-not-yet-executed load
        expectingPlayback = false
        errorCheckJob?.cancel()
        videoCheckJob?.cancel()
        if (initialized) mpvAsync { stopWithStopClassification("stop") }
        currentUrl = null
        pendingUrl = null
        _isPlaying.value = false
        _buffering.value = false
    }

    /**
     * The app moved to the background (Home / another app). An IPTV player has no background
     * playback — stop the stream so the demuxer cache and decoder buffers are freed immediately.
     * Holding them got the process LMK-killed at 490–620 MB PSS while invisible ("empty" state).
     */
    fun onAppBackgrounded() {
        val url = currentUrl ?: pendingUrl
        if (url != null) {
            // Remember a non-live item so the screensaver/Home → return can restore it paused at its
            // position. (Live just re-tunes; the archive/VOD stream is freed for memory while invisible.)
            backgroundRestore = if (!isLiveContent) {
                BackgroundRestore(url, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), _position.value, _isPlaying.value)
            } else null
            stop()
        }
    }

    /** Paired with [onAppBackgrounded]: restore a VOD that was freed while the app was in the background
     *  (e.g. the TV screensaver), so pressing Play just works instead of doing nothing on a freed stream. */
    fun onAppForegrounded() {
        val r = backgroundRestore ?: return
        backgroundRestore = null
        if (currentUrl != null) return // already playing something else
        play(r.url, subtitle = r.meta.subtitle, year = r.meta.year, logoUrl = r.meta.logoUrl,
            title = r.meta.title, isLive = false, startPositionMs = r.positionMs, startPaused = !r.wasPlaying)
    }

    /** Drop any pending restore (e.g. on profile switch — don't bring back the previous user's item). */
    fun discardBackgroundRestore() { backgroundRestore = null }

    /**
     * The OS signaled serious memory pressure while we're alive: yield before the kernel takes.
     * Shrinks the demuxer cache live (it prunes already-buffered data too).
     */
    fun onTrimMemory() {
        if (!initialized) return
        mpvAsync {
            setPropertyString("demuxer-max-bytes", PlayerBudget.TRIM_DEMUXER_BYTES)
            setPropertyString("demuxer-max-back-bytes", "8MiB")
        }
    }

    fun release() {
        errorCheckJob?.cancel()
        exoTickJob?.cancel()
        exoActive = false
        exoEngine?.release()
        exoEngine = null
        scope.cancel()
        if (initialized) {
            val m = mpv
            mpv = null
            initialized = false
            // Destroy on the command thread so queued commands drain first (and never block the UI).
            mpvExecutor.execute {
                runCatching {
                    m?.removeObserver(this)
                    m?.destroy()
                }
            }
        }
        mpvExecutor.shutdown()
    }

    // --- Surface (driven by the MpvVideoSurface view) ---
    fun attachSurface(surface: Surface) {
        ensureInit()
        attachedSurface = surface
        surfaceAttached = true
        if (playbackMode.value == PlaybackMode.LEGACY_EXO) {
            legacyExoEngine.setSurface(surface)
            pendingUrl?.let { url ->
                pendingUrl = null
                legacyExoEngine.start(url, pendingSeekMs, surface)
                startLegacyExoTick()
            }
            return
        }
        // ExoPlayer owns playback right now (image-sub handoff) → give it the (re)created surface.
        if (exoActive) { exoEngine?.setSurface(surface); return }
        mpv?.attachSurface(surface)
        mpv?.setOptionString("force-window", "yes")
        mpv?.setOptionString("vo", targetVo())
        // Flush a load that was waiting for the surface (so video output inits correctly the first time).
        pendingUrl?.let { startLoad(it) }
    }

    fun setSurfaceSize(width: Int, height: Int) {
        surfaceW = width; surfaceH = height // remembered for the freeze-frame PixelCopy at handoff time
        if (exoActive) return // ExoPlayer scales to the surface itself; nothing to tell mpv
        if (initialized) mpvAsync { setPropertyString("android-surface-size", "${width}x$height") }
    }

    fun detachSurface() {
        surfaceAttached = false
        attachedSurface = null
        if (playbackMode.value == PlaybackMode.LEGACY_EXO) {
            legacyExoEngine.setSurface(null)
            return
        }
        if (exoActive) { exoEngine?.setSurface(null); return }
        if (!initialized) return
        mpv?.setPropertyString("vo", "null")
        mpv?.setOptionString("force-window", "no")
        mpv?.detachSurface()
    }

    // --- Tracks ---
    // Track lists are queried once per loaded file (on mpv's event thread) and cached, so the HUD
    // never issues synchronous mpv reads from the UI thread (those block during network stalls → ANR).
    private val _audioTrackList = MutableStateFlow<List<TrackOption>>(emptyList())
    private val _subTrackList = MutableStateFlow<List<TrackOption>>(emptyList())

    fun audioTracks(): List<TrackOption> = _audioTrackList.value
    fun textTracks(): List<TrackOption> = _subTrackList.value

    /** Technical readout for the stream-info overlay, read live from mpv (libmpv get_property is thread-safe). */
    fun streamInfo(): List<Pair<String, String>> {
        val m = mpv ?: return emptyList()
        fun str(p: String) = m.getPropertyString(p)?.takeIf { it.isNotBlank() }
        val out = ArrayList<Pair<String, String>>()
        // Video
        val vw = m.getPropertyInt("video-params/w") ?: m.getPropertyInt("width")
        val vh = m.getPropertyInt("video-params/h") ?: m.getPropertyInt("height")
        val pix = str("video-params/pixelformat").orEmpty()
        val depth = when { "10" in pix -> "10-bit"; "12" in pix -> "12-bit"; pix.isNotEmpty() -> "8-bit"; else -> null }
        val videoLine = listOfNotNull(
            currentVideoCodec ?: str("video-codec"),
            if (vw != null && vh != null && vw > 0) "${vw}×${vh}" else null,
            str("container-fps")?.toDoubleOrNull()?.let { "%.2f fps".format(it) },
            depth,
        ).joinToString(" · ")
        if (videoLine.isNotBlank()) out += "Video" to videoLine
        // HDR (transfer curve)
        when (str("video-params/gamma")?.lowercase()) {
            "pq" -> "HDR10 (PQ)"; "hlg" -> "HLG"; null -> null; else -> "SDR"
        }?.let { out += "HDR" to it }
        // Bitrate
        str("video-bitrate")?.toLongOrNull()?.let { if (it > 0) out += "Bitrate" to "%.1f Mbps".format(it / 1_000_000.0) }
        // Decoder
        val hw = str("hwdec-current")
        out += "Decoder" to when {
            hw != null && hw != "no" -> "$hw (hardware)" + if (_directRender.value) " · direct" else ""
            else -> "software" + if (!_directRender.value) " (gpu)" else ""
        }
        // Audio
        val audioLine = listOfNotNull(
            str("audio-codec-name")?.uppercase(),
            when (m.getPropertyInt("audio-params/channel-count")) {
                1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; null -> null; else -> "ch"
            },
            m.getPropertyInt("audio-params/samplerate")?.let { "%.0f kHz".format(it / 1000.0) },
            str("audio-bitrate")?.toLongOrNull()?.let { if (it > 0) "%.0f kbps".format(it / 1000.0) else null },
        ).joinToString(" · ")
        if (audioLine.isNotBlank()) out += "Audio" to audioLine
        // Buffer
        val bufLine = listOfNotNull(
            str("demuxer-cache-duration")?.toDoubleOrNull()?.let { "%.1f s".format(it) },
            str("frame-drop-count")?.let { "drops $it" },
        ).joinToString(" · ")
        if (bufLine.isNotBlank()) out += "Buffer" to bufLine
        // Source — HIDDEN for TLL channels: their endpoint URL is a protected value that must never be
        // shown in the stream-info overlay (redaction only masks credentials, not the host/path).
        if (currentSourceType != com.thirutricks.tllplayer.core.model.SourceType.TLL) {
            currentUrl?.let { out += "Source" to HttpClient.redactUrl(it) }
        }
        return out
    }

    /** Synchronous mpv read — only call off the main thread (mpv event thread / mpv-cmd worker). */
    private fun queryTracks(type: String): List<TrackOption> {
        if (!initialized) return emptyList()
        val m = mpv ?: return emptyList()
        val count = m.getPropertyInt("track-list/count") ?: 0
        val out = ArrayList<TrackOption>()
        var typeIndex = 0
        for (i in 0 until count) {
            if (m.getPropertyString("track-list/$i/type") != type) continue
            val id = m.getPropertyInt("track-list/$i/id") ?: continue
            val title = m.getPropertyString("track-list/$i/title")
            val lang = m.getPropertyString("track-list/$i/lang")
            val codec = m.getPropertyString("track-list/$i/codec")
            val selected = m.getPropertyBoolean("track-list/$i/selected") ?: false
            // Image-based subtitle (PGS/VOBSUB/DVB): mpv's direct path can't draw it — on VOD, selecting
            // it hands playback to ExoPlayer. typeIndex lines the pick up with ExoPlayer's track order.
            val image = type == "sub" && codec?.lowercase() in BITMAP_SUB_CODECS
            out.add(TrackOption(label(title, lang, id), id, selected, image = image, codec = codec, lang = lang, typeIndex = typeIndex))
            typeIndex++
        }
        return out
    }

    fun selectAudio(mpvId: Int) {
        if (exoActive) exoEngine?.selectAudio(mpvId) else if (initialized) mpvAsync { setPropertyInt("aid", mpvId) }
        _audioTrackList.value = _audioTrackList.value.map { it.copy(selected = it.mpvId == mpvId) }
    }

    fun selectSubtitle(mpvId: Int) {
        val track = _subTrackList.value.find { it.mpvId == mpvId }
        // Image subtitle on a VOD → hand playback to ExoPlayer (it draws bitmap subs on its own layer).
        // Live image subs aren't supported (no handoff) — selecting one just shows nothing.
        if (track?.image == true) {
            if (!isLiveContent) handoffToExo(track)
            else _subTrackList.value = _subTrackList.value.map { it.copy(selected = it.mpvId == mpvId) }
            return
        }
        // Text subtitle: mpv's direct path + app overlay. If we're mid-handoff, return to mpv first and
        // apply this sub once it reloads.
        if (exoActive) { revertToMpv(thenSelectSid = mpvId); return }
        if (initialized) mpvAsync {
            setPropertyInt("sid", mpvId)
            setPropertyString("sub-visibility", "yes") // ensure subs aren't hidden
        }
        _subTrackList.value = _subTrackList.value.map { it.copy(selected = it.mpvId == mpvId) }
    }

    fun disableSubtitles() {
        if (exoActive) { revertToMpv(); return } // turning subs off ends the image-sub handoff
        if (initialized) mpvAsync { setPropertyString("sid", "no") }
        _subTrackList.value = _subTrackList.value.map { it.copy(selected = false) }
    }

    private fun label(title: String?, lang: String?, id: Int): String {
        val l = lang?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { runCatching { Locale(it).displayLanguage }.getOrNull()?.ifBlank { it } ?: it }
        return listOfNotNull(title?.takeIf { it.isNotBlank() }, l).joinToString(" · ").ifBlank { "Track $id" }
    }

    // --- mpv event callbacks (called off the main thread) ---
    override fun eventProperty(property: String) {
        // A string property went unavailable/null. For sub-text that means "no line on screen now".
        if (property == "sub-text") _subText.value = null
    }

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "time-pos" -> {
                _position.value = value * 1000
                if (value > 0) expectingPlayback = false // playback actually started
            }
            "duration" -> _duration.value = value * 1000
            "width" -> {
                currentWidthPx = value.toInt()
                updateAspect()
            }
            "height" -> {
                _videoRes.value = resolutionLabel(value.toInt())
                currentHeightPx = value.toInt()
                if (value > 0) {
                    lastVideoHeightPx = value.toInt() // remember for recovery decisions on a later failed load
                    videoCheckJob?.cancel() // video is decoding → watchdog not needed
                }
                updateAspect()
                enforceDecodeGuard()
            }
        }
    }

    private fun updateAspect() {
        val w = currentWidthPx
        val h = currentHeightPx
        _videoAspect.value = if (w > 0 && h > 0) w.toFloat() / h.toFloat() else null
        _videoSize.value = if (w > 0 && h > 0) w to h else null
    }

    /**
     * Abort playback when a >1080p video lands on the SOFTWARE decoder (hwdec-current == "no"):
     * TV CPUs can't sustain it — it stutters for a few seconds, then the memory/thermal pressure
     * gets the whole app killed. ≤1080p software decoding stays allowed (viable, and the rescue
     * path for streams the hardware decoder mangles).
     */
    private fun enforceDecodeGuard() {
        if (decodeGuardTripped) return
        val hw = currentHwdec ?: return
        val h = currentHeightPx
        if (h <= 1080 || (hw != "no" && hw.isNotEmpty())) return
        android.util.Log.w(TAG, "Decode guard TRIPPED: ${h}px on software decoder")
        decodeGuardTripped = true
        val res = resolutionLabel(h) ?: "${h}p"
        val msg = if (hwDecoding) {
            "This TV's hardware decoder doesn't support this $res video, and software decoding " +
                "above 1080p would overload the TV."
        } else {
            "Hardware decoding is turned off — software decoding can't handle $res video. " +
                "Enable it in Settings → Video Player."
        }
        // Halt decoding but KEEP currentUrl so the HUD's Retry works (e.g. after the user flips the
        // hardware-decoding setting, which applies live).
        loadGeneration++
        expectingPlayback = false
        errorCheckJob?.cancel()
        pendingUrl = null
        mpvAsync { stopWithStopClassification("decodeGuard") }
        scope.launch {
            _isPlaying.value = false
            _buffering.value = false
            _error.value = msg
        }
    }

    private fun resolutionLabel(height: Int): String? = when {
        height <= 0 -> null
        height >= 2160 -> "4K"
        height >= 1440 -> "1440p"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        height >= 480 -> "480p"
        else -> "${height}p"
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> _isPlaying.value = !value
            "paused-for-cache" -> _buffering.value = value
        }
    }

    override fun eventProperty(property: String, value: String) {
        when (property) {
            "hwdec-current" -> {
                android.util.Log.i(TAG, "hwdec-current='$value' (height=${currentHeightPx}px, setting=${if (hwDecoding) "on" else "off"})")
                currentHwdec = value
                enforceDecodeGuard()
            }
            "video-codec" -> currentVideoCodec = value.takeIf { it.isNotBlank() }
            // Active subtitle line for the app-drawn overlay (direct mode only; GL mode draws its own).
            "sub-text" -> {
                val line = value.trim().takeIf { it.isNotEmpty() }
                _subText.value = if (_directRender.value) line else null
                // Diagnostic: confirms caption/subtitle text is actually flowing (e.g. CEA-608 CC). DEBUG only.
                if (com.thirutricks.tllplayer.BuildConfig.DEBUG && line != null) {
                    android.util.Log.i(TAG, "sub-text (direct=${_directRender.value}): $line")
                }
            }
        }
    }
    override fun eventProperty(property: String, value: Double) {
        if (property == "speed") _speed.value = value
        if (property == "container-fps" && value > 0) _videoFps.value = value.toFloat()
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                val pendingStops = pendingStopEndFiles.getAndSet(0)
                if (pendingStops > 0) {
                    android.util.Log.w(
                        TAG,
                        "FILE_LOADED observed with pendingStopEndFiles=$pendingStops generation=$loadGeneration " +
                            "live=$isLiveContent; resetting counter",
                    )
                }
                markActiveFile(true, "file loaded")
                // The new file opened successfully — cancel any pending error and clear a stale one.
                // This callback runs on mpv's event thread (not main), so sync reads are safe here.
                expectingPlayback = false
                errorCheckJob?.cancel()
                _error.value = null
                _buffering.value = false // a reconnect's spinner ends when the new file loads
                _audioTrackList.value = queryTracks("audio")
                _subTrackList.value = queryTracks("sub")
                _audioCount.value = _audioTrackList.value.size
                _subCount.value = _subTrackList.value.size
                // Fast-zap safety net: a trimmed probe can miss the audio PMT on a sparse stream, leaving
                // a video-only load. If that happens, re-probe fully (once) so the channel plays with sound.
                if (usedTrimmedProbe && !forceFullProbe && _audioTrackList.value.isEmpty()) {
                    android.util.Log.w(TAG, "trimmed probe found no audio — re-probing fully")
                    forceFullProbe = true
                    reloadCurrentInPlace()
                    return
                }
                // A text subtitle the user picked while ExoPlayer was handling an image sub: apply it now
                // that mpv has reloaded and re-enumerated its tracks.
                pendingSelectSid?.let { sid ->
                    pendingSelectSid = null
                    mpv?.setPropertyInt("sid", sid)
                    mpv?.setPropertyString("sub-visibility", "yes")
                    _subTrackList.value = _subTrackList.value.map { it.copy(selected = it.mpvId == sid) }
                }
                mpv?.getPropertyBoolean("pause")?.let { _isPlaying.value = !it }
                mpv?.getPropertyInt("height")?.let { _videoRes.value = resolutionLabel(it) }
                setZoomMode(_zoomMode.value) // re-apply zoom on the new track
                if (pendingSeekMs > 0) {
                    val seekMs = pendingSeekMs
                    pendingSeekMs = 0
                    mpvAsync { command(arrayOf("seek", (seekMs / 1000).toString(), "absolute")) }
                }
                if (pendingStartPaused) { // restored a backgrounded VOD — hold it paused at the resume point
                    pendingStartPaused = false
                    mpvAsync { setPropertyBoolean("pause", true) }
                    _isPlaying.value = false
                }
                // Surround-output failsafe (#25): some sinks claim multichannel PCM support but mis-play it —
                // the audio drains ~2× fast, so mpv's audio-master clock (and the video) runs ~2× and the
                // sound is silent. mpv sees the output as fine (audio-params == audio-out-params, avsync ≈ 0),
                // so the only tell is the video running away: estimated-vf-fps ≈ 2× the file's container-fps.
                // Checked in the 5–15 s window (past the start-up burst, before long drift) and skipped while
                // seeking (a seek bursts frames to catch up and would false-trip). On a hit, latch surround off
                // for the session and reload this item in stereo.
                if (!isLiveContent) {
                    val sgen = loadGeneration
                    scope.launch {
                        delay(7_000)
                        if (sgen != loadGeneration || surroundOutputBroken || !surroundSound) return@launch
                        mpvAsync {
                            if (getPropertyString("seeking") == "yes") return@mpvAsync // catching up — not a real runaway
                            val cfps = getPropertyString("container-fps")?.toDoubleOrNull() ?: 0.0
                            val vfps = getPropertyString("estimated-vf-fps")?.toDoubleOrNull() ?: 0.0
                            if (cfps > 1.0 && vfps > cfps * 1.5) {
                                android.util.Log.w(TAG, "surround runaway: est-vf-fps=$vfps vs container-fps=$cfps — falling back to stereo")
                                surroundOutputBroken = true
                                setPropertyString("audio-channels", "stereo")
                                setPropertyString("audio-format", "")
                                setPropertyString("audio-samplerate", "0")
                                toast("This audio output can't do surround — switched to stereo.")
                                if (sgen == loadGeneration && currentUrl != null) {
                                    loadUrl(currentUrl!!, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, _position.value, resetRetries = false)
                                }
                            }
                        }
                    }
                }
                // Decode watchdog, polled: the decoder is chosen a few seconds AFTER the file loads,
                // so read it directly once it has settled (the observed event also runs enforceDecodeGuard).
                val gen = loadGeneration
                scope.launch {
                    delay(4_000)
                    if (gen != loadGeneration) return@launch
                    mpvAsync {
                        val hw = getPropertyString("hwdec-current") ?: ""
                        val h = getPropertyInt("height") ?: 0
                        android.util.Log.i(TAG, "decode check: hwdec-current='$hw' height=${h}px direct=${_directRender.value}")
                        // Diagnostics for "video plays like a slideshow": is mpv dropping frames (timing),
                        // is the decoder dropping (too slow), or is the network cache underrunning?
                        android.util.Log.i(
                            TAG,
                            "playback stats: container-fps=${getPropertyString("container-fps")} " +
                                "est-vf-fps=${getPropertyString("estimated-vf-fps")} " +
                                "frame-drops=${getPropertyString("frame-drop-count")} " +
                                "decoder-drops=${getPropertyString("decoder-frame-drop-count")} " +
                                "cache=${getPropertyString("demuxer-cache-duration")}s " +
                                "paused-for-cache=${getPropertyString("paused-for-cache")} " +
                                "video-bitrate=${getPropertyString("video-bitrate")}",
                        )
                        // The direct surface can only display hardware frames. If the direct decoder
                        // didn't engage (cold-boot decoder-busy, etc.), retry direct a few times (it
                        // usually frees within seconds), then fall back to software decode, then error.
                        if (_directRender.value && (hw.isEmpty() || hw == "no")) {
                            val pos = if (isLiveContent) 0L else _position.value
                            if (autoRetries < maxRetries()) {
                                autoRetries++
                                // The trimmed fast-zap probe is only for the FIRST attempt. If the hardware
                                // decoder failed to engage, the probe may have under-read this stream's
                                // config (e.g. a 4K HEVC/HDR channel needs more than 1 MB to get its VPS/SPS
                                // + HDR metadata, or MediaCodec errors 0x80001000) — so re-probe in FULL.
                                forceFullProbe = true
                                android.util.Log.w(TAG, "direct failed — retry $autoRetries/${maxRetries()} (full probe)")
                                _buffering.value = true
                                scope.launch {
                                    delay(backoffMs(autoRetries))
                                    if (gen == loadGeneration) loadUrl(currentUrl ?: return@launch, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, pos, resetRetries = false)
                                }
                            } else if (hwDecodingActive() && !glUnsupported && lastVideoHeightPx <= 1080) {
                                // Direct decoder never engaged after retries — fall back to software decode
                                // (GL) for this item (weak decoders that mangle the stream) before erroring.
                                // Skipped on emulators (translated GL crashes) and for >1080p (software can't
                                // sustain it — the guard would trip; we'd rather show a clean error).
                                android.util.Log.w(TAG, "direct failed — falling back to software decode for this item")
                                forceSoftwareThisLoad = true
                                applyRenderConfig()
                                scope.launch { loadUrl(currentUrl ?: return@launch, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, pos, resetRetries = false) }
                            } else {
                                android.util.Log.w(TAG, "direct failed — retries exhausted, showing error")
                                scope.launch { _buffering.value = false; _error.value = "This TV's video decoder is busy. Try again in a moment." }
                            }
                            return@mpvAsync
                        }
                        currentHwdec = hw.ifEmpty { null }
                        if (h > 0) currentHeightPx = h
                        enforceDecodeGuard()
                    }
                }
            }
            // mpv's Kotlin wrapper does not expose mpv_event_end_file.reason, so classify the cases we
            // know the app caused (replacement loadfile, manual stop, decode guard) before treating an
            // END_FILE as a possible playback failure.
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> {
                if (consumePendingStopEndFile()) return
                markActiveFile(false, "end file")
                if (expectingPlayback) {
                    val gen = loadGeneration
                    errorCheckJob?.cancel()
                    errorCheckJob = scope.launch {
                        // Unclassified END_FILE is not always a final failure: slow or flaky live
                        // streams can still report FILE_LOADED shortly after. Give mpv a short grace
                        // window; FILE_LOADED clears expectingPlayback/cancels this path.
                        delay(1500)
                        if (!expectingPlayback || gen != loadGeneration) return@launch
                        // No internet → don't burn the retry budget on a dead connection; surface the
                        // offline error straight away.
                        if (!connectivity.isOnlineNow()) {
                            android.util.Log.w(TAG, "playback didn't start — offline, skipping retries")
                            _buffering.value = false
                            _error.value = "No internet connection. Check your network and try again."
                            return@launch
                        }
                        // A live `.ts` stream that retried once and still won't start may be on a panel
                        // that only serves HLS — try the `.m3u8` variant of the same channel before erroring
                        // (we default to `.ts` since it's more widely supported, with this as the safety net).
                        val tsUrl = currentUrl
                        val catchupAlt = if (!isLiveContent && !triedAltFormat) com.thirutricks.tllplayer.core.epg.CatchupUrl.timeshiftPhpAlternate(tsUrl) else null
                        if (catchupAlt != null) {
                            // Some Xtream panels reject the path-style catch-up URL (they return an HTML
                            // error page → "unrecognized format") but accept the PHP query form. Try it.
                            triedAltFormat = true
                            autoRetries = 0
                            android.util.Log.w(TAG, "catch-up path URL rejected — trying timeshift.php fallback")
                            _buffering.value = true
                            delay(300)
                            if (gen == loadGeneration) {
                                loadUrl(catchupAlt, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, 0L, resetRetries = false)
                            }
                        } else if (isLiveContent && !triedAltFormat && autoRetries >= 1 && tsUrl != null && tsUrl.endsWith(".ts", ignoreCase = true)) {
                            triedAltFormat = true
                            autoRetries = 0
                            val alt = tsUrl.dropLast(3) + ".m3u8"
                            android.util.Log.w(TAG, "live .ts didn't start — trying .m3u8 fallback")
                            _buffering.value = true
                            delay(300)
                            if (gen == loadGeneration) {
                                loadUrl(alt, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl), isLiveContent, 0L, resetRetries = false)
                            }
                        }
                        // The stream didn't start. Silently retry a few times with exponential backoff
                        // before surfacing the error — handles transient failures (cold-boot decoder-busy,
                        // a provider 5xx, the first-play surface race) so the user rarely sees an error.
                        else if (autoRetries < maxRetries() && currentUrl != null) {
                            autoRetries++
                            // Re-probe in FULL on retry: the trimmed fast-zap probe is first-attempt only, and
                            // an under-read 4K/HDR stream is a common reason a load fails to start.
                            forceFullProbe = true
                            android.util.Log.w(TAG, "playback didn't start — auto-retry $autoRetries/${maxRetries()} (full probe)")
                            _buffering.value = true
                            delay(backoffMs(autoRetries))
                            if (gen == loadGeneration && currentUrl != null) {
                                loadUrl(
                                    currentUrl!!, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl),
                                    isLiveContent, if (isLiveContent) 0L else _position.value, resetRetries = false,
                                )
                            }
                        } else if (hwDecodingActive() && !glUnsupported && lastVideoHeightPx <= 1080 && currentUrl != null) {
                            // Hardware decoding never got it going — some weak TV decoders reject streams
                            // that software decoding plays fine. Try once in pure software before erroring.
                            // Skipped on emulators (translated GL crashes) and for >1080p (software can't
                            // sustain it — the guard would trip).
                            android.util.Log.w(TAG, "playback didn't start on hardware — falling back to software decode")
                            forceSoftwareThisLoad = true
                            _buffering.value = true
                            mpvAsync { applyRenderConfig() }
                            delay(200)
                            if (gen == loadGeneration && currentUrl != null) {
                                loadUrl(
                                    currentUrl!!, MediaMeta(currentTitle, currentSubtitle, currentYear, currentLogoUrl),
                                    isLiveContent, if (isLiveContent) 0L else _position.value, resetRetries = false,
                                )
                            }
                        } else {
                            _buffering.value = false
                            _error.value = "Couldn't play this stream. The source may be offline or use an unsupported format."
                        }
                    }
                } else if (isLiveContent && currentUrl != null) {
                    // A live stream died mid-play (provider hiccup / connection limit → HTTP 509, OR a
                    // hardware-decoder error like Realtek's 0x80001000 on 4K HEVC): mpv goes idle and the
                    // screen would stay blank. Reconnect after a pause LONG ENOUGH for the hardware decoder
                    // to finish releasing — a 4K decoder on TV-class silicon takes ~3 s, and re-initializing
                    // it sooner throws 0x80001000 and churns forever. Once it releases cleanly the reconnect
                    // succeeds, so the loop ends in playback rather than an endless re-init storm.
                    if (!connectivity.isOnlineNow()) {
                        _buffering.value = false
                        _error.value = "No internet connection. Check your network and try again."
                    } else {
                        _buffering.value = true
                        val gen = loadGeneration
                        scope.launch {
                            delay(3500)
                            if (gen == loadGeneration && currentUrl != null) retry() else _buffering.value = false
                        }
                    }
                } else if (!isLiveContent && currentUrl != null) {
                    // A VOD finished. If it reached the end (position is at/near the duration — not a
                    // mid-stream drop) and auto-play is on, continue an episode queue: advance to the next
                    // episode in the season, or signal the series VM to roll into the next season when the
                    // season's last episode ends. Single movies (empty playlist) just stop.
                    val dur = _duration.value
                    val pos = _position.value
                    val reachedEnd = dur > 0 && pos >= dur - 8_000
                    if (reachedEnd && autoPlayNext && playlist.isNotEmpty()) {
                        // Advance after a short settle (let the ended episode's decoder release). The fresh
                        // Surface in loadUrl is what actually prevents the back-to-back >1080p 0x80001000.
                        val gen = loadGeneration
                        if (playlistIndex < playlist.size - 1) {
                            scope.launch { delay(600); if (gen == loadGeneration) next() } // next ep, same season
                        } else {
                            scope.launch { delay(600); if (gen == loadGeneration) _queueEnded.tryEmit(Unit) } // → next season
                        }
                    }
                }
            }
        }
    }

}
