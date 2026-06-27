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
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.thirutricks.tllplayer.ui.glass.GlassDialogManager
import com.thirutricks.tllplayer.ui.glass.applyGlassDialogStyling

class SearchDialogFragment : DialogFragment() {
    private var listener: SearchListener? = null
    private var editText: EditText? = null
    private lateinit var glassDialogManager: GlassDialogManager
    private var initialQuery: String = ""

    interface SearchListener {
        fun onSearchQueryChanged(query: String)
        fun onSearchConfirmed(query: String)
    }

    companion object {
        private const val ARG_INITIAL_QUERY = "initial_query"

        fun newInstance(initialQuery: String = ""): SearchDialogFragment {
            val fragment = SearchDialogFragment()
            val args = Bundle()
            args.putString(ARG_INITIAL_QUERY, initialQuery)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialQuery = arguments?.getString(ARG_INITIAL_QUERY) ?: ""
        glassDialogManager = GlassDialogManager(requireContext())
    }

    fun setSearchListener(listener: SearchListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_search_glass, null)

        editText = view.findViewById<EditText>(R.id.edit_search)
        val btnConfirm = view.findViewById<android.widget.Button>(R.id.btn_confirm)
        val btnCancel = view.findViewById<android.widget.Button>(R.id.btn_cancel)

        editText?.setText(initialQuery)
        editText?.selectAll()

        // Focus and show keyboard
        editText?.requestFocus()

        // TextWatcher to support real-time filtering in parent layout
        editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                listener?.onSearchQueryChanged(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnConfirm.setOnClickListener {
            val query = editText?.text?.toString()?.trim() ?: ""
            listener?.onSearchConfirmed(query)
            dismiss()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        // Handle search actions from virtual keyboard (actionSearch or Enter key)
        editText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = editText?.text?.toString()?.trim() ?: ""
                listener?.onSearchConfirmed(query)
                dismiss()
                true
            } else {
                false
            }
        }

        editText?.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                val query = editText?.text?.toString()?.trim() ?: ""
                listener?.onSearchConfirmed(query)
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
        glassDialogManager.setupDialogFocusManagement(view, R.id.edit_search)
        
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
