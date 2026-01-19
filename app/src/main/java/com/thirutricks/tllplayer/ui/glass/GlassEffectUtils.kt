package com.thirutricks.tllplayer.ui.glass

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R

/**
 * Utility class for applying glass effects to views and managing glass styling.
 */
object GlassEffectUtils {
    
    /**
     * Applies glass background to a view based on the glass type
     */
    fun applyGlassBackground(
        view: View,
        glassType: GlassType,
        context: Context = view.context
    ) {
        val drawable = when (glassType) {
            GlassType.MENU -> ContextCompat.getDrawable(context, R.drawable.glass_menu_background)
            GlassType.PANEL -> ContextCompat.getDrawable(context, R.drawable.glass_panel_background)
            GlassType.ITEM -> ContextCompat.getDrawable(context, R.drawable.glass_item_background)
            GlassType.ITEM_FOCUSED -> ContextCompat.getDrawable(context, R.drawable.glass_item_focused)
            GlassType.ITEM_MOVING -> ContextCompat.getDrawable(context, R.drawable.glass_item_moving)
            GlassType.OVERLAY -> ContextCompat.getDrawable(context, R.drawable.glass_panel_background)
        }
        
        view.background = drawable
    }
    
    /**
     * Applies glass styling with elevation and corner radius
     */
    fun applyGlassStyle(
        view: View,
        styleConfig: GlassStyleConfig = GlassStyleConfig.DEFAULT,
        glassType: GlassType = GlassType.ITEM
    ) {
        applyGlassBackground(view, glassType)
        
        // Apply elevation if supported
        if (styleConfig.enableElevationShadows) {
            view.elevation = when (glassType) {
                GlassType.MENU -> styleConfig.focusElevation
                GlassType.PANEL -> styleConfig.focusElevation * 0.5f
                GlassType.ITEM -> 0f
                GlassType.ITEM_FOCUSED -> styleConfig.focusElevation
                GlassType.ITEM_MOVING -> styleConfig.moveElevation
                GlassType.OVERLAY -> styleConfig.overlayElevation
            }
        }
        
        // Set clip to outline for rounded corners
        view.clipToOutline = true
    }
    
    /**
     * Creates a glass state selector drawable for interactive elements
     */
    fun createGlassStateSelector(context: Context): Drawable? {
        return try {
            // Create a state list drawable that changes based on focus/pressed states
            val stateListDrawable = android.graphics.drawable.StateListDrawable()
            
            // Moving state (highest priority)
            stateListDrawable.addState(
                intArrayOf(android.R.attr.state_selected, android.R.attr.state_focused),
                ContextCompat.getDrawable(context, R.drawable.glass_item_moving)
            )
            
            // Focused state
            stateListDrawable.addState(
                intArrayOf(android.R.attr.state_focused),
                ContextCompat.getDrawable(context, R.drawable.glass_item_focused)
            )
            
            // Pressed state
            stateListDrawable.addState(
                intArrayOf(android.R.attr.state_pressed),
                ContextCompat.getDrawable(context, R.drawable.glass_item_focused)
            )
            
            // Default state
            stateListDrawable.addState(
                intArrayOf(),
                ContextCompat.getDrawable(context, R.drawable.glass_item_background)
            )
            
            stateListDrawable
        } catch (e: Exception) {
            // Fallback to simple glass background
            ContextCompat.getDrawable(context, R.drawable.glass_item_background)
        }
    }
    
    /**
     * Creates a high contrast focus background for D-pad navigation
     */
    fun createHighContrastFocusBackground(
        context: Context,
        styleConfig: GlassStyleConfig = GlassStyleConfig.DEFAULT
    ): Drawable? {
        return try {
            // Create a drawable with high contrast border for D-pad navigation
            val drawable = ContextCompat.getDrawable(context, R.drawable.glass_item_focused)?.mutate()
            drawable?.setTint(styleConfig.focusGlowColor)
            drawable
        } catch (e: Exception) {
            // Fallback to regular focused background
            ContextCompat.getDrawable(context, R.drawable.glass_item_focused)
        }
    }
    
    /**
     * Applies glass text styling based on hierarchy level
     */
    fun applyGlassTextStyle(
        view: android.widget.TextView,
        textLevel: TextLevel,
        styleConfig: GlassStyleConfig = GlassStyleConfig.DEFAULT
    ) {
        val context = view.context
        
        when (textLevel) {
            TextLevel.PRIMARY -> {
                view.setTextColor(styleConfig.textPrimaryColor)
                view.textSize = context.resources.getDimension(R.dimen.glass_text_size_large) / context.resources.displayMetrics.scaledDensity
                view.typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            TextLevel.SECONDARY -> {
                view.setTextColor(styleConfig.textSecondaryColor)
                view.textSize = context.resources.getDimension(R.dimen.glass_text_size_medium) / context.resources.displayMetrics.scaledDensity
                view.typeface = android.graphics.Typeface.DEFAULT
            }
            TextLevel.TERTIARY -> {
                view.setTextColor(styleConfig.textTertiaryColor)
                view.textSize = context.resources.getDimension(R.dimen.glass_text_size_small) / context.resources.displayMetrics.scaledDensity
                view.typeface = android.graphics.Typeface.DEFAULT
            }
            TextLevel.CAPTION -> {
                view.setTextColor(styleConfig.textTertiaryColor)
                view.textSize = context.resources.getDimension(R.dimen.glass_text_size_caption) / context.resources.displayMetrics.scaledDensity
                view.typeface = android.graphics.Typeface.DEFAULT
            }
        }
    }
    
    /**
     * Checks if the device supports advanced glass effects
     */
    fun supportsAdvancedEffects(context: Context): Boolean {
        return try {
            // Check for hardware acceleration
            val activity = context as? android.app.Activity
            val isHardwareAccelerated = activity?.window?.attributes?.flags?.and(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            ) != 0
            
            // Check API level for blur support
            val supportsBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            
            isHardwareAccelerated && supportsBlur
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets appropriate glass style config based on device capabilities
     */
    fun getOptimalGlassConfig(context: Context): GlassStyleConfig {
        val supportsAdvanced = supportsAdvancedEffects(context)
        val isLowEndDevice = isLowEndDevice(context)
        
        return GlassStyleConfig.DEFAULT.withPerformanceAdjustments(
            hasHardwareAcceleration = supportsAdvanced,
            isLowEndDevice = isLowEndDevice
        )
    }
    
    /**
     * Simple heuristic to detect low-end devices
     */
    private fun isLowEndDevice(context: Context): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            activityManager.isLowRamDevice
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Applies glass border to a view
     */
    fun applyGlassBorder(
        view: View,
        styleConfig: GlassStyleConfig = GlassStyleConfig.DEFAULT
    ) {
        // Apply subtle border effect through background drawable
        view.background?.alpha = (styleConfig.borderAlpha * 255).toInt()
    }
    
    /**
     * Applies consistent corner radius to a view group
     */
    fun applyConsistentCornerRadius(
        viewGroup: android.view.ViewGroup,
        cornerRadius: Float
    ) {
        // Apply corner radius through background drawable
        viewGroup.clipToOutline = true
        viewGroup.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }
    }
    
    /**
     * Animates panel transition with glass effects
     */
    fun animatePanelTransition(
        view: View,
        direction: TransitionDirection,
        onComplete: (() -> Unit)? = null
    ) {
        val animator = when (direction) {
            TransitionDirection.FADE_IN -> {
                view.alpha = 0f
                view.animate().alpha(1f)
            }
            TransitionDirection.FADE_OUT -> {
                view.animate().alpha(0f)
            }
            TransitionDirection.LEFT_TO_RIGHT -> {
                view.translationX = -view.width.toFloat()
                view.animate().translationX(0f)
            }
            TransitionDirection.RIGHT_TO_LEFT -> {
                view.translationX = view.width.toFloat()
                view.animate().translationX(0f)
            }
        }
        
        animator.setDuration(300L)
            .withEndAction { onComplete?.invoke() }
            .start()
    }
}

/**
 * Enum for different glass background types
 */
enum class GlassType {
    MENU,
    PANEL,
    ITEM,
    ITEM_FOCUSED,
    ITEM_MOVING,
    OVERLAY
}

/**
 * Enum for text hierarchy levels in glass UI
 */
enum class TextLevel {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    CAPTION
}

/**
 * Enum for panel transition directions
 */
enum class TransitionDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    FADE_IN,
    FADE_OUT
}