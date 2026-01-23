package com.thirutricks.tllplayer.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R
import com.thirutricks.tllplayer.ui.glass.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive fallback mechanism manager for all UI components
 * Provides progressive degradation and recovery strategies
 */
class FallbackMechanismManager(
    private val context: Context,
    private val resourceValidator: ResourceValidator,
    private val glassResourceValidator: GlassResourceValidator,
    private val crashDiagnosticManager: CrashDiagnosticManager,
    private val glassErrorRecovery: GlassErrorRecovery
) {
    companion object {
        private const val TAG = "FallbackMechanismManager"
        private const val MAX_GLOBAL_RETRY_ATTEMPTS = 3
        
        // Component fallback priorities (higher = more critical)
        private val COMPONENT_PRIORITIES = mapOf(
            "SettingsFragment" to 10,
            "ModernToggleSwitch" to 8,
            "GlassCard" to 7,
            "GlassDialog" to 6,
            "GlassyBackgroundView" to 5,
            "MenuFragment" to 9,
            "ListAdapter" to 8,
            "GroupAdapter" to 8
        )
    }

    private val globalRetryAttempts = ConcurrentHashMap<String, AtomicInteger>()
    private val componentFallbackStates = ConcurrentHashMap<String, FallbackState>()
    private val fallbackScope = CoroutineScope(Dispatchers.Main)
    private var systemFallbackLevel = SystemFallbackLevel.NORMAL

    /**
     * System-wide fallback levels
     */
    enum class SystemFallbackLevel {
        NORMAL,         // All components working normally
        DEGRADED,       // Some components using fallbacks
        EMERGENCY,      // Most components using emergency fallbacks
        CRITICAL        // System in critical state, minimal functionality
    }

    /**
     * Component fallback state tracking
     */
    data class FallbackState(
        val componentName: String,
        val currentLevel: ComponentFallbackLevel,
        val lastError: String?,
        val retryCount: Int,
        val timestamp: Long,
        val isRecoverable: Boolean
    )

    /**
     * Individual component fallback levels
     */
    enum class ComponentFallbackLevel {
        NORMAL,         // Component working normally
        REDUCED,        // Component using reduced functionality
        FALLBACK,       // Component using fallback implementation
        EMERGENCY,      // Component using emergency implementation
        FAILED          // Component completely failed
    }

    /**
     * Initialize fallback mechanism with system validation
     */
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "Initializing fallback mechanism manager")
            
            // Validate system resources
            val resourceValidation = resourceValidator.generateValidationReport()
            val glassValidation = glassResourceValidator.validateAllGlassResources()
            
            // Determine initial system state
            systemFallbackLevel = determineInitialSystemState(resourceValidation, glassValidation)
            
            Log.i(TAG, "Fallback mechanism initialized - System level: $systemFallbackLevel")
            
            // Log initial state
            crashDiagnosticManager.updateComponentState(
                "FallbackSystem",
                systemFallbackLevel.name,
                mapOf(
                    "resourcesAvailable" to resourceValidation.allResourcesAvailable,
                    "glassEffectsSupported" to glassValidation.glassEffectsSupported,
                    "totalMissingResources" to (resourceValidation.missingResources.size + glassValidation.totalMissingResources)
                )
            )
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize fallback mechanism", e)
            crashDiagnosticManager.logCrashDetails(e, "fallbackInitialization")
            false
        }
    }

    /**
     * Handle component failure with progressive fallback
     */
    fun handleComponentFailure(
        componentName: String,
        error: Exception,
        originalComponent: View? = null,
        retryCallback: (() -> View?)? = null
    ): View? {
        Log.w(TAG, "Handling component failure for: $componentName", error)
        
        crashDiagnosticManager.logCrashDetails(error, "componentFailure", componentName)
        
        val currentState = componentFallbackStates[componentName]
        val retryCount = globalRetryAttempts.computeIfAbsent(componentName) { AtomicInteger(0) }
        val currentAttempt = retryCount.incrementAndGet()
        
        // Update component state
        val newFallbackLevel = determineNextFallbackLevel(currentState?.currentLevel ?: ComponentFallbackLevel.NORMAL)
        updateComponentFallbackState(componentName, newFallbackLevel, error.message, currentAttempt)
        
        return when {
            currentAttempt <= MAX_GLOBAL_RETRY_ATTEMPTS && retryCallback != null -> {
                Log.i(TAG, "Attempting recovery for $componentName (attempt $currentAttempt/$MAX_GLOBAL_RETRY_ATTEMPTS)")
                attemptComponentRecovery(componentName, newFallbackLevel, retryCallback)
            }
            else -> {
                Log.w(TAG, "Max retry attempts reached for $componentName, creating fallback")
                createComponentFallback(componentName, newFallbackLevel, originalComponent)
            }
        }
    }

    /**
     * Create appropriate fallback for failed component
     */
    fun createComponentFallback(
        componentName: String,
        fallbackLevel: ComponentFallbackLevel,
        originalComponent: View? = null
    ): View? {
        return try {
            Log.d(TAG, "Creating fallback for $componentName at level $fallbackLevel")
            
            val fallback = when (componentName.lowercase()) {
                "moderntoggleswitch" -> createToggleSwitchFallback(fallbackLevel, originalComponent as? ModernToggleSwitch)
                "glasscard" -> createGlassCardFallback(fallbackLevel, originalComponent as? GlassCard)
                "glassybackgroundview" -> createGlassBackgroundFallback(fallbackLevel)
                "glassdialog" -> createGlassDialogFallback(fallbackLevel)
                "settingsfragment" -> createSettingsFragmentFallback(fallbackLevel)
                "menucontainer" -> createMenuContainerFallback(fallbackLevel)
                else -> createGenericComponentFallback(componentName, fallbackLevel)
            }
            
            if (fallback != null) {
                Log.i(TAG, "Successfully created fallback for $componentName")
                updateSystemFallbackLevel()
            } else {
                Log.e(TAG, "Failed to create fallback for $componentName")
            }
            
            fallback
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback for $componentName", e)
            crashDiagnosticManager.logCrashDetails(e, "fallbackCreation", componentName)
            createEmergencyFallback(componentName)
        }
    }

    /**
     * Attempt to recover component with progressive strategies
     */
    fun attemptComponentRecovery(
        componentName: String,
        targetLevel: ComponentFallbackLevel,
        retryCallback: () -> View?
    ): View? {
        return try {
            val attemptCount = globalRetryAttempts[componentName]?.get() ?: 0
            
            Log.d(TAG, "Attempting recovery for $componentName (level: $targetLevel, attempt: $attemptCount)")
            
            val result = when (targetLevel) {
                ComponentFallbackLevel.NORMAL -> {
                    // Try normal initialization
                    retryCallback()
                }
                ComponentFallbackLevel.REDUCED -> {
                    // Try with reduced functionality
                    attemptReducedRecovery(componentName, retryCallback)
                }
                ComponentFallbackLevel.FALLBACK -> {
                    // Use fallback implementation
                    createComponentFallback(componentName, ComponentFallbackLevel.FALLBACK)
                }
                ComponentFallbackLevel.EMERGENCY -> {
                    // Use emergency implementation
                    createComponentFallback(componentName, ComponentFallbackLevel.EMERGENCY)
                }
                ComponentFallbackLevel.FAILED -> {
                    // Component has failed completely
                    null
                }
            }
            
            if (result != null) {
                Log.i(TAG, "Recovery successful for $componentName at level $targetLevel")
                updateComponentFallbackState(componentName, targetLevel, null, attemptCount, isRecoverable = true)
            } else {
                Log.w(TAG, "Recovery failed for $componentName at level $targetLevel")
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during recovery attempt for $componentName", e)
            crashDiagnosticManager.logCrashDetails(e, "recoveryAttempt", componentName)
            null
        }
    }

    /**
     * Get current system fallback status
     */
    fun getSystemFallbackStatus(): SystemFallbackStatus {
        val componentStates = componentFallbackStates.values.toList()
        val totalComponents = componentStates.size
        val failedComponents = componentStates.count { it.currentLevel == ComponentFallbackLevel.FAILED }
        val degradedComponents = componentStates.count { 
            it.currentLevel in listOf(ComponentFallbackLevel.REDUCED, ComponentFallbackLevel.FALLBACK, ComponentFallbackLevel.EMERGENCY)
        }
        
        return SystemFallbackStatus(
            systemLevel = systemFallbackLevel,
            totalComponents = totalComponents,
            normalComponents = totalComponents - degradedComponents - failedComponents,
            degradedComponents = degradedComponents,
            failedComponents = failedComponents,
            componentStates = componentStates,
            canRecover = componentStates.any { it.isRecoverable }
        )
    }

    /**
     * Attempt system-wide recovery
     */
    fun attemptSystemRecovery(): Boolean {
        return try {
            Log.i(TAG, "Attempting system-wide recovery")
            
            val recoverableComponents = componentFallbackStates.values.filter { it.isRecoverable }
            var recoveredCount = 0
            
            recoverableComponents.forEach { state ->
                try {
                    // Reset retry count for recovery attempt
                    globalRetryAttempts[state.componentName]?.set(0)
                    
                    // Attempt to recover to normal state
                    val recovered = attemptComponentRecovery(
                        state.componentName,
                        ComponentFallbackLevel.NORMAL
                    ) { null } // No retry callback for system recovery
                    
                    if (recovered != null) {
                        recoveredCount++
                        Log.d(TAG, "Recovered component: ${state.componentName}")
                    }
                    
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to recover component: ${state.componentName}", e)
                }
            }
            
            // Update system state
            updateSystemFallbackLevel()
            
            val recoverySuccess = recoveredCount > 0
            Log.i(TAG, "System recovery completed - Recovered: $recoveredCount/${recoverableComponents.size} components")
            
            recoverySuccess
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during system recovery", e)
            crashDiagnosticManager.logCrashDetails(e, "systemRecovery")
            false
        }
    }

    // Private helper methods

    private fun determineInitialSystemState(
        resourceValidation: ValidationReport,
        glassValidation: GlassComprehensiveValidation
    ): SystemFallbackLevel {
        val totalMissingResources = resourceValidation.missingResources.size + glassValidation.totalMissingResources
        
        return when {
            totalMissingResources == 0 -> SystemFallbackLevel.NORMAL
            totalMissingResources <= 5 -> SystemFallbackLevel.DEGRADED
            totalMissingResources <= 15 -> SystemFallbackLevel.EMERGENCY
            else -> SystemFallbackLevel.CRITICAL
        }
    }

    private fun determineNextFallbackLevel(currentLevel: ComponentFallbackLevel): ComponentFallbackLevel {
        return when (currentLevel) {
            ComponentFallbackLevel.NORMAL -> ComponentFallbackLevel.REDUCED
            ComponentFallbackLevel.REDUCED -> ComponentFallbackLevel.FALLBACK
            ComponentFallbackLevel.FALLBACK -> ComponentFallbackLevel.EMERGENCY
            ComponentFallbackLevel.EMERGENCY -> ComponentFallbackLevel.FAILED
            ComponentFallbackLevel.FAILED -> ComponentFallbackLevel.FAILED
        }
    }

    private fun updateComponentFallbackState(
        componentName: String,
        level: ComponentFallbackLevel,
        error: String? = null,
        retryCount: Int = 0,
        isRecoverable: Boolean = level != ComponentFallbackLevel.FAILED
    ) {
        val state = FallbackState(
            componentName = componentName,
            currentLevel = level,
            lastError = error,
            retryCount = retryCount,
            timestamp = System.currentTimeMillis(),
            isRecoverable = isRecoverable
        )
        
        componentFallbackStates[componentName] = state
        
        crashDiagnosticManager.updateComponentState(
            componentName,
            level.name,
            mapOf(
                "error" to (error ?: "none"),
                "retryCount" to retryCount,
                "isRecoverable" to isRecoverable
            )
        )
    }

    private fun updateSystemFallbackLevel() {
        val componentStates = componentFallbackStates.values
        val failedCount = componentStates.count { it.currentLevel == ComponentFallbackLevel.FAILED }
        val emergencyCount = componentStates.count { it.currentLevel == ComponentFallbackLevel.EMERGENCY }
        val fallbackCount = componentStates.count { it.currentLevel == ComponentFallbackLevel.FALLBACK }
        val totalComponents = componentStates.size
        
        val newLevel = when {
            totalComponents == 0 -> SystemFallbackLevel.NORMAL
            failedCount > totalComponents * 0.5 -> SystemFallbackLevel.CRITICAL
            (failedCount + emergencyCount) > totalComponents * 0.3 -> SystemFallbackLevel.EMERGENCY
            (failedCount + emergencyCount + fallbackCount) > totalComponents * 0.1 -> SystemFallbackLevel.DEGRADED
            else -> SystemFallbackLevel.NORMAL
        }
        
        if (newLevel != systemFallbackLevel) {
            val previousLevel = systemFallbackLevel
            systemFallbackLevel = newLevel
            Log.i(TAG, "System fallback level changed from $previousLevel to $newLevel")
            
            crashDiagnosticManager.updateComponentState(
                "FallbackSystem",
                newLevel.name,
                mapOf(
                    "previousLevel" to previousLevel.name,
                    "failedComponents" to failedCount,
                    "emergencyComponents" to emergencyCount,
                    "fallbackComponents" to fallbackCount,
                    "totalComponents" to totalComponents
                )
            )
        }
    }

    private fun attemptReducedRecovery(componentName: String, retryCallback: () -> View?): View? {
        return try {
            // For glass components, try with reduced glass effects
            if (componentName.lowercase().contains("glass")) {
                glassErrorRecovery.degradeGlassEffects()
            }
            
            retryCallback()
            
        } catch (e: Exception) {
            Log.w(TAG, "Reduced recovery failed for $componentName", e)
            null
        }
    }

    private fun createToggleSwitchFallback(
        level: ComponentFallbackLevel,
        original: ModernToggleSwitch?
    ): View? {
        return when (level) {
            ComponentFallbackLevel.FALLBACK -> {
                Switch(context).apply {
                    isChecked = original?.isChecked ?: false
                    isEnabled = original?.isEnabled ?: true
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
            }
            ComponentFallbackLevel.EMERGENCY -> {
                Switch(context).apply {
                    isChecked = original?.isChecked ?: false
                    text = "Toggle"
                }
            }
            else -> null
        }
    }

    private fun createGlassCardFallback(
        level: ComponentFallbackLevel,
        original: GlassCard?
    ): View? {
        return when (level) {
            ComponentFallbackLevel.FALLBACK -> {
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(12, 12, 12, 12)
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    
                    // Copy children from original if available
                    original?.let { originalCard ->
                        for (i in 0 until originalCard.childCount) {
                            val child = originalCard.getChildAt(i)
                            originalCard.removeView(child)
                            addView(child)
                        }
                    }
                }
            }
            ComponentFallbackLevel.EMERGENCY -> {
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8)
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
                }
            }
            else -> null
        }
    }

    private fun createGlassBackgroundFallback(level: ComponentFallbackLevel): View? {
        return when (level) {
            ComponentFallbackLevel.FALLBACK -> {
                View(context).apply {
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                    alpha = 0.8f
                }
            }
            ComponentFallbackLevel.EMERGENCY -> {
                View(context).apply {
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
                }
            }
            else -> null
        }
    }

    private fun createGlassDialogFallback(level: ComponentFallbackLevel): View? {
        return when (level) {
            ComponentFallbackLevel.FALLBACK -> {
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                }
            }
            ComponentFallbackLevel.EMERGENCY -> {
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8)
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
                }
            }
            else -> null
        }
    }

    private fun createSettingsFragmentFallback(level: ComponentFallbackLevel): View? {
        return when (level) {
            ComponentFallbackLevel.FALLBACK -> {
                // Use the existing SettingsErrorRecovery emergency UI
                SettingsErrorRecovery(context, resourceValidator, crashDiagnosticManager)
                    .createEmergencySettingsUI()
            }
            ComponentFallbackLevel.EMERGENCY -> {
                TextView(context).apply {
                    text = "Settings unavailable. Press BACK to return."
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    setPadding(32, 32, 32, 32)
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
                }
            }
            else -> null
        }
    }

    private fun createMenuContainerFallback(level: ComponentFallbackLevel): View? {
        return when (level) {
            ComponentFallbackLevel.FALLBACK -> {
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
                }
            }
            ComponentFallbackLevel.EMERGENCY -> {
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8)
                    setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))
                }
            }
            else -> null
        }
    }

    private fun createGenericComponentFallback(componentName: String, level: ComponentFallbackLevel): View? {
        return when (level) {
            ComponentFallbackLevel.FALLBACK -> {
                TextView(context).apply {
                    text = "$componentName (Fallback)"
                    setTextColor(ContextCompat.getColor(context, android.R.color.secondary_text_dark))
                    setPadding(8, 8, 8, 8)
                }
            }
            ComponentFallbackLevel.EMERGENCY -> {
                TextView(context).apply {
                    text = "Component unavailable"
                    setTextColor(ContextCompat.getColor(context, android.R.color.tertiary_text_dark))
                    setPadding(4, 4, 4, 4)
                }
            }
            else -> null
        }
    }

    private fun createEmergencyFallback(componentName: String): View {
        return TextView(context).apply {
            text = "Error: $componentName failed"
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
            setPadding(8, 8, 8, 8)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
        }
    }
}

/**
 * Data class for system fallback status
 */
data class SystemFallbackStatus(
    val systemLevel: FallbackMechanismManager.SystemFallbackLevel,
    val totalComponents: Int,
    val normalComponents: Int,
    val degradedComponents: Int,
    val failedComponents: Int,
    val componentStates: List<FallbackMechanismManager.FallbackState>,
    val canRecover: Boolean
) {
    val healthPercentage: Float
        get() = if (totalComponents > 0) {
            (normalComponents.toFloat() / totalComponents.toFloat()) * 100f
        } else 100f
}