package com.thirutricks.tllplayer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.thirutricks.tllplayer.databinding.InfoBinding
import com.thirutricks.tllplayer.models.TVModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class InfoFragment : Fragment() {
    private var _binding: InfoBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private val delay: Long = 6000

    // Date/Time update mechanism
    private val dateTimeHandler = Handler(Looper.getMainLooper())
    private val dateTimeRunnable = object : Runnable {
        override fun run() {
            updateDateTime()
            dateTimeHandler.postDelayed(this, 1000) // Update every second
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = InfoBinding.inflate(inflater, container, false)

        val application = requireActivity().applicationContext as MyTVApplication

        // Scale info card dimensions
        binding.infoCard.layoutParams.width = application.px2Px(binding.infoCard.layoutParams.width)
        val layoutParams = binding.infoCard.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.bottomMargin = application.px2Px(binding.infoCard.marginBottom)
        binding.infoCard.layoutParams = layoutParams

        // Scale logo section
        binding.logo.layoutParams.width = application.px2Px(binding.logo.layoutParams.width)
        var padding = application.px2Px(binding.logo.paddingTop)
        binding.logo.setPadding(padding, padding, padding, padding)

        // Scale content section
        padding = application.px2Px(binding.main.paddingTop)
        binding.main.setPadding(padding, padding, padding, padding)

        // Scale font sizes
        binding.channelNumber.textSize = application.px2PxFont(binding.channelNumber.textSize)
        binding.dateTime.textSize = application.px2PxFont(binding.dateTime.textSize)
        binding.title.textSize = application.px2PxFont(binding.title.textSize)
        binding.desc.textSize = application.px2PxFont(binding.desc.textSize)
        binding.videoBadge.textSize = application.px2PxFont(binding.videoBadge.textSize)
        binding.audioBadge.textSize = application.px2PxFont(binding.audioBadge.textSize)

        // Scale container
        binding.container.layoutParams.width = application.shouldWidthPx()
        binding.container.layoutParams.height = application.shouldHeightPx()

        _binding!!.root.visibility = View.GONE
        _binding!!.root.alpha = 0f
        return binding.root
    }

    fun show(tvViewModel: TVModel) {
        // Format and display channel number
        val channelNumber = "#${tvViewModel.tv.id + 1}"
        binding.channelNumber.text = channelNumber

        // Display channel name with fallback
        binding.title.text = tvViewModel.tv.title.ifEmpty { "Unknown Channel" }

        // Display group name with fallback
        binding.desc.text = tvViewModel.tv.group?.ifEmpty { "Uncategorized" } ?: "Uncategorized"

        // Update date/time immediately and start updater
        updateDateTime()
        startDateTimeUpdater()

        // Handle logo display with fallback to channel number
        if (tvViewModel.tv.logo.isNullOrBlank()) {
            // Create fallback bitmap with channel number
            val width = Utils.dpToPx(140)
            val height = Utils.dpToPx(140)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint().apply {
                color = ContextCompat.getColor(requireContext(), R.color.info_text_accent)
                textSize = 120f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
            val text = "${tvViewModel.tv.id + 1}"
            val x = width / 2f
            val y = height / 2f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText(text, x, y, paint)

            Glide.with(this)
                .load(BitmapDrawable(context?.resources, bitmap))
                .centerCrop()
                .into(binding.logo)
        } else {
            Glide.with(this)
                .load(tvViewModel.tv.logo)
                .centerCrop()
                .error(R.drawable.logo1) // Fallback to app logo on error
                .into(binding.logo)
        }

        // Observe video quality and update badge
        tvViewModel.videoQuality.observe(viewLifecycleOwner) { quality ->
            if (!quality.isNullOrEmpty()) {
                binding.videoBadge.text = quality
                binding.videoBadge.background = ContextCompat.getDrawable(
                    requireContext(),
                    getVideoBadgeDrawable(quality)
                )
                binding.videoBadge.visibility = View.VISIBLE
                binding.qualityBadgesRow.visibility = View.VISIBLE
            } else {
                binding.videoBadge.visibility = View.GONE
                updateQualityBadgesRowVisibility()
            }
        }

        // Observe audio quality and update badge
        tvViewModel.audioQuality.observe(viewLifecycleOwner) { quality ->
            if (!quality.isNullOrEmpty()) {
                binding.audioBadge.text = quality
                binding.audioBadge.background = ContextCompat.getDrawable(
                    requireContext(),
                    getAudioBadgeDrawable(quality)
                )
                binding.audioBadge.visibility = View.VISIBLE
                binding.qualityBadgesRow.visibility = View.VISIBLE
            } else {
                binding.audioBadge.visibility = View.GONE
                updateQualityBadgesRowVisibility()
            }
        }

        // Reset auto-hide timer
        handler.removeCallbacks(removeRunnable)
        binding.root.visibility = View.VISIBLE
        binding.root.animate().alpha(1f).setDuration(300).start()
        handler.postDelayed(removeRunnable, delay)
    }

    private fun updateDateTime() {
        if (_binding == null) return
        
        val dateFormat = SimpleDateFormat("EEE dd MMM hh:mm a", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())
        binding.dateTime.text = currentDateTime
    }

    private fun startDateTimeUpdater() {
        dateTimeHandler.removeCallbacks(dateTimeRunnable)
        dateTimeHandler.post(dateTimeRunnable)
    }

    private fun stopDateTimeUpdater() {
        dateTimeHandler.removeCallbacks(dateTimeRunnable)
    }

    private fun getVideoBadgeDrawable(quality: String): Int {
        return when {
            quality.contains("4K", ignoreCase = true) || 
            quality.contains("UHD", ignoreCase = true) || 
            quality.contains("2160", ignoreCase = true) -> R.drawable.badge_video_4k
            
            quality.contains("1080", ignoreCase = true) || 
            quality.contains("FHD", ignoreCase = true) -> R.drawable.badge_video_1080p
            
            quality.contains("720", ignoreCase = true) || 
            quality.contains("HD", ignoreCase = true) -> R.drawable.badge_video_720p
            
            quality.contains("SD", ignoreCase = true) || 
            quality.contains("480", ignoreCase = true) || 
            quality.contains("360", ignoreCase = true) -> R.drawable.badge_video_sd
            
            else -> R.drawable.badge_video_1080p // Default to 1080p badge
        }
    }

    private fun getAudioBadgeDrawable(quality: String): Int {
        return when {
            quality.contains("5.1", ignoreCase = true) || 
            quality.contains("7.1", ignoreCase = true) || 
            quality.contains("Surround", ignoreCase = true) -> R.drawable.badge_audio_surround
            
            quality.contains("Stereo", ignoreCase = true) || 
            quality.contains("2.0", ignoreCase = true) -> R.drawable.badge_audio_stereo
            
            else -> R.drawable.badge_audio_default // Default badge
        }
    }

    private fun updateQualityBadgesRowVisibility() {
        // Hide the quality badges row if both badges are hidden
        if (binding.videoBadge.visibility == View.GONE && 
            binding.audioBadge.visibility == View.GONE) {
            binding.qualityBadgesRow.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        startDateTimeUpdater()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(removeRunnable)
        stopDateTimeUpdater()
    }

    private val removeRunnable = Runnable {
        stopDateTimeUpdater()
        binding.root.animate().alpha(0f).setDuration(300).withEndAction {
            binding.root.visibility = View.GONE
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(removeRunnable)
        stopDateTimeUpdater()
        _binding = null
    }

    companion object {
        private const val TAG = "InfoFragment"
    }
}
