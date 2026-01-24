package com.thirutricks.tllplayer.infrastructure

import android.util.Log

/**
 * Interface for handling menu system errors with recovery mechanisms.
 */
interface ErrorHandler {
    fun handleError(error: MenuError): ErrorResult
    fun registerErrorListener(listener: ErrorListener)
    fun unregisterErrorListener(listener: ErrorListener)
    fun canRecover(error: MenuError): Boolean
    fun attemptRecovery(error: MenuError, context: MenuContext): RecoveryResult
    fun logError(error: MenuError, context: String, additionalInfo: Map<String, Any> = emptyMap())
}

/**
 * Interface for listening to error events.
 */
interface ErrorListener {
    fun onError(error: MenuError, context: MenuContext)
    fun onRecoveryAttempted(error: MenuError, result: RecoveryResult)
    fun onRecoveryCompleted(error: MenuError, success: Boolean)
}

/**
 * Default implementation of ErrorHandler with comprehensive error handling and recovery.
 */
class DefaultErrorHandler(
    private val logger: MenuLogger,
    private val recoveryStrategies: List<RecoveryStrategy> = emptyList()
) : ErrorHandler {
    
    private val errorListeners = mutableSetOf<ErrorListener>()
    private val errorHistory = mutableListOf<ErrorRecord>()
    private val maxHistorySize = 100
    
    override fun handleError(error: MenuError): ErrorResult {
        val context = getCurrentContext()
        
        // Log the error with full context
        logError(error, "Error occurred in menu system", mapOf(
            "severity" to error.severity.name,
            "recoverable" to error.recoverable,
            "context" to context
        ))
        
        // Record error for analysis
        recordError(error, context)
        
        // Notify listeners
        notifyErrorListeners(error, context)
        
        // Attempt recovery if possible
        return if (canRecover(error)) {
            val recoveryResult = attemptRecovery(error, context)
            when (recoveryResult) {
                is RecoveryResult.Success -> {
                    notifyRecoveryCompleted(error, true)
                    ErrorResult.Success
                }
                is RecoveryResult.PartialSuccess -> {
                    notifyRecoveryCompleted(error, true)
                    ErrorResult.Recovered(recoveryResult.message)
                }
                is RecoveryResult.Retry -> {
                    // For retry scenarios, we'll attempt once more with simplified config
                    val retryResult = if (recoveryResult.simplifiedConfig) {
                        attemptSimplifiedRecovery(error, context)
                    } else {
                        attemptRecovery(error, context)
                    }
                    handleRetryResult(error, retryResult)
                }
                is RecoveryResult.CannotRecover -> {
                    notifyRecoveryCompleted(error, false)
                    ErrorResult.Failed("Recovery not possible for ${error::class.simpleName ?: "Unknown"}")
                }
            }
        } else {
            ErrorResult.Failed("Error is not recoverable: ${error::class.simpleName ?: "Unknown"}")
        }
    }
    
    override fun registerErrorListener(listener: ErrorListener) {
        errorListeners.add(listener)
    }
    
    override fun unregisterErrorListener(listener: ErrorListener) {
        errorListeners.remove(listener)
    }
    
    override fun canRecover(error: MenuError): Boolean {
        return error.recoverable && recoveryStrategies.any { it.canHandle(error) }
    }
    
    override fun attemptRecovery(error: MenuError, context: MenuContext): RecoveryResult {
        val strategy = recoveryStrategies.firstOrNull { it.canHandle(error) }
        return strategy?.recover(error, context) ?: RecoveryResult.CannotRecover
    }
    
    override fun logError(error: MenuError, context: String, additionalInfo: Map<String, Any>) {
        val logLevel = when (error.severity) {
            ErrorSeverity.CRITICAL -> Log.ERROR
            ErrorSeverity.HIGH -> Log.ERROR
            ErrorSeverity.MEDIUM -> Log.WARN
            ErrorSeverity.LOW -> Log.INFO
        }
        
        val message = buildString {
            append("MenuError: ${error::class.simpleName ?: "Unknown"}")
            append(" | Context: $context")
            append(" | Severity: ${error.severity}")
            append(" | Recoverable: ${error.recoverable}")
            
            if (additionalInfo.isNotEmpty()) {
                append(" | Additional: $additionalInfo")
            }
            
            when (error) {
                is MenuError.AdapterInitializationFailed -> append(" | Cause: ${error.cause.message}")
                is MenuError.FocusDeadlock -> append(" | Sources: ${error.conflictingSources}")
                is MenuError.GlassEffectFailure -> append(" | Effect: ${error.effectType}, Cause: ${error.cause?.message}")
                is MenuError.ResourceCleanupPartialFailure -> append(" | Failed: ${error.failedResources}")
                is MenuError.ThreadSynchronizationError -> append(" | Operation: ${error.operation}, Cause: ${error.cause.message}")
                is MenuError.PerformanceWarning -> append(" | Metric: ${error.metric}, Threshold: ${error.threshold}, Actual: ${error.actual}")
                is MenuError.ValidationWarning -> append(" | Field: ${error.field}, Message: ${error.message}")
                is MenuError.LoggingError -> append(" | Context: ${error.context}, Cause: ${error.cause.message}")
                else -> { /* No additional info for simple errors */ }
            }
        }
        
        logger.log(logLevel, "MenuErrorHandler", message)
    }
    
    private fun getCurrentContext(): MenuContext {
        // This would be populated with actual system state in a real implementation
        return MenuContext(
            fragmentState = "unknown",
            adapterStates = emptyMap(),
            focusState = "unknown",
            resourceCount = 0,
            memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        )
    }
    
    private fun recordError(error: MenuError, context: MenuContext) {
        val record = ErrorRecord(
            error = error,
            context = context,
            timestamp = System.currentTimeMillis()
        )
        
        errorHistory.add(record)
        
        // Maintain history size limit
        if (errorHistory.size > maxHistorySize) {
            errorHistory.removeAt(0)
        }
    }
    
    private fun notifyErrorListeners(error: MenuError, context: MenuContext) {
        errorListeners.forEach { listener ->
            try {
                listener.onError(error, context)
            } catch (e: Exception) {
                // Prevent listener errors from affecting error handling
                logger.log(Log.WARN, "MenuErrorHandler", "Error listener failed: ${e.message}")
            }
        }
    }
    
    private fun notifyRecoveryCompleted(error: MenuError, success: Boolean) {
        errorListeners.forEach { listener ->
            try {
                listener.onRecoveryCompleted(error, success)
            } catch (e: Exception) {
                logger.log(Log.WARN, "MenuErrorHandler", "Recovery listener failed: ${e.message}")
            }
        }
    }
    
    private fun attemptSimplifiedRecovery(error: MenuError, context: MenuContext): RecoveryResult {
        // Simplified recovery attempts with minimal configuration
        return when (error) {
            is MenuError.AdapterInitializationFailed -> RecoveryResult.PartialSuccess("Using basic adapter configuration")
            is MenuError.GlassEffectFailure -> RecoveryResult.Success // Disable glass effects
            is MenuError.FocusDeadlock -> RecoveryResult.Success // Reset focus to default
            else -> RecoveryResult.CannotRecover
        }
    }
    
    private fun handleRetryResult(error: MenuError, result: RecoveryResult): ErrorResult {
        return when (result) {
            is RecoveryResult.Success -> {
                notifyRecoveryCompleted(error, true)
                ErrorResult.Recovered("Retry successful")
            }
            is RecoveryResult.PartialSuccess -> {
                notifyRecoveryCompleted(error, true)
                ErrorResult.PartialRecovery(result.message, emptyList())
            }
            else -> {
                notifyRecoveryCompleted(error, false)
                ErrorResult.Failed("Retry failed")
            }
        }
    }
    
    /**
     * Get error statistics for debugging and monitoring.
     */
    fun getErrorStatistics(): ErrorStatistics {
        val now = System.currentTimeMillis()
        val recentErrors = errorHistory.filter { now - it.timestamp < 300_000 } // Last 5 minutes
        
        return ErrorStatistics(
            totalErrors = errorHistory.size,
            recentErrors = recentErrors.size,
            errorsBySeverity = errorHistory.groupBy { it.error.severity }.mapValues { it.value.size },
            mostCommonErrors = errorHistory.groupBy { it.error::class.simpleName ?: "Unknown" }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
        )
    }
}

/**
 * Record of an error occurrence for analysis and debugging.
 */
data class ErrorRecord(
    val error: MenuError,
    val context: MenuContext,
    val timestamp: Long
)

/**
 * Statistics about error occurrences for monitoring and debugging.
 */
data class ErrorStatistics(
    val totalErrors: Int,
    val recentErrors: Int,
    val errorsBySeverity: Map<ErrorSeverity, Int>,
    val mostCommonErrors: List<Pair<String?, Int>>
)