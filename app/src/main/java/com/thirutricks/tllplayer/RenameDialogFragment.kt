package com.thirutricks.tllplayer

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.thirutricks.tllplayer.ui.glass.GlassDialogManager
import com.thirutricks.tllplayer.ui.glass.applyGlassDialogStyling

class RenameDialogFragment : DialogFragment() {
    private var currentName: String = ""
    private var listener: RenameListener? = null
    private var editText: EditText? = null
    private lateinit var glassDialogManager: GlassDialogManager

    interface RenameListener {
        fun onRenameConfirmed(newName: String)
    }

    companion object {
        private const val ARG_CURRENT_NAME = "current_name"
        private const val ARG_TITLE = "title"

        fun newInstance(currentName: String, title: String = "Rename"): RenameDialogFragment {
            val fragment = RenameDialogFragment()
            val args = Bundle()
            args.putString(ARG_CURRENT_NAME, currentName)
            args.putString(ARG_TITLE, title)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentName = arguments?.getString(ARG_CURRENT_NAME) ?: ""
        glassDialogManager = GlassDialogManager(requireContext())
    }

    fun setRenameListener(listener: RenameListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_rename_glass, null)

        val titleText = view.findViewById<TextView>(R.id.dialog_title)
        editText = view.findViewById<EditText>(R.id.edit_name)
        val btnConfirm = view.findViewById<android.widget.Button>(R.id.btn_confirm)
        val btnCancel = view.findViewById<android.widget.Button>(R.id.btn_cancel)

        titleText?.text = arguments?.getString(ARG_TITLE) ?: "Rename"
        editText?.setText(currentName)
        editText?.selectAll()

        // Focus and show keyboard
        editText?.requestFocus()

        btnConfirm.setOnClickListener {
            val newName = editText?.text?.toString()?.trim() ?: ""
            if (newName.isNotEmpty() && newName != currentName) {
                listener?.onRenameConfirmed(newName)
            }
            dismiss()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        // Handle Enter key
        editText?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                val newName = editText?.text?.toString()?.trim() ?: ""
                if (newName.isNotEmpty() && newName != currentName) {
                    listener?.onRenameConfirmed(newName)
                }
                dismiss()
                true
            } else {
                false
            }
        }

        builder.setView(view)
        val dialog = builder.create()
        
        // Apply glass dialog styling
        applyGlassDialogStyling(dialog, view)
        
        // Set up focus management with EditText as default focus
        glassDialogManager.setupDialogFocusManagement(view, R.id.edit_name)
        
        // Configure keyboard display
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        
        return dialog
    }

    override fun onStart() {
        super.onStart()
        editText?.requestFocus()
        editText?.selectAll()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (::glassDialogManager.isInitialized) {
            glassDialogManager.cleanup()
        }
    }
}
