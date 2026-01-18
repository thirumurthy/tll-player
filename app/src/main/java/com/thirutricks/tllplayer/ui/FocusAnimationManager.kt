package com.thirutricks.tllplayer.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.thirutricks.tllplayer.R

/**
 * Centralized manager for focus animations across all interactive elements
 * Provides consistent scale and glow animations for TV navigation
 * Integrates with TvUiUtils for audio feedback
 */
class FocusAnimationManager(private val context: Context) {

    companion object {
        private const val FOCUS_SCALE = 1.08f
        private const val NORMAL_SCALE = 1.0f
        private const val ANIMATION_DURATION = 200L
        private const val FOCUS_ELEVATION = 16f
        private const val NORMAL_ELEVATION = 8f
        
        // Different animation types for different UI elements
        private const val SUBTLE_SCALE = 1.05f
        private const val ENHANCED_SCALE = 1.12f
        private const val BUTTON_SCALE = 1.06f
        private const val CARD_SCALE = 1.03f
        
        // High contrast mode settings
        private const val HIGH_CONTRAST_SCALE = 1.15f
        private const val HIGH_CONTRAST_ELEVATION = 24f
    }

    private val interpolator = AccelerateDecelerateInterpolator()
    private val overshootInterpolator = OvershootInterpolator(1.2f)
    private val decelerateInterpolator = DecelerateInterpolator()
    
    private var tvUiUtils: TvUiUtils? = null
    private var isHighContrastMode = false

    /**
     * Initialize with TvUiUtils for audio feedback
     */
    fun initializeWithAudio(tvUiUtils: TvUiUtils) {
        this.tvUiUtils = tvUiUtils
    }

    /**
     * Apply focus animation to a view with default settings
     */
    fun applyFocusAnimation(view: View, hasFocus: Boolean) {
        applyCustomFocusAnimation(view, hasFocus, FOCUS_SCALE, ANIMATION_DURATION)
    }

    /**
     * Set high contrast mode for enhanced focus indicators
     */
    fun setHighContrastMode(enabled: Boolean) {
        isHighContrastMode = enabled
    }

    /**
     * Apply focus animation with custom scale values
     */
    fun applyCustomFocusAnimation(
        view: View, 
        hasFocus: Boolean, 
        focusScale: Float = FOCUS_SCALE,
        animationDuration: Long = ANIMATION_DURATION
    ) {
        // Adjust scale and elevation for high contrast mode
        val adjustedScale = if (isHighContrastMode && hasFocus) {
            maxOf(focusScale, HIGH_CONTRAST_SCALE)
        } else {
            if (hasFocus) focusScale else NORMAL_SCALE
        }
        
        val adjustedElevation = if (isHighContrastMode && hasFocus) {
            HIGH_CONTRAST_ELEVATION
        } else {
            if (hasFocus) FOCUS_ELEVATION else NORMAL_ELEVATION
        }

        // Cancel any existing animations
        view.animate().cancel()

        val scaleXAnimator = ObjectAnimator.ofFloat(view, "scaleX", adjustedScale)
        val scaleYAnimator = ObjectAnimator.ofFloat(view, "scaleY", adjustedScale)
        val elevationAnimator = ObjectAnimator.ofFloat(view, "elevation", adjustedElevation)

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator, elevationAnimator)
            duration = animationDuration
            interpolator = this@FocusAnimationManager.interpolator
        }

        animatorSet.start()
        
        // Play audio feedback if available
        if (hasFocus) {
            tvUiUtils?.playFocusSound()
        }
    }

    /**
     * Apply subtle focus animation for smaller elements like toggles
     */
    fun applySubtleFocusAnimation(view: View, hasFocus: Boolean) {
        applyCustomFocusAnimation(view, hasFocus, focusScale = SUBTLE_SCALE)
    }

    /**
     * Apply enhanced focus animation for important elements like cards
     */
    fun applyEnhancedFocusAnimation(view: View, hasFocus: Boolean) {
        applyCustomFocusAnimation(view, hasFocus, focusScale = ENHANCED_SCALE)
    }

    /**
     * Apply button-specific focus animation with overshoot effect
     */
    fun applyButtonFocusAnimation(view: View, hasFocus: Boolean) {
        val targetScale = if (hasFocus) BUTTON_SCALE else NORMAL_SCALE
        val targetElevation = if (hasFocus) FOCUS_ELEVATION else NORMAL_ELEVATION

        view.animate().cancel()

        val scaleXAnimator = ObjectAnimator.ofFloat(view, "scaleX", targetScale).apply {
            interpolator = overshootInterpolator
        }
        val scaleYAnimator = ObjectAnimator.ofFloat(view, "scaleY", targetScale).apply {
            interpolator = overshootInterpolator
        }
        val elevationAnimator = ObjectAnimator.ofFloat(view, "elevation", targetElevation).apply {
            interpolator = decelerateInterpolator
        }

        val animatorSet = AnimatorSet().apply {
            playTogether(scaleXAnimator, scaleYAnimator, elevationAnimator)
            duration = ANIMATION_DURATION
        }

        animatorSet.start()
        
        if (hasFocus) {
            tvUiUtils?.playFocusSound()
        }
    }

    /**
     * Apply card-specific focus animation with gentle scaling
     */
    fun applyCardFocusAnimation(view: View, hasFocus: Boolean) {
        applyCustomFocusAnimation(view, hasFocus, focusScale = CARD_SCALE, animationDuration = 250L)
    }

    /**
     * Setup focus handling for a single view
     */
    fun setupFocusHandling(view: View, animationType: AnimationType = AnimationType.DEFAULT) {
        view.setOnFocusChangeListener { v, hasFocus ->
            when (animationType) {
                AnimationType.DEFAULT -> applyFocusAnimation(v, hasFocus)
                AnimationType.SUBTLE -> applySubtleFocusAnimation(v, hasFocus)
                AnimationType.ENHANCED -> applyEnhancedFocusAnimation(v, hasFocus)
                AnimationType.BUTTON -> applyButtonFocusAnimation(v, hasFocus)
                AnimationType.CARD -> applyCardFocusAnimation(v, hasFocus)
            }
        }
        
        // Setup click sound feedback
        val originalClickListener = view.hasOnClickListeners()
        view.setOnClickListener { v ->
            tvUiUtils?.playClickSound()
            // Preserve original click behavior
            if (originalClickListener) {
                v.callOnClick()
            }
        }
    }

    /**
     * Setup focus handling for all focusable views in a ViewGroup
     */
    fun setupFocusHandlingForGroup(
        viewGroup: ViewGroup, 
        animationType: AnimationType = AnimationType.DEFAULT
    ) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            
            when {
                child.isFocusable -> {
                    setupFocusHandling(child, animationType)
                }
                child is ViewGroup -> {
                    setupFocusHandlingForGroup(child, animationType)
                }
            }
        }
    }

    /**
     * Setup focus handling with automatic animation type detection
     */
    fun setupSmartFocusHandling(view: View) {
        val animationType = when {
            view.javaClass.simpleName.contains("Button") -> AnimationType.BUTTON
            view.javaClass.simpleName.contains("Card") -> AnimationType.CARD
            view.javaClass.simpleName.contains("Toggle") || 
            view.javaClass.simpleName.contains("Switch") -> AnimationType.SUBTLE
            view.javaClass.simpleName.contains("EditText") -> AnimationType.ENHANCED
            else -> AnimationType.DEFAULT
        }
        
        setupFocusHandling(view, animationType)
    }

    /**
     * Ensure logical focus order for TV navigation
     */
    fun setupFocusOrder(views: List<View>) {
        for (i in views.indices) {
            val currentView = views[i]
            val nextView = if (i < views.size - 1) views[i + 1] else null
            
            currentView.nextFocusDownId = nextView?.id ?: View.NO_ID
            if (i > 0) {
                currentView.nextFocusUpId = views[i - 1].id
            }
        }
    }

    /**
     * Cancel any ongoing animations on a view
     */
    fun cancelAnimations(view: View) {
        view.animate().cancel()
        view.scaleX = NORMAL_SCALE
        view.scaleY = NORMAL_SCALE
        view.elevation = NORMAL_ELEVATION
    }

    /**
     * Cancel animations for all views in a ViewGroup
     */
    fun cancelAllAnimations(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            when (child) {
                is ViewGroup -> cancelAllAnimations(child)
                else -> cancelAnimations(child)
            }
        }
    }

    /**
     * Animation types for different UI elements
     */
    enum class AnimationType {
        DEFAULT,    // Standard focus animation
        SUBTLE,     // Smaller scale for toggles/switches
        ENHANCED,   // Larger scale for important elements
        BUTTON,     // Overshoot effect for buttons
        CARD        // Gentle scaling for cards
    }
}