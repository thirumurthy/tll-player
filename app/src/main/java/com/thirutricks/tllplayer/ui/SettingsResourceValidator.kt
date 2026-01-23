package com.thirutricks.tllplayer.ui

import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import com.thirutricks.tllplayer.ui.glass.GlassResourceValidator
import com.thirutricks.tllplayer.ui.glass.GlassErrorRecovery
import com.thirutricks.tllplayer.ui.glass.GlassComprehensiveValidation
import com.thirutricks.tllplayer.ui.glass.GlassValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Specialized resource validator for settings screen components
 * Validates glass resources, fragment lifecycle, and provides comprehensive error recovery
 */
class SettingsResourceValidator(
    private val context: Context,
    private val baseResourceValidator: ResourceValidator,
    private val glassResourceValidator: GlassResourceValidator,
    private val crashDiagnosticManager: CrashDiagnosticManager,
    private val glassErrorRecovery: GlassErrorRecovery
) {
    companion object {
        private const val TAG = "SettingsResourceValidator"
        
        // Settings-specific resources that must be validated
        private val SETTINGS_CRITICAL_RESOURCES = listOf(
            "setting", // Main settings layout
            "glass_card_preferences", // Preferences card layout
            "glass_card_configuration", // Configuration card layout
            "glass_card_actions", // Actions card layout
            "modern_toggle_thumb", // Toggle switch thumb
            "modern_toggle_track", // Toggle switch track
            "tv_input_glass", // Glass input styling
            "tv_button_glass" // Glass button styling
        )
        
        // Settings-specific components that need validation
        private val SETTINGS_COMPONENTS = listOf(
            "ModernToggleSwitch",
            "GlassCard", 
            "GlassyBackgroundView",
            "SettingsFragment"
        )
    }

    private val validationScope = CoroutineScope(Dispatchers.IO)

    /**
     * Comprehensive validation of all settings-related resources
     */
    suspend fun validateSettingsResources(): SettingsValidationResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting comprehensive settings resource validation")
        
        val startTime = System.currentTimeMillis()
        
        try {
            // Validate base resources
            val baseValidation = baseResourceValidator.generateValidationReport()
            
            // Validate glass resources
            val glassValidation = glassResourceValidator.validateAllGlassResources()
            
            // Validate settings-specific resources
            val settingsSpecificValidation = validateSettingsSpecificResources()
            
            // Validate fragment lifecycle safety
            val fragmentValidation = validateFragmentLifecycleSafety()
            
            // Determine overall settings readiness
            val overallReadiness = determineSettingsReadiness(
                baseValidation, glassValidation, settingsSpecificValidation, fragmentValidation
            )
            
            val validationTime = System.currentTimeMillis() - startTime
            
            val result = SettingsValidationResult(
                baseResourceValidation = baseValidation,
                glassResourceValidation = glassValidation,
                settingsSpecificValidation = settingsSpecificValidation,
                fragmentLifecycleValidation = fragmentValidation,
                overallReadiness = overallReadiness,
                validationTimeMs = validationTime,
                timestamp = System.currentTimeMillis(),
                canProceedNormally = overallReadiness.canProceedNormally,
                requiresFallback = overallReadiness.requiresFallback,
                recommendedAction = overallReadiness.recommendedAction
            )
            
            Log.i(TAG, "Settings validation completed in ${validationTime}ms - " +
                    "Readiness: ${overallReadiness.readinessLevel}, " +
                    "Can proceed: ${result.canProceedNormally}")
            
            // Log validation results for diagnostics
            crashDiagnosticManager.updateComponentState(
                "SettingsValidation",
                overallReadiness.readinessLevel.name,
                mapOf(
                    "canProceedNormally" to result.canProceedNormally,
                    "requiresFallback" to result.requiresFallback,
                    "validationTime" to validationTime,
                    "totalMissingResources" to (baseValidation.missingResources.size + 
                                               glassValidation.totalMissingResources + 
                                               settingsSpecificValidation.missingResources.size)
                )
            )
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during settings validation", e)
            crashDiagnosticManager.logCrashDetails(e, "settingsValidation")
            
            // Return emergency validation result
            createEmergencyValidationResult(System.currentTimeMillis() - startTime)
        }
    }

    /**
     * Validate fragment lifecycle safety for settings operations
     */
    fun validateFragmentLifecycleSafety(
        fragmentManager: FragmentManager? = null,
        fragment: Fragment? = null
    ): FragmentLifecycleValidation {
        Log.d(TAG, "Validating fragment lifecycle safety")
        
        return try {
            val isActivityFinishing = false // Would need to be passed from activity
            val isFragmentManagerValid = fragmentManager?.let { fm ->
                !fm.isDestroyed && !fm.isStateSaved
            } ?: true // Assume valid if not provided
            
            val isFragmentSafe = fragment?.let { f ->
                f.isAdded && !f.isRemoving && !f.isDetached &&
                f.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
            } ?: true // Assume safe if not provided
            
            val canPerformTransactions = isFragmentManagerValid && !isActivityFinishing
            val canAccessViews = isFragmentSafe && fragment?.view != null
            
            val safetyLevel = when {
                canPerformTransactions && canAccessViews -> FragmentSafetyLevel.SAFE
                canPerformTransactions -> FragmentSafetyLevel.TRANSACTION_SAFE
                isFragmentSafe -> FragmentSafetyLevel.VIEW_SAFE
                else -> FragmentSafetyLevel.UNSAFE
            }
            
            FragmentLifecycleValidation(
                isActivityFinishing = isActivityFinishing,
                isFragmentManagerValid = isFragmentManagerValid,
                isFragmentSafe = isFragmentSafe,
                canPerformTransactions = canPerformTransactions,
                canAccessViews = canAccessViews,
                safetyLevel = safetyLevel,
                timestamp = System.currentTimeMillis()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating fragment lifecycle", e)
            crashDiagnosticManager.logCrashDetails(e, "fragmentLifecycleValidation")
            
            FragmentLifecycleValidation(
                isActivityFinishing = true,
                isFragmentManagerValid = false,
                isFragmentSafe = false,
                canPerformTransactions = false,
                canAccessViews = false,
                safetyLevel = FragmentSafetyLevel.UNSAFE,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Pre-flight validation before showing settings fragment
     */
    suspend fun preFlightValidation(
        fragmentManager: FragmentManager,
        fragment: Fragment? = null
    ): PreFlightResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Performing pre-flight validation for settings")
        
        try {
            // Validate resources
            val resourceValidation = validateSettingsResources()
            
            // Validate fragment lifecycle
            val fragmentValidation = validateFragmentLifecycleSafety(fragmentManager, fragment)
            
            // Check glass effects availability
            val glassEffectsAvailable = glassResourceValidator.canUseGlassEffects()
            
            // Determine if we can proceed
            val canProceed = resourceValidation.canProceedNormally && 
                           fragmentValidation.canPerformTransactions
            
            val recommendedStrategy = when {
                canProceed && glassEffectsAvailable -> InitializationStrategy.FULL_GLASS_UI
                canProceed && !glassEffectsAvailable -> InitializationStrategy.FALLBACK_UI
                fragmentValidation.canPerformTransactions -> InitializationStrategy.EMERGENCY_UI
                else -> InitializationStrategy.ABORT
            }
            
            PreFlightResult(
                canProceed = canProceed,
                resourceValidation = resourceValidation,
                fragmentValidation = fragmentValidation,
                glassEffectsAvailable = glassEffectsAvailable,
                recommendedStrategy = recommendedStrategy,
                timestamp = System.currentTimeMillis()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during pre-flight validation", e)
            crashDiagnosticManager.logCrashDetails(e, "preFlightValidation")
            
            PreFlightResult(
                canProceed = false,
                resourceValidation = createEmergencyValidationResult(0),
                fragmentValidation = FragmentLifecycleValidation(
                    isActivityFinishing = true,
                    isFragmentManagerValid = false,
                    isFragmentSafe = false,
                    canPerformTransactions = false,
                    canAccessViews = false,
                    safetyLevel = FragmentSafetyLevel.UNSAFE,
                    timestamp = System.currentTimeMillis()
                ),
                glassEffectsAvailable = false,
                recommendedStrategy = InitializationStrategy.ABORT,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Validate settings-specific resources
     */
    private fun validateSettingsSpecificResources(): SettingsSpecificValidation {
        val missingLayouts = mutableListOf<String>()
        val missingDrawables = mutableListOf<String>()
        val availableResources = mutableListOf<String>()
        
        // Check critical layout resources
        SETTINGS_CRITICAL_RESOURCES.forEach { resourceName ->
            val layoutId = context.resources.getIdentifier(resourceName, "layout", context.packageName)
            val drawableId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
            
            when {
                layoutId != 0 -> {
                    availableResources.add("layout:$resourceName")
                }
                drawableId != 0 -> {
                    availableResources.add("drawable:$resourceName")
                }
                else -> {
                    // Try to determine if it should be a layout or drawable
                    if (resourceName.contains("card") || resourceName == "setting") {
                        missingLayouts.add(resourceName)
                    } else {
                        missingDrawables.add(resourceName)
                    }
                }
            }
        }
        
        return SettingsSpecificValidation(
            missingLayouts = missingLayouts,
            missingDrawables = missingDrawables,
            availableResources = availableResources,
            criticalResourcesAvailable = missingLayouts.isEmpty() && missingDrawables.isEmpty(),
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Determine overall settings readiness based on all validations
     */
    private fun determineSettingsReadiness(
        baseValidation: ValidationReport,
        glassValidation: GlassComprehensiveValidation,
        settingsValidation: SettingsSpecificValidation,
        fragmentValidation: FragmentLifecycleValidation
    ): SettingsReadiness {
        
        val totalMissingResources = baseValidation.missingResources.size + 
                                   glassValidation.totalMissingResources + 
                                   settingsValidation.missingResources.size
        
        val fragmentSafe = fragmentValidation.safetyLevel != FragmentSafetyLevel.UNSAFE
        val criticalResourcesAvailable = settingsValidation.criticalResourcesAvailable
        val glassEffectsSupported = glassValidation.glassEffectsSupported
        
        val readinessLevel = when {
            fragmentSafe && criticalResourcesAvailable && totalMissingResources == 0 -> {
                SettingsReadinessLevel.FULLY_READY
            }
            fragmentSafe && criticalResourcesAvailable && totalMissingResources <= 5 -> {
                SettingsReadinessLevel.READY_WITH_FALLBACKS
            }
            fragmentSafe && totalMissingResources <= 15 -> {
                SettingsReadinessLevel.EMERGENCY_READY
            }
            fragmentSafe -> {
                SettingsReadinessLevel.MINIMAL_READY
            }
            else -> {
                SettingsReadinessLevel.NOT_READY
            }
        }
        
        val canProceedNormally = readinessLevel in listOf(
            SettingsReadinessLevel.FULLY_READY,
            SettingsReadinessLevel.READY_WITH_FALLBACKS
        )
        
        val requiresFallback = readinessLevel in listOf(
            SettingsReadinessLevel.READY_WITH_FALLBACKS,
            SettingsReadinessLevel.EMERGENCY_READY,
            SettingsReadinessLevel.MINIMAL_READY
        )
        
        val recommendedAction = when (readinessLevel) {
            SettingsReadinessLevel.FULLY_READY -> RecoveryAction.PROCEED_NORMAL
            SettingsReadinessLevel.READY_WITH_FALLBACKS -> RecoveryAction.USE_FALLBACK_UI
            SettingsReadinessLevel.EMERGENCY_READY -> RecoveryAction.USE_EMERGENCY_UI
            SettingsReadinessLevel.MINIMAL_READY -> RecoveryAction.USE_EMERGENCY_UI
            SettingsReadinessLevel.NOT_READY -> RecoveryAction.ABORT_WITH_ERROR
        }
        
        return SettingsReadiness(
            readinessLevel = readinessLevel,
            canProceedNormally = canProceedNormally,
            requiresFallback = requiresFallback,
            recommendedAction = recommendedAction,
            totalMissingResources = totalMissingResources,
            criticalResourcesAvailable = criticalResourcesAvailable,
            fragmentSafe = fragmentSafe,
            glassEffectsSupported = glassEffectsSupported
        )
    }

    /**
     * Create emergency validation result when validation itself fails
     */
    private fun createEmergencyValidationResult(validationTime: Long): SettingsValidationResult {
        val emergencyBaseValidation = ValidationReport(
            allResourcesAvailable = false,
            missingResources = listOf("validation_failed"),
            fallbacksRequired = emptyMap(),
            recommendedAction = RecoveryAction.ABORT_WITH_ERROR
        )
        
        val emergencyGlassValidation = GlassComprehensiveValidation(
            drawableValidation = GlassValidationResult("drawables", 0, emptyList(), listOf("validation_failed"), 0),
            colorValidation = GlassValidationResult("colors", 0, emptyList(), listOf("validation_failed"), 0),
            dimensionValidation = GlassValidationResult("dimensions", 0, emptyList(), listOf("validation_failed"), 0),
            totalMissingResources = 1,
            totalFallbacksAvailable = 0,
            glassEffectsSupported = false,
            recommendedGlassLevel = GlassErrorRecovery.Companion.GlassFallbackLevel.NO_GLASS,
            validationTimeMs = validationTime,
            timestamp = System.currentTimeMillis()
        )
        
        val emergencySettingsValidation = SettingsSpecificValidation(
            missingLayouts = listOf("validation_failed"),
            missingDrawables = listOf("validation_failed"),
            availableResources = emptyList(),
            criticalResourcesAvailable = false,
            timestamp = System.currentTimeMillis()
        )
        
        val emergencyFragmentValidation = FragmentLifecycleValidation(
            isActivityFinishing = true,
            isFragmentManagerValid = false,
            isFragmentSafe = false,
            canPerformTransactions = false,
            canAccessViews = false,
            safetyLevel = FragmentSafetyLevel.UNSAFE,
            timestamp = System.currentTimeMillis()
        )
        
        val emergencyReadiness = SettingsReadiness(
            readinessLevel = SettingsReadinessLevel.NOT_READY,
            canProceedNormally = false,
            requiresFallback = false,
            recommendedAction = RecoveryAction.ABORT_WITH_ERROR,
            totalMissingResources = 3,
            criticalResourcesAvailable = false,
            fragmentSafe = false,
            glassEffectsSupported = false
        )
        
        return SettingsValidationResult(
            baseResourceValidation = emergencyBaseValidation,
            glassResourceValidation = emergencyGlassValidation,
            settingsSpecificValidation = emergencySettingsValidation,
            fragmentLifecycleValidation = emergencyFragmentValidation,
            overallReadiness = emergencyReadiness,
            validationTimeMs = validationTime,
            timestamp = System.currentTimeMillis(),
            canProceedNormally = false,
            requiresFallback = false,
            recommendedAction = RecoveryAction.ABORT_WITH_ERROR
        )
    }
}

/**
 * Data classes for settings validation results
 */

data class SettingsValidationResult(
    val baseResourceValidation: ValidationReport,
    val glassResourceValidation: GlassComprehensiveValidation,
    val settingsSpecificValidation: SettingsSpecificValidation,
    val fragmentLifecycleValidation: FragmentLifecycleValidation,
    val overallReadiness: SettingsReadiness,
    val validationTimeMs: Long,
    val timestamp: Long,
    val canProceedNormally: Boolean,
    val requiresFallback: Boolean,
    val recommendedAction: RecoveryAction
) {
    val totalMissingResources: Int
        get() = baseResourceValidation.missingResources.size + 
                glassResourceValidation.totalMissingResources + 
                settingsSpecificValidation.missingResources.size
}

data class SettingsSpecificValidation(
    val missingLayouts: List<String>,
    val missingDrawables: List<String>,
    val availableResources: List<String>,
    val criticalResourcesAvailable: Boolean,
    val timestamp: Long
) {
    val missingResources: List<String>
        get() = missingLayouts + missingDrawables
}

data class FragmentLifecycleValidation(
    val isActivityFinishing: Boolean,
    val isFragmentManagerValid: Boolean,
    val isFragmentSafe: Boolean,
    val canPerformTransactions: Boolean,
    val canAccessViews: Boolean,
    val safetyLevel: FragmentSafetyLevel,
    val timestamp: Long
)

data class SettingsReadiness(
    val readinessLevel: SettingsReadinessLevel,
    val canProceedNormally: Boolean,
    val requiresFallback: Boolean,
    val recommendedAction: RecoveryAction,
    val totalMissingResources: Int,
    val criticalResourcesAvailable: Boolean,
    val fragmentSafe: Boolean,
    val glassEffectsSupported: Boolean
)

data class PreFlightResult(
    val canProceed: Boolean,
    val resourceValidation: SettingsValidationResult,
    val fragmentValidation: FragmentLifecycleValidation,
    val glassEffectsAvailable: Boolean,
    val recommendedStrategy: InitializationStrategy,
    val timestamp: Long
)

/**
 * Enums for settings validation
 */

enum class FragmentSafetyLevel {
    SAFE,               // All operations are safe
    TRANSACTION_SAFE,   // Fragment transactions are safe
    VIEW_SAFE,          // View access is safe but no transactions
    UNSAFE              // No operations are safe
}

enum class SettingsReadinessLevel {
    FULLY_READY,        // All resources available, can use full UI
    READY_WITH_FALLBACKS, // Some resources missing, can use fallback UI
    EMERGENCY_READY,    // Many resources missing, can use emergency UI
    MINIMAL_READY,      // Critical resources missing, minimal UI only
    NOT_READY           // Cannot proceed safely
}

enum class InitializationStrategy {
    FULL_GLASS_UI,      // Use full glassmorphism UI
    FALLBACK_UI,        // Use fallback UI with standard components
    EMERGENCY_UI,       // Use emergency minimal UI
    ABORT               // Cannot initialize safely
}