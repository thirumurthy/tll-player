package com.thirutricks.tllplayer.di

import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import com.thirutricks.tllplayer.core.backup.BackupManager
import com.thirutricks.tllplayer.core.backup.UserDataResolver
import com.thirutricks.tllplayer.core.customize.CustomizationStore
import com.thirutricks.tllplayer.core.download.DownloadManager
import com.thirutricks.tllplayer.core.network.ConnectivityObserver
import com.thirutricks.tllplayer.core.network.HttpClient
import com.thirutricks.tllplayer.core.parser.M3uParser
import com.thirutricks.tllplayer.core.parser.XtreamClient
import com.thirutricks.tllplayer.core.launcher.LauncherIntegrationRepository
import com.thirutricks.tllplayer.core.launcher.LauncherLaunchResolver
import com.thirutricks.tllplayer.core.launcher.LauncherRecommendationPlanner
import com.thirutricks.tllplayer.core.repository.EpgRepository
import com.thirutricks.tllplayer.core.repository.SeriesRepository
import com.thirutricks.tllplayer.core.repository.SourceRepository
import com.thirutricks.tllplayer.core.tv.TvHomeRepository
import com.thirutricks.tllplayer.core.update.UpdateManager
import com.thirutricks.tllplayer.core.sync.SyncManager
import java.util.concurrent.TimeUnit

/** Networking, parsers, sync engine, and repositories (Phase 5). */
val dataModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Force HTTP/1.1. Several IPTV panels / EPG hosts (and their CDNs) have flaky HTTP/2 stacks
            // that send RST_STREAM(PROTOCOL_ERROR) on large/slow responses — e.g. big EPG XML downloads
            // (#17) — which OkHttp surfaces as "stream was reset: PROTOCOL_ERROR". HTTP/1.1 sidesteps it
            // with no real downside for our mostly-single-stream downloads.
            .protocols(listOf(Protocol.HTTP_1_1))
            // Anti-tampering: block all requests while a debugger is attached or an HTTP proxy is set.
            // Added FIRST so it runs before any request processing — the modern stack (TLL HEAD probe,
            // ExoPlayer, EPG, Coil) gets the same protection the legacy SecureHttpClient always had.
            .addInterceptor(com.thirutricks.tllplayer.core.network.SecurityInterceptor(androidContext()))
            // Default a player-style UA for any request that didn't set one (e.g. Coil image loads),
            // since some IPTV panels reject the stock OkHttp UA. Per-source UAs still override this.
            .addInterceptor { chain ->
                val req = chain.request()
                val out = if (req.header("User-Agent").isNullOrBlank()) {
                    req.newBuilder().header("User-Agent", HttpClient.DEFAULT_USER_AGENT).build()
                } else {
                    req
                }
                chain.proceed(out)
            }
            .build()
    }
    single { HttpClient(get()) }
    single { ConnectivityObserver(androidContext()) }
    single { CustomizationStore(androidContext()) }
    single { com.thirutricks.tllplayer.core.epg.EpgSourceStore(androidContext()) }
    single { com.thirutricks.tllplayer.core.player.ForceMpvStore(androidContext()) }
    // store, sourceDao, epgRepository
    single { com.thirutricks.tllplayer.core.epg.EpgMigration(get(), get(), get()) }
    // channelDao, movieDao, seriesDao
    single { com.thirutricks.tllplayer.core.sync.ImportFinalizer(get(), get(), get()) }
    single { M3uParser() }
    single { XtreamClient(get()) }
    single { SyncManager(androidContext(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // context, channelDao, movieDao, seriesDao, profileDao, favoriteDao, historyDao, progressDao
    single { UserDataResolver(androidContext(), get(), get(), get(), get(), get(), get(), get()) }
    // sourceDao, syncManager, userDataResolver
    single { SourceRepository(get(), get(), get()) }
    // epgDao, httpClient, xtreamClient, channelDao, customize, settings, context, db
    single { EpgRepository(get(), get(), get(), get(), get(), get(), androidContext(), get()) }
    // seriesDao, sourceDao, xtreamClient, userDataResolver
    single { SeriesRepository(get(), get(), get(), get()) }
    // sourceDao, movieDao, seriesDao, progressDao
    single { LauncherRecommendationPlanner(get(), get(), get(), get()) }
    // sourceDao, channelDao, movieDao, seriesDao, progressDao
    single { LauncherLaunchResolver(get(), get(), get(), get(), get()) }
    // context, sourceDao, channelDao, movieDao, seriesDao, progressDao, tvProviderProgramDao, customize, settings
    single { TvHomeRepository(androidContext(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    // planner, resolver, tvHomeRepository
    single { LauncherIntegrationRepository(get(), get(), get()) }
    // context, downloadDao, okHttpClient, settings
    single { DownloadManager(androidContext(), get(), get(), get()) }
    // profileDao, sourceDao, settings, customizationStore, userDataResolver, epgSourceStore, tvHomeRepository
    single { BackupManager(get(), get(), get(), get(), get(), get(), get()) }
    // context, okHttpClient — in-app updates from GitHub Releases
    single { UpdateManager(androidContext(), get()) }
    // profileDao, sourceDao, settings — guarantees the default TLL profile + source always exist
    single { com.thirutricks.tllplayer.core.repository.DefaultProfileGuard(get(), get(), get()) }
}
