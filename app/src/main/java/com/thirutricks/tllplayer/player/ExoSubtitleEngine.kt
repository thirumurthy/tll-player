package com.thirutricks.tllplayer.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import okhttp3.OkHttpClient
import com.thirutricks.tllplayer.core.network.HttpClient
import java.util.Locale

/**
 * ExoPlayer (Media3) used **only** for the one case mpv's direct path can't handle: a VOD with an
 * **image-based** subtitle (PGS/VOBSUB/DVB) selected. ExoPlayer keeps the video on the same zero-copy
 * decoder→SurfaceView path *and* renders the bitmap subtitle on its own UI layer ([Cue]s → SubtitleView),
 * which mpv can't do without GL-compositing the whole 4K frame (the heavy path we removed).
 *
 * It is a **handoff**, not a sidecar: mpv is stopped first, so the provider only ever sees one connection
 * (IPTV panels routinely cap VOD at one). [OwnTVPlayer] owns this and mirrors its state into the same
 * StateFlows the HUD already observes, so the player UI is unchanged. All methods run on the main thread.
 */
@OptIn(UnstableApi::class)
class ExoSubtitleEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val budget: PlayerBudget,
    private val callbacks: Callbacks,
) {
    /** Hooks back into [OwnTVPlayer]'s StateFlows (all fire on the main thread). */
    interface Callbacks {
        fun onPlayingChanged(playing: Boolean)
        fun onBuffering(buffering: Boolean)
        fun onVideoSize(width: Int, height: Int)
        fun onPositionDuration(positionMs: Long, durationMs: Long)
        fun onFirstFrame()
        fun onCues(cues: List<Cue>)
        fun onAudioTracks(tracks: List<TrackOption>)
        fun onVideoFps(fps: Float)
        fun onError(message: String)
    }

    private var player: ExoPlayer? = null
    private var surface: Surface? = null
    private var pendingSubLang: String? = null
    private var pendingSubTypeIndex: Int = -1
    private var subtitleApplied = false
    // Maps the audio-track id the HUD selects (== its ordinal in the list we publish) → the ExoPlayer
    // track group + index to override. Rebuilt whenever the track list changes.
    private var audioSelections: List<AudioSel> = emptyList()

    private data class AudioSel(val id: Int, val group: TrackGroup, val trackIndex: Int)

    val isActive: Boolean get() = player != null

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
            // Report the video frame rate so the display can switch to match it (kills 24fps-on-60Hz judder).
            player?.videoFormat?.frameRate?.let { if (it > 0f) callbacks.onVideoFps(it) }
        }

        override fun onRenderedFirstFrame() {
            callbacks.onFirstFrame()
        }

        override fun onCues(cueGroup: CueGroup) {
            callbacks.onCues(cueGroup.cues)
        }

        override fun onTracksChanged(tracks: Tracks) {
            rebuildAudioTracks(tracks)
            applyPendingSubtitle(tracks)
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.w(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
            callbacks.onError(friendlyError(error))
        }
    }

    /** Build (if needed) and start playback of [url] at [positionMs] on [surface], selecting the image
     *  subtitle identified by [subLang]/[subTypeIndex] (the track the user picked in mpv's list). */
    fun start(url: String, positionMs: Long, surface: Surface, subLang: String?, subTypeIndex: Int) {
        this.surface = surface
        pendingSubLang = subLang
        pendingSubTypeIndex = subTypeIndex
        subtitleApplied = false

        val p = player ?: build().also { player = it }
        p.setVideoSurface(surface)
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        if (positionMs > 0) p.seekTo(positionMs)
        p.playWhenReady = true
    }

    private fun build(): ExoPlayer {
        val dataSource = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(HttpClient.DEFAULT_USER_AGENT)
        // Match mpv's buffering depth so stability doesn't drop after the handoff (Dev refinement #3).
        val maxBufferMs = (budget.cacheSecs.toIntOrNull() ?: 30) * 1000
        val minBufferMs = (maxBufferMs / 2).coerceIn(15_000, maxBufferMs)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBufferMs, maxBufferMs, 2_500, 5_000)
            .build()
        val trackSelector = DefaultTrackSelector(context).apply {
            // Honor the user's preferred languages where present; subtitle selection is forced explicitly.
            parameters = buildUponParameters().build()
        }
        return ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .build()
            .apply { addListener(listener) }
    }

    /** Re-point ExoPlayer at a (re)created surface, or null to release it (surfaceDestroyed). */
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

    fun setSpeed(speed: Double) { player?.setPlaybackSpeed(speed.toFloat()) }

    /** Set output volume. mpv uses 0–150 (%) with software boost; ExoPlayer is a 0–1 linear gain, so
     *  values above 100% just clamp to full (no boost in the handoff). */
    fun setVolume(percent: Int) { player?.volume = (percent / 100f).coerceIn(0f, 1f) }

    /** Called on a ~0.5s tick by [OwnTVPlayer] while active, so the HUD scrubber advances. */
    fun emitPositionDuration() {
        val p = player ?: return
        val dur = p.duration.let { if (it == C.TIME_UNSET) 0L else it }
        callbacks.onPositionDuration(p.currentPosition.coerceAtLeast(0), dur.coerceAtLeast(0))
    }

    /** Select an audio track by the id we published in [Callbacks.onAudioTracks]. */
    fun selectAudio(id: Int) {
        val p = player ?: return
        val sel = audioSelections.firstOrNull { it.id == id } ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(sel.group, listOf(sel.trackIndex)))
            .build()
    }

    private fun rebuildAudioTracks(tracks: Tracks) {
        val out = ArrayList<TrackOption>()
        val sels = ArrayList<AudioSel>()
        var id = 0
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val label = audioLabel(format.label, format.language, id)
                out.add(TrackOption(label = label, mpvId = id, selected = group.isTrackSelected(i)))
                sels.add(AudioSel(id, group.mediaTrackGroup, i))
                id++
            }
        }
        audioSelections = sels
        callbacks.onAudioTracks(out)
    }

    /** Force-select the image subtitle track that matches the one the user picked in mpv's list:
     *  prefer the same ordinal among text tracks, then fall back to language. */
    private fun applyPendingSubtitle(tracks: Tracks) {
        if (subtitleApplied) return
        val p = player ?: return
        // Flatten text tracks in declaration order so the mpv sub ordinal lines up with ExoPlayer's.
        data class TextTrack(val group: TrackGroup, val index: Int, val lang: String?)
        val textTracks = ArrayList<TextTrack>()
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT) continue
            for (i in 0 until group.length) {
                textTracks.add(TextTrack(group.mediaTrackGroup, i, group.getTrackFormat(i).language))
            }
        }
        if (textTracks.isEmpty()) return
        val target = textTracks.getOrNull(pendingSubTypeIndex)
            ?: pendingSubLang?.let { lang -> textTracks.firstOrNull { it.lang.equals(lang, ignoreCase = true) } }
            ?: textTracks.first()
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(target.group, listOf(target.index)))
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
        subtitleApplied = true
    }

    private fun audioLabel(title: String?, lang: String?, id: Int): String {
        val l = lang?.takeIf { it.isNotBlank() && it != "und" }
            ?.let { runCatching { Locale(it).displayLanguage }.getOrNull()?.ifBlank { it } ?: it }
        return listOfNotNull(title?.takeIf { it.isNotBlank() }, l).joinToString(" · ").ifBlank { "Track ${id + 1}" }
    }

    private fun friendlyError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ->
            "Image subtitles aren't available for this file's format."
        else -> "Couldn't show image subtitles for this file."
    }

    /** Stop and free the ExoPlayer (the handoff back to mpv, or stop()). Keeps the instance? No —
     *  fully release so we never hold a second decoder/connection while mpv plays. */
    fun stop() {
        surface = null
        callbacks.onCues(emptyList())
        player?.let { p ->
            p.removeListener(listener)
            p.clearVideoSurface()
            p.release()
        }
        player = null
        audioSelections = emptyList()
    }

    fun release() = stop()

    private companion object {
        const val TAG = "ExoSubtitleEngine"
    }
}
