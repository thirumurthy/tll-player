package com.thirutricks.tllplayer.ui.glass

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.thirutricks.tllplayer.R

/**
 * Manages glass-styled dialogs with backdrop blur and consistent styling.
 * Provides focus management and button styling consistency across all dialogs.
 */
class GlassDialogManager(
    private val context: Context,
    private val styleConfig: GlassStyleConfig = GlassEffectUtils.getOptimalGlassConfig(context),
    private val animationController: MenuAnimationController = MenuAnimationController(styleConfig),
    private val feedbackManager: InteractiveFeedbackManager = InteractiveFeedbackManager(context, styleConfig, animationController)
) {
    
    companion object {
        private const val BACKDROP_BLUR_RADIUS = 25f
        private const val BACKDROP_ALPHA = 0.6f
        private const val DIALOG_ENTER_DURATION = 300L
        private const val DIALOG_EXIT_DURATION = 200L
    }
    
    /**
     * Applies glass styling to a dialog
     */
    fun applyGlassDialogStyling(dialog: Dialog, dialogView: View) {
        // Configure dialog window
        val window = dialog.window
        window?.let { w ->
            // Remove default background
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            
            // Apply backdrop blur effect
            applyBackdropBlur(w)
            
            // Configure dialog appearance
            w.attributes?.let { attrs ->
                attrs.dimAmount = BACKDROP_ALPHA
                w.attributes = attrs
            }
            
            // Enable hardware acceleration for glass effects
            w.setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }
        
        // Apply glass styling to dialog content
        applyGlassContentStyling(dialogView)
        
        // Set up dialog animations
        setupDialogAnimations(dialog, dialogView)
    }
    
    /**
     * Sets up interactive feedback for dialog buttons
     */
    fun setupDialogButtonFeedback(dialogView: View) {
        // Find all buttons in the dialog and apply glass feedback
        val buttons = findAllButtons(dialogView)
        buttons.forEach { button ->
            // Apply glass button styling
            GlassEffectUtils.applyGlassStyle(button, styleConfig, GlassType.ITEM)
            
            // Set up interactive feedback
            feedbackManager.setupInteractiveFeedback(button, FeedbackType.BUTTON)
            
            // Apply focus styling
            button.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    GlassEffectUtils.applyGlassStyle(view, styleConfig, GlassType.ITEM_FOCUSED)
                    animationController.animateFocus(view, true)
                } else {
                    GlassEffectUtils.applyGlassStyle(view, styleConfig, GlassType.ITEM)
                    animationController.animateFocus(view, false)
                }
            }
        }
    }
    
    /**
     * Sets up focus management for dialog elements
     */
    fun setupDialogFocusManagement(dialogView: View, defaultFocusId: Int? = null) {
        // Make dialog focusable
        dialogView.isFocusableInTouchMode = true
        dialogView.requestFocus()
        
        // Set default focus if specified
        defaultFocusId?.let { id ->
            val defaultFocusView = dialogView.findViewById<View>(id)
            defaultFocusView?.requestFocus()
        }
        
        // Set up focus cycling for TV navigation
        setupFocusCycling(dialogView)
    }
    
    /**
     * Creates a glass-styled dialog backdrop
     */
    private fun applyBackdropBlur(window: Window) {
        if (GlassEffectUtils.supportsAdvancedEffects(context)) {
            // Apply backdrop blur for supported devices
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes?.let { attrs ->
                attrs.blurBehindRadius = BACKDROP_BLUR_RADIUS.toInt()
                window.attributes = attrs
            }
        } else {
            // Fallback to simple dimming for unsupported devices
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }
    
    /**
     * Applies glass styling to dialog content
     */
    private fun applyGlassContentStyling(dialogView: View) {
        // Apply glass background to main container
        GlassEffectUtils.applyGlassStyle(dialogView, styleConfig, GlassType.PANEL)
        
        // Apply glass text styling to title
        val titleView = dialogView.findViewById<android.widget.TextView>(R.id.dialog_title)
        titleView?.let { title ->
            GlassEffectUtils.applyGlassTextStyle(title, TextLevel.PRIMARY, styleConfig)
        }
        
        // Apply glass styling to input fields
        val editText = dialogView.findViewById<android.widget.EditText>(R.id.edit_name)
        editText?.let { input ->
            GlassEffectUtils.applyGlassStyle(input, styleConfig, GlassType.ITEM)
            GlassEffectUtils.applyGlassTextStyle(input, TextLevel.PRIMARY, styleConfig)
            
            // Set up input field focus styling
            input.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    GlassEffectUtils.applyGlassStyle(view, styleConfig, GlassType.ITEM_FOCUSED)
                } else {
                    GlassEffectUtils.applyGlassStyle(view, styleConfig, GlassType.ITEM)
                }
            }
        }
    }
    
    /**
     * Sets up dialog entrance and exit animations
     */
    private fun setupDialogAnimations(dialog: Dialog, dialogView: View) {
        // Initial state - scale down and fade out
        dialogView.scaleX = 0.8f
        dialogView.scaleY = 0.8f
        dialogView.alpha = 0f
        
        // Animate entrance
        dialogView.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(DIALOG_ENTER_DURATION)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        
        // Set up exit animation
        dialog.setOnDismissListener {
            dialogView.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .alpha(0f)
                .setDuration(DIALOG_EXIT_DURATION)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .start()
        }
    }
    
    /**
     * Finds all buttons in a view hierarchy
     */
    private fun findAllButtons(view: View): List<View> {
        val buttons = mutableListOf<View>()
        
        if (view is android.widget.Button) {
            buttons.add(view)
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                buttons.addAll(findAllButtons(view.getChildAt(i)))
            }
        }
        
        return buttons
    }
    
    /**
     * Sets up focus cycling for TV remote navigation
     */
    private fun setupFocusCycling(dialogView: View) {
        val focusableViews = findFocusableViews(dialogView)
        
        // Set up circular focus navigation
        for (i in focusableViews.indices) {
            val currentView = focusableViews[i]
            val nextView = focusableViews[(i + 1) % focusableViews.size]
            val prevView = focusableViews[(i - 1 + focusableViews.size) % focusableViews.size]
            
            currentView.nextFocusDownId = nextView.id
            currentView.nextFocusUpId = prevView.id
            currentView.nextFocusRightId = nextView.id
            currentView.nextFocusLeftId = prevView.id
        }
    }
    
    /**
     * Finds all focusable views in a view hierarchy
     */
    private fun findFocusableViews(view: View): List<View> {
        val focusableViews = mutableListOf<View>()
        
        if (view.isFocusable) {
            focusableViews.add(view)
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                focusableViews.addAll(findFocusableViews(view.getChildAt(i)))
            }
        }
        
        return focusableViews
    }
    
    /**
     * Cleans up resources
     */
    fun cleanup() {
        animationController.cancelAllAnimations()
        feedbackManager.cleanup()
    }
}

/**
 * Extension function to easily apply glass dialog styling
 */
fun DialogFragment.applyGlassDialogStyling(dialog: Dialog, dialogView: View) {
    val context = requireContext()
    val glassDialogManager = GlassDialogManager(context)
    glassDialogManager.applyGlassDialogStyling(dialog, dialogView)
    glassDialogManager.setupDialogButtonFeedback(dialogView)
    glassDialogManager.setupDialogFocusManagement(dialogView)
}