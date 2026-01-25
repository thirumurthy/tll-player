package com.thirutricks.tllplayer.ui.glass

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * Configuration class for consistent glass styling across the menu system.
 * Provides centralized styling parameters for glassmorphism effects.
 */
data class GlassStyleConfig(
    // Background transparency levels
    val backgroundAlpha: Float = 0.20f,
    val panelBackgroundAlpha: Float = 0.15f,
    val itemBackgroundAlpha: Float = 0.08f,
    
    // Border transparency levels
    val borderAlpha: Float = 0.30f,
    val panelBorderAlpha: Float = 0.20f,
    val itemBorderAlpha: Float = 0.10f,
    
    // Corner radius values
    val cornerRadiusLarge: Float = 16f,
    val cornerRadiusMedium: Float = 12f,
    val cornerRadiusSmall: Float = 8f,
    
    // Blur effect parameters
    val blurRadius: Float = 25f,
    val blurSampling: Float = 1f,
    
    // Focus animation parameters
    val focusElevation: Float = 16f,
    val focusScale: Float = 1.02f,
    val focusAnimationDuration: Long = 50L,
    
    // Move mode parameters
    val moveElevation: Float = 20f,
    val moveScale: Float = 1.05f,
    val moveAnimationDuration: Long = 150L,
    
    // Color parameters
    @ColorInt val focusGlowColor: Int = Color.parseColor("#802196F3"),
    @ColorInt val moveGlowColor: Int = Color.parseColor("#804CAF50"),
    @ColorInt val textPrimaryColor: Int = Color.parseColor("#FFFFFF"),
    @ColorInt val textSecondaryColor: Int = Color.parseColor("#CCFFFFFF"),
    @ColorInt val textTertiaryColor: Int = Color.parseColor("#99FFFFFF"),
    @ColorInt val favoriteActiveColor: Int = Color.parseColor("#FFE91E63"),
    @ColorInt val favoriteInactiveColor: Int = Color.parseColor("#99FFFFFF"),
    
    // Performance parameters
    val enableBlurEffects: Boolean = true,
    val enableElevationShadows: Boolean = true,
    val enableComplexAnimations: Boolean = true,
    val maxAnimationDuration: Long = 500L,
    
    // Additional elevation parameters
    val menuElevation: Float = 8f,
    val panelElevation: Float = 4f,
    val overlayElevation: Float = 24f,
    val menuBackgroundElevation: Float = 2f,
    
    // Additional alpha parameters
    val overlayAlpha: Float = 0.3f,
    
    // Corner radius property
    val cornerRadius: Float = cornerRadiusMedium
) {
    companion object {
        /**
         * Default glass style configuration optimized for TV viewing
         */
        val DEFAULT = GlassStyleConfig()
        
        /**
         * High contrast configuration for accessibility
         */
        val HIGH_CONTRAST = GlassStyleConfig(
            backgroundAlpha = 0.25f,
            panelBackgroundAlpha = 0.20f,
            itemBackgroundAlpha = 0.15f,
            borderAlpha = 0.50f,
            panelBorderAlpha = 0.40f,
            itemBorderAlpha = 0.30f,
            focusGlowColor = Color.parseColor("#FFFFFF"),
            moveGlowColor = Color.parseColor("#FFFF00")
        )
        
        /**
         * Performance optimized configuration for lower-end devices
         */
        val PERFORMANCE = GlassStyleConfig(
            enableBlurEffects = false,
            enableElevationShadows = false,
            enableComplexAnimations = false,
            focusAnimationDuration = 150L,
            moveAnimationDuration = 200L,
            maxAnimationDuration = 300L
        )
    }
    
    /**
     * Creates a glass style configuration with performance adjustments
     * based on device capabilities
     */
    fun withPerformanceAdjustments(
        hasHardwareAcceleration: Boolean,
        isLowEndDevice: Boolean
    ): GlassStyleConfig {
        return if (isLowEndDevice) {
            copy(
                enableBlurEffects = false,
                enableElevationShadows = hasHardwareAcceleration,
                enableComplexAnimations = hasHardwareAcceleration,
                focusAnimationDuration = if (hasHardwareAcceleration) 200L else 150L,
                moveAnimationDuration = if (hasHardwareAcceleration) 300L else 200L
            )
        } else {
            this
        }
    }
    
    /**
     * Creates a glass style configuration with accessibility adjustments
     */
    fun withAccessibilityAdjustments(
        isHighContrastEnabled: Boolean,
        reduceMotionEnabled: Boolean
    ): GlassStyleConfig {
        return copy(
            backgroundAlpha = if (isHighContrastEnabled) backgroundAlpha * 1.5f else backgroundAlpha,
            borderAlpha = if (isHighContrastEnabled) borderAlpha * 1.5f else borderAlpha,
            focusAnimationDuration = if (reduceMotionEnabled) 100L else focusAnimationDuration,
            moveAnimationDuration = if (reduceMotionEnabled) 150L else moveAnimationDuration,
            enableComplexAnimations = !reduceMotionEnabled,
            focusGlowColor = if (isHighContrastEnabled) Color.WHITE else focusGlowColor
        )
    }
}