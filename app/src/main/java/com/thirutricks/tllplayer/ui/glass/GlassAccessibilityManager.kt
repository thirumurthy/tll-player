package com.thirutricks.tllplayer.ui.glass

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.thirutricks.tllplayer.R
import kotlin.math.pow

/**
 * Manages accessibility features specifically for the glass menu system.
 * Provides high contrast mode support, enhanced focus indicators, and audio feedback integration.
 */
class GlassAccessibilityManager(
    private val context: Context,
    private val styleConfig: GlassStyleConfig = GlassEffectUtils.getOptimalGlassConfig(context),
    private val animationController: MenuAnimationController = MenuAnimationController(styleConfig)
) {
    
    private val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    
    companion object {
        // WCAG AA contrast ratios for glass design
        private const val MIN_CONTRAST_RATIO_GLASS = 5.0f // Higher than normal due to transparency
        private const val MIN_CONTRAST_RATIO_HIGH_CONTRAST = 7.0f
        private const val LARGE_TEXT_SIZE_SP = 18f
        
        // High contrast detection threshold
        private const val HIGH_CONTRAST_THRESHOLD = 0.7f
        
        // Audio feedback constants
        private const val FOCUS_AUDIO_DELAY_MS = 50L
        private const val OPERATION_AUDIO_DELAY_MS = 100L
    }
    
    /**
     * Check if accessibility services are enabled
     */
    fun isAccessibilityEnabled(): Boolean {
        return accessibilityManager.isEnabled
    }
    
    /**
     * Check if high contrast mode should be enabled
     */
    fun isHighContrastMode(): Boolean {
        return try {
            val resolver = context.contentResolver
            android.provider.Settings.Secure.getFloat(
                resolver,
                "accessibility_display_inversion_enabled",
                0f
            ) > 0f || 
            android.provider.Settings.System.getFloat(
                resolver,
                "high_text_contrast_enabled",
                0f
            ) > 0f
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Apply accessibility enhancements to glass menu components
     */
    fun applyGlassAccessibilityEnhancements(rootView: ViewGroup) {
        val isHighContrast = isHighContrastMode()
        val isAccessibilityEnabled = isAccessibilityEnabled()
        
        applyAccessibilityRecursive(rootView, isHighContrast, isAccessibilityEnabled)
        
        if (isHighContrast) {
            applyHighContrastGlassMode(rootView)
        }
        
        if (isAccessibilityEnabled) {
            enhanceForScreenReaders(rootView)
            setupAudioFeedback(rootView)
        }
        
        setupEnhancedFocusIndicators(rootView, isHighContrast)
    }
    
    private fun applyAccessibilityRecursive(
        view: View, 
        isHighContrast: Boolean, 
        isAccessibilityEnabled: Boolean
    ) {
        when (view) {
            is TextView -> {
                enhanceGlassTextAccessibility(view, isHighContrast)
            }
            is ViewGroup -> {
                // Apply to all children
                for (i in 0 until view.childCount) {
                    applyAccessibilityRecursive(view.getChildAt(i), isHighContrast, isAccessibilityEnabled)
                }
                
                // Enhance container accessibility
                enhanceGlassContainerAccessibility(view)
            }
        }
        
        // Apply focus enhancements
        if (view.isFocusable) {
            enhanceGlassFocusAccessibility(view, isHighContrast, isAccessibilityEnabled)
        }
    }
    
    private fun enhanceGlassTextAccessibility(textView: TextView, isHighContrast: Boolean) {
        // Ensure proper content description
        if (textView.contentDescription.isNullOrEmpty()) {
            textView.contentDescription = textView.text
        }
        
        // Apply high contrast colors if needed
        if (isHighContrast) {
            applyHighContrastGlassColors(textView)
        } else {
            // Ensure adequate contrast for glass design
            ensureGlassTextContrast(textView)
        }
        
        // Enhance text size for accessibility
        val textSize = textView.textSize / context.resources.displayMetrics.scaledDensity
        if (isAccessibilityEnabled() && textSize < LARGE_TEXT_SIZE_SP) {
            textView.textSize = LARGE_TEXT_SIZE_SP
        }
    }
    
    private fun enhanceGlassContainerAccessibility(container: ViewGroup) {
        // Set proper accessibility role for glass menu components
        ViewCompat.setAccessibilityDelegate(container, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                
                // Add role information based on container type
                info.roleDescription = "Glass menu panel"
                info.contentDescription = "Menu panel with glass styling"
            }
        })
    }
    
    private fun enhanceGlassFocusAccessibility(
        view: View, 
        isHighContrast: Boolean, 
        isAccessibilityEnabled: Boolean
    ) {
        view.setOnFocusChangeListener { focusedView, hasFocus ->
            if (hasFocus) {
                // Announce focus change for screen readers
                if (isAccessibilityEnabled) {
                    val announcement = when (focusedView.contentDescription?.toString()) {
                        null -> "Focused on ${getGlassViewTypeDescription(focusedView)}"
                        else -> "Focused on ${focusedView.contentDescription}"
                    }
                    
                    // Delay announcement to avoid conflicts with animations
                    focusedView.postDelayed({
                        focusedView.announceForAccessibility(announcement)
                    }, FOCUS_AUDIO_DELAY_MS)
                }
                
                // Apply enhanced focus styling
                if (isHighContrast) {
                    applyHighContrastFocusIndicator(focusedView)
                } else {
                    applyGlassFocusIndicator(focusedView)
                }
                
                // Animate focus with accessibility considerations
                animationController.animateFocus(focusedView, true)
            } else {
                // Remove focus styling
                removeGlassFocusIndicator(focusedView)
                animationController.animateFocus(focusedView, false)
            }
        }
    }
    
    private fun applyHighContrastGlassMode(rootView: ViewGroup) {
        applyHighContrastRecursive(rootView)
    }
    
    private fun applyHighContrastRecursive(view: View) {
        when (view) {
            is TextView -> {
                applyHighContrastGlassColors(view)
            }
            is ViewGroup -> {
                // Apply high contrast glass background
                GlassEffectUtils.applyGlassStyle(view, styleConfig.copy(
                    backgroundAlpha = 0.95f, // More opaque for better contrast
                    borderAlpha = 1.0f
                ), GlassType.PANEL)
                
                // Apply to all children
                for (i in 0 until view.childCount) {
                    applyHighContrastRecursive(view.getChildAt(i))
                }
            }
        }
    }
    
    private fun applyHighContrastGlassColors(textView: TextView) {
        // Use high contrast colors that work with glass design
        textView.setTextColor(Color.WHITE)
        textView.setShadowLayer(2f, 0f, 0f, Color.BLACK) // Add text shadow for better contrast
        
        // Increase text size for better readability
        val currentSize = textView.textSize / context.resources.displayMetrics.scaledDensity
        textView.textSize = currentSize * 1.15f
    }
    
    private fun ensureGlassTextContrast(textView: TextView) {
        // Ensure text has adequate contrast against glass backgrounds
        val textColor = textView.currentTextColor
        val contrastRatio = calculateGlassContrastRatio(textColor)
        
        if (contrastRatio < MIN_CONTRAST_RATIO_GLASS) {
            // Apply enhanced text styling for better contrast
            textView.setShadowLayer(1.5f, 0f, 1f, Color.BLACK)
            GlassEffectUtils.applyGlassTextStyle(textView, TextLevel.PRIMARY, styleConfig)
        }
    }
    
    private fun calculateGlassContrastRatio(textColor: Int): Float {
        // Calculate contrast ratio considering glass background transparency
        val textLuminance = getRelativeLuminance(textColor)
        val glassBackgroundLuminance = getRelativeLuminance(styleConfig.textPrimaryColor) * styleConfig.backgroundAlpha
        
        val lighter = maxOf(textLuminance, glassBackgroundLuminance)
        val darker = minOf(textLuminance, glassBackgroundLuminance)
        
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    
    private fun getRelativeLuminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        
        // Apply gamma correction
        val rLinear = if (r <= 0.03928f) r / 12.92f else ((r + 0.055f) / 1.055f).pow(2.4f)
        val gLinear = if (g <= 0.03928f) g / 12.92f else ((g + 0.055f) / 1.055f).pow(2.4f)
        val bLinear = if (b <= 0.03928f) b / 12.92f else ((b + 0.055f) / 1.055f).pow(2.4f)
        
        return 0.2126f * rLinear + 0.7152f * gLinear + 0.0722f * bLinear
    }
    
    private fun setupEnhancedFocusIndicators(rootView: ViewGroup, isHighContrast: Boolean) {
        // Setup enhanced focus indicators for glass design
        setupFocusIndicatorsRecursive(rootView, isHighContrast)
    }
    
    private fun setupFocusIndicatorsRecursive(view: View, isHighContrast: Boolean) {
        if (view.isFocusable) {
            // Ensure view has proper focus indicator
            if (isHighContrast) {
                view.background = ContextCompat.getDrawable(context, R.drawable.glass_item_focused)
            }
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setupFocusIndicatorsRecursive(view.getChildAt(i), isHighContrast)
            }
        }
    }
    
    private fun applyHighContrastFocusIndicator(view: View) {
        // Apply high contrast focus indicator
        view.background = ContextCompat.getDrawable(context, R.drawable.glass_item_focused)
        view.elevation = view.elevation + 4f // Additional elevation for high contrast
    }
    
    private fun applyGlassFocusIndicator(view: View) {
        // Apply standard glass focus indicator
        GlassEffectUtils.applyGlassStyle(view, styleConfig, GlassType.ITEM_FOCUSED)
    }
    
    private fun removeGlassFocusIndicator(view: View) {
        // Remove focus indicator and return to normal state
        GlassEffectUtils.applyGlassStyle(view, styleConfig, GlassType.ITEM)
        view.elevation = view.elevation - 4f // Reset elevation if it was increased
    }
    
    private fun enhanceForScreenReaders(rootView: ViewGroup) {
        // Add navigation hints for screen readers
        ViewCompat.setAccessibilityDelegate(rootView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = "Glass menu with ${getInteractiveElementCount(rootView)} interactive elements. Use D-pad or arrow keys to navigate."
            }
        })
    }
    
    private fun setupAudioFeedback(rootView: ViewGroup) {
        // Setup audio feedback integration points
        setupAudioFeedbackRecursive(rootView)
    }
    
    private fun setupAudioFeedbackRecursive(view: View) {
        if (view.isFocusable || view.isClickable) {
            // Add audio feedback for interactive elements
            view.setOnClickListener { clickedView ->
                // Announce operation completion
                clickedView.postDelayed({
                    val operationType = getOperationType(clickedView)
                    clickedView.announceForAccessibility("$operationType completed")
                }, OPERATION_AUDIO_DELAY_MS)
            }
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                setupAudioFeedbackRecursive(view.getChildAt(i))
            }
        }
    }
    
    private fun getGlassViewTypeDescription(view: View): String {
        return when (view.id) {
            // Note: These IDs would need to be defined in the actual layout files
            else -> when (view) {
                is android.widget.Button -> "button"
                is android.widget.EditText -> "text input"
                is android.widget.TextView -> "text"
                else -> "interactive element"
            }
        }
    }
    
    private fun getOperationType(view: View): String {
        return when (view.id) {
            // Note: These IDs would need to be defined in the actual layout files
            else -> "Operation"
        }
    }
    
    private fun getInteractiveElementCount(rootView: ViewGroup): Int {
        var count = 0
        for (i in 0 until rootView.childCount) {
            val child = rootView.getChildAt(i)
            if (child.isFocusable || child.isClickable) {
                count++
            }
            if (child is ViewGroup) {
                count += getInteractiveElementCount(child)
            }
        }
        return count
    }
    
    /**
     * Create fallback styling for devices with limited graphics capabilities
     */
    fun createFallbackStyling(): GlassStyleConfig {
        return styleConfig.copy(
            backgroundAlpha = 0.9f, // More opaque
            blurRadius = 0f, // No blur
            borderAlpha = 1.0f // Solid borders
        )
    }
    
    /**
     * Apply fallback styling for limited graphics devices
     */
    fun applyFallbackStyling(rootView: ViewGroup) {
        val fallbackConfig = createFallbackStyling()
        applyFallbackStylingRecursive(rootView, fallbackConfig)
    }
    
    private fun applyFallbackStylingRecursive(view: View, fallbackConfig: GlassStyleConfig) {
        when (view) {
            is ViewGroup -> {
                // Apply fallback glass styling
                GlassEffectUtils.applyGlassStyle(view, fallbackConfig, GlassType.PANEL)
                
                // Apply to all children
                for (i in 0 until view.childCount) {
                    applyFallbackStylingRecursive(view.getChildAt(i), fallbackConfig)
                }
            }
        }
    }
    
    /**
     * Check if device has limited graphics capabilities
     */
    fun hasLimitedGraphicsCapabilities(): Boolean {
        return !GlassEffectUtils.supportsAdvancedEffects(context)
    }
    
    /**
     * Apply high contrast mode to a view group
     */
    fun applyHighContrastMode(view: View) {
        when (view) {
            is android.widget.TextView -> {
                applyHighContrastGlassColors(view)
            }
            is ViewGroup -> {
                // Apply high contrast glass background
                GlassEffectUtils.applyGlassStyle(view, styleConfig.copy(
                    backgroundAlpha = 0.95f, // More opaque for better contrast
                    borderAlpha = 1.0f
                ), GlassType.PANEL)
                
                // Apply to all children
                for (i in 0 until view.childCount) {
                    applyHighContrastMode(view.getChildAt(i))
                }
            }
        }
    }
}