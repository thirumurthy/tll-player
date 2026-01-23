package com.thirutricks.tllplayer.ui.glass

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R
import com.thirutricks.tllplayer.ui.ResourceValidator

/**
 * Specialized resource validator for glass UI components
 * Validates glass-specific resources and provides fallback mappings
 */
class GlassResourceValidator(
    private val context: Context,
    private val baseValidator: ResourceValidator
) {
    companion object {
        private const val TAG = "GlassResourceValidator"
        
        // Glass-specific drawable resources
        private val GLASS_DRAWABLES = listOf(
            "glass_menu_background",
            "glass_panel_background", 
            "glass_item_background",
            "glass_item_focused",
            "glass_item_moving",
            "glass_card_background",
            "glass_card_focused",
            "glass_card_selector",
            "glassmorphism_overlay",
            "blur_background"
        )
        
        // Glass-specific color resources
        private val GLASS_COLORS = listOf(
            "glass_background",
            "glass_background_focused",
            "glass_border",
            "glass_border_focused",
            "glass_text_primary",
            "glass_text_secondary",
            "glass_highlight",
            "glass_highlight_focused",
            "glass_shadow"
        )
        
        // Glass-specific dimension resources
        private val GLASS_DIMENSIONS = listOf(
            "glass_corner_radius",
            "glass_elevation",
            "glass_blur_radius",
            "glass_border_width",
            "glass_card_padding",
            "glass_card_margin",
            "glass_card_elevation",
            "glass_text_size_large",
            "glass_text_size_medium",
            "glass_text_size_small",
            "glass_text_size_caption"
        )
        
        // Fallback mappings for glass drawables
        private val GLASS_DRAWABLE_FALLBACKS = mapOf(
            "glass_menu_background" to R.drawable.menu_panel_bg,
            "glass_panel_background" to R.drawable.tv_panel_bg,
            "glass_item_background" to R.drawable.list_item_bg,
            "glass_item_focused" to R.drawable.focus_background,
            "glass_item_moving" to R.drawable.focus_background,
            "glass_card_background" to R.drawable.simple_card_background,
            "glass_card_focused" to R.drawable.focus_background,
            "glass_card_selector" to R.drawable.focus_background,
            "glassmorphism_overlay" to android.R.drawable.screen_background_dark_transparent,
            "blur_background" to android.R.drawable.screen_background_dark
        )
        
        // Fallback mappings for glass colors
        private val GLASS_COLOR_FALLBACKS = mapOf(
            "glass_background" to android.R.color.background_dark,
            "glass_background_focused" to android.R.color.holo_blue_dark,
            "glass_border" to android.R.color.darker_gray,
            "glass_border_focused" to android.R.color.holo_blue_bright,
            "glass_text_primary" to android.R.color.primary_text_dark,
            "glass_text_secondary" to android.R.color.secondary_text_dark,
            "glass_highlight" to android.R.color.white,
            "glass_highlight_focused" to android.R.color.holo_blue_bright,
            "glass_shadow" to android.R.color.black
        )
        
        // Fallback values for glass dimensions (in dp)
        private val GLASS_DIMENSION_FALLBACKS = mapOf(
            "glass_corner_radius" to 8f,
            "glass_elevation" to 4f,
            "glass_blur_radius" to 25f,
            "glass_border_width" to 1f,
            "glass_card_padding" to 16f,
            "glass_card_margin" to 8f,
            "glass_card_elevation" to 6f,
            "glass_text_size_large" to 20f,
            "glass_text_size_medium" to 16f,
            "glass_text_size_small" to 14f,
            "glass_text_size_caption" to 12f
        )
    }

    /**
     * Validates all glass-specific drawable resources
     */
    fun validateGlassDrawables(): GlassValidationResult {
        val missingDrawables = mutableListOf<String>()
        val availableDrawables = mutableListOf<String>()
        
        GLASS_DRAWABLES.forEach { drawableName ->
            val resourceId = getGlassDrawableResourceId(drawableName)
            if (resourceId == 0) {
                missingDrawables.add(drawableName)
                Log.w(TAG, "Missing glass drawable: $drawableName")
            } else {
                try {
                    ContextCompat.getDrawable(context, resourceId)
                    availableDrawables.add(drawableName)
                    Log.d(TAG, "Glass drawable validated: $drawableName")
                } catch (e: Exception) {
                    missingDrawables.add(drawableName)
                    Log.e(TAG, "Failed to load glass drawable: $drawableName", e)
                }
            }
        }
        
        return GlassValidationResult(
            resourceType = "drawables",
            totalResources = GLASS_DRAWABLES.size,
            availableResources = availableDrawables,
            missingResources = missingDrawables,
            fallbacksAvailable = missingDrawables.count { GLASS_DRAWABLE_FALLBACKS.containsKey(it) }
        )
    }

    /**
     * Validates all glass-specific color resources
     */
    fun validateGlassColors(): GlassValidationResult {
        val missingColors = mutableListOf<String>()
        val availableColors = mutableListOf<String>()
        
        GLASS_COLORS.forEach { colorName ->
            val resourceId = getGlassColorResourceId(colorName)
            if (resourceId == 0) {
                missingColors.add(colorName)
                Log.w(TAG, "Missing glass color: $colorName")
            } else {
                try {
                    ContextCompat.getColor(context, resourceId)
                    availableColors.add(colorName)
                    Log.d(TAG, "Glass color validated: $colorName")
                } catch (e: Exception) {
                    missingColors.add(colorName)
                    Log.e(TAG, "Failed to load glass color: $colorName", e)
                }
            }
        }
        
        return GlassValidationResult(
            resourceType = "colors",
            totalResources = GLASS_COLORS.size,
            availableResources = availableColors,
            missingResources = missingColors,
            fallbacksAvailable = missingColors.count { GLASS_COLOR_FALLBACKS.containsKey(it) }
        )
    }

    /**
     * Validates all glass-specific dimension resources
     */
    fun validateGlassDimensions(): GlassValidationResult {
        val missingDimensions = mutableListOf<String>()
        val availableDimensions = mutableListOf<String>()
        
        GLASS_DIMENSIONS.forEach { dimensionName ->
            val resourceId = getGlassDimensionResourceId(dimensionName)
            if (resourceId == 0) {
                missingDimensions.add(dimensionName)
                Log.w(TAG, "Missing glass dimension: $dimensionName")
            } else {
                try {
                    context.resources.getDimension(resourceId)
                    availableDimensions.add(dimensionName)
                    Log.d(TAG, "Glass dimension validated: $dimensionName")
                } catch (e: Exception) {
                    missingDimensions.add(dimensionName)
                    Log.e(TAG, "Failed to load glass dimension: $dimensionName", e)
                }
            }
        }
        
        return GlassValidationResult(
            resourceType = "dimensions",
            totalResources = GLASS_DIMENSIONS.size,
            availableResources = availableDimensions,
            missingResources = missingDimensions,
            fallbacksAvailable = missingDimensions.count { GLASS_DIMENSION_FALLBACKS.containsKey(it) }
        )
    }

    /**
     * Performs comprehensive glass resource validation
     */
    fun validateAllGlassResources(): GlassComprehensiveValidation {
        Log.i(TAG, "Starting comprehensive glass resource validation")
        
        val startTime = System.currentTimeMillis()
        
        val drawableValidation = validateGlassDrawables()
        val colorValidation = validateGlassColors()
        val dimensionValidation = validateGlassDimensions()
        
        val validationTime = System.currentTimeMillis() - startTime
        
        val totalMissing = drawableValidation.missingResources.size + 
                          colorValidation.missingResources.size + 
                          dimensionValidation.missingResources.size
        
        val totalFallbacks = drawableValidation.fallbacksAvailable + 
                           colorValidation.fallbacksAvailable + 
                           dimensionValidation.fallbacksAvailable
        
        val glassEffectsSupported = GlassEffectUtils.supportsAdvancedEffects(context)
        val recommendedLevel = determineRecommendedGlassLevel(
            drawableValidation, colorValidation, dimensionValidation, glassEffectsSupported
        )
        
        val result = GlassComprehensiveValidation(
            drawableValidation = drawableValidation,
            colorValidation = colorValidation,
            dimensionValidation = dimensionValidation,
            totalMissingResources = totalMissing,
            totalFallbacksAvailable = totalFallbacks,
            glassEffectsSupported = glassEffectsSupported,
            recommendedGlassLevel = recommendedLevel,
            validationTimeMs = validationTime,
            timestamp = System.currentTimeMillis()
        )
        
        Log.i(TAG, "Glass validation completed in ${validationTime}ms - " +
                "Missing: $totalMissing resources, " +
                "Fallbacks: $totalFallbacks, " +
                "Recommended level: $recommendedLevel")
        
        return result
    }

    /**
     * Creates fallback resource mappings for missing glass resources
     */
    fun createGlassFallbackMap(): Map<String, Int> {
        val fallbackMap = mutableMapOf<String, Int>()
        
        // Add drawable fallbacks
        val drawableValidation = validateGlassDrawables()
        drawableValidation.missingResources.forEach { drawableName ->
            GLASS_DRAWABLE_FALLBACKS[drawableName]?.let { fallbackId ->
                fallbackMap[drawableName] = fallbackId
                Log.i(TAG, "Mapped missing glass drawable '$drawableName' to fallback resource $fallbackId")
            }
        }
        
        // Add color fallbacks
        val colorValidation = validateGlassColors()
        colorValidation.missingResources.forEach { colorName ->
            GLASS_COLOR_FALLBACKS[colorName]?.let { fallbackId ->
                fallbackMap[colorName] = fallbackId
                Log.i(TAG, "Mapped missing glass color '$colorName' to fallback resource $fallbackId")
            }
        }
        
        return fallbackMap
    }

    /**
     * Gets fallback drawable for missing glass drawable
     */
    fun getGlassFallbackDrawable(drawableName: String): Int {
        return GLASS_DRAWABLE_FALLBACKS[drawableName] ?: android.R.drawable.btn_default
    }

    /**
     * Gets fallback color for missing glass color
     */
    fun getGlassFallbackColor(colorName: String): Int {
        return GLASS_COLOR_FALLBACKS[colorName] ?: android.R.color.darker_gray
    }

    /**
     * Gets fallback dimension value for missing glass dimension
     */
    fun getGlassFallbackDimension(dimensionName: String): Float {
        val fallbackDp = GLASS_DIMENSION_FALLBACKS[dimensionName] ?: 16f
        return fallbackDp * context.resources.displayMetrics.density
    }

    /**
     * Checks if glass effects can be safely used
     */
    fun canUseGlassEffects(): Boolean {
        val validation = validateAllGlassResources()
        return validation.recommendedGlassLevel != GlassErrorRecovery.Companion.GlassFallbackLevel.NO_GLASS
    }

    /**
     * Gets optimal glass style config based on available resources
     */
    fun getOptimalGlassStyleConfig(): GlassStyleConfig {
        val validation = validateAllGlassResources()
        val baseConfig = GlassEffectUtils.getOptimalGlassConfig(context)
        
        return when (validation.recommendedGlassLevel) {
            GlassErrorRecovery.Companion.GlassFallbackLevel.FULL_GLASS -> baseConfig
            GlassErrorRecovery.Companion.GlassFallbackLevel.REDUCED_GLASS -> baseConfig.withReducedEffects()
            GlassErrorRecovery.Companion.GlassFallbackLevel.MINIMAL_GLASS -> baseConfig.withMinimalEffects()
            GlassErrorRecovery.Companion.GlassFallbackLevel.NO_GLASS -> baseConfig.withNoEffects()
        }
    }

    // Private helper methods

    private fun getGlassDrawableResourceId(name: String): Int {
        return context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    private fun getGlassColorResourceId(name: String): Int {
        return context.resources.getIdentifier(name, "color", context.packageName)
    }

    private fun getGlassDimensionResourceId(name: String): Int {
        return context.resources.getIdentifier(name, "dimen", context.packageName)
    }

    private fun determineRecommendedGlassLevel(
        drawableValidation: GlassValidationResult,
        colorValidation: GlassValidationResult,
        dimensionValidation: GlassValidationResult,
        glassEffectsSupported: Boolean
    ): GlassErrorRecovery.Companion.GlassFallbackLevel {
        
        if (!glassEffectsSupported) {
            return GlassErrorRecovery.Companion.GlassFallbackLevel.REDUCED_GLASS
        }
        
        val totalMissing = drawableValidation.missingResources.size + 
                          colorValidation.missingResources.size + 
                          dimensionValidation.missingResources.size
        
        val totalResources = drawableValidation.totalResources + 
                           colorValidation.totalResources + 
                           dimensionValidation.totalResources
        
        val missingPercentage = (totalMissing.toFloat() / totalResources.toFloat()) * 100
        
        return when {
            missingPercentage == 0f -> GlassErrorRecovery.Companion.GlassFallbackLevel.FULL_GLASS
            missingPercentage <= 25f -> GlassErrorRecovery.Companion.GlassFallbackLevel.REDUCED_GLASS
            missingPercentage <= 50f -> GlassErrorRecovery.Companion.GlassFallbackLevel.MINIMAL_GLASS
            else -> GlassErrorRecovery.Companion.GlassFallbackLevel.NO_GLASS
        }
    }
}

/**
 * Data classes for glass validation results
 */

data class GlassValidationResult(
    val resourceType: String,
    val totalResources: Int,
    val availableResources: List<String>,
    val missingResources: List<String>,
    val fallbacksAvailable: Int
) {
    val availabilityPercentage: Float
        get() = (availableResources.size.toFloat() / totalResources.toFloat()) * 100f
}

data class GlassComprehensiveValidation(
    val drawableValidation: GlassValidationResult,
    val colorValidation: GlassValidationResult,
    val dimensionValidation: GlassValidationResult,
    val totalMissingResources: Int,
    val totalFallbacksAvailable: Int,
    val glassEffectsSupported: Boolean,
    val recommendedGlassLevel: GlassErrorRecovery.Companion.GlassFallbackLevel,
    val validationTimeMs: Long,
    val timestamp: Long
) {
    val overallAvailabilityPercentage: Float
        get() {
            val totalResources = drawableValidation.totalResources + 
                               colorValidation.totalResources + 
                               dimensionValidation.totalResources
            val totalAvailable = drawableValidation.availableResources.size + 
                               colorValidation.availableResources.size + 
                               dimensionValidation.availableResources.size
            return (totalAvailable.toFloat() / totalResources.toFloat()) * 100f
        }
}

/**
 * Extension functions for GlassStyleConfig
 */
fun GlassStyleConfig.withReducedEffects(): GlassStyleConfig {
    return this.copy(
        enableBlurEffects = false,
        enableElevationShadows = true,
        enableComplexAnimations = true,
        blurRadius = 0f,
        backgroundAlpha = 0.8f
    )
}

fun GlassStyleConfig.withMinimalEffects(): GlassStyleConfig {
    return this.copy(
        enableBlurEffects = false,
        enableElevationShadows = false,
        enableComplexAnimations = false,
        blurRadius = 0f,
        backgroundAlpha = 0.9f,
        borderAlpha = 0.5f
    )
}

fun GlassStyleConfig.withNoEffects(): GlassStyleConfig {
    return this.copy(
        enableBlurEffects = false,
        enableElevationShadows = false,
        enableComplexAnimations = false,
        blurRadius = 0f,
        backgroundAlpha = 1.0f,
        borderAlpha = 0f
    )
}