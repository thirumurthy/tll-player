package com.thirutricks.tllplayer.infrastructure

/**
 * Interface for implementing specific error recovery strategies.
 */
interface RecoveryStrategy {
    fun canHandle(error: MenuError): Boolean
    fun recover(error: MenuError, context: MenuContext): RecoveryResult
    fun getPriority(): Int // Higher priority strategies are tried first
}

/**
 * Recovery strategy for data initialization failures.
 */
class DataInitializationRecovery : RecoveryStrategy {
    
    override fun canHandle(error: MenuError): Boolean {
        return when (error) {
            is MenuError.DataNotInitialized,
            is MenuError.AdapterInitializationFailed,
            is MenuError.DataCorruption -> true
            else -> false
        }
    }
    
    override fun recover(error: MenuError, context: MenuContext): RecoveryResult {
        return when (error) {
            is MenuError.DataNotInitialized -> {
                // Attempt to initialize with empty/default data
                RecoveryResult.Retry(simplifiedConfig = true)
            }
            is MenuError.AdapterInitializationFailed -> {
                // Retry with minimal configuration
                RecoveryResult.Retry(simplifiedConfig = true)
            }
            is MenuError.DataCorruption -> {
                // Data corruption is critical and may not be recoverable
                RecoveryResult.CannotRecover
            }
            else -> RecoveryResult.CannotRecover
        }
    }
    
    override fun getPriority(): Int = 100 // High priority for data issues
}

/**
 * Recovery strategy for focus management issues.
 */
class FocusRecovery : RecoveryStrategy {
    
    override fun canHandle(error: MenuError): Boolean {
        return when (error) {
            is MenuError.FocusDeadlock -> true
            else -> false
        }
    }
    
    override fun recover(error: MenuError, context: MenuContext): RecoveryResult {
        return when (error) {
            is MenuError.FocusDeadlock -> {
                // Clear all focus and reset to default state
                RecoveryResult.Success
            }
            else -> RecoveryResult.CannotRecover
        }
    }
    
    override fun getPriority(): Int = 90 // High priority for focus issues
}

/**
 * Recovery strategy for glass effect failures.
 */
class GlassEffectRecovery : RecoveryStrategy {
    
    override fun canHandle(error: MenuError): Boolean {
        return when (error) {
            is MenuError.GlassEffectFailure -> true
            else -> false
        }
    }
    
    override fun recover(error: MenuError, context: MenuContext): RecoveryResult {
        return when (error) {
            is MenuError.GlassEffectFailure -> {
                // Disable glass effects and fall back to standard UI
                RecoveryResult.PartialSuccess("Glass effects disabled, using standard UI")
            }
            else -> RecoveryResult.CannotRecover
        }
    }
    
    override fun getPriority(): Int = 50 // Medium priority for visual effects
}

/**
 * Recovery strategy for resource cleanup issues.
 */
class ResourceCleanupRecovery : RecoveryStrategy {
    
    override fun canHandle(error: MenuError): Boolean {
        return when (error) {
            is MenuError.ResourceCleanupFailed,
            is MenuError.ResourceCleanupPartialFailure -> true
            else -> false
        }
    }
    
    override fun recover(error: MenuError, context: MenuContext): RecoveryResult {
        return when (error) {
            is MenuError.ResourceCleanupFailed -> {
                // Force garbage collection and retry cleanup
                System.gc()
                RecoveryResult.Retry()
            }
            is MenuError.ResourceCleanupPartialFailure -> {
                // Some resources were cleaned up, continue with partial success
                RecoveryResult.PartialSuccess("Partial cleanup completed: ${error.failedResources.size} resources failed")
            }
            else -> RecoveryResult.CannotRecover
        }
    }
    
    override fun getPriority(): Int = 70 // High priority for resource management
}

/**
 * Recovery strategy for performance issues.
 */
class PerformanceRecovery : RecoveryStrategy {
    
    override fun canHandle(error: MenuError): Boolean {
        return when (error) {
            is MenuError.PerformanceWarning,
            is MenuError.OutOfMemory -> true
            else -> false
        }
    }
    
    override fun recover(error: MenuError, context: MenuContext): RecoveryResult {
        return when (error) {
            is MenuError.PerformanceWarning -> {
                // Reduce visual complexity and optimize performance
                RecoveryResult.PartialSuccess("Performance optimizations applied")
            }
            is MenuError.OutOfMemory -> {
                // Aggressive cleanup and simplification
                System.gc()
                RecoveryResult.Retry(simplifiedConfig = true)
            }
            else -> RecoveryResult.CannotRecover
        }
    }
    
    override fun getPriority(): Int = 80 // High priority for memory issues
}

/**
 * Recovery strategy for thread synchronization issues.
 */
class ThreadSynchronizationRecovery : RecoveryStrategy {
    
    override fun canHandle(error: MenuError): Boolean {
        return when (error) {
            is MenuError.ThreadSynchronizationError -> true
            else -> false
        }
    }
    
    override fun recover(error: MenuError, context: MenuContext): RecoveryResult {
        return when (error) {
            is MenuError.ThreadSynchronizationError -> {
                // Reset synchronization state and retry
                RecoveryResult.Retry()
            }
            else -> RecoveryResult.CannotRecover
        }
    }
    
    override fun getPriority(): Int = 85 // High priority for thread safety
}

/**
 * Fallback recovery strategy that handles any unhandled errors.
 */
class FallbackRecovery : RecoveryStrategy {
    
    override fun canHandle(error: MenuError): Boolean {
        return true // Can handle any error as a last resort
    }
    
    override fun recover(error: MenuError, context: MenuContext): RecoveryResult {
        return when (error.severity) {
            ErrorSeverity.CRITICAL -> {
                // For critical errors, try to maintain minimal functionality
                RecoveryResult.PartialSuccess("Minimal functionality maintained")
            }
            ErrorSeverity.HIGH -> {
                // For high severity, attempt simplified operation
                RecoveryResult.Retry(simplifiedConfig = true)
            }
            ErrorSeverity.MEDIUM,
            ErrorSeverity.LOW -> {
                // For medium/low severity, continue with degraded functionality
                RecoveryResult.PartialSuccess("Continuing with degraded functionality")
            }
        }
    }
    
    override fun getPriority(): Int = 1 // Lowest priority - last resort
}

/**
 * Factory for creating default recovery strategies.
 */
object RecoveryStrategyFactory {
    
    fun createDefaultStrategies(): List<RecoveryStrategy> {
        return listOf(
            DataInitializationRecovery(),
            FocusRecovery(),
            ResourceCleanupRecovery(),
            PerformanceRecovery(),
            ThreadSynchronizationRecovery(),
            GlassEffectRecovery(),
            FallbackRecovery()
        ).sortedByDescending { it.getPriority() }
    }
    
    fun createMinimalStrategies(): List<RecoveryStrategy> {
        return listOf(
            DataInitializationRecovery(),
            FocusRecovery(),
            FallbackRecovery()
        ).sortedByDescending { it.getPriority() }
    }
}