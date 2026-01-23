package com.thirutricks.tllplayer.ui

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import com.thirutricks.tllplayer.R

/**
 * Safe fragment manager for robust fragment operations with comprehensive error handling
 * Provides fragment lifecycle protection and safe transaction management
 */
class SafeFragmentManager(
    private val activity: FragmentActivity,
    private val crashDiagnosticManager: CrashDiagnosticManager
) {
    companion object {
        private const val TAG = "SafeFragmentManager"
        private const val TRANSACTION_TIMEOUT_MS = 5000L
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 500L
    }

    private val pendingOperations = ConcurrentHashMap<String, PendingOperation>()
    private val fragmentStates = ConcurrentHashMap<String, FragmentState>()
    private val operationScope = CoroutineScope(Dispatchers.Main)
    private val isDestroyed = AtomicBoolean(false)

    /**
     * Safely show a fragment with comprehensive validation and error handling
     */
    fun safeShowFragment(
        fragment: Fragment,
        containerId: Int,
        tag: String? = null,
        addToBackStack: Boolean = false
    ): Boolean {
        return try {
            Log.d(TAG, "Attempting to safely show fragment: ${fragment.javaClass.simpleName}")
            
            // Pre-flight validation
            val validationResult = validateFragmentOperation(fragment, FragmentOperation.SHOW)
            if (!validationResult.canProceed) {
                Log.w(TAG, "Fragment show operation validation failed: ${validationResult.reason}")
                return false
            }
            
            val fragmentManager = activity.supportFragmentManager
            
            // Check if fragment is already visible
            if (isFragmentVisible(fragment, fragmentManager)) {
                Log.d(TAG, "Fragment is already visible, skipping show operation")
                return true
            }
            
            // Perform safe transaction
            val operationId = generateOperationId("show", fragment)
            val operation = PendingOperation(
                id = operationId,
                type = FragmentOperation.SHOW,
                fragment = fragment,
                timestamp = System.currentTimeMillis()
            )
            
            pendingOperations[operationId] = operation
            
            val success = performSafeTransaction(fragmentManager) { transaction ->
                // Check if fragment is already added to the fragment manager
                if (fragment.isAdded) {
                    // Fragment is already added, just show it
                    transaction.show(fragment)
                } else {
                    // Fragment not added yet, add it
                    if (addToBackStack) {
                        transaction.add(containerId, fragment, tag)
                            .addToBackStack(tag)
                    } else {
                        transaction.add(containerId, fragment, tag)
                    }
                }
            }
            
            if (success) {
                updateFragmentState(fragment, FragmentState.VISIBLE)
                Log.i(TAG, "Fragment shown successfully: ${fragment.javaClass.simpleName}")
            } else {
                Log.e(TAG, "Failed to show fragment: ${fragment.javaClass.simpleName}")
            }
            
            pendingOperations.remove(operationId)
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing fragment", e)
            crashDiagnosticManager.logCrashDetails(e, "safeShowFragment", fragment.javaClass.simpleName)
            false
        }
    }

    /**
     * Safely hide a fragment with validation and error handling
     */
    fun safeHideFragment(fragment: Fragment): Boolean {
        return try {
            Log.d(TAG, "Attempting to safely hide fragment: ${fragment.javaClass.simpleName}")
            
            // Pre-flight validation
            val validationResult = validateFragmentOperation(fragment, FragmentOperation.HIDE)
            if (!validationResult.canProceed) {
                Log.w(TAG, "Fragment hide operation validation failed: ${validationResult.reason}")
                return false
            }
            
            val fragmentManager = activity.supportFragmentManager
            
            // Check if fragment is already hidden
            if (!isFragmentVisible(fragment, fragmentManager)) {
                Log.d(TAG, "Fragment is already hidden, skipping hide operation")
                return true
            }
            
            // Perform safe transaction
            val operationId = generateOperationId("hide", fragment)
            val operation = PendingOperation(
                id = operationId,
                type = FragmentOperation.HIDE,
                fragment = fragment,
                timestamp = System.currentTimeMillis()
            )
            
            pendingOperations[operationId] = operation
            
            val success = performSafeTransaction(fragmentManager) { transaction ->
                transaction.hide(fragment)
            }
            
            if (success) {
                updateFragmentState(fragment, FragmentState.HIDDEN)
                Log.i(TAG, "Fragment hidden successfully: ${fragment.javaClass.simpleName}")
            } else {
                Log.e(TAG, "Failed to hide fragment: ${fragment.javaClass.simpleName}")
            }
            
            pendingOperations.remove(operationId)
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding fragment", e)
            crashDiagnosticManager.logCrashDetails(e, "safeHideFragment", fragment.javaClass.simpleName)
            false
        }
    }

    /**
     * Safely remove a fragment with cleanup
     */
    fun safeRemoveFragment(fragment: Fragment): Boolean {
        return try {
            Log.d(TAG, "Attempting to safely remove fragment: ${fragment.javaClass.simpleName}")
            
            // Pre-flight validation
            val validationResult = validateFragmentOperation(fragment, FragmentOperation.REMOVE)
            if (!validationResult.canProceed) {
                Log.w(TAG, "Fragment remove operation validation failed: ${validationResult.reason}")
                return false
            }
            
            val fragmentManager = activity.supportFragmentManager
            
            // Perform safe transaction
            val operationId = generateOperationId("remove", fragment)
            val operation = PendingOperation(
                id = operationId,
                type = FragmentOperation.REMOVE,
                fragment = fragment,
                timestamp = System.currentTimeMillis()
            )
            
            pendingOperations[operationId] = operation
            
            val success = performSafeTransaction(fragmentManager) { transaction ->
                transaction.remove(fragment)
            }
            
            if (success) {
                updateFragmentState(fragment, FragmentState.REMOVED)
                fragmentStates.remove(fragment.javaClass.simpleName)
                Log.i(TAG, "Fragment removed successfully: ${fragment.javaClass.simpleName}")
            } else {
                Log.e(TAG, "Failed to remove fragment: ${fragment.javaClass.simpleName}")
            }
            
            pendingOperations.remove(operationId)
            success
            
        } catch (e: Exception) {
            Log.e(TAG, "Error removing fragment", e)
            crashDiagnosticManager.logCrashDetails(e, "safeRemoveFragment", fragment.javaClass.simpleName)
            false
        }
    }

    /**
     * Validate if a fragment transaction can be safely performed
     */
    fun validateFragmentTransaction(fragmentManager: FragmentManager? = null): FragmentTransactionValidation {
        return try {
            val fm = fragmentManager ?: activity.supportFragmentManager
            
            val isActivityFinishing = activity.isFinishing || activity.isDestroyed
            val isFragmentManagerDestroyed = fm.isDestroyed
            val isStateSaved = fm.isStateSaved
            val canCommit = !isActivityFinishing && !isFragmentManagerDestroyed && !isStateSaved
            
            val validationLevel = when {
                canCommit -> TransactionValidationLevel.SAFE
                !isActivityFinishing && !isFragmentManagerDestroyed -> TransactionValidationLevel.ALLOW_STATE_LOSS
                !isActivityFinishing -> TransactionValidationLevel.UNSAFE_MANAGER
                else -> TransactionValidationLevel.UNSAFE_ACTIVITY
            }
            
            FragmentTransactionValidation(
                canCommit = canCommit,
                canCommitAllowingStateLoss = !isActivityFinishing && !isFragmentManagerDestroyed,
                isActivityFinishing = isActivityFinishing,
                isFragmentManagerDestroyed = isFragmentManagerDestroyed,
                isStateSaved = isStateSaved,
                validationLevel = validationLevel,
                timestamp = System.currentTimeMillis()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating fragment transaction", e)
            crashDiagnosticManager.logCrashDetails(e, "validateFragmentTransaction")
            
            FragmentTransactionValidation(
                canCommit = false,
                canCommitAllowingStateLoss = false,
                isActivityFinishing = true,
                isFragmentManagerDestroyed = true,
                isStateSaved = true,
                validationLevel = TransactionValidationLevel.UNSAFE_ACTIVITY,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Handle fragment errors with recovery strategies
     */
    fun handleFragmentError(
        fragment: Fragment,
        error: Exception,
        operation: FragmentOperation
    ): FragmentErrorRecovery {
        Log.w(TAG, "Handling fragment error for ${fragment.javaClass.simpleName}", error)
        
        crashDiagnosticManager.logCrashDetails(error, "fragmentError", fragment.javaClass.simpleName)
        
        return try {
            val errorType = classifyFragmentError(error)
            val recoveryStrategy = determineRecoveryStrategy(errorType, operation)
            
            val recoveryResult = when (recoveryStrategy) {
                RecoveryStrategy.RETRY_WITH_STATE_LOSS -> {
                    retryOperationWithStateLoss(fragment, operation)
                }
                RecoveryStrategy.RETRY_AFTER_DELAY -> {
                    scheduleRetryOperation(fragment, operation)
                    true // Assume success for async operation
                }
                RecoveryStrategy.FORCE_CLEANUP -> {
                    forceFragmentCleanup(fragment)
                }
                RecoveryStrategy.ABORT -> {
                    false
                }
            }
            
            FragmentErrorRecovery(
                errorType = errorType,
                recoveryStrategy = recoveryStrategy,
                recoverySuccessful = recoveryResult,
                canRetry = recoveryStrategy != RecoveryStrategy.ABORT,
                timestamp = System.currentTimeMillis()
            )
            
        } catch (recoveryError: Exception) {
            Log.e(TAG, "Error during fragment error recovery", recoveryError)
            crashDiagnosticManager.logCrashDetails(recoveryError, "fragmentErrorRecovery")
            
            FragmentErrorRecovery(
                errorType = FragmentErrorType.UNKNOWN,
                recoveryStrategy = RecoveryStrategy.ABORT,
                recoverySuccessful = false,
                canRetry = false,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Cancel all pending fragment operations (called when activity is destroyed)
     */
    fun cancelPendingOperations() {
        Log.i(TAG, "Cancelling ${pendingOperations.size} pending fragment operations")
        
        isDestroyed.set(true)
        pendingOperations.clear()
        fragmentStates.clear()
        
        crashDiagnosticManager.updateComponentState(
            "SafeFragmentManager",
            "DESTROYED",
            mapOf("cancelledOperations" to pendingOperations.size)
        )
    }

    /**
     * Get current fragment management statistics
     */
    fun getFragmentStatistics(): FragmentStatistics {
        return FragmentStatistics(
            totalFragments = fragmentStates.size,
            visibleFragments = fragmentStates.values.count { it == FragmentState.VISIBLE },
            hiddenFragments = fragmentStates.values.count { it == FragmentState.HIDDEN },
            pendingOperations = pendingOperations.size,
            isManagerDestroyed = isDestroyed.get(),
            timestamp = System.currentTimeMillis()
        )
    }

    // Private helper methods

    private fun performSafeTransaction(
        fragmentManager: FragmentManager,
        transactionBlock: (FragmentTransaction) -> FragmentTransaction
    ): Boolean {
        return try {
            val validation = validateFragmentTransaction(fragmentManager)
            
            when (validation.validationLevel) {
                TransactionValidationLevel.SAFE -> {
                    val transaction = fragmentManager.beginTransaction()
                    transactionBlock(transaction).commit()
                    true
                }
                TransactionValidationLevel.ALLOW_STATE_LOSS -> {
                    Log.w(TAG, "Using commitAllowingStateLoss due to saved state")
                    val transaction = fragmentManager.beginTransaction()
                    transactionBlock(transaction).commitAllowingStateLoss()
                    true
                }
                else -> {
                    Log.e(TAG, "Cannot perform fragment transaction: ${validation.validationLevel}")
                    false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing fragment transaction", e)
            crashDiagnosticManager.logCrashDetails(e, "performSafeTransaction")
            false
        }
    }

    private fun validateFragmentOperation(
        fragment: Fragment,
        operation: FragmentOperation
    ): OperationValidation {
        try {
            val isActivityValid = !activity.isFinishing && !activity.isDestroyed
            val isFragmentValid = !fragment.isRemoving && !fragment.isDetached
            val isLifecycleValid = fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
            
            val canProceed = isActivityValid && isFragmentValid && isLifecycleValid
            
            val reason = when {
                !isActivityValid -> "Activity is finishing or destroyed"
                !isFragmentValid -> "Fragment is removing or detached"
                !isLifecycleValid -> "Fragment lifecycle state is invalid"
                else -> null
            }
            
            return OperationValidation(
                canProceed = canProceed,
                reason = reason,
                isActivityValid = isActivityValid,
                isFragmentValid = isFragmentValid,
                isLifecycleValid = isLifecycleValid
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error validating fragment operation", e)
            return OperationValidation(
                canProceed = false,
                reason = "Validation error: ${e.message}",
                isActivityValid = false,
                isFragmentValid = false,
                isLifecycleValid = false
            )
        }
    }

    private fun isFragmentVisible(fragment: Fragment, fragmentManager: FragmentManager): Boolean {
        return try {
            fragment.isAdded && fragment.isVisible && !fragment.isHidden
        } catch (e: Exception) {
            Log.w(TAG, "Error checking fragment visibility", e)
            false
        }
    }

    private fun updateFragmentState(fragment: Fragment, state: FragmentState) {
        fragmentStates[fragment.javaClass.simpleName] = state
        
        crashDiagnosticManager.updateComponentState(
            fragment.javaClass.simpleName,
            state.name,
            mapOf("timestamp" to System.currentTimeMillis())
        )
    }

    private fun generateOperationId(operation: String, fragment: Fragment): String {
        return "${operation}_${fragment.javaClass.simpleName}_${System.currentTimeMillis()}"
    }

    private fun classifyFragmentError(error: Exception): FragmentErrorType {
        return when {
            error.message?.contains("state loss", ignoreCase = true) == true -> FragmentErrorType.STATE_LOSS
            error.message?.contains("not attached", ignoreCase = true) == true -> FragmentErrorType.NOT_ATTACHED
            error.message?.contains("destroyed", ignoreCase = true) == true -> FragmentErrorType.LIFECYCLE_ERROR
            error is IllegalStateException -> FragmentErrorType.ILLEGAL_STATE
            else -> FragmentErrorType.UNKNOWN
        }
    }

    private fun determineRecoveryStrategy(
        errorType: FragmentErrorType,
        operation: FragmentOperation
    ): RecoveryStrategy {
        return when (errorType) {
            FragmentErrorType.STATE_LOSS -> RecoveryStrategy.RETRY_WITH_STATE_LOSS
            FragmentErrorType.NOT_ATTACHED -> RecoveryStrategy.RETRY_AFTER_DELAY
            FragmentErrorType.LIFECYCLE_ERROR -> RecoveryStrategy.FORCE_CLEANUP
            FragmentErrorType.ILLEGAL_STATE -> RecoveryStrategy.RETRY_WITH_STATE_LOSS
            FragmentErrorType.UNKNOWN -> RecoveryStrategy.ABORT
        }
    }

    private fun retryOperationWithStateLoss(fragment: Fragment, operation: FragmentOperation): Boolean {
        return try {
            Log.d(TAG, "Retrying operation with state loss: $operation")
            
            val fragmentManager = activity.supportFragmentManager
            performSafeTransaction(fragmentManager) { transaction ->
                when (operation) {
                    FragmentOperation.SHOW -> {
                        // Check if fragment is already added before showing
                        if (fragment.isAdded) {
                            transaction.show(fragment)
                        } else {
                            // Fragment not added, this shouldn't happen in retry but handle it
                            Log.w(TAG, "Fragment not added during retry show operation")
                            transaction.add(R.id.main_browse_fragment, fragment)
                        }
                    }
                    FragmentOperation.HIDE -> transaction.hide(fragment)
                    FragmentOperation.REMOVE -> transaction.remove(fragment)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Retry with state loss failed", e)
            false
        }
    }

    private fun scheduleRetryOperation(fragment: Fragment, operation: FragmentOperation) {
        operationScope.launch {
            delay(RETRY_DELAY_MS)
            
            if (!isDestroyed.get()) {
                when (operation) {
                    FragmentOperation.SHOW -> {
                        // Use the main fragment container ID from MainActivity
                        safeShowFragment(fragment, R.id.main_browse_fragment)
                    }
                    FragmentOperation.HIDE -> safeHideFragment(fragment)
                    FragmentOperation.REMOVE -> safeRemoveFragment(fragment)
                }
            }
        }
    }

    private fun forceFragmentCleanup(fragment: Fragment): Boolean {
        return try {
            Log.w(TAG, "Forcing fragment cleanup: ${fragment.javaClass.simpleName}")
            
            // Remove from our tracking
            fragmentStates.remove(fragment.javaClass.simpleName)
            
            // Try to clean up fragment state
            if (fragment.isAdded) {
                val fragmentManager = activity.supportFragmentManager
                val transaction = fragmentManager.beginTransaction()
                transaction.remove(fragment)
                transaction.commitAllowingStateLoss()
            }
            
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Force cleanup failed", e)
            false
        }
    }
}

/**
 * Data classes for fragment management
 */

data class PendingOperation(
    val id: String,
    val type: FragmentOperation,
    val fragment: Fragment,
    val timestamp: Long
)

data class FragmentTransactionValidation(
    val canCommit: Boolean,
    val canCommitAllowingStateLoss: Boolean,
    val isActivityFinishing: Boolean,
    val isFragmentManagerDestroyed: Boolean,
    val isStateSaved: Boolean,
    val validationLevel: TransactionValidationLevel,
    val timestamp: Long
)

data class OperationValidation(
    val canProceed: Boolean,
    val reason: String?,
    val isActivityValid: Boolean,
    val isFragmentValid: Boolean,
    val isLifecycleValid: Boolean
)

data class FragmentErrorRecovery(
    val errorType: FragmentErrorType,
    val recoveryStrategy: RecoveryStrategy,
    val recoverySuccessful: Boolean,
    val canRetry: Boolean,
    val timestamp: Long
)

data class FragmentStatistics(
    val totalFragments: Int,
    val visibleFragments: Int,
    val hiddenFragments: Int,
    val pendingOperations: Int,
    val isManagerDestroyed: Boolean,
    val timestamp: Long
)

/**
 * Enums for fragment management
 */

enum class FragmentOperation {
    SHOW, HIDE, REMOVE
}

enum class FragmentState {
    VISIBLE, HIDDEN, REMOVED
}

enum class TransactionValidationLevel {
    SAFE,                   // Can commit normally
    ALLOW_STATE_LOSS,       // Must use commitAllowingStateLoss
    UNSAFE_MANAGER,         // FragmentManager is destroyed
    UNSAFE_ACTIVITY         // Activity is finishing/destroyed
}

enum class FragmentErrorType {
    STATE_LOSS,             // State was saved, cannot commit
    NOT_ATTACHED,           // Fragment not attached to activity
    LIFECYCLE_ERROR,        // Fragment lifecycle issue
    ILLEGAL_STATE,          // General illegal state
    UNKNOWN                 // Unknown error type
}

enum class RecoveryStrategy {
    RETRY_WITH_STATE_LOSS,  // Retry using commitAllowingStateLoss
    RETRY_AFTER_DELAY,      // Wait and retry
    FORCE_CLEANUP,          // Force cleanup and remove
    ABORT                   // Cannot recover
}