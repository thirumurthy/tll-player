package com.thirutricks.tllplayer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.thirutricks.tllplayer.databinding.LoadingBinding

class LoadingFragment : Fragment() {
    private var _binding: LoadingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LoadingBinding.inflate(inflater, container, false)

        val application = requireActivity().applicationContext as MyTVApplication

        binding.bar.layoutParams.width = application.px2Px(binding.bar.layoutParams.width)
        binding.bar.layoutParams.height = application.px2Px(binding.bar.layoutParams.height)

        (activity as MainActivity).ready(TAG)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "LoadingFragment"
    }
}