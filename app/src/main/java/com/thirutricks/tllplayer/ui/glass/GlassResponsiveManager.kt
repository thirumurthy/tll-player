package com.thirutricks.tllplayer.ui.glass

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
 * Manages responsive scaling and layout adaptations specifically for the glass menu system.
 * Optimized for various TV resolutions and screen sizes while maintaining glass design integrity.
 */
class GlassResponsiveManager(
    private val context: Context,
    private val styleConfig: GlassStyleConfig = GlassEffectUtils.getOptimalGlassConfig(context)
) {
    
    private val displayMetrics: DisplayMetrics = context.resources.displayMetrics
    private val configuration: Configuration = context.resources.configuration
    
    companion object {
        // TV resolution breakpoints
        private const val HD_WIDTH_DP = 1280f
        private const val FHD_WIDTH_DP = 1920f
        private const val UHD_WIDTH_DP = 3840f
        
        // Glass menu optimal dimensions
        private const val GLASS_MENU_OPTIMAL_WIDTH_DP = 1200f
        private const val GLASS_MENU_MAX_WIDTH_DP = 1600f
        private const val GLASS_PANEL_MIN_WIDTH_DP = 200f
        private const val GLASS_PANEL_MAX_WIDTH_DP = 500f
        
        // Scale factor bounds for glass design
        private const val MIN_GLASS_SCALE_FACTOR = 0.7f
        private const val MAX_GLASS_SCALE_FACTOR = 1.6f
        
        // Density thresholds
        private const val HIGH_DENSITY_THRESHOLD = 2.5f
        private const val ULTRA_HIGH_DENSITY_THRESHOLD = 4.0f
    }
    
    /**
     * Calculate optimal scale factor for glass menu components
     */
    fun calculateGlassScaleFactor(): Float {
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val screenHeightDp = displayMetrics.heightPixels / displayMetrics.density
        val density = displayMetrics.density
        
        // Base scale factor on screen resolution
        val resolutionScale = when {
            screenWidthDp >= UHD_WIDTH_DP -> 1.4f // 4K displays
            screenWidthDp >= FHD_WIDTH_DP -> 1.2f // Full HD displays
            screenWidthDp >= HD_WIDTH_DP -> 1.0f // HD displays
            else -> 0.8f // Lower resolution displays
        }
        
        // Adjust for screen density
        val densityAdjustment = when {
            density >= ULTRA_HIGH_DENSITY_THRESHOLD -> 0.85f // Very high DPI
            density >= HIGH_DENSITY_THRESHOLD -> 0.9f // High DPI
            density <= 1.0f -> 1.15f // Low DPI
            else -> 1.0f // Standard DPI
        }
        
        // Consider viewing distance (TV vs tablet/phone)
        val viewingDistanceAdjustment = if (isTVDevice()) 1.1f else 0.95f
        
        // Calculate final scale factor
        val scaleFactor = resolutionScale * densityAdjustment * viewingDistanceAdjustment
        
        // Clamp to glass design bounds
        return scaleFactor.coerceIn(MIN_GLASS_SCALE_FACTOR, MAX_GLASS_SCALE_FACTOR)
    }
    
    /**
     * Apply responsive scaling to glass menu components
     */
    fun applyGlassResponsiveScaling(rootView: ViewGroup, scaleFactor: Float = calculateGlassScaleFactor()) {
        applyGlassScalingRecursive(rootView, scaleFactor)
        adjustGlassPanelDimensions(rootView, scaleFactor)
    }
    
    private fun applyGlassScalingRecursive(view: View, scaleFactor: Float) {
        when (view) {
            is TextView -> {
                // Scale text size while maintaining glass text hierarchy
                scaleGlassText(view, scaleFactor)
            }
            is ViewGroup -> {
                // Apply scaling to all children first
                for (i in 0 until view.childCount) {
                    applyGlassScalingRecursive(view.getChildAt(i), scaleFactor)
                }
                
                // Scale glass-specific properties
                scaleGlassContainer(view, scaleFactor)
            }
        }
    }
    
    private fun scaleGlassText(textView: TextView, scaleFactor: Float) {
        // Scale text size while preserving glass text hierarchy
        val currentTextSize = textView.textSize / displayMetrics.scaledDensity
        val newTextSize = currentTextSize * scaleFactor
        
        // Ensure minimum readable size for TV
        val minTextSize = if (isTVDevice()) 14f else 12f
        val maxTextSize = if (isTVDevice()) 24f else 20f
        
        textView.textSize = newTextSize.coerceIn(minTextSize, maxTextSize)
        
        // Adjust text shadow for scaled text
        val shadowRadius = (1.5f * scaleFactor).coerceIn(0.5f, 3f)
        textView.setShadowLayer(shadowRadius, 0f, 1f, android.graphics.Color.BLACK)
    }
    
    private fun scaleGlassContainer(container: ViewGroup, scaleFactor: Float) {
        // Scale padding while maintaining glass design proportions
        val paddingScale = if (scaleFactor > 1.0f) scaleFactor * 0.85f else scaleFactor
        val scaledPaddingLeft = (container.paddingLeft * paddingScale).toInt()
        val scaledPaddingTop = (container.paddingTop * paddingScale).toInt()
        val scaledPaddingRight = (container.paddingRight * paddingScale).toInt()
        val scaledPaddingBottom = (container.paddingBottom * paddingScale).toInt()
        
        container.setPadding(scaledPaddingLeft, scaledPaddingTop, scaledPaddingRight, scaledPaddingBottom)
        
        // Scale glass-specific visual properties
        scaleGlassVisualProperties(container, scaleFactor)
    }
    
    private fun scaleGlassVisualProperties(view: View, scaleFactor: Float) {
        // Scale corner radius
        val scaledCornerRadius = (styleConfig.cornerRadiusMedium * scaleFactor).coerceIn(4f, 24f)
        
        // Scale blur properties
        val scaledBlurRadius = (styleConfig.blurRadius * scaleFactor).coerceIn(2f, 16f)
        
        // Create scaled glass config
        val scaledConfig = styleConfig.copy(
            cornerRadiusMedium = scaledCornerRadius,
            blurRadius = scaledBlurRadius
        )
        
        // Apply scaled glass styling
        val glassType = when (view.id) {
            // Note: These IDs would need to be defined in the actual layout files
            else -> GlassType.PANEL
        }
        
        GlassEffectUtils.applyGlassStyle(view, scaledConfig, glassType)
    }
    
    private fun adjustGlassPanelDimensions(rootView: ViewGroup, scaleFactor: Float) {
        // Note: Panel dimension adjustment would need actual panel views with proper IDs
        // For now, this is a placeholder implementation
        
        // The actual implementation would find category and channel panels
        // and adjust their dimensions based on the scale factor
        val minWidth = (200 * context.resources.displayMetrics.density).toInt()
        val maxWidth = (500 * context.resources.displayMetrics.density).toInt()
        
        // This would be applied to actual panel views when they exist
    }
    
    /**
     * Optimize glass menu for different TV resolutions
     */
    fun optimizeForTVResolution(rootView: ViewGroup) {
        val resolution = getTVResolution()
        val scaleFactor = calculateGlassScaleFactor()
        
        when (resolution) {
            TVResolution.UHD_4K -> {
                optimizeFor4K(rootView, scaleFactor)
            }
            TVResolution.FHD_1080P -> {
                optimizeForFullHD(rootView, scaleFactor)
            }
            TVResolution.HD_720P -> {
                optimizeForHD(rootView, scaleFactor)
            }
            TVResolution.UNKNOWN -> {
                // Apply conservative optimizations
                applyGlassResponsiveScaling(rootView, scaleFactor * 0.9f)
            }
        }
        
        // Apply TV-specific glass enhancements
        applyTVGlassEnhancements(rootView)
    }
    
    private fun optimizeFor4K(rootView: ViewGroup, scaleFactor: Float) {
        // 4K optimization: larger elements, enhanced glass effects
        applyGlassResponsiveScaling(rootView, scaleFactor * 1.2f)
        
        // Enhanced glass effects for 4K
        val enhancedConfig = styleConfig.copy(
            blurRadius = styleConfig.blurRadius * 1.3f,
            backgroundAlpha = styleConfig.backgroundAlpha * 0.95f
        )
        
        applyEnhancedGlassEffects(rootView, enhancedConfig)
    }
    
    private fun optimizeForFullHD(rootView: ViewGroup, scaleFactor: Float) {
        // Full HD optimization: standard scaling with optimized glass effects
        applyGlassResponsiveScaling(rootView, scaleFactor)
        
        // Standard glass effects
        applyStandardGlassEffects(rootView)
    }
    
    private fun optimizeForHD(rootView: ViewGroup, scaleFactor: Float) {
        // HD optimization: reduced effects for performance
        applyGlassResponsiveScaling(rootView, scaleFactor * 0.9f)
        
        // Simplified glass effects for HD
        val simplifiedConfig = styleConfig.copy(
            blurRadius = styleConfig.blurRadius * 0.7f,
            focusAnimationDuration = (styleConfig.focusAnimationDuration * 0.8f).toLong()
        )
        
        applySimplifiedGlassEffects(rootView, simplifiedConfig)
    }
    
    private fun applyEnhancedGlassEffects(rootView: ViewGroup, enhancedConfig: GlassStyleConfig) {
        applyGlassEffectsRecursive(rootView, enhancedConfig)
    }
    
    private fun applyStandardGlassEffects(rootView: ViewGroup) {
        applyGlassEffectsRecursive(rootView, styleConfig)
    }
    
    private fun applySimplifiedGlassEffects(rootView: ViewGroup, simplifiedConfig: GlassStyleConfig) {
        applyGlassEffectsRecursive(rootView, simplifiedConfig)
    }
    
    private fun applyGlassEffectsRecursive(view: View, config: GlassStyleConfig) {
        when (view) {
            is ViewGroup -> {
                // Apply glass effects to container
                val glassType = when (view.id) {
                    // Note: These IDs would need to be defined in the actual layout files
                    else -> GlassType.PANEL
                }
                
                GlassEffectUtils.applyGlassStyle(view, config, glassType)
                
                // Apply to all children
                for (i in 0 until view.childCount) {
                    applyGlassEffectsRecursive(view.getChildAt(i), config)
                }
            }
        }
    }
    
    private fun applyTVGlassEnhancements(rootView: ViewGroup) {
        // TV-specific glass enhancements
        enhanceTVFocusIndicators(rootView)
        optimizeTVTouchTargets(rootView)
        setupTVNavigationHints(rootView)
    }
    
    private fun enhanceTVFocusIndicators(view: View) {
        // Accessibility and focus management are now handled by adapters and MenuAnimationController
        // for better performance and consistency.
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                enhanceTVFocusIndicators(view.getChildAt(i))
            }
        }
    }
    
    private fun optimizeTVTouchTargets(view: View) {
        val minTouchTarget = (48 * displayMetrics.density).toInt() // 48dp minimum
        
        when (view) {
            is ViewGroup -> {
                // Ensure minimum touch target size for TV
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
                    optimizeTVTouchTargets(view.getChildAt(i))
                }
            }
        }
    }
    
    private fun setupTVNavigationHints(rootView: ViewGroup) {
        // Setup navigation hints for TV remote control
        ViewCompat.setAccessibilityDelegate(rootView, object : androidx.core.view.AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.contentDescription = "Glass menu optimized for ${getTVResolution().name} resolution. Use D-pad to navigate."
            }
        })
    }
    
    /**
     * Get optimal container width for current screen
     */
    fun getOptimalGlassMenuWidth(): Int {
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        val optimalWidthDp = min(screenWidthDp * 0.85f, GLASS_MENU_MAX_WIDTH_DP)
        return (optimalWidthDp * displayMetrics.density).toInt()
    }
    
    /**
     * Check if the current device is likely a TV
     */
    fun isTVDevice(): Boolean {
        val uiMode = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }
    
    /**
     * Get TV resolution category
     */
    fun getTVResolution(): TVResolution {
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        
        return when {
            screenWidthDp >= UHD_WIDTH_DP -> TVResolution.UHD_4K
            screenWidthDp >= FHD_WIDTH_DP -> TVResolution.FHD_1080P
            screenWidthDp >= HD_WIDTH_DP -> TVResolution.HD_720P
            else -> TVResolution.UNKNOWN
        }
    }
    
    /**
     * Get screen size category for glass menu adaptation
     */
    fun getGlassScreenSizeCategory(): GlassScreenSizeCategory {
        val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        
        return when {
            isTVDevice() -> when (getTVResolution()) {
                TVResolution.UHD_4K -> GlassScreenSizeCategory.TV_4K
                TVResolution.FHD_1080P -> GlassScreenSizeCategory.TV_FHD
                TVResolution.HD_720P -> GlassScreenSizeCategory.TV_HD
                TVResolution.UNKNOWN -> GlassScreenSizeCategory.TV_UNKNOWN
            }
            screenLayout >= Configuration.SCREENLAYOUT_SIZE_XLARGE -> GlassScreenSizeCategory.TABLET_LARGE
            screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE -> GlassScreenSizeCategory.TABLET_MEDIUM
            screenWidthDp >= 600 -> GlassScreenSizeCategory.TABLET_SMALL
            else -> GlassScreenSizeCategory.PHONE
        }
    }
    
    /**
     * Apply adaptive glass layout based on screen characteristics
     */
    fun applyAdaptiveGlassLayout(rootView: ViewGroup) {
        val scaleFactor = calculateGlassScaleFactor()
        val screenCategory = getGlassScreenSizeCategory()
        
        // Apply different optimizations based on screen category
        when (screenCategory) {
            GlassScreenSizeCategory.TV_4K -> optimizeFor4K(rootView, scaleFactor)
            GlassScreenSizeCategory.TV_FHD -> optimizeForFullHD(rootView, scaleFactor)
            GlassScreenSizeCategory.TV_HD -> optimizeForHD(rootView, scaleFactor)
            else -> applyGlassResponsiveScaling(rootView, scaleFactor)
        }
        
        // Set optimal container width
        val optimalWidth = getOptimalGlassMenuWidth()
        rootView.minimumWidth = optimalWidth
        
        // Apply TV-specific enhancements if needed
        if (isTVDevice()) {
            applyTVGlassEnhancements(rootView)
        }
    }
    
    enum class TVResolution {
        UHD_4K, FHD_1080P, HD_720P, UNKNOWN
    }
    
    enum class GlassScreenSizeCategory {
        PHONE, TABLET_SMALL, TABLET_MEDIUM, TABLET_LARGE, 
        TV_HD, TV_FHD, TV_4K, TV_UNKNOWN
    }
}