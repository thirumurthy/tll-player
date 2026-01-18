package com.thirutricks.tllplayer.ui

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.thirutricks.tllplayer.R
import kotlin.math.min
import kotlin.math.max

/**
 * Manages responsive layout adaptations for different screen sizes and densities
 * Optimized for Android TV and 10-foot viewing experience
 */
class ResponsiveLayoutManager(private val context: Context) {

    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val configuration: Configuration = context.resources.configuration
    
    companion object {
        private const val TV_OPTIMAL_WIDTH_DP = 1200f
        private const val TV_MAX_WIDTH_DP = 1600f
        private const val MIN_SCALE_FACTOR = 0.8f
        private const val MAX_SCALE_FACTOR = 1.4f
        private const val DENSITY_SCALE_THRESHOLD = 2.0f
    }

    /**
     * Calculate optimal scale factor based on screen characteristics
     */
    fun calculateScaleFactor(): Float {
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
        val density = displayMetrics.density
        
        // Base scale factor on screen width relative to TV optimal width
        val widthScale = screenWidthDp / TV_OPTIMAL_WIDTH_DP
        
        // Adjust for screen density
        val densityAdjustment = when {
            density >= DENSITY_SCALE_THRESHOLD -> 0.9f // Slightly smaller for high DPI
            density <= 1.0f -> 1.1f // Slightly larger for low DPI
            else -> 1.0f
        }
        
        // Consider screen orientation and size class
        val orientationAdjustment = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 1.0f else 0.9f
        val sizeClassAdjustment = when (configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) {
            Configuration.SCREENLAYOUT_SIZE_LARGE -> 1.1f
            Configuration.SCREENLAYOUT_SIZE_XLARGE -> 1.2f
            else -> 1.0f
        }
        
        // Calculate final scale factor
        val scaleFactor = widthScale * densityAdjustment * orientationAdjustment * sizeClassAdjustment
        
        // Clamp to reasonable bounds
        return scaleFactor.coerceIn(MIN_SCALE_FACTOR, MAX_SCALE_FACTOR)
    }

    /**
     * Apply responsive scaling to a view hierarchy
     */
    fun applyResponsiveScaling(rootView: ViewGroup, scaleFactor: Float = calculateScaleFactor()) {
        applyScalingRecursive(rootView, scaleFactor)
    }

    private fun applyScalingRecursive(view: View, scaleFactor: Float) {
        when (view) {
            is TextView -> {
                // Scale text size
                val currentTextSize = view.textSize / displayMetrics.scaledDensity
                val newTextSize = currentTextSize * scaleFactor
                view.textSize = newTextSize
            }
            is ViewGroup -> {
                // Apply scaling to all children
                for (i in 0 until view.childCount) {
                    applyScalingRecursive(view.getChildAt(i), scaleFactor)
                }
                
                // Scale padding if needed
                val paddingScale = if (scaleFactor > 1.0f) scaleFactor * 0.8f else scaleFactor
                val scaledPaddingLeft = (view.paddingLeft * paddingScale).toInt()
                val scaledPaddingTop = (view.paddingTop * paddingScale).toInt()
                val scaledPaddingRight = (view.paddingRight * paddingScale).toInt()
                val scaledPaddingBottom = (view.paddingBottom * paddingScale).toInt()
                
                view.setPadding(scaledPaddingLeft, scaledPaddingTop, scaledPaddingRight, scaledPaddingBottom)
            }
        }
    }

    /**
     * Optimize layout for TV viewing distance
     */
    fun optimizeForTVViewing(rootView: ViewGroup) {
        val scaleFactor = calculateScaleFactor()
        
        // Apply TV-specific optimizations
        optimizeMinimumTouchTargets(rootView)
        optimizeFocusIndicators(rootView)
        optimizeSpacing(rootView, scaleFactor)
    }

    private fun optimizeMinimumTouchTargets(view: View) {
        val minTouchTarget = context.resources.getDimensionPixelSize(R.dimen.tv_min_touch_target)
        
        when (view) {
            is ViewGroup -> {
                // Ensure minimum touch target size
                val layoutParams = view.layoutParams
                if (layoutParams != null) {
                    if (layoutParams.width > 0 && layoutParams.width < minTouchTarget) {
                        layoutParams.width = minTouchTarget
                    }
                    if (layoutParams.height > 0 && layoutParams.height < minTouchTarget) {
                        layoutParams.height = minTouchTarget
                    }
                }
                
                // Apply to all children
                for (i in 0 until view.childCount) {
                    optimizeMinimumTouchTargets(view.getChildAt(i))
                }
            }
        }
    }

    private fun optimizeFocusIndicators(view: View) {
        if (view.isFocusable) {
            // Ensure proper focus indicators for TV navigation
            ViewCompat.setAccessibilityDelegate(view, null) // Reset any existing delegate
            
            // Add enhanced focus change listener for TV
            view.setOnFocusChangeListener { focusedView, hasFocus ->
                if (hasFocus) {
                    // Enhanced focus indication for TV
                    focusedView.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(150)
                        .start()
                } else {
                    // Return to normal state
                    focusedView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(150)
                        .start()
                }
            }
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                optimizeFocusIndicators(view.getChildAt(i))
            }
        }
    }

    private fun optimizeSpacing(view: ViewGroup, scaleFactor: Float) {
        // Optimize spacing between elements for TV viewing
        val baseSpacing = context.resources.getDimensionPixelSize(R.dimen.tv_item_spacing)
        val optimizedSpacing = (baseSpacing * scaleFactor).toInt()
        
        // Apply optimized spacing to layout parameters
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val layoutParams = child.layoutParams
            
            if (layoutParams is ViewGroup.MarginLayoutParams) {
                layoutParams.topMargin = max(layoutParams.topMargin, optimizedSpacing / 2)
                layoutParams.bottomMargin = max(layoutParams.bottomMargin, optimizedSpacing / 2)
                child.layoutParams = layoutParams
            }
        }
    }

    /**
     * Check if the current device is likely a TV
     */
    fun isTVDevice(): Boolean {
        val uiMode = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    /**
     * Get recommended container width for current screen
     */
    fun getOptimalContainerWidth(): Int {
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val optimalWidthDp = min(screenWidthDp * 0.9f, TV_MAX_WIDTH_DP)
        return (optimalWidthDp * displayMetrics.density).toInt()
    }

    /**
     * Apply adaptive layout based on screen characteristics
     */
    fun applyAdaptiveLayout(rootView: ViewGroup) {
        val scaleFactor = calculateScaleFactor()
        
        // Apply different optimizations based on device type
        if (isTVDevice()) {
            optimizeForTVViewing(rootView)
        } else {
            applyResponsiveScaling(rootView, scaleFactor)
        }
        
        // Set optimal container width
        val optimalWidth = getOptimalContainerWidth()
        rootView.minimumWidth = optimalWidth
    }

    /**
     * Apply TV-specific container width constraints
     */
    fun applyTVContainerConstraints(container: View) {
        val layoutParams = container.layoutParams
        if (layoutParams != null && isTVDevice()) {
            val tvMinWidth = context.resources.getDimensionPixelSize(R.dimen.tv_settings_min_width)
            val tvMaxWidth = context.resources.getDimensionPixelSize(R.dimen.tv_settings_max_width)
            
            // Set minimum width for TV
            container.minimumWidth = tvMinWidth
            
            // Constrain maximum width if layout params support it
            if (layoutParams.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
                val screenWidth = displayMetrics.widthPixels
                val constrainedWidth = minOf(screenWidth, tvMaxWidth)
                layoutParams.width = constrainedWidth
                container.layoutParams = layoutParams
            }
        }
    }

    /**
     * Get screen size category for adaptive behavior
     */
    fun getScreenSizeCategory(): ScreenSizeCategory {
        val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        
        return when {
            isTVDevice() -> ScreenSizeCategory.TV
            screenLayout >= Configuration.SCREENLAYOUT_SIZE_XLARGE -> ScreenSizeCategory.XLARGE
            screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE -> ScreenSizeCategory.LARGE
            screenWidthDp >= 600 -> ScreenSizeCategory.TABLET
            else -> ScreenSizeCategory.PHONE
        }
    }

    /**
     * Apply density-specific optimizations
     */
    fun applyDensityOptimizations(rootView: ViewGroup) {
        val density = displayMetrics.density
        val densityCategory = when {
            density >= 4.0f -> DensityCategory.XXXHDPI
            density >= 3.0f -> DensityCategory.XXHDPI
            density >= 2.0f -> DensityCategory.XHDPI
            density >= 1.5f -> DensityCategory.HDPI
            else -> DensityCategory.MDPI
        }

        // Apply density-specific adjustments
        when (densityCategory) {
            DensityCategory.XXXHDPI, DensityCategory.XXHDPI -> {
                // Reduce padding slightly for very high density screens
                adjustPaddingRecursive(rootView, 0.9f)
            }
            DensityCategory.MDPI -> {
                // Increase padding for low density screens
                adjustPaddingRecursive(rootView, 1.1f)
            }
            else -> {
                // Standard density, no adjustment needed
            }
        }
    }

    private fun adjustPaddingRecursive(view: View, paddingScale: Float) {
        if (view is ViewGroup) {
            // Adjust padding for the container
            val scaledPaddingLeft = (view.paddingLeft * paddingScale).toInt()
            val scaledPaddingTop = (view.paddingTop * paddingScale).toInt()
            val scaledPaddingRight = (view.paddingRight * paddingScale).toInt()
            val scaledPaddingBottom = (view.paddingBottom * paddingScale).toInt()
            
            view.setPadding(scaledPaddingLeft, scaledPaddingTop, scaledPaddingRight, scaledPaddingBottom)
            
            // Apply to all children
            for (i in 0 until view.childCount) {
                adjustPaddingRecursive(view.getChildAt(i), paddingScale)
            }
        }
    }

    enum class ScreenSizeCategory {
        PHONE, TABLET, LARGE, XLARGE, TV
    }

    enum class DensityCategory {
        MDPI, HDPI, XHDPI, XXHDPI, XXXHDPI
    }
}