package com.thirutricks.tllplayer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.fragment.app.Fragment
import com.thirutricks.tllplayer.databinding.PlayerBinding
import com.thirutricks.tllplayer.models.TVModel
import kotlinx.coroutines.*
//
//import org.videolan.libvlc.*
//import org.videolan.libvlc.util.VLCVideoLayout
import java.io.IOException

class VLCFragment : Fragment() {
    private lateinit var mainActivity: MainActivity
//    private lateinit var videoLayout: VLCVideoLayout
//    private lateinit var mediaPlayer: MediaPlayer
//    private lateinit var libVLC: LibVLC

    private val client = OkHttpClient()
    private var tvModel: TVModel? = null

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

        //videoLayout = binding.videoLayout
        binding.videoLayout.visibility = View.VISIBLE
        binding.webView.visibility = View.GONE

        val options = ArrayList<String>().apply {
            add("--aout=opensles")
            add("--audio-time-stretch")
            add("--no-drop-late-frames")
            add("--no-skip-frames")
            add("--avcodec-hw=any") // Hardware decoding
        }

//        libVLC = LibVLC(requireContext(), options)
//        mediaPlayer = MediaPlayer(libVLC)
//
//        mediaPlayer.attachViews(videoLayout, null, false, false)

        (activity as MainActivity).ready(TAG)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
//        mediaPlayer.stop()
//        mediaPlayer.detachViews()
//        libVLC.release()
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

            streamUrl?.let { playWithVLC(it) } ?: Log.e(TAG, "No stream URL found")
        }
    }

    private fun playWithVLC(url: String) {
//        val media = Media(libVLC, Uri.parse(url)).apply {
//            // Enable 5.1 audio output if available
//            setHWDecoderEnabled(true, false)
//            addOption(":network-caching=200") // You can tweak this
//        }
//
//        CoroutineScope(Dispatchers.Main).launch {
//            mediaPlayer.media = media
//            media.release()
//            mediaPlayer.play()
//        }
    }

    private fun performNetworkRequest(url: String): String? {
        // Simulated network operation
        val m3u8Link = null
        try {

// Create the request
            val request = url?.let {
                Request.Builder()
                    .url(it)
                    .build()
            }

            // Execute the request
            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body()?.string()
                if (responseBody != null) {
                    val regex = """src:\s*"(https://.*?\.m3u8.*?)"""".toRegex()
                    // Find matches
                    val match = responseBody?.let { regex.find(it) }

                    if (match != null) {
                        val m3u8Link = match.groupValues[1]
                        return m3u8Link
                        println("Extracted m3u8 Link: $m3u8Link")
                    }else{
                        // specifically for athavantv
                        val athavantvRegex = """file:"(https?://[^\"]+\.m3u8)"""".toRegex()
                        val matchResult = athavantvRegex.find(responseBody)
                        val hlsLink = matchResult?.groups?.get(1)?.value

                        if (hlsLink != null) {
                            println("Extracted HLS Link: $hlsLink")
                            return hlsLink
                        }else{
                            val ttnregex = """source:\s*['"]([^'"]+\.m3u8)['"]""".toRegex()

                            val matchResult1 = ttnregex.find(responseBody)
                            val ttnhlsLink = matchResult1?.groups?.get(1)?.value

                            if (ttnhlsLink != null) {
                                println("Extracted HLS Link: $ttnhlsLink")
                                return ttnhlsLink
                            } else {
                                // youtube
                                val youtubeRegex = """"hlsManifestUrl":"(https?:\/\/[^"]+\.m3u8)"""".toRegex()

                                // Extracting the match
                                val matchResult2 = youtubeRegex.find(responseBody)
                                val hlsLink2 = matchResult2?.groups?.get(1)?.value

                                // Output the result
                                if (hlsLink2 != null) {
                                    println("Extracted HLS Link: $hlsLink2")
                                    return hlsLink2
                                } else {
                                    println("No HLS link found.")
                                }
                            }
                        }
                    }

                }


            }
        } catch (e: IOException) {
            println("Error during the API call: ${e.message}")
        }
        return m3u8Link
    }

    companion object {
        private const val TAG = "VLCFragment"
    }
}
