package com.thirutricks.tllplayer.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R

/**
 * Reusable glass card component with glassmorphism styling
 * Provides consistent card-based layout with rounded corners and elevation
 */
class GlassCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val focusAnimationManager = FocusAnimationManager(context)
    
    init {
        setupCard()
        setupFocusHandling()
    }

    private fun setupCard() {
        // Set default orientation
        orientation = VERTICAL
        
        // Apply glass card background
        background = ContextCompat.getDrawable(context, R.drawable.glass_card_selector)
        
        // Set default padding for content
        val padding = resources.getDimensionPixelSize(R.dimen.glass_card_padding)
        setPadding(padding, padding, padding, padding)
        
        // Set default margins
        val margin = resources.getDimensionPixelSize(R.dimen.glass_card_margin)
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(margin, margin, margin, margin)
        }
        
        // Enable focus for TV navigation
        isFocusable = true
        isFocusableInTouchMode = true
        
        // Set elevation for depth
        elevation = resources.getDimension(R.dimen.glass_card_elevation)
    }

    private fun setupFocusHandling() {
        focusAnimationManager.setupFocusHandling(this, FocusAnimationManager.AnimationType.CARD)
    }

    /**
     * Initialize the card with TvUiUtils for audio feedback
     */
    fun initializeWithAudio(tvUiUtils: TvUiUtils) {
        focusAnimationManager.initializeWithAudio(tvUiUtils)
    }

    /**
     * Setup focus handling for all child views
     */
    fun setupChildFocusHandling() {
        focusAnimationManager.setupFocusHandlingForGroup(this)
    }

    /**
     * Set the card title with proper styling
     */
    fun setTitle(title: String) {
        // Find or create title TextView
        // This would be implemented based on specific layout needs
    }

    /**
     * Enable or disable focus animations
     */
    fun setFocusAnimationsEnabled(enabled: Boolean) {
        if (enabled) {
            setupFocusHandling()
        } else {
            onFocusChangeListener = null
        }
    }

    /**
     * Set custom glass intensity
     */
    fun setGlassIntensity(intensity: Float) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        alpha = 0.7f + (0.3f * clampedIntensity)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Ensure proper layout params when attached
        if (layoutParams !is MarginLayoutParams) {
            val margin = resources.getDimensionPixelSize(R.dimen.glass_card_margin)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(margin, margin, margin, margin)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Cancel any ongoing animations
        focusAnimationManager.cancelAnimations(this)
    }
}