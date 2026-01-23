package com.thirutricks.tllplayer.ui

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive crash diagnostic manager for tracking and analyzing settings crashes
 * Captures detailed crash information, component states, and resource validation results
 */
class CrashDiagnosticManager(
    private val context: Context,
    private val resourceValidator: ResourceValidator
) {
    companion object {
        private const val TAG = "CrashDiagnosticManager"
        private const val MAX_CRASH_REPORTS = 50
        private const val DIAGNOSTIC_VERSION = "1.0.0"
        
        @Volatile
        private var instance: CrashDiagnosticManager? = null
        
        fun getInstance(context: Context): CrashDiagnosticManager {
            return instance ?: synchronized(this) {
                instance ?: CrashDiagnosticManager(
                    context.applicationContext,
                    ResourceValidator(context.applicationContext)
                ).also { instance = it }
            }
        }
    }

    private val crashReports = ConcurrentHashMap<String, CrashReport>()
    private val componentStates = ConcurrentHashMap<String, ComponentState>()
    private val diagnosticScope = CoroutineScope(Dispatchers.IO)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Validates all settings-related resources and returns comprehensive results
     */
    fun validateSettingsResources(): ResourceValidationResult {
        Log.d(TAG, "Starting comprehensive settings resource validation")
        
        val startTime = System.currentTimeMillis()
        
        val missingDrawables = resourceValidator.validateDrawableResources()
        val missingLayouts = resourceValidator.validateLayoutResources()
        val missingColors = resourceValidator.validateColorResources()
        val missingDimensions = resourceValidator.validateDimensionResources()
        val customComponentsAvailable = resourceValidator.validateCustomComponents()
        
        val validationTime = System.currentTimeMillis() - startTime
        
        val result = ResourceValidationResult(
            missingDrawables = missingDrawables,
            missingLayouts = missingLayouts,
            missingColors = missingColors,
            missingDimensions = missingDimensions,
            customComponentsAvailable = customComponentsAvailable,
            fallbackRequired = missingDrawables.isNotEmpty() || missingColors.isNotEmpty(),
            validationTimeMs = validationTime,
            timestamp = System.currentTimeMillis()
        )
        
        Log.i(TAG, "Resource validation completed in ${validationTime}ms - " +
                "Missing: ${result.totalMissingResources} resources, " +
                "Fallback required: ${result.fallbackRequired}")
        
        return result
    }

    /**
     * Captures current fragment state for diagnostic purposes
     */
    fun captureFragmentState(
        fragmentManager: FragmentManager? = null,
        fragment: Fragment? = null
    ): FragmentStateSnapshot {
        Log.d(TAG, "Capturing fragment state snapshot")
        
        val snapshot = FragmentStateSnapshot(
            timestamp = System.currentTimeMillis(),
            isActivityFinishing = captureActivityFinishingState(),
            isFragmentAttached = fragment?.isAdded ?: false,
            fragmentManagerState = captureFragmentManagerState(fragmentManager),
            fragmentLifecycleState = fragment?.lifecycle?.currentState?.name ?: "UNKNOWN",
            viewBindingState = captureViewBindingState(fragment),
            fragmentTag = fragment?.tag,
            fragmentId = fragment?.id,
            isFragmentVisible = fragment?.isVisible ?: false,
            isFragmentHidden = fragment?.isHidden ?: true,
            isFragmentRemoving = fragment?.isRemoving ?: false,
            isFragmentDetached = fragment?.isDetached ?: true,
            backStackEntryCount = fragmentManager?.backStackEntryCount ?: 0
        )
        
        Log.d(TAG, "Fragment state captured - Attached: ${snapshot.isFragmentAttached}, " +
                "Lifecycle: ${snapshot.fragmentLifecycleState}, " +
                "Activity finishing: ${snapshot.isActivityFinishing}")
        
        return snapshot
    }

    /**
     * Logs detailed crash information with context and component states
     */
    fun logCrashDetails(
        exception: Exception,
        context: String,
        componentName: String? = null,
        fragmentManager: FragmentManager? = null,
        fragment: Fragment? = null
    ) {
        Log.e(TAG, "Logging crash details for context: $context", exception)
        
        diagnosticScope.launch {
            try {
                val crashId = generateCrashId()
                val stackTrace = getStackTraceString(exception)
                val deviceInfo = captureDeviceInfo()
                val fragmentState = captureFragmentState(fragmentManager, fragment)
                val resourceState = validateSettingsResources()
                val componentState = componentName?.let { captureComponentState(it) }
                
                val crashReport = CrashReport(
                    id = crashId,
                    timestamp = System.currentTimeMillis(),
                    crashType = determineCrashType(exception, context),
                    exception = exception.javaClass.simpleName,
                    message = exception.message ?: "No message",
                    stackTrace = stackTrace,
                    context = context,
                    componentName = componentName,
                    deviceInfo = deviceInfo,
                    fragmentState = fragmentState,
                    resourceState = resourceState,
                    componentState = componentState,
                    recoveryAttempts = mutableListOf()
                )
                
                storeCrashReport(crashReport)
                logCrashSummary(crashReport)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log crash details", e)
            }
        }
    }

    /**
     * Records a recovery attempt for a specific crash
     */
    fun recordRecoveryAttempt(
        crashId: String,
        strategy: String,
        success: Boolean,
        errorMessage: String? = null
    ) {
        Log.d(TAG, "Recording recovery attempt - Strategy: $strategy, Success: $success")
        
        val recoveryAttempt = RecoveryAttempt(
            strategy = strategy,
            success = success,
            errorMessage = errorMessage,
            timestamp = System.currentTimeMillis()
        )
        
        crashReports[crashId]?.let { crashReport ->
            crashReport.recoveryAttempts.add(recoveryAttempt)
            Log.i(TAG, "Recovery attempt recorded for crash $crashId")
        }
    }

    /**
     * Updates component state for tracking
     */
    fun updateComponentState(componentName: String, state: String, details: Map<String, Any> = emptyMap()) {
        val componentState = ComponentState(
            name = componentName,
            state = state,
            timestamp = System.currentTimeMillis(),
            details = details
        )
        
        componentStates[componentName] = componentState
        Log.d(TAG, "Component state updated - $componentName: $state")
    }

    /**
     * Generates comprehensive diagnostic report
     */
    fun generateDiagnosticReport(): DiagnosticReport {
        Log.i(TAG, "Generating comprehensive diagnostic report")
        
        val currentResourceState = validateSettingsResources()
        val recentCrashes = crashReports.values
            .sortedByDescending { it.timestamp }
            .take(10)
        
        val crashSummary = CrashSummary(
            totalCrashes = crashReports.size,
            recentCrashes = recentCrashes.size,
            crashTypes = recentCrashes.groupBy { it.crashType }.mapValues { it.value.size },
            mostCommonCrashType = recentCrashes.groupBy { it.crashType }
                .maxByOrNull { it.value.size }?.key,
            averageRecoveryAttempts = recentCrashes.map { it.recoveryAttempts.size }.average().takeIf { !it.isNaN() } ?: 0.0,
            successfulRecoveries = recentCrashes.count { crash ->
                crash.recoveryAttempts.any { it.success }
            }
        )
        
        val report = DiagnosticReport(
            version = DIAGNOSTIC_VERSION,
            timestamp = System.currentTimeMillis(),
            deviceInfo = captureDeviceInfo(),
            resourceState = currentResourceState,
            crashSummary = crashSummary,
            recentCrashes = recentCrashes,
            componentStates = componentStates.values.toList(),
            recommendations = generateRecommendations(crashSummary, currentResourceState)
        )
        
        Log.i(TAG, "Diagnostic report generated - ${crashSummary.totalCrashes} total crashes, " +
                "${crashSummary.successfulRecoveries} successful recoveries")
        
        return report
    }

    /**
     * Gets crash report by ID
     */
    fun getCrashReport(crashId: String): CrashReport? {
        return crashReports[crashId]
    }

    /**
     * Gets all crash reports
     */
    fun getAllCrashReports(): List<CrashReport> {
        return crashReports.values.sortedByDescending { it.timestamp }
    }

    /**
     * Clears old crash reports to prevent memory issues
     */
    fun clearOldCrashReports() {
        if (crashReports.size > MAX_CRASH_REPORTS) {
            val sortedReports = crashReports.values.sortedBy { it.timestamp }
            val toRemove = sortedReports.take(crashReports.size - MAX_CRASH_REPORTS)
            toRemove.forEach { crashReports.remove(it.id) }
            Log.i(TAG, "Cleared ${toRemove.size} old crash reports")
        }
    }

    // Private helper methods
    
    private fun generateCrashId(): String {
        return "crash_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    private fun getStackTraceString(exception: Exception): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        exception.printStackTrace(printWriter)
        return stringWriter.toString()
    }

    private fun captureDeviceInfo(): DeviceInfo {
        return DeviceInfo(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            brand = Build.BRAND,
            device = Build.DEVICE,
            hardware = Build.HARDWARE,
            isEmulator = Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("Emulator"),
            availableMemoryMB = getAvailableMemoryMB(),
            totalMemoryMB = getTotalMemoryMB()
        )
    }

    private fun captureActivityFinishingState(): Boolean {
        // This would need to be passed from the activity context
        // For now, we'll return false as a safe default
        return false
    }

    private fun captureFragmentManagerState(fragmentManager: FragmentManager?): String {
        return when {
            fragmentManager == null -> "NULL"
            fragmentManager.isDestroyed -> "DESTROYED"
            fragmentManager.isStateSaved -> "STATE_SAVED"
            else -> "ACTIVE"
        }
    }

    private fun captureViewBindingState(fragment: Fragment?): String {
        return when {
            fragment == null -> "NO_FRAGMENT"
            fragment.view == null -> "NO_VIEW"
            fragment.viewLifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED -> "VIEW_DESTROYED"
            else -> "VIEW_ACTIVE"
        }
    }

    private fun captureComponentState(componentName: String): ComponentState? {
        return componentStates[componentName]
    }

    private fun determineCrashType(exception: Exception, context: String): CrashType {
        return when {
            exception is android.content.res.Resources.NotFoundException -> CrashType.RESOURCE_NOT_FOUND
            exception.message?.contains("fragment", ignoreCase = true) == true -> CrashType.FRAGMENT_LIFECYCLE_ERROR
            exception.message?.contains("view", ignoreCase = true) == true -> CrashType.CUSTOM_COMPONENT_FAILURE
            exception is OutOfMemoryError -> CrashType.MEMORY_ERROR
            context.contains("settings", ignoreCase = true) -> CrashType.SETTINGS_SPECIFIC_ERROR
            else -> CrashType.UNKNOWN
        }
    }

    private fun storeCrashReport(crashReport: CrashReport) {
        crashReports[crashReport.id] = crashReport
        clearOldCrashReports()
    }

    private fun logCrashSummary(crashReport: CrashReport) {
        Log.e(TAG, """
            |CRASH SUMMARY [${crashReport.id}]
            |Type: ${crashReport.crashType}
            |Exception: ${crashReport.exception}
            |Context: ${crashReport.context}
            |Component: ${crashReport.componentName ?: "N/A"}
            |Fragment State: ${crashReport.fragmentState.fragmentLifecycleState}
            |Missing Resources: ${crashReport.resourceState.totalMissingResources}
            |Device: ${crashReport.deviceInfo.manufacturer} ${crashReport.deviceInfo.model}
            |Time: ${dateFormatter.format(Date(crashReport.timestamp))}
        """.trimMargin())
    }

    private fun generateRecommendations(
        crashSummary: CrashSummary,
        resourceState: ResourceValidationResult
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (resourceState.missingDrawables.isNotEmpty()) {
            recommendations.add("Add missing drawable resources: ${resourceState.missingDrawables.joinToString()}")
        }
        
        if (resourceState.missingLayouts.isNotEmpty()) {
            recommendations.add("Add missing layout resources: ${resourceState.missingLayouts.joinToString()}")
        }
        
        if (crashSummary.mostCommonCrashType == CrashType.FRAGMENT_LIFECYCLE_ERROR) {
            recommendations.add("Implement safer fragment lifecycle management")
        }
        
        if (crashSummary.mostCommonCrashType == CrashType.CUSTOM_COMPONENT_FAILURE) {
            recommendations.add("Add fallback mechanisms for custom UI components")
        }
        
        if (crashSummary.averageRecoveryAttempts > 3) {
            recommendations.add("Improve error recovery strategies to reduce retry attempts")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("System appears stable - continue monitoring")
        }
        
        return recommendations
    }

    private fun getAvailableMemoryMB(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.availMem / (1024 * 1024)
        } catch (e: Exception) {
            -1L
        }
    }

    private fun getTotalMemoryMB(): Long {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memoryInfo = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            memoryInfo.totalMem / (1024 * 1024)
        } catch (e: Exception) {
            -1L
        }
    }
}

/**
 * Data classes for crash diagnostic information
 */

data class ResourceValidationResult(
    val missingDrawables: List<String>,
    val missingLayouts: List<String>,
    val missingColors: List<String>,
    val missingDimensions: List<String>,
    val customComponentsAvailable: Boolean,
    val fallbackRequired: Boolean,
    val validationTimeMs: Long,
    val timestamp: Long
) {
    val totalMissingResources: Int
        get() = missingDrawables.size + missingLayouts.size + missingColors.size + missingDimensions.size
}

data class FragmentStateSnapshot(
    val timestamp: Long,
    val isActivityFinishing: Boolean,
    val isFragmentAttached: Boolean,
    val fragmentManagerState: String,
    val fragmentLifecycleState: String,
    val viewBindingState: String,
    val fragmentTag: String?,
    val fragmentId: Int?,
    val isFragmentVisible: Boolean,
    val isFragmentHidden: Boolean,
    val isFragmentRemoving: Boolean,
    val isFragmentDetached: Boolean,
    val backStackEntryCount: Int
)

data class CrashReport(
    val id: String,
    val timestamp: Long,
    val crashType: CrashType,
    val exception: String,
    val message: String,
    val stackTrace: String,
    val context: String,
    val componentName: String?,
    val deviceInfo: DeviceInfo,
    val fragmentState: FragmentStateSnapshot,
    val resourceState: ResourceValidationResult,
    val componentState: ComponentState?,
    val recoveryAttempts: MutableList<RecoveryAttempt>
)

data class RecoveryAttempt(
    val strategy: String,
    val success: Boolean,
    val errorMessage: String?,
    val timestamp: Long
)

data class ComponentState(
    val name: String,
    val state: String,
    val timestamp: Long,
    val details: Map<String, Any>
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val brand: String,
    val device: String,
    val hardware: String,
    val isEmulator: Boolean,
    val availableMemoryMB: Long,
    val totalMemoryMB: Long
)

data class DiagnosticReport(
    val version: String,
    val timestamp: Long,
    val deviceInfo: DeviceInfo,
    val resourceState: ResourceValidationResult,
    val crashSummary: CrashSummary,
    val recentCrashes: List<CrashReport>,
    val componentStates: List<ComponentState>,
    val recommendations: List<String>
)

data class CrashSummary(
    val totalCrashes: Int,
    val recentCrashes: Int,
    val crashTypes: Map<CrashType, Int>,
    val mostCommonCrashType: CrashType?,
    val averageRecoveryAttempts: Double,
    val successfulRecoveries: Int
)

enum class CrashType {
    RESOURCE_NOT_FOUND,
    FRAGMENT_LIFECYCLE_ERROR,
    CUSTOM_COMPONENT_FAILURE,
    MEMORY_ERROR,
    SETTINGS_SPECIFIC_ERROR,
    UNKNOWN
}