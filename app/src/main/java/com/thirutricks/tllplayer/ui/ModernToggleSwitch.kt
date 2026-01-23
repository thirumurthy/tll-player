package com.thirutricks.tllplayer.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.animation.OvershootInterpolator
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R

/**
 * Modern toggle switch with smooth animations and enhanced visual feedback
 * Designed specifically for TV interfaces with glassmorphism styling
 * Includes defensive initialization and fallback mechanisms to prevent crashes
 */
class ModernToggleSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Switch(context, attrs, defStyleAttr) {

    private var focusAnimationManager: FocusAnimationManager? = null
    private var isAnimating = false
    private var resourceValidator: ResourceValidator? = null
    private var useFallbackMode = false
    
    companion object {
        private const val TAG = "ModernToggleSwitch"
        private const val ANIMATION_DURATION = 200L
        private const val THUMB_ANIMATION_DURATION = 250L
        private const val GLOW_ANIMATION_DURATION = 150L
    }

    init {
        // Ensure text is never null to prevent Switch.makeLayout crashes
        ensureTextNotNull()
        initializeWithValidation()
    }

    /**
     * Ensure text is never null to prevent Switch.makeLayout crashes
     * This addresses the NullPointerException in android.widget.Switch.makeLayout
     */
    private fun ensureTextNotNull() {
        try {
            // If text is null or empty, set a default empty string
            if (text.isNullOrEmpty()) {
                text = ""
                Log.d(TAG, "Set empty text to prevent null pointer exception")
            }
            
            // Ensure textOn and textOff are also not null
            if (textOn == null) {
                textOn = ""
            }
            if (textOff == null) {
                textOff = ""
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring text not null", e)
            // Set safe defaults
            try {
                text = ""
                textOn = ""
                textOff = ""
            } catch (e2: Exception) {
                Log.e(TAG, "Critical error setting text defaults", e2)
            }
        }
    }

    /**
     * Initialize the toggle switch with comprehensive validation and fallback mechanisms
     */
    private fun initializeWithValidation() {
        try {
            // Initialize resource validator
            resourceValidator = ResourceValidator(context)
            
            // Validate resources before setup
            val validationReport = resourceValidator?.generateValidationReport()
            
            when (validationReport?.recommendedAction) {
                RecoveryAction.PROCEED_NORMAL -> {
                    Log.d(TAG, "All resources available, proceeding with full initialization")
                    setupToggleSwitch()
                    setupAnimations()
                }
                RecoveryAction.USE_FALLBACK_UI -> {
                    Log.w(TAG, "Some resources missing, using fallback mode")
                    useFallbackMode = true
                    setupFallbackToggleSwitch()
                    setupBasicAnimations()
                }
                RecoveryAction.USE_EMERGENCY_UI -> {
                    Log.e(TAG, "Critical resources missing, using emergency mode")
                    useFallbackMode = true
                    setupEmergencyToggleSwitch()
                }
                else -> {
                    Log.e(TAG, "Cannot initialize toggle switch, using basic Android Switch")
                    useFallbackMode = true
                    setupBasicSwitch()
                }
            }
            
            // Always try to initialize focus animation manager
            initializeFocusManager()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during initialization, falling back to basic switch", e)
            useFallbackMode = true
            setupBasicSwitch()
        }
    }

    /**
     * Initialize focus animation manager with error handling
     */
    private fun initializeFocusManager() {
        try {
            focusAnimationManager = FocusAnimationManager(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize FocusAnimationManager, animations disabled", e)
            focusAnimationManager = null
        }
    }

    private fun setupToggleSwitch() {
        try {
            // Ensure text is safe before any layout operations
            ensureTextNotNull()
            
            // Set custom track and thumb drawables with validation
            val trackDrawable = getValidatedDrawable(R.drawable.modern_toggle_track_animated, "modern_toggle_track_animated")
            val thumbDrawable = getValidatedDrawable(R.drawable.modern_toggle_thumb, "modern_toggle_thumb")
            
            this.trackDrawable = trackDrawable
            this.thumbDrawable = thumbDrawable
            
            // Enable focus for TV navigation
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Set minimum dimensions for TV with validation
            val minTouchTarget = getValidatedDimension(R.dimen.tv_min_touch_target, "tv_min_touch_target")
            minWidth = minTouchTarget.toInt()
            minHeight = minTouchTarget.toInt()
            
            // Set proper text appearance with validation
            val textColor = getValidatedColor(R.color.info_text_primary, "info_text_primary")
            val textSizePx = getValidatedDimension(R.dimen.tv_text_size_medium, "tv_text_size_medium")
            
            setTextColor(textColor)
            textSize = textSizePx / resources.displayMetrics.scaledDensity
            
            // Set proper padding for better visual alignment
            val padding = getValidatedDimension(R.dimen.toggle_padding, "toggle_padding").toInt()
            setPadding(padding, padding, padding, padding)
            
            // Final text safety check after all setup
            ensureTextNotNull()
            
            Log.d(TAG, "Toggle switch setup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupToggleSwitch, falling back", e)
            setupFallbackToggleSwitch()
        }
    }

    /**
     * Setup fallback toggle switch when some resources are missing
     */
    private fun setupFallbackToggleSwitch() {
        try {
            // Ensure text is safe before any operations
            ensureTextNotNull()
            
            // Use fallback drawables
            trackDrawable = ContextCompat.getDrawable(context, android.R.drawable.btn_default)
            thumbDrawable = ContextCompat.getDrawable(context, android.R.drawable.btn_default_small)
            
            // Basic TV navigation setup
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Use fallback dimensions
            val fallbackSize = resourceValidator?.getFallbackDimension("tv_min_touch_target")?.toInt() ?: 48
            minWidth = fallbackSize
            minHeight = fallbackSize
            
            // Use fallback colors
            val fallbackColor = resourceValidator?.getFallbackColor("info_text_primary") ?: android.R.color.primary_text_dark
            setTextColor(ContextCompat.getColor(context, fallbackColor))
            
            // Use fallback padding
            val fallbackPadding = resourceValidator?.getFallbackDimension("toggle_padding")?.toInt() ?: 8
            setPadding(fallbackPadding, fallbackPadding, fallbackPadding, fallbackPadding)
            
            // Final text safety check
            ensureTextNotNull()
            
            Log.i(TAG, "Fallback toggle switch setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback setup, using emergency mode", e)
            setupEmergencyToggleSwitch()
        }
    }

    /**
     * Setup emergency toggle switch with minimal styling
     */
    private fun setupEmergencyToggleSwitch() {
        try {
            // Ensure text is safe before any operations
            ensureTextNotNull()
            
            // Use system default drawables
            trackDrawable = null
            thumbDrawable = null
            
            // Basic focus setup
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Minimal dimensions
            minWidth = 48
            minHeight = 48
            
            // System default colors
            setTextColor(ContextCompat.getColor(context, android.R.color.primary_text_dark))
            
            // Basic padding
            setPadding(8, 8, 8, 8)
            
            // Final text safety check
            ensureTextNotNull()
            
            Log.i(TAG, "Emergency toggle switch setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in emergency setup, using basic switch", e)
            setupBasicSwitch()
        }
    }

    /**
     * Setup basic Android Switch as last resort
     */
    private fun setupBasicSwitch() {
        try {
            // Ensure text is safe before any operations
            ensureTextNotNull()
            
            // Clear any custom drawables
            trackDrawable = null
            thumbDrawable = null
            
            // Basic focus
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Final text safety check
            ensureTextNotNull()
            
            Log.i(TAG, "Basic switch setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in basic switch setup", e)
            // Last resort - try to set empty text
            try {
                text = ""
                textOn = ""
                textOff = ""
            } catch (e2: Exception) {
                Log.e(TAG, "Cannot set text - critical failure", e2)
            }
        }
    }

    private fun setupAnimations() {
        if (useFallbackMode) {
            setupBasicAnimations()
            return
        }
        
        try {
            // Focus change listener for enhanced visual feedback
            setOnFocusChangeListener { view, hasFocus ->
                safeAnimateFocusChange(hasFocus)
            }
            
            // State change listener for smooth toggle animation
            setOnCheckedChangeListener { _, isChecked ->
                if (!isAnimating) {
                    safeAnimateStateChange(isChecked)
                }
            }
            
            Log.d(TAG, "Full animations setup completed")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up full animations, using basic animations", e)
            setupBasicAnimations()
        }
    }

    /**
     * Setup basic animations when full animations are not available
     */
    private fun setupBasicAnimations() {
        try {
            // Simple focus change listener
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    focusAnimationManager?.applySubtleFocusAnimation(this, true)
                } else {
                    focusAnimationManager?.applySubtleFocusAnimation(this, false)
                }
            }
            
            Log.d(TAG, "Basic animations setup completed")
            
        } catch (e: Exception) {
            Log.w(TAG, "Error setting up basic animations, animations disabled", e)
        }
    }

    private fun safeAnimateFocusChange(hasFocus: Boolean) {
        try {
            if (useFallbackMode) {
                // Simple focus animation for fallback mode
                focusAnimationManager?.applySubtleFocusAnimation(this, hasFocus)
                return
            }
            
            if (hasFocus) {
                // Apply enhanced thumb drawable with glow effect
                val focusedThumb = getValidatedDrawable(R.drawable.modern_toggle_thumb_focused, "modern_toggle_thumb_focused")
                thumbDrawable = focusedThumb
                
                // Apply focus animation from manager
                focusAnimationManager?.applySubtleFocusAnimation(this, true)
                
                // Add subtle glow animation
                safeAnimateGlow(true)
            } else {
                // Revert to normal thumb drawable
                val normalThumb = getValidatedDrawable(R.drawable.modern_toggle_thumb, "modern_toggle_thumb")
                thumbDrawable = normalThumb
                
                // Remove focus animation
                focusAnimationManager?.applySubtleFocusAnimation(this, false)
                
                // Remove glow effect
                safeAnimateGlow(false)
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error in focus animation, using basic focus handling", e)
            focusAnimationManager?.applySubtleFocusAnimation(this, hasFocus)
        }
    }

    private fun safeAnimateStateChange(isChecked: Boolean) {
        try {
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
            
        } catch (e: Exception) {
            Log.w(TAG, "Error in state change animation", e)
            isAnimating = false
        }
    }

    private fun safeAnimateGlow(show: Boolean) {
        try {
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
                
        } catch (e: Exception) {
            Log.w(TAG, "Error in glow animation", e)
        }
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
        try {
            if (enabled && !useFallbackMode) {
                setupAnimations()
            } else {
                setOnFocusChangeListener(null)
                setOnCheckedChangeListener(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling animations", e)
        }
    }

    /**
     * Initialize with TvUiUtils for audio feedback
     */
    fun initializeWithAudio(tvUiUtils: TvUiUtils) {
        try {
            focusAnimationManager?.initializeWithAudio(tvUiUtils)
        } catch (e: Exception) {
            Log.w(TAG, "Error initializing audio feedback", e)
        }
    }

    /**
     * Check if the toggle switch is running in fallback mode
     */
    fun isInFallbackMode(): Boolean = useFallbackMode

    /**
     * Override setText to ensure text is never null
     */
    override fun setText(text: CharSequence?, type: TextView.BufferType?) {
        try {
            val safeText = text ?: ""
            super.setText(safeText, type)
            Log.d(TAG, "setText called with: '$safeText'")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setText, using empty string", e)
            try {
                super.setText("", type)
            } catch (e2: Exception) {
                Log.e(TAG, "Critical error in setText fallback", e2)
            }
        }
    }

    /**
     * Override setTextOn to ensure textOn is never null
     */
    override fun setTextOn(textOn: CharSequence?) {
        try {
            val safeTextOn = textOn ?: ""
            super.setTextOn(safeTextOn)
            Log.d(TAG, "setTextOn called with: '$safeTextOn'")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setTextOn, using empty string", e)
            try {
                super.setTextOn("")
            } catch (e2: Exception) {
                Log.e(TAG, "Critical error in setTextOn fallback", e2)
            }
        }
    }

    /**
     * Override setTextOff to ensure textOff is never null
     */
    override fun setTextOff(textOff: CharSequence?) {
        try {
            val safeTextOff = textOff ?: ""
            super.setTextOff(safeTextOff)
            Log.d(TAG, "setTextOff called with: '$safeTextOff'")
        } catch (e: Exception) {
            Log.e(TAG, "Error in setTextOff, using empty string", e)
            try {
                super.setTextOff("")
            } catch (e2: Exception) {
                Log.e(TAG, "Critical error in setTextOff fallback", e2)
            }
        }
    }

    // Helper methods for resource validation
    private fun getValidatedDrawable(resourceId: Int, resourceName: String) = try {
        ContextCompat.getDrawable(context, resourceId) ?: run {
            Log.w(TAG, "Drawable resource $resourceName not found, using fallback")
            val fallbackId = resourceValidator?.getFallbackDrawable(resourceName) ?: android.R.drawable.btn_default
            ContextCompat.getDrawable(context, fallbackId)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error loading drawable $resourceName", e)
        val fallbackId = resourceValidator?.getFallbackDrawable(resourceName) ?: android.R.drawable.btn_default
        ContextCompat.getDrawable(context, fallbackId)
    }

    private fun getValidatedColor(resourceId: Int, resourceName: String): Int = try {
        ContextCompat.getColor(context, resourceId)
    } catch (e: Exception) {
        Log.w(TAG, "Color resource $resourceName not found, using fallback")
        val fallbackId = resourceValidator?.getFallbackColor(resourceName) ?: android.R.color.darker_gray
        ContextCompat.getColor(context, fallbackId)
    }

    private fun getValidatedDimension(resourceId: Int, resourceName: String): Float = try {
        resources.getDimension(resourceId)
    } catch (e: Exception) {
        Log.w(TAG, "Dimension resource $resourceName not found, using fallback")
        resourceValidator?.getFallbackDimension(resourceName) ?: 16f * resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            // Cancel any ongoing animations
            animate().cancel()
            focusAnimationManager?.cancelAnimations(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
    }
}