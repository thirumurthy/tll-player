package com.thirutricks.tllplayer

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.thirutricks.tllplayer.ui.glass.GlassDialogManager
import com.thirutricks.tllplayer.ui.glass.applyGlassDialogStyling

class ChannelOptionsDialogFragment : DialogFragment() {
    private var listener: ChannelOptionsListener? = null
    private var channelName: String = ""
    private lateinit var glassDialogManager: GlassDialogManager

    interface ChannelOptionsListener {
        fun onMoveSelected()
        fun onRenameSelected()
        fun onFavouriteSelected()
        fun onDeleteSelected()
        fun onCancelSelected()
    }

    companion object {
        private const val ARG_CHANNEL_NAME = "channel_name"

        fun newInstance(channelName: String): ChannelOptionsDialogFragment {
            val fragment = ChannelOptionsDialogFragment()
            val args = Bundle()
            args.putString(ARG_CHANNEL_NAME, channelName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        channelName = arguments?.getString(ARG_CHANNEL_NAME) ?: ""
        glassDialogManager = GlassDialogManager(requireContext())
    }

    fun setChannelOptionsListener(listener: ChannelOptionsListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = android.app.AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_channel_options_glass, null)

        val titleText = view.findViewById<android.widget.TextView>(R.id.dialog_title)
        val btnMove = view.findViewById<Button>(R.id.btn_move)
        val btnRename = view.findViewById<Button>(R.id.btn_rename)
        val btnFavourite = view.findViewById<Button>(R.id.btn_favourite)
        val btnDelete = view.findViewById<Button>(R.id.btn_delete)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        titleText?.text = "Channel Options: $channelName"

        btnMove?.setOnClickListener {
            listener?.onMoveSelected()
            dismiss()
        }

        btnRename?.setOnClickListener {
            listener?.onRenameSelected()
            dismiss()
        }

        btnFavourite?.setOnClickListener {
            listener?.onFavouriteSelected()
            dismiss()
        }

        btnDelete?.setOnClickListener {
            listener?.onDeleteSelected()
            dismiss()
        }

        btnCancel?.setOnClickListener {
            listener?.onCancelSelected()
            dismiss()
        }

        builder.setView(view)
        val dialog = builder.create()
        
        // Apply glass dialog styling
        applyGlassDialogStyling(dialog, view)
        
        // Set up focus management with Move button as default focus
        glassDialogManager.setupDialogFocusManagement(view, R.id.btn_move)
        
        return dialog
    }
    
    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::glassDialogManager.isInitialized) {
            glassDialogManager.cleanup()
        }
    }
}