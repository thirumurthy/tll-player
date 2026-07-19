package com.thirutricks.tllplayer

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import com.thirutricks.tllplayer.di.appModule
import com.thirutricks.tllplayer.di.databaseModule
import com.thirutricks.tllplayer.di.dataModule
import com.thirutricks.tllplayer.di.playerModule

class OwnTVApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@OwnTVApp)
            modules(appModule, databaseModule, dataModule, playerModule)
        }
        // Build the EPG Guide read-index once, in the background, for users who already have EPG synced
        // (new EPG syncs ensure it themselves). It's permanent + auto-maintained, so this is a one-time
        // build then an instant no-op on every later launch.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { GlobalContext.get().get<tv.own.owntv.core.repository.EpgRepository>().ensureEpgIndexes() }
        }
    }

    /**
     * App-wide Coil loader that fetches images through the app's OkHttpClient — so posters/logos get
     * the same player-style User-Agent and connection pooling our IPTV requests use (some panels reject
     * default UAs). Crossfade smooths the grid as images stream in. The memory cache is capped well
     * below Coil's default ~25% — on a 4K TV every megabyte belongs to the video pipeline, not to
     * channel-logo bitmaps.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { GlobalContext.get().get<OkHttpClient>() })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.10).build() }
            .crossfade(true)
            .build()

    /**
     * Memory-pressure airbag: when the OS warns we're a kill candidate, drop the image cache and
     * shrink the player's stream cache live instead of waiting for the low-memory killer.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
            runCatching { GlobalContext.getOrNull()?.getOrNull<tv.own.owntv.player.OwnTVPlayer>()?.onTrimMemory() }
        }
    }
}
