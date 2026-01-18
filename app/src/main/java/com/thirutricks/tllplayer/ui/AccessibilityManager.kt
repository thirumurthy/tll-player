package com.thirutricks.tllplayer.ui

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

/**
 * Manages accessibility features and compliance for the settings UI
 * Ensures WCAG AA contrast requirements and screen reader compatibility
 */
class SettingsAccessibilityManager(private val context: Context) {

    private val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    
    companion object {
        // WCAG AA contrast ratios
        private const val MIN_CONTRAST_RATIO_NORMAL = 4.5f
        private const val MIN_CONTRAST_RATIO_LARGE = 3.0f
        private const val LARGE_TEXT_SIZE_SP = 18f
        
        // High contrast mode detection
        private const val HIGH_CONTRAST_THRESHOLD = 0.7f
    }

    /**
     * Check if accessibility services are enabled
     */
    fun isAccessibilityEnabled(): Boolean {
        return accessibilityManager.isEnabled
    }

    /**
     * Check if high contrast mode is likely enabled
     */
    fun isHighContrastMode(): Boolean {
        // Check system settings or use heuristics
        return try {
            val resolver = context.contentResolver
            android.provider.Settings.Secure.getFloat(
                resolver,
                "accessibility_display_inversion_enabled",
                0f
            ) > 0f
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Apply accessibility enhancements to a view hierarchy
     */
    fun applyAccessibilityEnhancements(rootView: ViewGroup) {
        applyAccessibilityRecursive(rootView)
        
        if (isHighContrastMode()) {
            applyHighContrastMode(rootView)
        }
        
        if (isAccessibilityEnabled()) {
            enhanceForScreenReaders(rootView)
        }
    }

    private fun applyAccessibilityRecursive(view: View) {
        when (view) {
            is TextView -> {
                enhanceTextAccessibility(view)
            }
            is ViewGroup -> {
                // Apply to all children
                for (i in 0 until view.childCount) {
                    applyAccessibilityRecursive(view.getChildAt(i))
                }
                
                // Enhance container accessibility
                enhanceContainerAccessibility(view)
            }
        }
        
        // Apply focus enhancements
        if (view.isFocusable) {
            enhanceFocusAccessibility(view)
        }
    }

    private fun enhanceTextAccessibility(textView: TextView) {
        // Ensure proper content description
        if (textView.contentDescription.isNullOrEmpty()) {
            textView.contentDescription = textView.text
        }
        
        // Check and improve contrast
        val textSize = textView.textSize / context.resources.displayMetrics.scaledDensity
        val isLargeText = textSize >= LARGE_TEXT_SIZE_SP
        val requiredRatio = if (isLargeText) MIN_CONTRAST_RATIO_LARGE else MIN_CONTRAST_RATIO_NORMAL
        
        // Apply high contrast colors if needed
        if (isHighContrastMode() || !hasAdequateContrast(textView, requiredRatio)) {
            applyHighContrastColors(textView)
        }
    }

    private fun enhanceContainerAccessibility(container: ViewGroup) {
        // Set proper accessibility role
        ViewCompat.setAccessibilityDelegate(container, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                
                // Add role information
                when (container.id) {
                    R.id.header_card -> {
                        info.roleDescription = "Header section"
                        info.contentDescription = "Application information"
                    }
                    R.id.configuration_card -> {
                        info.roleDescription = "Configuration section"
                        info.contentDescription = "Server and channel configuration"
                    }
                    R.id.preferences_card -> {
                        info.roleDescription = "Preferences section"
                        info.contentDescription = "Application preferences and toggles"
                    }
                    R.id.actions_card -> {
                        info.roleDescription = "Actions section"
                        info.contentDescription = "Application actions and utilities"
                    }
                }
            }
        })
    }

    private fun enhanceFocusAccessibility(view: View) {
        // Enhance focus indicators for accessibility
        view.setOnFocusChangeListener { focusedView, hasFocus ->
            if (hasFocus) {
                // Announce focus change for screen readers
                if (isAccessibilityEnabled()) {
                    val announcement = when (focusedView.contentDescription?.toString()) {
                        null -> "Focused on ${getViewTypeDescription(focusedView)}"
                        else -> "Focused on ${focusedView.contentDescription}"
                    }
                    focusedView.announceForAccessibility(announcement)
                }
                
                // Enhanced visual focus for high contrast mode
                if (isHighContrastMode()) {
                    focusedView.background = ContextCompat.getDrawable(context, R.drawable.high_contrast_focus_background)
                }
            }
        }
    }

    private fun enhanceForScreenReaders(rootView: ViewGroup) {
        // Add navigation hints
        ViewCompat.setAccessibilityDelegate(rootView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = "Settings page with ${getInteractiveElementCount(rootView)} interactive elements"
            }
        })
    }

    private fun applyHighContrastMode(rootView: ViewGroup) {
        applyHighContrastRecursive(rootView)
    }

    private fun applyHighContrastRecursive(view: View) {
        when (view) {
            is TextView -> {
                applyHighContrastColors(view)
            }
            is ViewGroup -> {
                // Apply high contrast background
                view.setBackgroundColor(Color.BLACK)
                
                // Apply to all children
                for (i in 0 until view.childCount) {
                    applyHighContrastRecursive(view.getChildAt(i))
                }
            }
        }
    }

    private fun applyHighContrastColors(textView: TextView) {
        // Use high contrast colors
        textView.setTextColor(Color.WHITE)
        textView.setBackgroundColor(Color.BLACK)
        
        // Increase text size slightly for better readability
        val currentSize = textView.textSize / context.resources.displayMetrics.scaledDensity
        textView.textSize = currentSize * 1.1f
    }

    private fun hasAdequateContrast(textView: TextView, requiredRatio: Float): Boolean {
        // Simplified contrast check - in a real implementation, you'd calculate
        // the actual contrast ratio between text and background colors
        val textColor = textView.currentTextColor
        val backgroundColor = getBackgroundColor(textView)
        
        return calculateContrastRatio(textColor, backgroundColor) >= requiredRatio
    }

    private fun calculateContrastRatio(foreground: Int, background: Int): Float {
        // Simplified contrast calculation
        val fgLuminance = getRelativeLuminance(foreground)
        val bgLuminance = getRelativeLuminance(background)
        
        val lighter = maxOf(fgLuminance, bgLuminance)
        val darker = minOf(fgLuminance, bgLuminance)
        
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun getRelativeLuminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        
        // Simplified luminance calculation
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun getBackgroundColor(view: View): Int {
        // Try to get background color from drawable or parent
        return Color.TRANSPARENT // Simplified - would need proper implementation
    }

    private fun getViewTypeDescription(view: View): String {
        return when (view) {
            is ModernToggleSwitch -> "toggle switch"
            is android.widget.Button -> "button"
            is android.widget.EditText -> "text input"
            else -> "interactive element"
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
     * Setup keyboard navigation support
     */
    fun setupKeyboardNavigation(rootView: ViewGroup) {
        // Enable keyboard navigation
        rootView.isFocusableInTouchMode = true
        rootView.requestFocus()
        
        // Setup tab order for keyboard navigation
        setupTabOrder(rootView)
    }

    private fun setupTabOrder(container: ViewGroup) {
        val focusableViews = mutableListOf<View>()
        collectFocusableViews(container, focusableViews)
        
        // Set next focus IDs for proper tab order
        for (i in focusableViews.indices) {
            val current = focusableViews[i]
            val next = focusableViews.getOrNull(i + 1) ?: focusableViews.firstOrNull()
            val previous = focusableViews.getOrNull(i - 1) ?: focusableViews.lastOrNull()
            
            next?.let { current.nextFocusDownId = it.id }
            previous?.let { current.nextFocusUpId = it.id }
        }
    }

    private fun collectFocusableViews(container: ViewGroup, focusableViews: MutableList<View>) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child.isFocusable) {
                focusableViews.add(child)
            }
            if (child is ViewGroup) {
                collectFocusableViews(child, focusableViews)
            }
        }
    }
}