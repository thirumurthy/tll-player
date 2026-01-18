package com.thirutricks.tllplayer.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.animation.OvershootInterpolator
import android.widget.Switch
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R

/**
 * Modern toggle switch with smooth animations and enhanced visual feedback
 * Designed specifically for TV interfaces with glassmorphism styling
 */
class ModernToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Switch(context, attrs, defStyleAttr) {

    private val focusAnimationManager = FocusAnimationManager(context)
    private var isAnimating = false
    
    companion object {
        private const val ANIMATION_DURATION = 200L
        private const val THUMB_ANIMATION_DURATION = 250L
        private const val GLOW_ANIMATION_DURATION = 150L
    }

    init {
        setupToggleSwitch()
        setupAnimations()
    }

    private fun setupToggleSwitch() {
        // Set custom track and thumb drawables
        trackDrawable = ContextCompat.getDrawable(context, R.drawable.modern_toggle_track_animated)
        thumbDrawable = ContextCompat.getDrawable(context, R.drawable.modern_toggle_thumb)
        
        // Enable focus for TV navigation
        isFocusable = true
        isFocusableInTouchMode = true
        
        // Set minimum dimensions for TV
        minWidth = resources.getDimensionPixelSize(R.dimen.tv_min_touch_target)
        minHeight = resources.getDimensionPixelSize(R.dimen.tv_min_touch_target)
        
        // Set proper text appearance
        setTextColor(ContextCompat.getColor(context, R.color.info_text_primary))
        textSize = resources.getDimension(R.dimen.tv_text_size_medium) / resources.displayMetrics.scaledDensity
        
        // Set proper padding for better visual alignment
        val padding = resources.getDimensionPixelSize(R.dimen.toggle_padding)
        setPadding(padding, padding, padding, padding)
    }

    private fun setupAnimations() {
        // Focus change listener for enhanced visual feedback
        setOnFocusChangeListener { view, hasFocus ->
            animateFocusChange(hasFocus)
        }
        
        // State change listener for smooth toggle animation
        setOnCheckedChangeListener { _, isChecked ->
            if (!isAnimating) {
                animateStateChange(isChecked)
            }
        }
    }

    private fun animateFocusChange(hasFocus: Boolean) {
        if (hasFocus) {
            // Apply enhanced thumb drawable with glow effect
            thumbDrawable = ContextCompat.getDrawable(context, R.drawable.modern_toggle_thumb_focused)
            
            // Apply focus animation from manager
            focusAnimationManager.applySubtleFocusAnimation(this, true)
            
            // Add subtle glow animation
            animateGlow(true)
        } else {
            // Revert to normal thumb drawable
            thumbDrawable = ContextCompat.getDrawable(context, R.drawable.modern_toggle_thumb)
            
            // Remove focus animation
            focusAnimationManager.applySubtleFocusAnimation(this, false)
            
            // Remove glow effect
            animateGlow(false)
        }
    }

    private fun animateStateChange(isChecked: Boolean) {
        isAnimating = true
        
        // Create scale animation for visual feedback
        val scaleAnimator = ObjectAnimator.ofFloat(
            this, "scaleX", 1.0f, 1.05f, 1.0f
        ).apply {
            duration = THUMB_ANIMATION_DURATION
            interpolator = OvershootInterpolator(1.2f)
        }
        
        // Create alpha animation for smooth transition
        val alphaAnimator = ObjectAnimator.ofFloat(
            this, "alpha", 0.8f, 1.0f
        ).apply {
            duration = ANIMATION_DURATION
        }
        
        // Combine animations
        val animatorSet = AnimatorSet().apply {
            playTogether(scaleAnimator, alphaAnimator)
        }
        
        animatorSet.start()
        
        // Reset animation flag after completion
        postDelayed({ isAnimating = false }, THUMB_ANIMATION_DURATION)
    }

    private fun animateGlow(show: Boolean) {
        val targetAlpha = if (show) 1.0f else 0.8f
        val targetElevation = if (show) 12f else 8f
        
        animate()
            .alpha(targetAlpha)
            .setDuration(GLOW_ANIMATION_DURATION)
            .start()
            
        animate()
            .translationZ(targetElevation)
            .setDuration(GLOW_ANIMATION_DURATION)
            .start()
    }

    /**
     * Set the toggle state with animation
     */
    fun setCheckedAnimated(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
        }
    }

    /**
     * Enable or disable animations
     */
    fun setAnimationsEnabled(enabled: Boolean) {
        if (enabled) {
            setupAnimations()
        } else {
            setOnFocusChangeListener(null)
            setOnCheckedChangeListener(null)
        }
    }

    /**
     * Initialize with TvUiUtils for audio feedback
     */
    fun initializeWithAudio(tvUiUtils: TvUiUtils) {
        focusAnimationManager.initializeWithAudio(tvUiUtils)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel any ongoing animations
        animate().cancel()
        focusAnimationManager.cancelAnimations(this)
    }
}