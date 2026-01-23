package com.thirutricks.tllplayer.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R

/**
 * Manages resource validation and fallback mechanisms for settings UI components
 * Provides pre-flight checks and safe initialization of custom UI elements
 */
class SettingsResourceManager(private val context: Context) {

    companion object {
        private const val TAG = "SettingsResourceManager"
    }

    private val resourceValidator = ResourceValidator(context)
    private var validationReport: ValidationReport? = null
    private var isInitialized = false

    /**
     * Perform comprehensive pre-flight validation of all settings resources
     */
    fun performPreFlightValidation(): Boolean {
        try {
            Log.i(TAG, "Starting pre-flight resource validation")
            
            validationReport = resourceValidator.generateValidationReport()
            
            val report = validationReport!!
            Log.i(TAG, "Validation completed - All resources available: ${report.allResourcesAvailable}")
            
            if (!report.allResourcesAvailable) {
                Log.w(TAG, "Missing resources detected: ${report.missingResources}")
                Log.i(TAG, "Recommended action: ${report.recommendedAction}")
                
                if (report.fallbacksRequired.isNotEmpty()) {
                    Log.i(TAG, "Fallbacks available for: ${report.fallbacksRequired.keys}")
                }
            }
            
            isInitialized = true
            return report.recommendedAction != RecoveryAction.ABORT_WITH_ERROR
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during pre-flight validation", e)
            return false
        }
    }

    /**
     * Get the current validation report
     */
    fun getValidationReport(): ValidationReport? = validationReport

    /**
     * Check if the resource manager is properly initialized
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * Safely initialize ModernToggleSwitch components in a ViewGroup
     */
    fun safeInitializeToggleSwitches(rootView: ViewGroup, tvUiUtils: TvUiUtils?): Int {
        if (!isInitialized) {
            Log.w(TAG, "Resource manager not initialized, performing validation first")
            if (!performPreFlightValidation()) {
                Log.e(TAG, "Pre-flight validation failed, cannot initialize toggle switches")
                return 0
            }
        }

        var initializedCount = 0
        
        try {
            val toggleSwitches = findToggleSwitches(rootView)
            Log.i(TAG, "Found ${toggleSwitches.size} toggle switches to initialize")
            
            toggleSwitches.forEach { switch ->
                try {
                    when (switch) {
                        is ModernToggleSwitch -> {
                            // Initialize with audio feedback if available
                            tvUiUtils?.let { utils ->
                                switch.initializeWithAudio(utils)
                            }
                            
                            // Log initialization status
                            val fallbackMode = switch.isInFallbackMode()
                            Log.d(TAG, "ModernToggleSwitch initialized (fallback mode: $fallbackMode)")
                            initializedCount++
                        }
                        is Switch -> {
                            // Handle standard Android Switch as fallback
                            setupBasicSwitchFallback(switch)
                            Log.d(TAG, "Standard Switch configured as fallback")
                            initializedCount++
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing individual toggle switch", e)
                    // Continue with other switches
                }
            }
            
            Log.i(TAG, "Successfully initialized $initializedCount toggle switches")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during toggle switch initialization", e)
        }
        
        return initializedCount
    }

    /**
     * Create fallback UI when custom components cannot be initialized
     */
    fun createFallbackToggleSwitch(context: Context): Switch {
        return try {
            val report = validationReport
            
            when (report?.recommendedAction) {
                RecoveryAction.USE_FALLBACK_UI -> {
                    Log.i(TAG, "Creating fallback ModernToggleSwitch")
                    ModernToggleSwitch(context)
                }
                else -> {
                    Log.i(TAG, "Creating basic Android Switch fallback")
                    Switch(context).apply {
                        setupBasicSwitchFallback(this)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating fallback toggle switch", e)
            Switch(context).apply {
                setupBasicSwitchFallback(this)
            }
        }
    }

    /**
     * Setup basic switch with minimal styling as ultimate fallback
     */
    private fun setupBasicSwitchFallback(switch: Switch) {
        try {
            // Basic TV navigation setup
            switch.isFocusable = true
            switch.isFocusableInTouchMode = true
            
            // Set minimum touch target for TV
            switch.minWidth = 48 * context.resources.displayMetrics.density.toInt()
            switch.minHeight = 48 * context.resources.displayMetrics.density.toInt()
            
            // Basic padding
            val padding = (8 * context.resources.displayMetrics.density).toInt()
            switch.setPadding(padding, padding, padding, padding)
            
            // Try to set basic colors
            try {
                switch.setTextColor(ContextCompat.getColor(context, android.R.color.primary_text_dark))
            } catch (e: Exception) {
                Log.w(TAG, "Could not set text color for basic switch", e)
            }
            
            // Basic focus handling
            switch.setOnFocusChangeListener { view, hasFocus ->
                try {
                    val scale = if (hasFocus) 1.05f else 1.0f
                    view.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .setDuration(200)
                        .start()
                } catch (e: Exception) {
                    Log.w(TAG, "Error in basic focus animation", e)
                }
            }
            
            Log.d(TAG, "Basic switch fallback configured")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up basic switch fallback", e)
        }
    }

    /**
     * Find all toggle switches in a ViewGroup recursively
     */
    private fun findToggleSwitches(viewGroup: ViewGroup): List<Switch> {
        val switches = mutableListOf<Switch>()
        
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            
            when (child) {
                is Switch -> switches.add(child)
                is ViewGroup -> switches.addAll(findToggleSwitches(child))
            }
        }
        
        return switches
    }

    /**
     * Validate that all critical settings resources are available
     */
    fun validateCriticalResources(): Boolean {
        return try {
            val report = validationReport ?: resourceValidator.generateValidationReport()
            
            // Check if we can at least create basic UI
            report.recommendedAction != RecoveryAction.ABORT_WITH_ERROR
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating critical resources", e)
            false
        }
    }

    /**
     * Get diagnostic information for troubleshooting
     */
    fun getDiagnosticInfo(): Map<String, Any> {
        return try {
            val report = validationReport ?: resourceValidator.generateValidationReport()
            
            mapOf(
                "initialized" to isInitialized,
                "allResourcesAvailable" to report.allResourcesAvailable,
                "missingResourceCount" to report.missingResources.size,
                "missingResources" to report.missingResources,
                "recommendedAction" to report.recommendedAction.name,
                "fallbacksAvailable" to report.fallbacksRequired.size
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error generating diagnostic info", e)
            mapOf(
                "error" to (e.message ?: "Unknown error"),
                "initialized" to isInitialized
            )
        }
    }

    /**
     * Log comprehensive diagnostic information
     */
    fun logDiagnostics() {
        try {
            val diagnostics = getDiagnosticInfo()
            Log.i(TAG, "=== Settings Resource Manager Diagnostics ===")
            diagnostics.forEach { (key, value) ->
                Log.i(TAG, "$key: $value")
            }
            Log.i(TAG, "============================================")
        } catch (e: Exception) {
            Log.e(TAG, "Error logging diagnostics", e)
        }
    }
}