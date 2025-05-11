package com.thirutricks.tllplayer

import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.thirutricks.tllplayer.models.TVList
import com.thirutricks.tllplayer.RootCheckUtil


class MainActivity : FragmentActivity() {

    private var ok = 0
    private var webFragment = WebFragment()
   // private var webFragment = VLCFragment()
    //private var webFragment = GSYVideoPlayerFragment()

    private var webFragment = WebFragment()
    private val errorFragment = ErrorFragment()
    private val loadingFragment = LoadingFragment()
    private var infoFragment = InfoFragment()
    private var channelFragment = ChannelFragment()
    private var timeFragment = TimeFragment()
    private var menuFragment = MenuFragment()
    private var settingFragment = SettingFragment()

    private val handler = Handler(Looper.myLooper()!!)
    private val delayHideMenu = 10 * 1000L
    private val delayHideSetting = 3 * 60 * 1000L

    private var doubleBackToExitPressedOnce = false

    lateinit var gestureDetector: GestureDetector

    private var server: SimpleServer? = null

    private val rootHandler = Handler(Looper.getMainLooper())
    private val checkInterval: Long = 5000 // Check every 5 seconds
    private var wasRooted = false

    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            if (isVpnActive()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "VPN detected. Exiting app.", Toast.LENGTH_LONG).show()
                    finishAffinity() // Close all activities
                }
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            if (!isVpnActive()) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "VPN disconnected.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        startRootMonitoring()

//        requestWindowFeature(FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            windowInsetsController.let { controller ->
                controller.isAppearanceLightNavigationBars = true
                controller.isAppearanceLightStatusBars = true
                controller.hide(WindowInsetsCompat.Type.statusBars())
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.navigationBarDividerColor = Color.TRANSPARENT
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.setAttributes(lp)
        }

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.main_browse_fragment, webFragment)
                .add(R.id.main_browse_fragment, errorFragment)
                .add(R.id.main_browse_fragment, loadingFragment)
                .add(R.id.main_browse_fragment, timeFragment)
                .add(R.id.main_browse_fragment, infoFragment)
                .add(R.id.main_browse_fragment, channelFragment)
                .add(R.id.main_browse_fragment, menuFragment)
                .add(R.id.main_browse_fragment, settingFragment)
                .hide(menuFragment)
                .hide(settingFragment)
                .hide(errorFragment)
                .hide(loadingFragment)
                .hide(timeFragment)
                .show(webFragment)
                .commitNow()
        }

        gestureDetector = GestureDetector(this, GestureListener(this))

        showTime()
    }

    override fun onResumeFragments() {
        super.onResumeFragments()

        Log.i(TAG, "watch")
        TVList.groupModel.change.observe(this) { _ ->
            Log.i(TAG, "groupModel changed")
            if (TVList.groupModel.tvGroupModel.value != null) {
                watch()
                Log.i(TAG, "menuFragment update")
                menuFragment.update()
            }
        }

        if (SP.channel > 0) {
            if (SP.channel < TVList.listModel.size) {
                TVList.setPosition(SP.channel - 1)
                "Play Default Channel".showToast(Toast.LENGTH_LONG)
            } else {
                SP.channel = 0
                TVList.setPosition(0)
                "The default channel is out of range in the channel list and has been automatically set to 0".showToast(Toast.LENGTH_LONG)
            }
        } else {
            if (!TVList.setPosition(SP.position)) {
                TVList.setPosition(0)
                "The last time the channel was out of range in the channel list, it was automatically set to 0".showToast(Toast.LENGTH_LONG)
            } else {
                "Play last channel".showToast(Toast.LENGTH_LONG)
            }
        }

        // TODO group position

        val port = PortUtil.findFreePort()
        if (port != -1) {
            server = SimpleServer(this, port)
        }
    }

    fun ready(tag: String) {
        Log.i(TAG, "ready $tag")
    }

    fun setServer(server: String) {
        settingFragment.setServer(server)
    }

    private fun watch() {
        TVList.listModel.forEach { tvModel ->
            tvModel.errInfo.observe(this) { _ ->
                if (tvModel.errInfo.value != null
                    && tvModel.tv.id == TVList.position.value
                ) {
                    hideFragment(loadingFragment)
                    if (tvModel.errInfo.value == "") {
                        Log.i(TAG, "${tvModel.tv.title} Playing")
                        hideErrorFragment()
                        showFragment(webFragment)
                    } else if (tvModel.errInfo.value == "web ok") {
                        Log.i(TAG, "${tvModel.tv.title} Playing")
                        hideErrorFragment()
                        showFragment(webFragment)
                    } else {
                        Log.i(TAG, "${tvModel.tv.title} ${tvModel.errInfo.value.toString()}")
                        hideFragment(webFragment)
                        hideFragment(webFragment)
                        showErrorFragment(tvModel.errInfo.value.toString())
                    }
                }
            }

            tvModel.ready.observe(this) { _ ->

                // not first time && channel is not changed
                if (tvModel.ready.value != null
                    && tvModel.tv.id == TVList.position.value
                ) {
                    Log.i(TAG, "loading ${tvModel.tv.title}")
                    hideErrorFragment()
                    showFragment(loadingFragment)
                    webFragment.play(tvModel)
                    infoFragment.show(tvModel)
                    if (SP.channelNum) {
                        channelFragment.show(tvModel)
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        hideFragment(loadingFragment)
                    }, 4000)


                }
            }

            tvModel.like.observe(this) { _ ->
                if (tvModel.like.value != null) {
                    val liked = tvModel.like.value as Boolean
                    if (liked) {
                        TVList.groupModel.getTVListModel(0)?.replaceTVModel(tvModel)
                    } else {
                        TVList.groupModel.getTVListModel(0)?.removeTVModel(tvModel.tv.id)
                    }
                    SP.setLike(tvModel.tv.id, liked)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            gestureDetector.onTouchEvent(event)
        }
        return super.onTouchEvent(event)
    }

    private inner class GestureListener(private val context: Context) :
        GestureDetector.SimpleOnGestureListener() {

        private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            showFragment(menuFragment)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            showSetting()
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if ((e1?.x ?: 0f) > windowManager.defaultDisplay.width / 3
                && (e1?.x ?: 0f) < windowManager.defaultDisplay.width * 2 / 3
            ) {
                if (velocityY > 0) {
                    if (menuFragment.isHidden && settingFragment.isHidden) {
                        prev()
                    }
                }
                if (velocityY < 0) {
                    if (menuFragment.isHidden && settingFragment.isHidden) {
                        next()
                    }
                }
            }

            return super.onFling(e1, e2, velocityX, velocityY)
        }

//        override fun onScroll(
//            e1: MotionEvent?,
//            e2: MotionEvent,
//            distanceX: Float,
//            distanceY: Float
//        ): Boolean {
//            val deltaY = e1?.y?.let { e2.y.minus(it) } ?: 0f
//            val deltaX = e1?.x?.let { e2.x.minus(it) } ?: 0f
//
//            if (abs(deltaY) > abs(deltaX)) {
//                if ((e1?.x ?: 0f) > windowManager.defaultDisplay.width * 2 / 3) {
//                    adjustVolume(deltaY)
//                }
//            }
//
//            return super.onScroll(e1, e2, distanceX, distanceY)
//        }

        private fun adjustVolume(deltaY: Float) {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val deltaVolume = deltaY / 1000 * maxVolume / windowManager.defaultDisplay.height

            var newVolume = currentVolume + deltaVolume
            if (newVolume < 0) {
                newVolume = 0F
            } else if (newVolume > maxVolume) {
                newVolume = maxVolume.toFloat()
            }

            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.toInt(), 0)

            // You can add a toast to display the current volume
            Toast.makeText(context, "Volume: $newVolume / $maxVolume", Toast.LENGTH_SHORT).show()
        }

//        private fun changeBrightness(deltaBrightness: Float) {
//            brightness += deltaBrightness
//            if (brightness < 0) {
//                brightness = 0f
//            } else if (brightness > 1) {
//                brightness = 1f
//            }
//
//            val layoutParams = windowManager.attributes
//            layoutParams.screenBrightness = brightness
//            windowManager.attributes = layoutParams
//
//            //You can add a toast to show the current brightness
//            Toast.makeText(context, "Brightness: $brightness", Toast.LENGTH_SHORT).show()
//        }
    }

    fun onPlayEnd() {
        val tvModel = TVList.getTVModel()!!
        if (SP.repeatInfo) {
            infoFragment.show(tvModel)
            if (SP.channelNum) {
                channelFragment.show(tvModel)
            }
        }
    }

    fun play(position: Int) {
        val prevGroup = TVList.getTVModel()!!.groupIndex
        if (position > -1 && position < TVList.size()) {
            TVList.setPosition(position)
            val currentGroup = TVList.getTVModel()!!.groupIndex
            if (currentGroup != prevGroup) {
                Log.i(TAG, "group change")
                menuFragment.updateList(currentGroup)
            }
        } else {
            Toast.makeText(this, "Channel does not exist", Toast.LENGTH_LONG).show()
        }
    }

    fun prev() {
        val prevGroup = TVList.getTVModel()!!.groupIndex
        var position = TVList.position.value?.dec() ?: 0
        if (position == -1) {
            position = TVList.size() - 1
        }
        TVList.setPosition(position)
        val currentGroup = TVList.getTVModel()!!.groupIndex
        if (currentGroup != prevGroup) {
            Log.i(TAG, "group change")
            menuFragment.updateList(currentGroup)
        }
    }

    fun next() {
        val prevGroup = TVList.getTVModel()!!.groupIndex
        var position = TVList.position.value?.inc() ?: 0
        if (position == TVList.size()) {
            position = 0
        }
        TVList.setPosition(position)
        val currentGroup = TVList.getTVModel()!!.groupIndex
        if (currentGroup != prevGroup) {
            Log.i(TAG, "group change")
            menuFragment.updateList(currentGroup)
        }
    }

    private fun showFragment(fragment: Fragment) {
        if (!fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(fragment)
            .commitNow()
    }

    private fun hideFragment(fragment: Fragment) {
        if (fragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .hide(fragment)
            .commitNow()
    }

    fun menuActive() {
        handler.removeCallbacks(hideMenu)
        handler.postDelayed(hideMenu, delayHideMenu)
    }

    private val hideMenu = Runnable {
        if (!isFinishing && !supportFragmentManager.isStateSaved) {
            if (!menuFragment.isHidden) {
                supportFragmentManager.beginTransaction().hide(menuFragment).commit()
            }
        }
    }

    fun settingActive() {
        handler.removeCallbacks(hideSetting)
        handler.postDelayed(hideSetting, delayHideSetting)
    }

    private val hideSetting = Runnable {
        if (!settingFragment.isHidden) {
            supportFragmentManager.beginTransaction().hide(settingFragment).commitNow()
            showTime()
        }
    }

    fun showTime() {
        if (SP.time) {
            showFragment(timeFragment)
        } else {
            hideFragment(timeFragment)
        }
    }

    private fun showChannel(channel: String) {
        if (!menuFragment.isHidden) {
            return
        }

        if (settingFragment.isVisible) {
            return
        }

//        if (SP.channelNum) {
//            channelFragment.show(channel)
//        }
        channelFragment.show(channel)
    }


    private fun channelUp() {
        if (menuFragment.isHidden && settingFragment.isHidden) {
            if (SP.channelReversal) {
                next()
                return
            }
            prev()
        }
    }

    private fun channelDown() {
        if (menuFragment.isHidden && settingFragment.isHidden) {
            if (SP.channelReversal) {
                prev()
                return
            }
            next()
        }
    }

    private fun back() {
        if (!menuFragment.isHidden) {
            hideMenuFragment()
            return
        }

        if (!settingFragment.isHidden) {
            hideSettingFragment()
            return
        }

        if (doubleBackToExitPressedOnce) {
            super.onBackPressed()
            return
        }

        doubleBackToExitPressedOnce = true
        Toast.makeText(this, "Press again to exit", Toast.LENGTH_SHORT).show()

        Handler(Looper.getMainLooper()).postDelayed({
            doubleBackToExitPressedOnce = false
        }, 2000)
    }

    private fun showSetting() {
        if (!menuFragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(settingFragment)
            .commit()
        settingActive()
    }

    fun hideMenuFragment() {
        supportFragmentManager.beginTransaction()
            .hide(menuFragment)
            .commit()
        Log.i(TAG, "SP.time ${SP.time}")
    }

    private fun hideSettingFragment() {
        supportFragmentManager.beginTransaction()
            .hide(settingFragment)
            .commit()
        showTime()
    }

    private fun showErrorFragment(msg: String) {
        errorFragment.show(msg)
        if (!errorFragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .show(errorFragment)
            .commitNow()
    }

    private fun hideErrorFragment() {
        errorFragment.show("hide")
        if (errorFragment.isHidden) {
            return
        }

        supportFragmentManager.beginTransaction()
            .hide(errorFragment)
            .commitNow()
    }

    fun onKey(keyCode: Int): Boolean {
        Log.d(TAG, "keyCode $keyCode")
        when (keyCode) {
            KeyEvent.KEYCODE_0 -> {
                showChannel("0")
                return true
            }

            KeyEvent.KEYCODE_1 -> {
                showChannel("1")
                return true
            }

            KeyEvent.KEYCODE_2 -> {
                showChannel("2")
                return true
            }

            KeyEvent.KEYCODE_3 -> {
                showChannel("3")
                return true
            }

            KeyEvent.KEYCODE_4 -> {
                showChannel("4")
                return true
            }

            KeyEvent.KEYCODE_5 -> {
                showChannel("5")
                return true
            }

            KeyEvent.KEYCODE_6 -> {
                showChannel("6")
                return true
            }

            KeyEvent.KEYCODE_7 -> {
                showChannel("7")
                return true
            }

            KeyEvent.KEYCODE_8 -> {
                showChannel("8")
                return true
            }

            KeyEvent.KEYCODE_9 -> {
                showChannel("9")
                return true
            }

            KeyEvent.KEYCODE_ESCAPE -> {
                back()
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                back()
                return true
            }

            KeyEvent.KEYCODE_BOOKMARK -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_UNKNOWN -> {
//                showSetting()
                return true
            }

            KeyEvent.KEYCODE_HELP -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_SETTINGS -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_MENU -> {
                showSetting()
                return true
            }

            KeyEvent.KEYCODE_ENTER -> {
                showFragment(menuFragment)
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                showFragment(menuFragment)
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                channelUp()
            }

            KeyEvent.KEYCODE_CHANNEL_UP -> {
                channelUp()
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                channelDown()
            }

            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                channelDown()
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (settingFragment.isHidden) {
                    showFragment(menuFragment)
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showSetting()
//                return true
            }
        }
        return false
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (onKey(keyCode)) {
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        rootHandler.removeCallbacksAndMessages(null) // Stop monitoring to prevent memory leaks
        server?.stop()
    }

    companion object {
        private const val TAG = "MainActivity"
    }

    private fun isVpnActive(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } else {
            @Suppress("DEPRECATION")
            val networks = connectivityManager.allNetworks
            for (network in networks) {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.getNetworkInfo(network)
                if (networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_VPN) {
                    return true
                }
            }
        }
        return false
    }

    private fun startRootMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                val isRooted = RootCheckUtil.isDeviceRooted()
                if (isRooted != wasRooted) {
                    wasRooted = isRooted
                    handleRootStatusChange(isRooted)
                }
                handler.postDelayed(this, checkInterval)
            }
        })
    }

    private fun handleRootStatusChange(isRooted: Boolean) {
        if (isRooted) {
            Toast.makeText(this, "Root detected. Exiting app.", Toast.LENGTH_LONG).show()
            finishAffinity() // Exit the app
        } else {
            Toast.makeText(this, "Device is no longer rooted.", Toast.LENGTH_SHORT).show()
        }
    }

}