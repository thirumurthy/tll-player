package com.thirutricks.tllplayer.infrastructure

/**
 * Sealed class hierarchy representing all possible menu system errors.
 * Each error includes severity level and recoverability information.
 */
sealed class MenuError(val severity: ErrorSeverity, val recoverable: Boolean) {
    
    // Critical errors that require immediate attention
    object DataCorruption : MenuError(ErrorSeverity.CRITICAL, false)
    object OutOfMemory : MenuError(ErrorSeverity.CRITICAL, true)
    
    // High priority errors that affect functionality
    data class AdapterInitializationFailed(val cause: Throwable) : MenuError(ErrorSeverity.HIGH, true)
    data class FocusDeadlock(val conflictingSources: List<FocusSource>) : MenuError(ErrorSeverity.HIGH, true)
    object DataNotInitialized : MenuError(ErrorSeverity.HIGH, true)
    object AdapterSetupFailed : MenuError(ErrorSeverity.HIGH, true)
    
    // Medium priority errors with workarounds
    data class GlassEffectFailure(val effectType: String, val cause: Throwable? = null) : MenuError(ErrorSeverity.MEDIUM, true)
    data class ResourceCleanupPartialFailure(val failedResources: List<String>) : MenuError(ErrorSeverity.MEDIUM, true)
    data class ThreadSynchronizationError(val operation: String, val cause: Throwable) : MenuError(ErrorSeverity.MEDIUM, true)
    object ResourceCleanupFailed : MenuError(ErrorSeverity.MEDIUM, true)
    
    // Low priority errors that don't affect core functionality
    data class PerformanceWarning(val metric: String, val threshold: Double, val actual: Double) : MenuError(ErrorSeverity.LOW, true)
    data class ValidationWarning(val field: String, val message: String) : MenuError(ErrorSeverity.LOW, true)
    data class LoggingError(val context: String, val cause: Throwable) : MenuError(ErrorSeverity.LOW, true)
}

/**
 * Error severity levels for prioritizing error handling and recovery.
 */
enum class ErrorSeverity {
    CRITICAL,   // System-threatening errors requiring immediate action
    HIGH,       // Functionality-affecting errors requiring prompt attention
    MEDIUM,     // Errors with workarounds that should be addressed
    LOW         // Warnings and non-critical issues
}

/**
 * Focus sources for tracking focus conflicts.
 */
enum class FocusSource {
    GROUP_ADAPTER,
    LIST_ADAPTER,
    MENU_FRAGMENT,
    EXTERNAL
}

/**
 * Result of error handling operations.
 */
sealed class ErrorResult {
    object Success : ErrorResult()
    data class Recovered(val message: String) : ErrorResult()
    data class PartialRecovery(val message: String, val remainingIssues: List<String>) : ErrorResult()
    data class Failed(val reason: String) : ErrorResult()
}

/**
 * Result of recovery operations.
 */
sealed class RecoveryResult {
    object Success : RecoveryResult()
    data class PartialSuccess(val message: String) : RecoveryResult()
    data class Retry(val simplifiedConfig: Boolean = false) : RecoveryResult()
    object CannotRecover : RecoveryResult()
}

/**
 * Context information for error handling and recovery operations.
 */
data class MenuContext(
    val fragmentState: String,
    val adapterStates: Map<String, String>,
    val focusState: String,
    val resourceCount: Int,
    val memoryUsage: Long,
    val timestamp: Long = System.currentTimeMillis()
)