package com.thirutricks.tllplayer.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import com.thirutricks.tllplayer.player.HeroPreviewEngine
import com.thirutricks.tllplayer.player.LivePreviewEngine
import com.thirutricks.tllplayer.player.OwnTVPlayer

/** App-wide libmpv player. */
val playerModule = module {
    // Tails own-process logcat for MediaCodec/AudioTrack errors the engines can't expose.
    single { com.thirutricks.tllplayer.player.PlayerDiagnostics() }
    // context, settings, connectivity, okHttpClient (ExoPlayer image-sub handoff), diagnostics
    single { OwnTVPlayer(androidContext(), get(), get(), get(), get()) }
    // ExoPlayer engine for the fast Live preview pane (mpv stays the full/fullscreen player).
    single { LivePreviewEngine(androidContext(), get(), get()) }
    // Muted ExoPlayer engine for the Home hero preview.
    single { HeroPreviewEngine(androidContext(), get()) }
}
