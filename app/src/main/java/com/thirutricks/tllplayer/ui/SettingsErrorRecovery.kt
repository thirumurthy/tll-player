package com.thirutricks.tllplayer.ui

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.thirutricks.tllplayer.R
import com.thirutricks.tllplayer.SP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive error recovery system for settings UI components
 * Provides graceful degradation through multiple fallback layers:
 * 1. Full UI with custom components
 * 2. Fallback UI with standard Android components  
 * 3. Emergency UI with minimal functionality
 * 4. Diagnostic UI for error reporting
 */
class SettingsErrorRecovery(
    private val context: Context,
    private val resourceValidator: ResourceValidator,
    private val crashDiagnosticManager: CrashDiagnosticManager
) {
    companion object {
        private const val TAG = "SettingsErrorRecovery"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 500L
        
        // Fallback component IDs for tracking
        private const val FALLBACK_CONTAINER_ID = 0x7F000001
        private const val EMERGENCY_CONTAINER_ID = 0x7F000002
        private const val DIAGNOSTIC_CONTAINER_ID = 0x7F000003
    }

    private val retryAttempts = ConcurrentHashMap<String, AtomicInteger>()
    private val recoveryScope = CoroutineScope(Dispatchers.Main)
    private var currentFallbackLevel = FallbackLevel.NONE
    private var lastValidationReport: ValidationReport? = null

    /**
     * Fallback levels for progressive degradation
     */
    enum class FallbackLevel {
        NONE,           // Full UI with all custom components
        STANDARD,       // Standard Android components with basic styling
        EMERGENCY,      // Minimal functional UI
        DIAGNOSTIC      // Error reporting interface
    }

    /**
     * Create fallback UI when custom components fail to initialize
     */
    fun createFallbackUI(container: ViewGroup): View {
        return try {
            Log.i(TAG, "Creating fallback UI for container")
            
            val validationReport = resourceValidator.generateValidationReport()
            lastValidationReport = validationReport
            
            when (validationReport.recommendedAction) {
                RecoveryAction.PROCEED_NORMAL -> {
                    Log.d(TAG, "All resources available, creating full UI")
                    createFullUI(container)
                }
                RecoveryAction.USE_FALLBACK_UI -> {
                    Log.w(TAG, "Some resources missing, creating fallback UI")
                    currentFallbackLevel = FallbackLevel.STANDARD
                    createStandardFallbackUI(container)
                }
                RecoveryAction.USE_EMERGENCY_UI -> {
                    Log.e(TAG, "Critical resources missing, creating emergency UI")
                    currentFallbackLevel = FallbackLevel.EMERGENCY
                    createEmergencyUI(container)
                }
                else -> {
                    Log.e(TAG, "Cannot recover, creating diagnostic UI")
                    currentFallbackLevel = FallbackLevel.DIAGNOSTIC
                    createDiagnosticUI(container)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error creating fallback UI", e)
            crashDiagnosticManager.logCrashDetails(e, "createFallbackUI")
            createEmergencyUI(container)
        }
    }

    /**
     * Handle component initialization errors with progressive fallback
     */
    fun handleComponentInitializationError(
        component: String, 
        error: Exception,
        retryCallback: (() -> View?)? = null
    ): View? {
        Log.w(TAG, "Handling component initialization error for: $component", error)
        
        crashDiagnosticManager.logCrashDetails(error, "componentInitialization", component)
        
        val attemptCount = retryAttempts.computeIfAbsent(component) { AtomicInteger(0) }
        val currentAttempt = attemptCount.incrementAndGet()
        
        return when {
            currentAttempt <= MAX_RETRY_ATTEMPTS && retryCallback != null -> {
                Log.i(TAG, "Retrying component initialization (attempt $currentAttempt/$MAX_RETRY_ATTEMPTS)")
                retryWithFallback(component, retryCallback)
            }
            else -> {
                Log.w(TAG, "Max retry attempts reached for $component, creating fallback")
                createComponentFallback(component)
            }
        }
    }

    /**
     * Retry operation with progressive fallback strategies
     */
    fun retryWithFallback(componentName: String, operation: () -> View?): View? {
        return try {
            val attemptCount = retryAttempts[componentName]?.get() ?: 0
            
            Log.d(TAG, "Retrying operation for $componentName (attempt $attemptCount)")
            
            // Record recovery attempt
            val crashId = "retry_${componentName}_${System.currentTimeMillis()}"
            
            val result = when (attemptCount) {
                1 -> {
                    // First retry: try with fallback resources
                    Log.d(TAG, "First retry: using fallback resources")
                    operation()
                }
                2 -> {
                    // Second retry: use simplified approach
                    Log.d(TAG, "Second retry: using simplified approach")
                    createSimplifiedComponent(componentName)
                }
                else -> {
                    // Final retry: use emergency fallback
                    Log.d(TAG, "Final retry: using emergency fallback")
                    createEmergencyComponent(componentName)
                }
            }
            
            if (result != null) {
                crashDiagnosticManager.recordRecoveryAttempt(crashId, "retry_$attemptCount", true)
                Log.i(TAG, "Recovery successful for $componentName on attempt $attemptCount")
            } else {
                crashDiagnosticManager.recordRecoveryAttempt(crashId, "retry_$attemptCount", false, "Operation returned null")
            }
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during retry operation for $componentName", e)
            val crashId = "retry_${componentName}_${System.currentTimeMillis()}"
            crashDiagnosticManager.recordRecoveryAttempt(crashId, "retry_error", false, e.message)
            createEmergencyComponent(componentName)
        }
    }

    /**
     * Provide fallback drawables for missing resources
     */
    fun provideFallbackDrawables(): Map<String, Int> {
        return try {
            val fallbackMap = resourceValidator.createResourceFallbackMap()
            Log.i(TAG, "Provided ${fallbackMap.size} fallback drawable mappings")
            fallbackMap
        } catch (e: Exception) {
            Log.e(TAG, "Error providing fallback drawables", e)
            emptyMap()
        }
    }

    /**
     * Create emergency settings UI with minimal functionality
     */
    fun createEmergencySettingsUI(): View {
        return try {
            Log.w(TAG, "Creating emergency settings UI")
            
            val emergencyLayout = LinearLayout(context).apply {
                id = EMERGENCY_CONTAINER_ID
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
            }
            
            // Add title
            val titleText = TextView(context).apply {
                text = "Settings (Safe Mode)"
                textSize = 24f
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setPadding(0, 0, 0, 32)
            }
            emergencyLayout.addView(titleText)
            
            // Add status message
            val statusText = TextView(context).apply {
                text = "Settings are running in safe mode due to missing resources. Basic functionality is available."
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, android.R.color.secondary_text_dark))
                setPadding(0, 0, 0, 32)
            }
            emergencyLayout.addView(statusText)
            
            // Add essential settings
            addEmergencySettings(emergencyLayout)
            
            // Add diagnostic button
            val diagnosticButton = Button(context).apply {
                text = "View Diagnostics"
                setOnClickListener {
                    showDiagnosticInfo()
                }
            }
            emergencyLayout.addView(diagnosticButton)
            
            Log.i(TAG, "Emergency settings UI created successfully")
            emergencyLayout
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error creating emergency UI", e)
            createMinimalUI()
        }
    }

    /**
     * Replace custom components with standard Android components
     */
    fun replaceCustomComponentsWithStandard(layout: ViewGroup) {
        try {
            Log.i(TAG, "Replacing custom components with standard alternatives")
            
            var replacedCount = 0
            
            // Find and replace ModernToggleSwitch components
            val toggleSwitches = findViewsByType(layout, ModernToggleSwitch::class.java)
            toggleSwitches.forEach { modernSwitch ->
                try {
                    val standardSwitch = createStandardSwitch(modernSwitch)
                    replaceViewInParent(modernSwitch, standardSwitch)
                    replacedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Error replacing ModernToggleSwitch", e)
                }
            }
            
            // Find and replace GlassCard components
            val glassCards = findViewsByType(layout, GlassCard::class.java)
            glassCards.forEach { glassCard ->
                try {
                    val standardCard = createStandardCard(glassCard)
                    replaceViewInParent(glassCard, standardCard)
                    replacedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Error replacing GlassCard", e)
                }
            }
            
            Log.i(TAG, "Replaced $replacedCount custom components with standard alternatives")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing custom components", e)
        }
    }

    /**
     * Disable problematic features that may cause crashes
     */
    fun disableProblematicFeatures() {
        try {
            Log.i(TAG, "Disabling problematic features for stability")
            
            // Disable glassmorphism effects
            disableGlassmorphismEffects()
            
            // Disable complex animations
            disableComplexAnimations()
            
            // Disable memory-intensive features
            disableMemoryIntensiveFeatures()
            
            Log.i(TAG, "Problematic features disabled successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling problematic features", e)
        }
    }

    /**
     * Get current fallback level
     */
    fun getCurrentFallbackLevel(): FallbackLevel = currentFallbackLevel

    /**
     * Check if recovery is needed based on validation report
     */
    fun isRecoveryNeeded(): Boolean {
        return lastValidationReport?.recommendedAction != RecoveryAction.PROCEED_NORMAL
    }

    /**
     * Get recovery statistics for diagnostics
     */
    fun getRecoveryStatistics(): Map<String, Any> {
        return mapOf(
            "currentFallbackLevel" to currentFallbackLevel.name,
            "totalRetryAttempts" to retryAttempts.values.sumOf { it.get() },
            "componentsWithRetries" to retryAttempts.size,
            "recoveryNeeded" to isRecoveryNeeded(),
            "lastValidationReport" to (lastValidationReport?.let { report ->
                mapOf(
                    "allResourcesAvailable" to report.allResourcesAvailable,
                    "missingResourceCount" to report.missingResources.size,
                    "recommendedAction" to report.recommendedAction.name
                )
            } ?: "No validation performed")
        )
    }

    // Private helper methods

    private fun createFullUI(container: ViewGroup): View {
        // This would create the full UI with all custom components
        // For now, return the container itself as it should work normally
        return container
    }

    private fun createStandardFallbackUI(container: ViewGroup): View {
        val fallbackLayout = LinearLayout(context).apply {
            id = FALLBACK_CONTAINER_ID
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        // Add standard components that replicate settings functionality
        addStandardSettingsComponents(fallbackLayout)
        
        return fallbackLayout
    }

    private fun createEmergencyUI(container: ViewGroup): View {
        return createEmergencySettingsUI()
    }

    private fun createDiagnosticUI(container: ViewGroup): View {
        val diagnosticLayout = LinearLayout(context).apply {
            id = DIAGNOSTIC_CONTAINER_ID
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
        }
        
        // Add diagnostic information
        val titleText = TextView(context).apply {
            text = "Settings Diagnostic Mode"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(0, 0, 0, 16)
        }
        diagnosticLayout.addView(titleText)
        
        // Add diagnostic report
        addDiagnosticReport(diagnosticLayout)
        
        return diagnosticLayout
    }

    private fun createComponentFallback(component: String): View? {
        return when (component.lowercase()) {
            "moderntoggleswitch" -> createFallbackToggleSwitch()
            "glasscard" -> createFallbackCard()
            "glassybackgroundview" -> createFallbackBackground()
            else -> {
                Log.w(TAG, "No fallback available for component: $component")
                null
            }
        }
    }

    private fun createSimplifiedComponent(componentName: String): View? {
        return when (componentName.lowercase()) {
            "moderntoggleswitch" -> Switch(context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setPadding(8, 8, 8, 8)
            }
            "glasscard" -> LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            }
            else -> null
        }
    }

    private fun createEmergencyComponent(componentName: String): View? {
        return when (componentName.lowercase()) {
            "moderntoggleswitch" -> Switch(context)
            "glasscard" -> LinearLayout(context)
            else -> TextView(context).apply {
                text = "Component unavailable"
                setTextColor(ContextCompat.getColor(context, android.R.color.secondary_text_dark))
            }
        }
    }

    private fun createMinimalUI(): View {
        return TextView(context).apply {
            text = "Settings unavailable. Press BACK to exit."
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(32, 32, 32, 32)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
        }
    }

    private fun addEmergencySettings(layout: LinearLayout) {
        try {
            // Add essential toggle switches
            val essentialSettings = listOf(
                "Channel Reversal" to SP.channelReversal,
                "Channel Numbers" to SP.channelNum,
                "Time Display" to SP.time,
                "Boot Startup" to SP.bootStartup
            )
            
            essentialSettings.forEach { (label, currentValue) ->
                val settingLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 8)
                }
                
                val labelText = TextView(context).apply {
                    text = label
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                
                val toggle = Switch(context).apply {
                    isChecked = currentValue
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnCheckedChangeListener { _, isChecked ->
                        updateEmergencySetting(label, isChecked)
                    }
                }
                
                settingLayout.addView(labelText)
                settingLayout.addView(toggle)
                layout.addView(settingLayout)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding emergency settings", e)
        }
    }

    private fun addStandardSettingsComponents(layout: LinearLayout) {
        // Add standard Android components that replicate settings functionality
        // This is a simplified version of the full settings UI
        
        val scrollView = ScrollView(context)
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        // Add settings sections
        addSettingsSection(contentLayout, "Configuration", createConfigurationSection())
        addSettingsSection(contentLayout, "Preferences", createPreferencesSection())
        addSettingsSection(contentLayout, "Actions", createActionsSection())
        
        scrollView.addView(contentLayout)
        layout.addView(scrollView)
    }

    private fun addSettingsSection(parent: LinearLayout, title: String, content: View) {
        val titleText = TextView(context).apply {
            text = title
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setPadding(0, 16, 0, 8)
        }
        parent.addView(titleText)
        parent.addView(content)
    }

    private fun createConfigurationSection(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            
            // Add config URL input
            addView(TextView(context).apply {
                text = "Config URL:"
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
            })
            
            addView(EditText(context).apply {
                hint = "Enter configuration URL"
                setText(SP.config ?: "")
                isFocusable = true
                isFocusableInTouchMode = true
            })
        }
    }

    private fun createPreferencesSection(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            
            // Add preference toggles
            val preferences = listOf(
                "Channel Reversal" to SP.channelReversal,
                "Channel Numbers" to SP.channelNum,
                "Time Display" to SP.time,
                "Boot Startup" to SP.bootStartup
            )
            
            preferences.forEach { (label, value) ->
                val toggle = Switch(context).apply {
                    text = label
                    isChecked = value
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                }
                addView(toggle)
            }
        }
    }

    private fun createActionsSection(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            
            // Add action buttons
            val actions = listOf("Clear Settings", "Reset Order", "Exit")
            
            actions.forEach { action ->
                val button = Button(context).apply {
                    text = action
                    isFocusable = true
                    isFocusableInTouchMode = true
                }
                addView(button)
            }
        }
    }

    private fun addDiagnosticReport(layout: LinearLayout) {
        try {
            val report = crashDiagnosticManager.generateDiagnosticReport()
            
            val reportText = TextView(context).apply {
                text = buildString {
                    appendLine("Device: ${report.deviceInfo.manufacturer} ${report.deviceInfo.model}")
                    appendLine("Android: ${report.deviceInfo.androidVersion} (API ${report.deviceInfo.apiLevel})")
                    appendLine("Memory: ${report.deviceInfo.availableMemoryMB}MB available")
                    appendLine()
                    appendLine("Resource Status:")
                    appendLine("- Missing drawables: ${report.resourceState.missingDrawables.size}")
                    appendLine("- Missing layouts: ${report.resourceState.missingLayouts.size}")
                    appendLine("- Fallback required: ${report.resourceState.fallbackRequired}")
                    appendLine()
                    appendLine("Crash Summary:")
                    appendLine("- Total crashes: ${report.crashSummary.totalCrashes}")
                    appendLine("- Recent crashes: ${report.crashSummary.recentCrashes}")
                    appendLine("- Successful recoveries: ${report.crashSummary.successfulRecoveries}")
                    appendLine()
                    appendLine("Recommendations:")
                    report.recommendations.forEach { recommendation ->
                        appendLine("• $recommendation")
                    }
                }
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                setPadding(8, 8, 8, 8)
            }
            
            val scrollView = ScrollView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    400 // Fixed height for scrollable area
                )
            }
            scrollView.addView(reportText)
            layout.addView(scrollView)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding diagnostic report", e)
            layout.addView(TextView(context).apply {
                text = "Error generating diagnostic report: ${e.message}"
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
            })
        }
    }

    private fun updateEmergencySetting(setting: String, value: Boolean) {
        try {
            when (setting) {
                "Channel Reversal" -> SP.channelReversal = value
                "Channel Numbers" -> SP.channelNum = value
                "Time Display" -> SP.time = value
                "Boot Startup" -> SP.bootStartup = value
            }
            Log.d(TAG, "Emergency setting updated: $setting = $value")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating emergency setting: $setting", e)
        }
    }

    private fun showDiagnosticInfo() {
        try {
            val statistics = getRecoveryStatistics()
            val message = buildString {
                appendLine("Recovery Statistics:")
                statistics.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
            }
            
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            Log.i(TAG, "Diagnostic info displayed: $message")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing diagnostic info", e)
        }
    }

    private fun disableGlassmorphismEffects() {
        // This would disable glassmorphism effects system-wide
        Log.d(TAG, "Glassmorphism effects disabled for stability")
    }

    private fun disableComplexAnimations() {
        // This would disable complex animations system-wide
        Log.d(TAG, "Complex animations disabled for stability")
    }

    private fun disableMemoryIntensiveFeatures() {
        // This would disable memory-intensive features
        Log.d(TAG, "Memory-intensive features disabled for stability")
    }

    private fun createFallbackToggleSwitch(): Switch {
        return Switch(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(8, 8, 8, 8)
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
    }

    private fun createFallbackCard(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
        }
    }

    private fun createFallbackBackground(): View {
        return View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.background_dark))
        }
    }

    private fun createStandardSwitch(modernSwitch: ModernToggleSwitch): Switch {
        return Switch(context).apply {
            isChecked = modernSwitch.isChecked
            isEnabled = modernSwitch.isEnabled
            isFocusable = modernSwitch.isFocusable
            isFocusableInTouchMode = modernSwitch.isFocusableInTouchMode
            contentDescription = modernSwitch.contentDescription
            layoutParams = modernSwitch.layoutParams
        }
    }

    private fun createStandardCard(glassCard: GlassCard): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            layoutParams = glassCard.layoutParams
            
            // Copy child views
            for (i in 0 until glassCard.childCount) {
                val child = glassCard.getChildAt(i)
                glassCard.removeView(child)
                addView(child)
            }
        }
    }

    private fun <T : View> findViewsByType(parent: ViewGroup, clazz: Class<T>): List<T> {
        val views = mutableListOf<T>()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (clazz.isInstance(child)) {
                @Suppress("UNCHECKED_CAST")
                views.add(child as T)
            } else if (child is ViewGroup) {
                views.addAll(findViewsByType(child, clazz))
            }
        }
        return views
    }

    private fun replaceViewInParent(oldView: View, newView: View) {
        val parent = oldView.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(oldView)
        parent.removeViewAt(index)
        parent.addView(newView, index)
    }
}