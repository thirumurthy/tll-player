package com.thirutricks.tllplayer.ui.glass

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R
import com.thirutricks.tllplayer.ui.CrashDiagnosticManager
import com.thirutricks.tllplayer.ui.ResourceValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Enhanced error recovery system specifically for glass UI components
 * Provides progressive fallback mechanisms for glass effects and styling
 */
class GlassErrorRecovery(
    private val context: Context,
    private val resourceValidator: ResourceValidator,
    private val crashDiagnosticManager: CrashDiagnosticManager
) {
    companion object {
        private const val TAG = "GlassErrorRecovery"
        private const val MAX_GLASS_RETRY_ATTEMPTS = 2
        
        // Glass effect fallback levels
        enum class GlassFallbackLevel {
            FULL_GLASS,      // All glass effects enabled
            REDUCED_GLASS,   // Basic glass effects only
            MINIMAL_GLASS,   // Simple backgrounds with transparency
            NO_GLASS         // Standard Android styling
        }
    }

    private val glassRetryAttempts = ConcurrentHashMap<String, AtomicInteger>()
    private val recoveryScope = CoroutineScope(Dispatchers.Main)
    private var currentGlassFallbackLevel = GlassFallbackLevel.FULL_GLASS
    private var glassEffectsSupported = true

    init {
        // Check if glass effects are supported on this device
        glassEffectsSupported = GlassEffectUtils.supportsAdvancedEffects(context)
        if (!glassEffectsSupported) {
            currentGlassFallbackLevel = GlassFallbackLevel.REDUCED_GLASS
            Log.w(TAG, "Advanced glass effects not supported, using reduced glass mode")
        }
    }

    /**
     * Apply glass styling with error recovery
     */
    fun applyGlassStylingWithRecovery(
        view: View,
        glassType: GlassType,
        styleConfig: GlassStyleConfig = GlassStyleConfig.DEFAULT
    ): Boolean {
        return try {
            when (currentGlassFallbackLevel) {
                GlassFallbackLevel.FULL_GLASS -> {
                    GlassEffectUtils.applyGlassStyle(view, styleConfig, glassType)
                    true
                }
                GlassFallbackLevel.REDUCED_GLASS -> {
                    applyReducedGlassStyle(view, glassType, styleConfig)
                    true
                }
                GlassFallbackLevel.MINIMAL_GLASS -> {
                    applyMinimalGlassStyle(view, glassType)
                    true
                }
                GlassFallbackLevel.NO_GLASS -> {
                    applyStandardStyle(view, glassType)
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying glass styling", e)
            crashDiagnosticManager.logCrashDetails(e, "applyGlassStyling", glassType.name)
            handleGlassStyleError(view, glassType, e)
        }
    }

    /**
     * Create glass background with fallback handling
     */
    fun createGlassBackgroundWithFallback(
        view: View,
        glassType: GlassType
    ): Boolean {
        return try {
            val drawable = when (currentGlassFallbackLevel) {
                GlassFallbackLevel.FULL_GLASS -> {
                    GlassEffectUtils.createGlassStateSelector(context)
                }
                GlassFallbackLevel.REDUCED_GLASS -> {
                    createReducedGlassBackground(glassType)
                }
                GlassFallbackLevel.MINIMAL_GLASS -> {
                    createMinimalGlassBackground(glassType)
                }
                GlassFallbackLevel.NO_GLASS -> {
                    createStandardBackground(glassType)
                }
            }
            
            view.background = drawable
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating glass background", e)
            crashDiagnosticManager.logCrashDetails(e, "createGlassBackground", glassType.name)
            
            // Ultimate fallback - simple colored background
            try {
                view.setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                true
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Even fallback background failed", fallbackError)
                false
            }
        }
    }

    /**
     * Apply glass text styling with error recovery
     */
    fun applyGlassTextStylingWithRecovery(
        textView: TextView,
        textLevel: TextLevel,
        styleConfig: GlassStyleConfig = GlassStyleConfig.DEFAULT
    ): Boolean {
        return try {
            when (currentGlassFallbackLevel) {
                GlassFallbackLevel.FULL_GLASS,
                GlassFallbackLevel.REDUCED_GLASS -> {
                    GlassEffectUtils.applyGlassTextStyle(textView, textLevel, styleConfig)
                    true
                }
                GlassFallbackLevel.MINIMAL_GLASS -> {
                    applyMinimalTextStyle(textView, textLevel)
                    true
                }
                GlassFallbackLevel.NO_GLASS -> {
                    applyStandardTextStyle(textView, textLevel)
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying glass text styling", e)
            crashDiagnosticManager.logCrashDetails(e, "applyGlassTextStyling", textLevel.name)
            
            // Fallback to basic text styling
            try {
                textView.setTextColor(ContextCompat.getColor(context, android.R.color.white))
                true
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Text styling fallback failed", fallbackError)
                false
            }
        }
    }

    /**
     * Handle glass component initialization errors
     */
    fun handleGlassComponentError(
        componentName: String,
        error: Exception,
        retryCallback: (() -> View?)? = null
    ): View? {
        Log.w(TAG, "Handling glass component error for: $componentName", error)
        
        crashDiagnosticManager.logCrashDetails(error, "glassComponentInitialization", componentName)
        
        val attemptCount = glassRetryAttempts.computeIfAbsent(componentName) { AtomicInteger(0) }
        val currentAttempt = attemptCount.incrementAndGet()
        
        return when {
            currentAttempt <= MAX_GLASS_RETRY_ATTEMPTS && retryCallback != null -> {
                Log.i(TAG, "Retrying glass component initialization (attempt $currentAttempt/$MAX_GLASS_RETRY_ATTEMPTS)")
                retryGlassComponentWithFallback(componentName, retryCallback)
            }
            else -> {
                Log.w(TAG, "Max retry attempts reached for $componentName, creating fallback")
                createGlassComponentFallback(componentName)
            }
        }
    }

    /**
     * Degrade glass effects level when errors occur
     */
    fun degradeGlassEffects() {
        val previousLevel = currentGlassFallbackLevel
        
        currentGlassFallbackLevel = when (currentGlassFallbackLevel) {
            GlassFallbackLevel.FULL_GLASS -> GlassFallbackLevel.REDUCED_GLASS
            GlassFallbackLevel.REDUCED_GLASS -> GlassFallbackLevel.MINIMAL_GLASS
            GlassFallbackLevel.MINIMAL_GLASS -> GlassFallbackLevel.NO_GLASS
            GlassFallbackLevel.NO_GLASS -> GlassFallbackLevel.NO_GLASS
        }
        
        Log.w(TAG, "Glass effects degraded from $previousLevel to $currentGlassFallbackLevel")
        crashDiagnosticManager.updateComponentState(
            "GlassEffects", 
            currentGlassFallbackLevel.name,
            mapOf("previousLevel" to previousLevel.name, "reason" to "error_recovery")
        )
    }

    /**
     * Check if glass effects are currently available
     */
    fun areGlassEffectsAvailable(): Boolean {
        return currentGlassFallbackLevel != GlassFallbackLevel.NO_GLASS
    }

    /**
     * Get current glass fallback level
     */
    fun getCurrentGlassFallbackLevel(): GlassFallbackLevel = currentGlassFallbackLevel

    /**
     * Create emergency glass UI when all else fails
     */
    fun createEmergencyGlassUI(container: ViewGroup): View {
        return try {
            Log.w(TAG, "Creating emergency glass UI")
            
            val emergencyLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
            }
            
            val titleText = TextView(context).apply {
                text = "Glass UI (Safe Mode)"
                textSize = 18f
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setPadding(0, 0, 0, 16)
            }
            emergencyLayout.addView(titleText)
            
            val statusText = TextView(context).apply {
                text = "Glass effects are disabled due to compatibility issues. Basic functionality is available."
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, android.R.color.secondary_text_dark))
            }
            emergencyLayout.addView(statusText)
            
            emergencyLayout
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error creating emergency glass UI", e)
            createMinimalFallbackView()
        }
    }

    // Private helper methods

    private fun handleGlassStyleError(view: View, glassType: GlassType, error: Exception): Boolean {
        Log.w(TAG, "Handling glass style error, degrading effects", error)
        
        degradeGlassEffects()
        
        return try {
            applyGlassStylingWithRecovery(view, glassType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply fallback glass styling", e)
            false
        }
    }

    private fun applyReducedGlassStyle(
        view: View,
        glassType: GlassType,
        styleConfig: GlassStyleConfig
    ) {
        // Apply basic glass background without advanced effects
        val backgroundColor = when (glassType) {
            GlassType.MENU -> ContextCompat.getColor(context, android.R.color.background_dark)
            GlassType.PANEL -> ContextCompat.getColor(context, android.R.color.darker_gray)
            GlassType.ITEM -> ContextCompat.getColor(context, android.R.color.transparent)
            GlassType.ITEM_FOCUSED -> styleConfig.focusGlowColor
            GlassType.ITEM_MOVING -> styleConfig.moveGlowColor
            GlassType.OVERLAY -> ContextCompat.getColor(context, android.R.color.background_dark)
        }
        
        view.setBackgroundColor(backgroundColor)
        view.alpha = 0.9f
    }

    private fun applyMinimalGlassStyle(view: View, glassType: GlassType) {
        val backgroundColor = when (glassType) {
            GlassType.ITEM_FOCUSED -> ContextCompat.getColor(context, android.R.color.holo_blue_dark)
            GlassType.ITEM_MOVING -> ContextCompat.getColor(context, android.R.color.holo_orange_dark)
            else -> ContextCompat.getColor(context, android.R.color.darker_gray)
        }
        
        view.setBackgroundColor(backgroundColor)
        view.alpha = 0.8f
    }

    private fun applyStandardStyle(view: View, glassType: GlassType) {
        val backgroundColor = when (glassType) {
            GlassType.ITEM_FOCUSED -> ContextCompat.getColor(context, android.R.color.holo_blue_bright)
            GlassType.ITEM_MOVING -> ContextCompat.getColor(context, android.R.color.holo_orange_light)
            else -> ContextCompat.getColor(context, android.R.color.background_dark)
        }
        
        view.setBackgroundColor(backgroundColor)
        view.alpha = 1.0f
    }

    private fun createReducedGlassBackground(glassType: GlassType): android.graphics.drawable.Drawable? {
        return try {
            val colorId = when (glassType) {
                GlassType.MENU -> android.R.color.background_dark
                GlassType.PANEL -> android.R.color.darker_gray
                GlassType.ITEM -> android.R.color.transparent
                GlassType.ITEM_FOCUSED -> android.R.color.holo_blue_dark
                GlassType.ITEM_MOVING -> android.R.color.holo_orange_dark
                GlassType.OVERLAY -> android.R.color.background_dark
            }
            
            android.graphics.drawable.ColorDrawable(ContextCompat.getColor(context, colorId))
        } catch (e: Exception) {
            Log.e(TAG, "Error creating reduced glass background", e)
            null
        }
    }

    private fun createMinimalGlassBackground(glassType: GlassType): android.graphics.drawable.Drawable? {
        return try {
            val color = when (glassType) {
                GlassType.ITEM_FOCUSED -> ContextCompat.getColor(context, android.R.color.holo_blue_dark)
                GlassType.ITEM_MOVING -> ContextCompat.getColor(context, android.R.color.holo_orange_dark)
                else -> ContextCompat.getColor(context, android.R.color.darker_gray)
            }
            
            android.graphics.drawable.ColorDrawable(color)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating minimal glass background", e)
            null
        }
    }

    private fun createStandardBackground(glassType: GlassType): android.graphics.drawable.Drawable? {
        return try {
            val color = when (glassType) {
                GlassType.ITEM_FOCUSED -> ContextCompat.getColor(context, android.R.color.holo_blue_bright)
                GlassType.ITEM_MOVING -> ContextCompat.getColor(context, android.R.color.holo_orange_light)
                else -> ContextCompat.getColor(context, android.R.color.background_dark)
            }
            
            android.graphics.drawable.ColorDrawable(color)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating standard background", e)
            null
        }
    }

    private fun applyMinimalTextStyle(textView: TextView, textLevel: TextLevel) {
        val color = when (textLevel) {
            TextLevel.PRIMARY -> ContextCompat.getColor(context, android.R.color.white)
            TextLevel.SECONDARY -> ContextCompat.getColor(context, android.R.color.secondary_text_dark)
            TextLevel.TERTIARY -> ContextCompat.getColor(context, android.R.color.tertiary_text_dark)
            TextLevel.CAPTION -> ContextCompat.getColor(context, android.R.color.tertiary_text_dark)
        }
        
        textView.setTextColor(color)
        textView.textSize = when (textLevel) {
            TextLevel.PRIMARY -> 18f
            TextLevel.SECONDARY -> 16f
            TextLevel.TERTIARY -> 14f
            TextLevel.CAPTION -> 12f
        }
    }

    private fun applyStandardTextStyle(textView: TextView, textLevel: TextLevel) {
        val color = when (textLevel) {
            TextLevel.PRIMARY -> ContextCompat.getColor(context, android.R.color.primary_text_dark)
            TextLevel.SECONDARY -> ContextCompat.getColor(context, android.R.color.secondary_text_dark)
            TextLevel.TERTIARY -> ContextCompat.getColor(context, android.R.color.tertiary_text_dark)
            TextLevel.CAPTION -> ContextCompat.getColor(context, android.R.color.tertiary_text_dark)
        }
        
        textView.setTextColor(color)
        textView.textSize = when (textLevel) {
            TextLevel.PRIMARY -> 16f
            TextLevel.SECONDARY -> 14f
            TextLevel.TERTIARY -> 12f
            TextLevel.CAPTION -> 10f
        }
    }

    private fun retryGlassComponentWithFallback(
        componentName: String,
        retryCallback: () -> View?
    ): View? {
        return try {
            val attemptCount = glassRetryAttempts[componentName]?.get() ?: 0
            
            Log.d(TAG, "Retrying glass component for $componentName (attempt $attemptCount)")
            
            val result = when (attemptCount) {
                1 -> {
                    // First retry: degrade glass effects and try again
                    degradeGlassEffects()
                    retryCallback()
                }
                else -> {
                    // Final retry: use emergency fallback
                    createGlassComponentFallback(componentName)
                }
            }
            
            if (result != null) {
                Log.i(TAG, "Glass component recovery successful for $componentName")
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during glass component retry for $componentName", e)
            createGlassComponentFallback(componentName)
        }
    }

    private fun createGlassComponentFallback(componentName: String): View? {
        return when (componentName.lowercase()) {
            "glasscard" -> createFallbackGlassCard()
            "glassybackgroundview" -> createFallbackGlassBackground()
            "glassdialog" -> createFallbackGlassDialog()
            else -> {
                Log.w(TAG, "No glass fallback available for component: $componentName")
                createMinimalFallbackView()
            }
        }
    }

    private fun createFallbackGlassCard(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 12, 12, 12)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        }
    }

    private fun createFallbackGlassBackground(): View {
        return View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
            alpha = 0.8f
        }
    }

    private fun createFallbackGlassDialog(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
        }
    }

    private fun createMinimalFallbackView(): TextView {
        return TextView(context).apply {
            text = "Component unavailable"
            setTextColor(ContextCompat.getColor(context, android.R.color.secondary_text_dark))
            setPadding(8, 8, 8, 8)
        }
    }
}