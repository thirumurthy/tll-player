package com.thirutricks.tllplayer

import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication

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

import com.thirutricks.tllplayer.legacy.OrderPreferenceManager

class MyTVApplication : MultiDexApplication(), SingletonImageLoader.Factory {

    companion object {
        private const val TAG = "MyTVApplication"
        private lateinit var instance: MyTVApplication

        fun getInstance(): MyTVApplication {
            return instance
        }
    }

    private lateinit var displayMetrics: DisplayMetrics
    private lateinit var realDisplayMetrics: DisplayMetrics

    private var width = 0
    private var height = 0
    private var shouldWidth = 0
    private var shouldHeight = 0
    private var ratio = 1.0
    private var density = 2.0f
    private var scale = 1.0f

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Start Koin dependency injection
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@MyTVApplication)
            modules(appModule, databaseModule, dataModule, playerModule)
        }

        // Build the EPG Guide read-index once in the background
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { GlobalContext.get().get<com.thirutricks.tllplayer.core.repository.EpgRepository>().ensureEpgIndexes() }
        }

        displayMetrics = DisplayMetrics()
        realDisplayMetrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        windowManager.defaultDisplay.getRealMetrics(realDisplayMetrics)

        if (realDisplayMetrics.heightPixels > realDisplayMetrics.widthPixels) {
            width = realDisplayMetrics.heightPixels
            height = realDisplayMetrics.widthPixels
        } else {
            width = realDisplayMetrics.widthPixels
            height = realDisplayMetrics.heightPixels
        }

        density = Resources.getSystem().displayMetrics.density
        scale = displayMetrics.scaledDensity

        if ((width.toDouble() / height) < (16.0 / 9.0)) {
            ratio = width * 2 / 1920.0 / density
            shouldWidth = width
            shouldHeight = (width * 9.0 / 16.0).toInt()
        } else {
            ratio = height * 2 / 1080.0 / density
            shouldHeight = height
            shouldWidth = (height * 16.0 / 9.0).toInt()
        }

        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().apply {
                setCustomKey("release_version", BuildConfig.VERSION_NAME)
                setCustomKey("version_code", BuildConfig.VERSION_CODE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize OrderPreferenceManager
        OrderPreferenceManager.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { GlobalContext.get().get<OkHttpClient>() })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.10).build() }
            .crossfade(true)
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
            runCatching { GlobalContext.getOrNull()?.getOrNull<com.thirutricks.tllplayer.player.OwnTVPlayer>()?.onTrimMemory() }
        }
    }

    fun getDisplayMetrics(): DisplayMetrics {
        return displayMetrics
    }

    fun toast(message: CharSequence = "", duration: Int = Toast.LENGTH_SHORT) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, duration).show()
        }
    }

    fun shouldWidthPx(): Int {
        return shouldWidth
    }

    fun shouldHeightPx(): Int {
        return shouldHeight
    }

    fun dp2Px(dp: Int): Int {
        return (dp * ratio * density + 0.5f).toInt()
    }

    fun px2Px(px: Int): Int {
        return (px * ratio + 0.5f).toInt()
    }

    fun px2PxFont(px: Float): Float {
        return (px * ratio / scale).toFloat()
    }

    fun sp2Px(sp: Float): Float {
        return (sp * ratio * scale).toFloat()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
