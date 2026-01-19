package com.thirutricks.tllplayer

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.thirutricks.tllplayer.ui.glass.GlassDialogManager
import com.thirutricks.tllplayer.ui.glass.applyGlassDialogStyling

class CategoryOptionsDialogFragment : DialogFragment() {
    private var listener: CategoryOptionsListener? = null
    private var categoryName: String = ""
    private lateinit var glassDialogManager: GlassDialogManager

    interface CategoryOptionsListener {
        fun onMoveSelected()
        fun onRenameSelected()
        fun onCancelSelected()
    }

    companion object {
        private const val ARG_CATEGORY_NAME = "category_name"

        fun newInstance(categoryName: String): CategoryOptionsDialogFragment {
            val fragment = CategoryOptionsDialogFragment()
            val args = Bundle()
            args.putString(ARG_CATEGORY_NAME, categoryName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        categoryName = arguments?.getString(ARG_CATEGORY_NAME) ?: ""
        glassDialogManager = GlassDialogManager(requireContext())
    }

    fun setCategoryOptionsListener(listener: CategoryOptionsListener) {
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = android.app.AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_category_options_glass, null)

        val titleText = view.findViewById<android.widget.TextView>(R.id.dialog_title)
        val btnMove = view.findViewById<Button>(R.id.btn_move)
        val btnRename = view.findViewById<Button>(R.id.btn_rename)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        titleText?.text = "Category Options: $categoryName"

        btnMove?.setOnClickListener {
            listener?.onMoveSelected()
            dismiss()
        }

        btnRename?.setOnClickListener {
            listener?.onRenameSelected()
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (::glassDialogManager.isInitialized) {
            glassDialogManager.cleanup()
        }
    }
}