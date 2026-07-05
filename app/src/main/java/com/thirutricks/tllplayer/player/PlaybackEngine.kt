package com.thirutricks.tllplayer.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the player HUD needs from "whichever engine is currently playing" — mpv ([OwnTVPlayer], via
 * [MpvPlaybackEngine]) or the ExoPlayer live engine ([LivePreviewEngine] when a Live preview is promoted to
 * full-screen). VOD-only controls (seek/speed/prev-next/position) have no-op defaults so a live engine need
 * only implement the live-relevant members.
 */
interface PlaybackEngine {
    val isPlaying: StateFlow<Boolean>
    val buffering: StateFlow<Boolean>
    val error: StateFlow<String?>
    /** The structured underlying failure (plain reason • media spec • raw engine text), shown under the
     *  friendly message so users can report the real cause without adb/logcat. Null when none. */
    val errorInfo: StateFlow<ErrorInfo?> get() = NULL_ERROR
    val videoRes: StateFlow<String?>
    val volume: StateFlow<Int>
    val zoomMode: StateFlow<ZoomMode>
    val audioCount: StateFlow<Int>
    val subCount: StateFlow<Int>
    val currentMeta: StateFlow<MediaMeta>
    val isLiveContent: Boolean

    fun togglePlayPause()
    fun setZoomMode(mode: ZoomMode)
    fun adjustVolume(delta: Int)
    fun toggleMute()
    fun retry()
    fun selectAudio(id: Int)
    fun selectSubtitle(id: Int)
    fun disableSubtitles()
    fun audioTracks(): List<TrackOption>
    fun textTracks(): List<TrackOption>

    /** Live technical readout (label → value) for the stream-info overlay — codec, resolution, fps, HDR,
     *  bitrate, decoder, audio, buffer, source. A snapshot; the overlay re-reads it periodically. */
    fun streamInfo(): List<Pair<String, String>> = emptyList()

    // VOD-only — sensible no-op / empty defaults for a live engine.
    val position: StateFlow<Long> get() = ZERO_LONG
    val duration: StateFlow<Long> get() = ZERO_LONG
    val speed: StateFlow<Double> get() = ONE_DOUBLE
    val nav: StateFlow<NavState> get() = NO_NAV
    /** In-player A/V-sync nudge (ms) — VOD/mpv only; a live engine leaves it at 0. */
    val audioDelayMs: StateFlow<Int> get() = ZERO_INT
    fun setSpeed(speed: Double) {}
    fun adjustAudioDelay(deltaMs: Int) {}
    fun previous() {}
    fun next() {}
    fun seekBy(deltaMs: Long) {}

    companion object {
        private val ZERO_INT: StateFlow<Int> = MutableStateFlow(0)
        private val ZERO_LONG: StateFlow<Long> = MutableStateFlow(0L)
        private val ONE_DOUBLE: StateFlow<Double> = MutableStateFlow(1.0)
        private val NO_NAV: StateFlow<NavState> = MutableStateFlow(NavState(hasPrev = false, hasNext = false))
        private val NULL_ERROR: StateFlow<ErrorInfo?> = MutableStateFlow(null)
    }
}

/** Adapts the full mpv player to [PlaybackEngine] (delegation only — keeps [OwnTVPlayer] untouched). */
class MpvPlaybackEngine(private val p: OwnTVPlayer) : PlaybackEngine {
    override val isPlaying get() = p.isPlaying
    override val buffering get() = p.buffering
    override val error get() = p.error
    override val errorInfo get() = p.errorInfo
    override val videoRes get() = p.videoRes
    override val volume get() = p.volume
    override val zoomMode get() = p.zoomMode
    override val audioCount get() = p.audioCount
    override val subCount get() = p.subCount
    override val currentMeta get() = p.currentMeta
    override val isLiveContent get() = p.isLiveContent
    override val position get() = p.position
    override val duration get() = p.duration
    override val speed get() = p.speed
    override val nav get() = p.nav
    override val audioDelayMs get() = p.audioDelayMs
    override fun togglePlayPause() = p.togglePlayPause()
    override fun setZoomMode(mode: ZoomMode) = p.setZoomMode(mode)
    override fun adjustVolume(delta: Int) = p.adjustVolume(delta)
    override fun toggleMute() = p.toggleMute()
    override fun retry() = p.retry()
    override fun selectAudio(id: Int) = p.selectAudio(id)
    override fun selectSubtitle(id: Int) = p.selectSubtitle(id)
    override fun disableSubtitles() = p.disableSubtitles()
    override fun audioTracks() = p.audioTracks()
    override fun textTracks() = p.textTracks()
    override fun streamInfo() = p.streamInfo()
    override fun setSpeed(speed: Double) = p.setSpeed(speed)
    override fun adjustAudioDelay(deltaMs: Int) = p.adjustAudioDelay(deltaMs)
    override fun previous() = p.previous()
    override fun next() = p.next()
    override fun seekBy(deltaMs: Long) = p.seekBy(deltaMs)
}
