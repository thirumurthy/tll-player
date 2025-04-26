package com.thirutricks.tllplayer

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.shuyu.gsyvideoplayer.listener.VideoAllCallBack
import com.shuyu.gsyvideoplayer.video.base.GSYVideoPlayer
import com.thirutricks.tllplayer.databinding.PlayerBinding
import com.thirutricks.tllplayer.models.TVModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class GSYVideoPlayerFragment : Fragment() {
    private lateinit var mainActivity: MainActivity
    private lateinit var videoPlayer: GSYVideoPlayer
    private val client = OkHttpClient()
    private var tvModel: TVModel? = null
    private var menuFragment = MenuFragment()
    private var _binding: PlayerBinding? = null
    private val binding get() = _binding!!

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mainActivity = activity as MainActivity
        super.onActivityCreated(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PlayerBinding.inflate(inflater, container, false)

        videoPlayer = binding.videoLayout
        videoPlayer.setUp("", true, "Video Title") // default values, will update dynamically

        // Optional settings to make it full-screen, scale to fit, etc.
        // videoPlayer.setFullScreenOnly(true) // Fullscreen support
//
//        videoPlayer.setRotateViewAuto(false) // Auto rotate view
//        videoPlayer.setIsTouchWiget(true) // Enable touch gestures for seeking
//        videoPlayer.setIsTouchWigetFull(true)
//        videoPlayer.setNeedLockFull(true) // Lock fullscreen mode
        val menuView = requireActivity().findViewById<View>(R.id.menu)
        menuView?.bringToFront()
        menuView?.invalidate()

        binding.webView.visibility = View.GONE
        videoPlayer.setVideoAllCallBack(object : VideoAllCallBack {
            override fun onClickBlank(url: String?, vararg objects: Any?) {
                showChannelListFragment()
            }

            override fun onClickStartIcon(url: String?, vararg objects: Any?) {}

            override fun onClickStartError(url: String?, vararg objects: Any?) {}

            override fun onClickStop(url: String?, vararg objects: Any?) {}

            override fun onClickStopFullscreen(url: String?, vararg objects: Any?) {}

            override fun onClickResume(url: String?, vararg objects: Any?) {}

            override fun onClickResumeFullscreen(url: String?, vararg objects: Any?) {}

            override fun onClickSeekbar(url: String?, vararg objects: Any?) {}

            override fun onClickSeekbarFullscreen(url: String?, vararg objects: Any?) {}

            override fun onAutoComplete(url: String?, vararg objects: Any?) {}
            override fun onComplete(url: String?, vararg objects: Any?) {
                Log.d("GSY", "Video completed: $url")
            }

            override fun onEnterFullscreen(url: String?, vararg objects: Any?) {}

            override fun onQuitFullscreen(url: String?, vararg objects: Any?) {}

            override fun onQuitSmallWidget(url: String?, vararg objects: Any?) {}

            override fun onEnterSmallWidget(url: String?, vararg objects: Any?) {}

            override fun onTouchScreenSeekVolume(url: String?, vararg objects: Any?) {}

            override fun onTouchScreenSeekPosition(url: String?, vararg objects: Any?) {}

            override fun onTouchScreenSeekLight(url: String?, vararg objects: Any?) {}

            override fun onPlayError(url: String?, vararg objects: Any?) {}

            override fun onClickStartThumb(url: String?, vararg objects: Any?) {}

            override fun onClickBlankFullscreen(url: String?, vararg objects: Any?) {
                showChannelListFragment()
            }

            override fun onStartPrepared(url: String?, vararg objects: Any?) {
                Log.d("GSY", "Video start prepared: $url")
            }

            override fun onPrepared(url: String?, vararg objects: Any?) {}

            fun onClickStartThumbFullscreen(url: String?, vararg objects: Any?) {}
        })
        (activity as MainActivity).ready(TAG)
        return binding.root
    }

    private fun showChannelListFragment() {

//        if (!menuFragment.isHidden) {
//            return
//        }
        val menuView = requireActivity().findViewById<View>(R.id.menu)
        menuView?.bringToFront()
        menuView?.invalidate()

        requireActivity().supportFragmentManager.beginTransaction()
            .show(menuFragment)
            .commitNow()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        videoPlayer.release()
        _binding = null
    }

    fun play(tvModel: TVModel) {
        this.tvModel = tvModel
        val rawUrl = tvModel.videoUrl.value as String

        CoroutineScope(Dispatchers.IO).launch {
            val streamUrl = if (rawUrl.contains("yupptv.com") || rawUrl.contains("athavantv.com") || rawUrl.contains("ttn.tv") || rawUrl.contains("youtube.com")) {
                performNetworkRequest(rawUrl)
            } else {
                rawUrl
            }

            streamUrl?.let { playWithGSY(it) } ?: Log.e(TAG, "No stream URL found")
        }
    }

    private fun playWithGSY(url: String) {
        CoroutineScope(Dispatchers.Main).launch {
            videoPlayer.setUp(url, true, "Video Title") // Set the stream URL and title
            videoPlayer.startPlayLogic()
        }
    }

    private fun performNetworkRequest(url: String): String? {
        var m3u8Link: String? = null
        try {
            val request = Request.Builder()
                .url(url)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody != null) {
                    val regex = """src:\s*"(https://.*?\.m3u8.*?)"""".toRegex()
                    val match = regex.find(responseBody)

                    match?.let {
                        m3u8Link = it.groupValues[1]
                        Log.d(TAG, "Extracted m3u8 Link: $m3u8Link")
                    } ?: run {
                        // Handle other cases like athavantv, ttn.tv, youtube, etc.
                        // Similar regex-based extraction can be added here
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error during the API call: ${e.message}")
        }
        return m3u8Link
    }

    companion object {
        private const val TAG = "GSYVideoPlayerFragment"
    }
}
