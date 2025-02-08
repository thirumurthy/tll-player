package com.thirutricks.tllplayer

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginEnd
import androidx.core.view.marginTop
import androidx.fragment.app.Fragment
import com.thirutricks.tllplayer.Utils.getDateFormat
import com.thirutricks.tllplayer.databinding.TimeBinding

class TimeFragment : Fragment() {
    private var _binding: TimeBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler()
    private val delay: Long = 1000

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TimeBinding.inflate(inflater, container, false)

        val application = requireActivity().applicationContext as MyTVApplication

        binding.time.layoutParams.width = application.px2Px(binding.time.layoutParams.width)
        binding.time.layoutParams.height = application.px2Px(binding.time.layoutParams.height)

        val layoutParams = binding.time.layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.topMargin = application.px2Px(binding.time.marginTop)
        layoutParams.marginEnd = application.px2Px(binding.time.marginEnd)
        binding.time.layoutParams = layoutParams

        binding.content.textSize = application.px2PxFont(binding.content.textSize)
        binding.channel.textSize = application.px2PxFont(binding.channel.textSize)

        binding.main.layoutParams.width = application.shouldWidthPx()
        binding.main.layoutParams.height = application.shouldHeightPx()

        return binding.root
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            handler.removeCallbacks(showRunnable)
            handler.postDelayed(showRunnable, 0)
        } else {
            handler.removeCallbacks(showRunnable)
        }
    }

    private val showRunnable: Runnable = Runnable {
        run {
            if (_binding == null) {
                return@Runnable
            }
            binding.content.text = getDateFormat("hh:mm")
            handler.postDelayed(showRunnable, delay)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "TimeFragment"
    }
}