package com.thirutricks.tllplayer.ui

import android.content.Context
import android.content.res.Resources
import android.util.Log
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R

/**
 * Validates all required resources before component initialization
 * Provides fallback mappings for missing resources to prevent crashes
 */
class ResourceValidator(private val context: Context) {

    companion object {
        private const val TAG = "ResourceValidator"
        
        // Required drawable resources for ModernToggleSwitch
        private val MODERN_TOGGLE_DRAWABLES = listOf(
            "modern_toggle_track_animated",
            "modern_toggle_thumb",
            "modern_toggle_thumb_focused"
        )
        
        // Required color resources
        private val REQUIRED_COLORS = listOf(
            "focus",
            "glass_border_focused",
            "glass_card_background_focused",
            "glass_border",
            "glass_card_background",
            "glass_highlight_focused",
            "info_text_primary",
            "info_text_secondary",
            "white"
        )
        
        // Required dimension resources
        private val REQUIRED_DIMENSIONS = listOf(
            "tv_min_touch_target",
            "tv_text_size_medium",
            "toggle_padding"
        )
        
        // Fallback drawable mappings
        private val DRAWABLE_FALLBACKS = mapOf(
            "modern_toggle_track_animated" to android.R.drawable.btn_default,
            "modern_toggle_thumb" to android.R.drawable.btn_default_small,
            "modern_toggle_thumb_focused" to android.R.drawable.btn_default_small
        )
        
        // Fallback color mappings
        private val COLOR_FALLBACKS = mapOf(
            "focus" to android.R.color.holo_blue_bright,
            "glass_border_focused" to android.R.color.white,
            "glass_card_background_focused" to android.R.color.darker_gray,
            "glass_border" to android.R.color.darker_gray,
            "glass_card_background" to android.R.color.background_dark,
            "glass_highlight_focused" to android.R.color.white,
            "info_text_primary" to android.R.color.primary_text_dark,
            "info_text_secondary" to android.R.color.secondary_text_dark,
            "white" to android.R.color.white
        )
        
        // Fallback dimension values (in dp)
        private val DIMENSION_FALLBACKS = mapOf(
            "tv_min_touch_target" to 48f,
            "tv_text_size_medium" to 16f,
            "toggle_padding" to 8f
        )
    }

    /**
     * Validates all drawable resources required by the application
     */
    fun validateDrawableResources(): List<String> {
        val missingDrawables = mutableListOf<String>()
        
        MODERN_TOGGLE_DRAWABLES.forEach { drawableName ->
            val resourceId = getDrawableResourceId(drawableName)
            if (resourceId == 0) {
                missingDrawables.add(drawableName)
                Log.w(TAG, "Missing drawable resource: $drawableName")
            } else {
                try {
                    ContextCompat.getDrawable(context, resourceId)
                    Log.d(TAG, "Drawable resource validated: $drawableName")
                } catch (e: Exception) {
                    missingDrawables.add(drawableName)
                    Log.e(TAG, "Failed to load drawable resource: $drawableName", e)
                }
            }
        }
        
        return missingDrawables
    }

    /**
     * Validates all layout resources required by the application
     */
    fun validateLayoutResources(): List<String> {
        val missingLayouts = mutableListOf<String>()
        
        // Check critical layout resources
        val criticalLayouts = listOf(
            "setting",
            "glass_card_preferences",
            "glass_card_configuration",
            "glass_card_actions"
        )
        
        criticalLayouts.forEach { layoutName ->
            val resourceId = getLayoutResourceId(layoutName)
            if (resourceId == 0) {
                missingLayouts.add(layoutName)
                Log.w(TAG, "Missing layout resource: $layoutName")
            } else {
                Log.d(TAG, "Layout resource validated: $layoutName")
            }
        }
        
        return missingLayouts
    }

    /**
     * Validates color resources
     */
    fun validateColorResources(): List<String> {
        val missingColors = mutableListOf<String>()
        
        REQUIRED_COLORS.forEach { colorName ->
            val resourceId = getColorResourceId(colorName)
            if (resourceId == 0) {
                missingColors.add(colorName)
                Log.w(TAG, "Missing color resource: $colorName")
            } else {
                try {
                    ContextCompat.getColor(context, resourceId)
                    Log.d(TAG, "Color resource validated: $colorName")
                } catch (e: Exception) {
                    missingColors.add(colorName)
                    Log.e(TAG, "Failed to load color resource: $colorName", e)
                }
            }
        }
        
        return missingColors
    }

    /**
     * Validates dimension resources
     */
    fun validateDimensionResources(): List<String> {
        val missingDimensions = mutableListOf<String>()
        
        REQUIRED_DIMENSIONS.forEach { dimensionName ->
            val resourceId = getDimensionResourceId(dimensionName)
            if (resourceId == 0) {
                missingDimensions.add(dimensionName)
                Log.w(TAG, "Missing dimension resource: $dimensionName")
            } else {
                try {
                    context.resources.getDimension(resourceId)
                    Log.d(TAG, "Dimension resource validated: $dimensionName")
                } catch (e: Exception) {
                    missingDimensions.add(dimensionName)
                    Log.e(TAG, "Failed to load dimension resource: $dimensionName", e)
                }
            }
        }
        
        return missingDimensions
    }

    /**
     * Checks if custom components can be safely initialized
     */
    fun validateCustomComponents(): Boolean {
        val missingDrawables = validateDrawableResources()
        val missingColors = validateColorResources()
        val missingDimensions = validateDimensionResources()
        
        val allResourcesAvailable = missingDrawables.isEmpty() && 
                                   missingColors.isEmpty() && 
                                   missingDimensions.isEmpty()
        
        Log.i(TAG, "Custom components validation: ${if (allResourcesAvailable) "PASSED" else "FAILED"}")
        
        if (!allResourcesAvailable) {
            Log.w(TAG, "Missing resources - Drawables: $missingDrawables, Colors: $missingColors, Dimensions: $missingDimensions")
        }
        
        return allResourcesAvailable
    }

    /**
     * Creates a mapping of missing resources to their fallback alternatives
     */
    fun createResourceFallbackMap(): Map<String, Int> {
        val fallbackMap = mutableMapOf<String, Int>()
        
        // Add drawable fallbacks
        val missingDrawables = validateDrawableResources()
        missingDrawables.forEach { drawableName ->
            DRAWABLE_FALLBACKS[drawableName]?.let { fallbackId ->
                fallbackMap[drawableName] = fallbackId
                Log.i(TAG, "Mapped missing drawable '$drawableName' to fallback resource $fallbackId")
            }
        }
        
        // Add color fallbacks
        val missingColors = validateColorResources()
        missingColors.forEach { colorName ->
            COLOR_FALLBACKS[colorName]?.let { fallbackId ->
                fallbackMap[colorName] = fallbackId
                Log.i(TAG, "Mapped missing color '$colorName' to fallback resource $fallbackId")
            }
        }
        
        return fallbackMap
    }

    /**
     * Generates a comprehensive validation report
     */
    fun generateValidationReport(): ValidationReport {
        val missingDrawables = validateDrawableResources()
        val missingLayouts = validateLayoutResources()
        val missingColors = validateColorResources()
        val missingDimensions = validateDimensionResources()
        
        val allMissingResources = missingDrawables + missingLayouts + missingColors + missingDimensions
        val allResourcesAvailable = allMissingResources.isEmpty()
        
        val fallbacksRequired = createResourceFallbackMap()
        
        val recommendedAction = when {
            allResourcesAvailable -> RecoveryAction.PROCEED_NORMAL
            fallbacksRequired.isNotEmpty() -> RecoveryAction.USE_FALLBACK_UI
            missingLayouts.isNotEmpty() -> RecoveryAction.USE_EMERGENCY_UI
            else -> RecoveryAction.ABORT_WITH_ERROR
        }
        
        Log.i(TAG, "Validation report generated - Action: $recommendedAction, Missing: ${allMissingResources.size} resources")
        
        return ValidationReport(
            allResourcesAvailable = allResourcesAvailable,
            missingResources = allMissingResources,
            fallbacksRequired = fallbacksRequired.mapValues { it.value.toString() },
            recommendedAction = recommendedAction
        )
    }

    /**
     * Get fallback drawable resource ID for a missing drawable
     */
    fun getFallbackDrawable(drawableName: String): Int {
        return DRAWABLE_FALLBACKS[drawableName] ?: android.R.drawable.btn_default
    }

    /**
     * Get fallback color resource ID for a missing color
     */
    fun getFallbackColor(colorName: String): Int {
        return COLOR_FALLBACKS[colorName] ?: android.R.color.darker_gray
    }

    /**
     * Get fallback dimension value for a missing dimension
     */
    fun getFallbackDimension(dimensionName: String): Float {
        val fallbackDp = DIMENSION_FALLBACKS[dimensionName] ?: 16f
        return fallbackDp * context.resources.displayMetrics.density
    }

    // Private helper methods
    private fun getDrawableResourceId(name: String): Int {
        return context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    private fun getLayoutResourceId(name: String): Int {
        return context.resources.getIdentifier(name, "layout", context.packageName)
    }

    private fun getColorResourceId(name: String): Int {
        return context.resources.getIdentifier(name, "color", context.packageName)
    }

    private fun getDimensionResourceId(name: String): Int {
        return context.resources.getIdentifier(name, "dimen", context.packageName)
    }
}

/**
 * Data class representing the results of resource validation
 */
data class ValidationReport(
    val allResourcesAvailable: Boolean,
    val missingResources: List<String>,
    val fallbacksRequired: Map<String, String>,
    val recommendedAction: RecoveryAction
)

/**
 * Enum representing different recovery actions based on validation results
 */
enum class RecoveryAction {
    PROCEED_NORMAL,     // All resources available, proceed normally
    USE_FALLBACK_UI,    // Some resources missing, use fallback alternatives
    USE_EMERGENCY_UI,   // Critical resources missing, use minimal UI
    ABORT_WITH_ERROR    // Cannot recover, abort with error message
}