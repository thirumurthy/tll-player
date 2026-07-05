package com.thirutricks.tllplayer.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import com.thirutricks.tllplayer.core.network.HttpClient

/**
 * ExoPlayer (Media3) that drives the muted **in-pane Live preview**. ExoPlayer starts HLS far faster than
 * mpv (which full-probes ~5 s before the first frame), so scrolling the channel list feels responsive.
 *
 * The **full** player stays on mpv (4K/HDR direct path, broad IPTV/raw-TS compatibility) — going fullscreen
 * [stop]s this engine and hands the channel to mpv. Preview and fullscreen use separate SurfaceViews on
 * separate screens, so the two decoders never share a surface. A single long-lived instance (Koin single),
 * like [OwnTVPlayer]; it's [stop]ped (not released) whenever the preview isn't on screen.
 *
 * All calls must be on the main thread (ExoPlayer is single-threaded): the VM invokes [play]/[stop]/
 * [setMuted] from the UI thread and the Compose surface invokes [setSurface] from the holder callback.
 */
@UnstableApi
class LivePreviewEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val diagnostics: PlayerDiagnostics,
) : PlaybackEngine {
    enum class State { IDLE, LOADING, PLAYING, ERROR }

    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var muted: Boolean = true

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()
    private val _videoHeight = MutableStateFlow<Int?>(null)
    val videoHeight: StateFlow<Int?> = _videoHeight.asStateFlow()
    // Up-to-4 mini stream chips for the preview pane: aspect · resolution · fps · audio.
    private val _streamChips = MutableStateFlow<List<String>>(emptyList())
    val streamChips: StateFlow<List<String>> = _streamChips.asStateFlow()

    // --- PlaybackEngine: lets the full-screen HUD drive a promoted preview (play/pause, state, volume) ---
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _buffering = MutableStateFlow(false)
    override val buffering: StateFlow<Boolean> = _buffering.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()
    private val _errorInfo = MutableStateFlow<ErrorInfo?>(null)
    override val errorInfo: StateFlow<ErrorInfo?> = _errorInfo.asStateFlow()
    private val _videoRes = MutableStateFlow<String?>(null)
    override val videoRes: StateFlow<String?> = _videoRes.asStateFlow()
    private val _volume = MutableStateFlow(100)
    override val volume: StateFlow<Int> = _volume.asStateFlow()
    private val _zoomMode = MutableStateFlow(ZoomMode.FIT)
    override val zoomMode: StateFlow<ZoomMode> = _zoomMode.asStateFlow()
    private val _audioCount = MutableStateFlow(0)
    override val audioCount: StateFlow<Int> = _audioCount.asStateFlow()
    private val _subCount = MutableStateFlow(0)
    override val subCount: StateFlow<Int> = _subCount.asStateFlow()
    // Audio/text tracks enumerated from the active stream (multi-language live, or a VOD file added via M3U).
    private var audioTrackList: List<TrackOption> = emptyList()
    private var audioSelections: List<AudioSel> = emptyList()
    private var textTrackList: List<TrackOption> = emptyList()
    private var textSelections: List<TextSel> = emptyList()
    private data class AudioSel(val id: Int, val group: androidx.media3.common.TrackGroup, val trackIndex: Int)
    private data class TextSel(val id: Int, val group: androidx.media3.common.TrackGroup, val trackIndex: Int)
    // Subtitle cues + an "on" flag. The Compose surface mounts a SubtitleView ONLY while [subtitleOn] (else
    // any overlaid view knocks the SurfaceView off the hardware-overlay path and stutters 4K — same as VOD).
    private val _cues = MutableStateFlow<List<androidx.media3.common.text.Cue>>(emptyList())
    val cues: StateFlow<List<androidx.media3.common.text.Cue>> = _cues.asStateFlow()
    private val _subtitleOn = MutableStateFlow(false)
    val subtitleOn: StateFlow<Boolean> = _subtitleOn.asStateFlow()
    // True when the stream HAS audio but ExoPlayer can decode NONE of it (e.g. AC3/E-AC3/DTS on a device
    // without that decoder) — the VM hands such a stream to mpv (FFmpeg decodes everything) so it isn't silent.
    private val _audioUnsupported = MutableStateFlow(false)
    val audioUnsupported: StateFlow<Boolean> = _audioUnsupported.asStateFlow()

    // Programmatic codec/audio errors (Reviewer: more reliable than logcat for ExoPlayer, and survives the
    // Android 14+ own-logcat lockdown). MediaCodec.CodecException.diagnosticInfo carries the exact code
    // (e.g. 0x80001000); AudioSink errors name the audio failure. Reset per load, preferred when present.
    @Volatile private var lastCodecError: String? = null
    @Volatile private var lastVideoDecoder: String? = null // e.g. "OMX.realtek.video.decoder", for the spec line
    private val analytics = object : androidx.media3.exoplayer.analytics.AnalyticsListener {
        override fun onVideoCodecError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, videoCodecError: Exception) {
            lastCodecError = codecDetail("video", videoCodecError)
        }
        override fun onAudioCodecError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, audioCodecError: Exception) {
            lastCodecError = codecDetail("audio", audioCodecError)
        }
        override fun onAudioSinkError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, audioSinkError: Exception) {
            lastCodecError = "audio: ${audioSinkError.message ?: audioSinkError.javaClass.simpleName}"
        }
        override fun onVideoDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
            lastVideoDecoder = decoderName
        }
    }

    /** "HEVC 1920x1080 • OMX.realtek.video.decoder" from the active stream, for the error screen's spec line. */
    private fun exoSpec(): String? {
        val f = player?.videoFormat
        val codec = f?.sampleMimeType?.substringAfterLast('/')?.let { mimeName(it) }
        val res = if (f != null && f.width > 0 && f.height > 0) "${f.width}x${f.height}" else null
        val head = listOfNotNull(codec, res).joinToString(" ").ifBlank { null }
        return listOfNotNull(head, lastVideoDecoder).joinToString(" • ").ifBlank { null }
    }
    private fun mimeName(m: String) = when (m.lowercase()) {
        "hevc" -> "HEVC"; "avc" -> "H.264"; "av01" -> "AV1"; "x-vnd.on2.vp9", "vp9" -> "VP9"
        "mp4v-es" -> "MPEG-4"; "mpeg2" -> "MPEG-2"; else -> m.uppercase()
    }
    private fun codecDetail(kind: String, e: Exception): String {
        (e as? android.media.MediaCodec.CodecException)?.let { return "$kind codec: ${it.diagnosticInfo}" }
        return "$kind codec: ${e.message ?: e.javaClass.simpleName}"
    }

    /** Technical readout for the stream-info overlay, from the active ExoPlayer formats. */
    override fun streamInfo(): List<Pair<String, String>> {
        val p = player ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        p.videoFormat?.let { f ->
            val line = listOfNotNull(
                f.sampleMimeType?.substringAfterLast('/')?.let { mimeName(it) },
                if (f.width > 0 && f.height > 0) "${f.width}×${f.height}" else null,
                if (f.frameRate > 0) "%.2f fps".format(f.frameRate) else null,
            ).joinToString(" · ")
            if (line.isNotBlank()) out += "Video" to line
            when (f.colorInfo?.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> "HDR10 (PQ)"; C.COLOR_TRANSFER_HLG -> "HLG"; else -> null
            }?.let { out += "HDR" to it }
            if (f.bitrate > 0) out += "Bitrate" to "%.1f Mbps".format(f.bitrate / 1_000_000.0)
        }
        out += "Decoder" to "ExoPlayer (hardware)"
        p.audioFormat?.let { f ->
            val line = listOfNotNull(
                f.sampleMimeType?.substringAfterLast('/')?.uppercase(),
                when (f.channelCount) { 1 -> "mono"; 2 -> "stereo"; 6 -> "5.1"; 8 -> "7.1"; else -> null },
                if (f.sampleRate > 0) "%.0f kHz".format(f.sampleRate / 1000.0) else null,
            ).joinToString(" · ")
            if (line.isNotBlank()) out += "Audio" to line
        }
        if (p.totalBufferedDuration > 0) out += "Buffer" to "%.1f s".format(p.totalBufferedDuration / 1000.0)
        currentUrl?.let { out += "Source" to HttpClient.redactUrl(it) }
        return out
    }
    /** Recompute the preview's mini chips (aspect · resolution · fps · audio) from the active formats. */
    private fun updateStreamChips() {
        val p = player ?: run { _streamChips.value = emptyList(); return }
        val chips = ArrayList<String>(4)
        p.videoFormat?.let { f ->
            if (f.width > 0 && f.height > 0) aspectLabel(f.width, f.height)?.let { chips += it }
            qualityLabel(f.height)?.let { chips += it }
            if (f.frameRate > 0) chips += "${Math.round(f.frameRate)} FPS"
        }
        p.audioFormat?.let { f ->
            (when (f.channelCount) { 1 -> "MONO"; 2 -> "STEREO"; 6 -> "5.1"; 8 -> "7.1"; else -> null })?.let { chips += it }
        }
        _streamChips.value = chips
    }
    private fun aspectLabel(w: Int, h: Int): String? {
        if (w <= 0 || h <= 0) return null
        val r = w.toFloat() / h
        return when {
            r in 1.72f..1.82f -> "16:9"
            r in 1.28f..1.40f -> "4:3"
            r >= 2.15f -> "21:9"
            r in 1.55f..1.66f -> "16:10"
            else -> "%.2f:1".format(r)
        }
    }
    private fun qualityLabel(h: Int): String? = when {
        h <= 0 -> null
        h >= 2160 -> "4K"
        h >= 1440 -> "1440p"
        h >= 1080 -> "1080p"
        h >= 720 -> "720p"
        h >= 480 -> "480p"
        else -> "${h}p"
    }

    private val _currentMeta = MutableStateFlow(MediaMeta())
    override val currentMeta: StateFlow<MediaMeta> = _currentMeta.asStateFlow()
    override val isLiveContent: Boolean = true

    /** URL the preview is currently on (null when stopped) — lets the VM skip a redundant reload. */
    var currentUrl: String? = null
        private set

    // Live auto-reconnect: a channel that DID play and then errors/stalls (provider hiccup / Wi-Fi blip)
    // re-fetches from the live edge instead of dead-ending. A channel that NEVER opened keeps the old
    // ERROR (so the VM falls back to mpv). retryCount resets whenever playback goes healthy again.
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var hasPlayed = false
    private var retryCount = 0
    private val stallWatchdog = Runnable { reconnect("buffering stalled") }

    // Silent-freeze watchdog: some live streams stop advancing while ExoPlayer stays in STATE_READY with no
    // buffering event and no error — so neither the stall watchdog nor onPlayerError fires and the channel
    // hangs forever with no retry. We also poll the playback position: if it's frozen while we're supposed to
    // be playing, treat it as a dropped feed and reconnect.
    private var lastProgressPos = -1L
    private var frozenChecks = 0
    private val progressWatchdog = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && hasPlayed && p.playWhenReady && p.playbackState == Player.STATE_READY) {
                val pos = p.currentPosition
                if (pos > 0 && pos == lastProgressPos) {
                    if (++frozenChecks >= FROZEN_LIMIT) { frozenChecks = 0; lastProgressPos = -1L; reconnect("stream frozen"); return }
                } else {
                    frozenChecks = 0; lastProgressPos = pos
                }
            } else {
                frozenChecks = 0; lastProgressPos = -1L
            }
            mainHandler.postDelayed(this, PROGRESS_CHECK_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.value = State.LOADING; _buffering.value = true
                    // After it has played, a long buffer == a dropped feed → reconnect (live streams don't
                    // resume on their own here). Before first play, leave initial load alone.
                    if (hasPlayed) { mainHandler.removeCallbacks(stallWatchdog); mainHandler.postDelayed(stallWatchdog, STALL_MS) }
                }
                Player.STATE_READY -> {
                    _state.value = State.PLAYING; _buffering.value = false
                    hasPlayed = true; retryCount = 0; mainHandler.removeCallbacks(stallWatchdog)
                    // (re)start the silent-freeze poll now that we're actually playing
                    lastProgressPos = -1L; frozenChecks = 0
                    mainHandler.removeCallbacks(progressWatchdog); mainHandler.postDelayed(progressWatchdog, PROGRESS_CHECK_MS)
                }
                else -> { _buffering.value = false; mainHandler.removeCallbacks(stallWatchdog) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) { _isPlaying.value = isPlaying }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height > 0) {
                _videoHeight.value = videoSize.height
                _videoRes.value = "${videoSize.height}p"
            }
            updateStreamChips()
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) { rebuildTracks(tracks); updateStreamChips() }
        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) { _cues.value = cueGroup.cues }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.w(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
            if (hasPlayed) { reconnect("error ${error.errorCodeName}"); return } // mid-stream drop → reconnect
            // Never opened → a stream ExoPlayer can't handle; the VM falls back to mpv on this ERROR.
            _state.value = State.ERROR
            _isPlaying.value = false
            _buffering.value = false
            _error.value = "Couldn't play this channel."
            val raw = lastCodecError ?: diagnostics.recentError()
                ?: error.errorCodeName + ((error.cause?.message ?: error.message)?.let { ": $it" } ?: "")
            _errorInfo.value = ErrorInfo(PlayerErrors.reasonFor(raw), exoSpec(), raw)
        }
    }

    /** Attach the preview SurfaceView's surface, or null when it's destroyed. */
    fun setSurface(s: Surface?) {
        surface = s
        if (s != null) player?.setVideoSurface(s) else player?.clearVideoSurface()
    }

    /** Start (or switch to) [url] as a muted/unmuted preview. Never throws — a stream ExoPlayer can't set
     *  up just falls back to the channel logo (the full mpv player can still play it). [meta] populates the
     *  full-screen HUD title when this preview is promoted. */
    fun play(url: String, muted: Boolean, meta: MediaMeta = MediaMeta()) {
        diagnostics.start(); diagnostics.markLoad()
        lastCodecError = null; lastVideoDecoder = null
        this.muted = muted
        currentUrl = url
        hasPlayed = false; retryCount = 0; mainHandler.removeCallbacks(stallWatchdog); mainHandler.removeCallbacks(progressWatchdog)
        audioTrackList = emptyList(); audioSelections = emptyList(); _audioCount.value = 0
        textTrackList = emptyList(); textSelections = emptyList(); _subCount.value = 0
        _subtitleOn.value = false; _cues.value = emptyList(); _audioUnsupported.value = false
        _videoHeight.value = null; _streamChips.value = emptyList()
        _videoRes.value = null
        _error.value = null
        _errorInfo.value = null
        _currentMeta.value = meta
        _volume.value = if (muted) 0 else 100
        _state.value = State.LOADING
        _buffering.value = true
        runCatching {
            val p = player ?: build().also { player = it }
            surface?.let { p.setVideoSurface(it) }
            p.volume = if (muted) 0f else 1f
            p.setMediaSource(mediaSourceFor(url))
            p.prepare()
            p.playWhenReady = true
        }.onFailure {
            android.util.Log.w(TAG, "preview play() failed for $url", it)
            _state.value = State.ERROR
            _error.value = "Couldn't play this channel."
            val raw = lastCodecError ?: diagnostics.recentError() ?: it.message
            _errorInfo.value = raw?.let { r -> ErrorInfo(PlayerErrors.reasonFor(r), exoSpec(), r) }
        }
    }

    fun setMuted(m: Boolean) {
        muted = m
        player?.volume = if (m) 0f else 1f
        _volume.value = if (m) 0 else 100
    }

    // Snapshot of the live channel taken when the app backgrounds (screensaver / Home), so it can be restored
    // on return — otherwise onStop frees the stream and a paused live channel never resumes (even on Play).
    private data class LiveRestore(val url: String, val muted: Boolean, val meta: MediaMeta)
    @Volatile private var backgroundRestore: LiveRestore? = null

    /** Backgrounded (screensaver / Home): remember what's playing, then free the stream. Paired with
     *  [onAppForegrounded]. */
    fun onAppBackgrounded() {
        currentUrl?.let { backgroundRestore = LiveRestore(it, muted, _currentMeta.value) }
        stop()
    }

    /** Foregrounded: re-tune the live channel that was freed while backgrounded (at the live edge), so it
     *  resumes instead of sitting on a dead/empty stream. No-op if something is already playing. */
    fun onAppForegrounded() {
        val r = backgroundRestore ?: return
        backgroundRestore = null
        if (currentUrl != null) return
        play(r.url, muted = r.muted, meta = r.meta)
    }

    /** Drop any pending restore (e.g. on profile switch — don't bring back the previous user's channel). */
    fun discardBackgroundRestore() { backgroundRestore = null }

    /** Stop playback and free the decoder/connection (e.g. before mpv takes over for fullscreen). Keeps the
     *  ExoPlayer instance alive for the next preview. */
    fun stop() {
        currentUrl = null
        hasPlayed = false; retryCount = 0; mainHandler.removeCallbacks(stallWatchdog); mainHandler.removeCallbacks(progressWatchdog)
        audioTrackList = emptyList(); audioSelections = emptyList(); _audioCount.value = 0
        textTrackList = emptyList(); textSelections = emptyList(); _subCount.value = 0
        _subtitleOn.value = false; _cues.value = emptyList(); _audioUnsupported.value = false
        _videoHeight.value = null; _streamChips.value = emptyList()
        _state.value = State.IDLE
        player?.run { stop(); clearMediaItems() }
    }

    fun release() {
        mainHandler.removeCallbacks(stallWatchdog)
        player?.run { removeListener(listener); release() }
        player = null
        surface = null
        currentUrl = null
        _state.value = State.IDLE
    }

    /** Live auto-reconnect: re-fetch [currentUrl] from the live edge after a mid-stream error/stall. Backs
     *  off and gives up after [MAX_RECONNECTS] consecutive failures (then the HUD's Retry button takes over).
     *  retryCount is reset to 0 as soon as playback goes healthy again (STATE_READY). */
    private fun reconnect(reason: String) {
        mainHandler.removeCallbacks(stallWatchdog); mainHandler.removeCallbacks(progressWatchdog)
        val p = player
        val url = currentUrl
        if (p == null || url == null || retryCount >= MAX_RECONNECTS) {
            _state.value = State.ERROR; _isPlaying.value = false; _buffering.value = false
            _error.value = "Lost connection to this channel."
            val raw = lastCodecError ?: diagnostics.recentError() ?: reason
            _errorInfo.value = ErrorInfo(PlayerErrors.reasonFor(raw), exoSpec(), raw)
            return
        }
        retryCount++
        _error.value = null; _errorInfo.value = null; _state.value = State.LOADING; _buffering.value = true
        android.util.Log.w(TAG, "live reconnect ($reason) — attempt $retryCount/$MAX_RECONNECTS")
        mainHandler.postDelayed({
            if (currentUrl != url) return@postDelayed // superseded (zapped / stopped)
            runCatching {
                p.setMediaItem(MediaItem.fromUri(url)) // fresh fetch (live edge)
                p.prepare()
                p.playWhenReady = true
            }.onFailure { _state.value = State.ERROR; _error.value = "Lost connection to this channel." }
        }, (1500L * retryCount).coerceAtMost(4000L))
    }

    // --- PlaybackEngine controls (full-screen HUD) ---
    override fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    override fun setZoomMode(mode: ZoomMode) { _zoomMode.value = mode } // surface scaling is Phase 3; renders FIT

    override fun adjustVolume(delta: Int) {
        // Live engine (ExoPlayer) caps at 100% — boost above 100 is mpv-only (Exo can't amplify past unity
        // and a gain audio-processor broke the audio sink). VOD/series/compat-live play on mpv and do boost.
        val v = (_volume.value + delta).coerceIn(0, 100)
        _volume.value = v
        muted = v == 0
        player?.volume = v / 100f
    }

    override fun toggleMute() = setMuted(!muted)
    override fun retry() { currentUrl?.let { play(it, muted, _currentMeta.value) } }
    override fun selectAudio(id: Int) {
        val p = player ?: return
        val sel = audioSelections.firstOrNull { it.id == id } ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(sel.group, listOf(sel.trackIndex)))
            .build()
        audioTrackList = audioTrackList.map { it.copy(selected = it.mpvId == id) }
    }

    override fun selectSubtitle(id: Int) {
        val p = player ?: return
        val sel = textSelections.firstOrNull { it.id == id } ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(androidx.media3.common.TrackSelectionOverride(sel.group, listOf(sel.trackIndex)))
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
            .build()
        _subtitleOn.value = true // mount the SubtitleView overlay
        textTrackList = textTrackList.map { it.copy(selected = it.mpvId == id) }
    }

    override fun disableSubtitles() {
        player?.let {
            it.trackSelectionParameters = it.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true).build()
        }
        _subtitleOn.value = false
        _cues.value = emptyList()
        textTrackList = textTrackList.map { it.copy(selected = false) }
    }

    override fun audioTracks(): List<TrackOption> = audioTrackList
    override fun textTracks(): List<TrackOption> = textTrackList

    /** Build the audio + subtitle track lists from the active stream so the HUD menus can switch language /
     *  subtitles (multi-track live channels, or a VOD file imported via M3U). Mirrors [ExoSubtitleEngine]. */
    private fun rebuildTracks(tracks: androidx.media3.common.Tracks) {
        val audio = ArrayList<TrackOption>(); val aSel = ArrayList<AudioSel>(); var aId = 0
        val text = ArrayList<TrackOption>(); val tSel = ArrayList<TextSel>(); var tId = 0
        for (group in tracks.groups) {
            when (group.type) {
                androidx.media3.common.C.TRACK_TYPE_AUDIO -> for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
                    audio.add(TrackOption(f.label ?: lang?.uppercase() ?: "Audio ${aId + 1}", aId, group.isTrackSelected(i), lang = lang))
                    aSel.add(AudioSel(aId, group.mediaTrackGroup, i)); aId++
                }
                androidx.media3.common.C.TRACK_TYPE_TEXT -> for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }
                    text.add(TrackOption(f.label ?: lang?.uppercase() ?: "Subtitle ${tId + 1}", tId, _subtitleOn.value && group.isTrackSelected(i), lang = lang))
                    tSel.add(TextSel(tId, group.mediaTrackGroup, i)); tId++
                }
            }
        }
        audioTrackList = audio; audioSelections = aSel; _audioCount.value = audio.size
        textTrackList = text; textSelections = tSel; _subCount.value = text.size
        // Audio exists but ExoPlayer can decode none of it → the VM will route this stream to mpv.
        val anySupportedAudio = tracks.groups.any { g ->
            g.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && (0 until g.length).any { g.isTrackSupported(it) }
        }
        _audioUnsupported.value = audio.isNotEmpty() && !anySupportedAudio
    }

    private val httpDataSource by lazy {
        OkHttpDataSource.Factory(okHttpClient).setUserAgent(HttpClient.DEFAULT_USER_AGENT)
    }
    private val defaultSourceFactory by lazy { DefaultMediaSourceFactory(httpDataSource) }
    // HLS factory that exposes embedded CEA-608 closed captions even when the playlist doesn't *declare*
    // them — US premium channels (HBO/Showtime/Cinemax) carry CC inside the video stream, not as a subtitle
    // track, so ExoPlayer otherwise shows nothing. The forced 608 track then appears in the subtitle HUD.
    private val hlsCcSourceFactory by lazy {
        HlsMediaSource.Factory(httpDataSource).setExtractorFactory(DefaultHlsExtractorFactory(0, true))
    }

    /** HLS → caption-aware factory; everything else (raw MPEG-TS, etc.) → default. */
    private fun mediaSourceFor(url: String): MediaSource {
        val item = MediaItem.fromUri(url)
        val uri = item.localConfiguration?.uri ?: return defaultSourceFactory.createMediaSource(item)
        return if (Util.inferContentType(uri) == C.CONTENT_TYPE_HLS) hlsCcSourceFactory.createMediaSource(item)
        else defaultSourceFactory.createMediaSource(item)
    }

    private fun build(): ExoPlayer {
        // Shallow buffers — a preview only needs to start quickly, not buffer deep.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_000, 8_000, 1_000, 2_000)
            .build()
        // forceDisableMediaCodecAsynchronousQueueing(): Media3 runs MediaCodec asynchronously by default on
        // API 31+, which corrupts (macroblocks) some UHD-HEVC streams on Realtek/Amlogic VPUs — the
        // synchronous path is what players like TiviMate use to avoid it. Channels it still can't decode
        // cleanly are handed to mpv via the per-channel "force mpv" routing.
        val renderers = DefaultRenderersFactory(context).forceDisableMediaCodecAsynchronousQueueing()
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderers)
            .setMediaSourceFactory(defaultSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply { addListener(listener); addAnalyticsListener(analytics) }
    }

    companion object {
        private const val TAG = "LivePreviewEngine"
        private const val MAX_RECONNECTS = 6        // ~consecutive failures before giving up (HUD Retry then)
        private const val STALL_MS = 12_000L        // buffering this long after playing == a dropped feed
        private const val PROGRESS_CHECK_MS = 2_500L // poll interval for the silent-freeze watchdog
        private const val FROZEN_LIMIT = 3          // position frozen this many polls (~7.5s) == a dropped feed
    }
}
