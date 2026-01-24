package com.thirutricks.tllplayer.infrastructure

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

/**
 * Interface for listening to focus changes and conflicts.
 */
interface FocusListener {
    fun onFocusChanged(view: View?, source: FocusSource, hasFocus: Boolean)
    fun onFocusConflict(conflictingSources: List<FocusSource>)
    fun onFocusRecovered(recoveryAction: String)
}



/**
 * Enum representing different focus modes for the menu system.
 */
enum class FocusMode {
    CATEGORY_PANEL,     // Focus is on category list
    CHANNEL_PANEL,      // Focus is on channel list
    TRANSITIONING,      // Focus is transitioning between panels
    DISABLED            // Focus management is disabled
}

/**
 * Data class representing focus state information.
 */
data class FocusState(
    val currentView: View?,
    val source: FocusSource,
    val mode: FocusMode,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Centralized focus state management system for the menu system.
 * Implements Requirements 3.1, 3.2, 3.3, 3.4, 3.5
 * 
 * This component provides:
 * - Centralized focus state coordination
 * - Focus conflict detection and resolution
 * - Focus transition coordination between panels
 * - Focus recovery mechanisms for deadlock scenarios
 */
class FocusManager(
    private val errorHandler: ErrorHandler,
    private val logger: MenuLogger
) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val focusScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Focus state management
    private val currentFocusState = AtomicReference<FocusState?>(null)
    private val currentMode = AtomicReference(FocusMode.DISABLED)
    private val isTransitioning = AtomicBoolean(false)
    private val isRecovering = AtomicBoolean(false)
    
    // Focus tracking
    private val focusListeners = ConcurrentHashMap<String, FocusListener>()
    private val activeFocusSources = ConcurrentHashMap<FocusSource, View?>()
    private val focusHistory = mutableListOf<FocusState>()
    private val maxHistorySize = 20
    
    // Conflict detection
    private val conflictDetectionEnabled = AtomicBoolean(true)
    private val lastConflictTime = AtomicReference(0L)
    private val conflictCooldownMs = 1000L // 1 second cooldown between conflict detections
    
    // Recovery mechanisms
    private val recoveryAttempts = AtomicInteger(0)
    private val maxRecoveryAttempts = 3
    private val recoveryTimeoutMs = 5000L // 5 seconds
    
    /**
     * Request focus for a view from a specific source.
     * Requirement 3.1: Coordinate all focus-related state updates centrally
     */
    fun requestFocus(view: View, source: FocusSource): Boolean {
        if (isRecovering.get()) {
            logger.logWithContext(
                Log.DEBUG,
                "FocusManager",
                "Focus request ignored during recovery",
                mapOf(
                    "source" to source.name,
                    "view" to view.javaClass.simpleName
                )
            )
            return false
        }
        
        return try {
            logger.logWithContext(
                Log.DEBUG,
                "FocusManager",
                "Focus requested",
                mapOf(
                    "source" to source.name,
                    "view" to view.javaClass.simpleName,
                    "current_mode" to currentMode.get().name
                )
            )
            
            // Check for conflicts before granting focus
            if (conflictDetectionEnabled.get() && detectFocusConflict(source, view)) {
                return handleFocusConflict()
            }
            
            // Update focus state
            val previousState = currentFocusState.get()
            val newState = FocusState(view, source, currentMode.get())
            
            if (currentFocusState.compareAndSet(previousState, newState)) {
                // Update active sources tracking
                activeFocusSources[source] = view
                
                // Add to history
                addToHistory(newState)
                
                // Actually request focus on the view
                val focusGranted = if (Looper.myLooper() == Looper.getMainLooper()) {
                    view.requestFocus()
                } else {
                    var result = false
                    mainHandler.post {
                        result = view.requestFocus()
                    }
                    result
                }
                
                if (focusGranted) {
                    // Notify listeners
                    notifyFocusChanged(view, source, true)
                    
                    logger.logWithContext(
                        Log.DEBUG,
                        "FocusManager",
                        "Focus granted successfully",
                        mapOf(
                            "source" to source.name,
                            "view" to view.javaClass.simpleName
                        )
                    )
                } else {
                    // Revert state if focus was not granted
                    currentFocusState.compareAndSet(newState, previousState)
                    activeFocusSources.remove(source)
                    
                    logger.logWithContext(
                        Log.WARN,
                        "FocusManager",
                        "Focus request failed - view did not accept focus",
                        mapOf(
                            "source" to source.name,
                            "view" to view.javaClass.simpleName
                        )
                    )
                }
                
                focusGranted
            } else {
                logger.log(Log.WARN, "FocusManager", "Focus state update failed due to concurrent modification")
                false
            }
        } catch (e: Exception) {
            logger.logWithContext(
                Log.ERROR,
                "FocusManager",
                "Error requesting focus",
                mapOf(
                    "source" to source.name,
                    "error" to (e.message ?: "Unknown error")
                ),
                e
            )
            
            errorHandler.handleError(MenuError.FocusDeadlock(listOf(source)))
            false
        }
    }
    
    /**
     * Clear focus from a specific source.
     * Requirement 3.1: Coordinate all focus-related state updates centrally
     */
    fun clearFocus(source: FocusSource) {
        try {
            logger.logWithContext(
                Log.DEBUG,
                "FocusManager",
                "Focus cleared",
                mapOf("source" to source.name)
            )
            
            val currentState = currentFocusState.get()
            if (currentState?.source == source) {
                // Clear the current focus state
                currentFocusState.set(null)
                
                // Clear the view's focus if it's on the main thread
                currentState.currentView?.let { view ->
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        view.clearFocus()
                    } else {
                        mainHandler.post {
                            view.clearFocus()
                        }
                    }
                }
                
                // Notify listeners
                notifyFocusChanged(currentState.currentView, source, false)
            }
            
            // Remove from active sources
            activeFocusSources.remove(source)
            
        } catch (e: Exception) {
            logger.logWithContext(
                Log.ERROR,
                "FocusManager",
                "Error clearing focus",
                mapOf(
                    "source" to source.name,
                    "error" to (e.message ?: "Unknown error")
                ),
                e
            )
        }
    }
    
    /**
     * Set the current focus mode.
     * Requirement 3.4: Handle smooth focus transitions between panels
     */
    fun setFocusMode(mode: FocusMode) {
        val previousMode = currentMode.getAndSet(mode)
        
        logger.logWithContext(
            Log.DEBUG,
            "FocusManager",
            "Focus mode changed",
            mapOf(
                "previous_mode" to previousMode.name,
                "new_mode" to mode.name
            )
        )
        
        // Update current state with new mode
        val currentState = currentFocusState.get()
        if (currentState != null) {
            val updatedState = currentState.copy(mode = mode)
            currentFocusState.set(updatedState)
            addToHistory(updatedState)
        }
        
        // Handle mode-specific logic
        when (mode) {
            FocusMode.TRANSITIONING -> {
                isTransitioning.set(true)
                // Start transition timeout
                focusScope.launch {
                    delay(2000) // 2 second timeout for transitions
                    if (isTransitioning.get() && currentMode.get() == FocusMode.TRANSITIONING) {
                        logger.log(Log.WARN, "FocusManager", "Focus transition timeout - recovering")
                        recoverFromTransitionTimeout()
                    }
                }
            }
            else -> {
                isTransitioning.set(false)
            }
        }
    }
    
    /**
     * Get the currently focused view.
     */
    fun getCurrentFocus(): View? {
        return currentFocusState.get()?.currentView
    }
    
    /**
     * Get the current focus mode.
     */
    fun getCurrentMode(): FocusMode {
        return currentMode.get()
    }
    
    /**
     * Get the current focus source.
     */
    fun getCurrentSource(): FocusSource? {
        return currentFocusState.get()?.source
    }
    
    /**
     * Register a focus listener.
     */
    fun registerFocusListener(listener: FocusListener) {
        val listenerId = listener.hashCode().toString()
        focusListeners[listenerId] = listener
        
        logger.logWithContext(
            Log.DEBUG,
            "FocusManager",
            "Focus listener registered",
            mapOf(
                "listener_id" to listenerId,
                "total_listeners" to focusListeners.size
            )
        )
    }
    
    /**
     * Unregister a focus listener.
     */
    fun unregisterFocusListener(listener: FocusListener) {
        val listenerId = listener.hashCode().toString()
        focusListeners.remove(listenerId)
        
        logger.logWithContext(
            Log.DEBUG,
            "FocusManager",
            "Focus listener unregistered",
            mapOf(
                "listener_id" to listenerId,
                "remaining_listeners" to focusListeners.size
            )
        )
    }
    
    /**
     * Handle focus conflicts by implementing resolution strategies.
     * Requirement 3.2: Prevent conflicts and ensure single source of truth
     * Requirement 3.5: Implement recovery mechanisms for deadlock scenarios
     */
    fun handleFocusConflict(): Boolean {
        val now = System.currentTimeMillis()
        val lastConflict = lastConflictTime.get()
        
        // Implement cooldown to prevent rapid conflict detection
        if (now - lastConflict < conflictCooldownMs) {
            logger.log(Log.DEBUG, "FocusManager", "Focus conflict ignored due to cooldown")
            return false
        }
        
        lastConflictTime.set(now)
        
        try {
            logger.log(Log.WARN, "FocusManager", "Focus conflict detected - attempting resolution")
            
            val conflictingSources = activeFocusSources.keys.toList()
            
            // Notify listeners about the conflict
            notifyFocusConflict(conflictingSources)
            
            // Attempt resolution based on current mode and priority
            val resolved = when (currentMode.get()) {
                FocusMode.CATEGORY_PANEL -> {
                    // Priority: GROUP_ADAPTER > MENU_FRAGMENT > others
                    resolveFocusConflict(listOf(FocusSource.GROUP_ADAPTER, FocusSource.MENU_FRAGMENT))
                }
                FocusMode.CHANNEL_PANEL -> {
                    // Priority: LIST_ADAPTER > MENU_FRAGMENT > others
                    resolveFocusConflict(listOf(FocusSource.LIST_ADAPTER, FocusSource.MENU_FRAGMENT))
                }
                FocusMode.TRANSITIONING -> {
                    // During transition, clear all focus and let the transition complete
                    clearAllFocus()
                    true
                }
                FocusMode.DISABLED -> {
                    // Clear all focus when disabled
                    clearAllFocus()
                    true
                }
            }
            
            if (resolved) {
                logger.log(Log.INFO, "FocusManager", "Focus conflict resolved successfully")
                notifyFocusRecovered("Conflict resolved using priority-based resolution")
            } else {
                logger.log(Log.ERROR, "FocusManager", "Failed to resolve focus conflict")
                errorHandler.handleError(MenuError.FocusDeadlock(conflictingSources))
            }
            
            return resolved
            
        } catch (e: Exception) {
            logger.logWithContext(
                Log.ERROR,
                "FocusManager",
                "Error handling focus conflict",
                mapOf("error" to (e.message ?: "Unknown error")),
                e
            )
            
            // Last resort: clear all focus
            clearAllFocus()
            errorHandler.handleError(MenuError.FocusDeadlock(activeFocusSources.keys.toList()))
            return false
        }
    }
    
    /**
     * Perform focus recovery when deadlocks are detected.
     * Requirement 3.5: Implement recovery mechanisms for deadlock scenarios
     */
    fun recoverFromDeadlock(): Boolean {
        if (isRecovering.get()) {
            logger.log(Log.DEBUG, "FocusManager", "Recovery already in progress")
            return false
        }
        
        if (!isRecovering.compareAndSet(false, true)) {
            return false
        }
        
        val attempts = recoveryAttempts.incrementAndGet()
        
        return try {
            logger.logWithContext(
                Log.WARN,
                "FocusManager",
                "Starting focus deadlock recovery",
                mapOf(
                    "attempt" to attempts,
                    "max_attempts" to maxRecoveryAttempts
                )
            )
            
            if (attempts > maxRecoveryAttempts) {
                logger.log(Log.ERROR, "FocusManager", "Maximum recovery attempts exceeded")
                resetToDefaultState()
                return false
            }
            
            // Recovery strategy: Progressive escalation
            val recovered = when (attempts) {
                1 -> {
                    // First attempt: Clear conflicting focus and restore based on mode
                    clearAllFocus()
                    restoreFocusBasedOnMode()
                }
                2 -> {
                    // Second attempt: Reset to category panel mode
                    setFocusMode(FocusMode.CATEGORY_PANEL)
                    clearAllFocus()
                    true
                }
                else -> {
                    // Final attempt: Complete reset
                    resetToDefaultState()
                    true
                }
            }
            
            if (recovered) {
                logger.log(Log.INFO, "FocusManager", "Focus deadlock recovery successful")
                notifyFocusRecovered("Deadlock resolved using recovery strategy $attempts")
                
                // Reset recovery state after a delay
                focusScope.launch {
                    delay(recoveryTimeoutMs)
                    recoveryAttempts.set(0)
                }
            }
            
            recovered
            
        } catch (e: Exception) {
            logger.logWithContext(
                Log.ERROR,
                "FocusManager",
                "Error during focus recovery",
                mapOf(
                    "attempt" to attempts,
                    "error" to (e.message ?: "Unknown error")
                ),
                e
            )
            false
        } finally {
            isRecovering.set(false)
        }
    }
    
    /**
     * Get focus statistics for monitoring and debugging.
     */
    fun getFocusStatistics(): FocusStatistics {
        val currentState = currentFocusState.get()
        return FocusStatistics(
            currentMode = currentMode.get(),
            currentSource = currentState?.source,
            isTransitioning = isTransitioning.get(),
            isRecovering = isRecovering.get(),
            activeSources = activeFocusSources.keys.toList(),
            listenerCount = focusListeners.size,
            historySize = focusHistory.size,
            recoveryAttempts = recoveryAttempts.get(),
            lastConflictTime = lastConflictTime.get()
        )
    }
    
    /**
     * Cleanup resources and reset state.
     */
    fun cleanup() {
        logger.log(Log.INFO, "FocusManager", "Cleaning up focus manager")
        
        // Cancel any ongoing operations
        focusScope.cancel()
        
        // Clear all state
        clearAllFocus()
        focusListeners.clear()
        focusHistory.clear()
        
        // Reset atomic values
        currentFocusState.set(null)
        currentMode.set(FocusMode.DISABLED)
        isTransitioning.set(false)
        isRecovering.set(false)
        recoveryAttempts.set(0)
        lastConflictTime.set(0L)
        
        logger.log(Log.INFO, "FocusManager", "Focus manager cleanup completed")
    }
    
    // Private helper methods
    
    private fun detectFocusConflict(source: FocusSource, view: View): Boolean {
        val currentState = currentFocusState.get()
        
        // No conflict if no current focus
        if (currentState == null) {
            return false
        }
        
        // No conflict if same source
        if (currentState.source == source) {
            return false
        }
        
        // Check if multiple sources are trying to claim focus
        val activeSourceCount = activeFocusSources.size
        if (activeSourceCount > 1) {
            logger.logWithContext(
                Log.WARN,
                "FocusManager",
                "Multiple active focus sources detected",
                mapOf(
                    "active_sources" to activeFocusSources.keys.map { it.name },
                    "requesting_source" to source.name
                )
            )
            return true
        }
        
        return false
    }
    
    private fun resolveFocusConflict(priorityOrder: List<FocusSource>): Boolean {
        try {
            // Find the highest priority source that has an active view
            for (source in priorityOrder) {
                val view = activeFocusSources[source]
                if (view != null) {
                    // Clear all other sources
                    activeFocusSources.keys.filter { it != source }.forEach { otherSource ->
                        activeFocusSources.remove(otherSource)
                    }
                    
                    // Set this as the current focus
                    val newState = FocusState(view, source, currentMode.get())
                    currentFocusState.set(newState)
                    addToHistory(newState)
                    
                    logger.logWithContext(
                        Log.INFO,
                        "FocusManager",
                        "Focus conflict resolved",
                        mapOf(
                            "winning_source" to source.name,
                            "priority_order" to priorityOrder.map { it.name }
                        )
                    )
                    
                    return true
                }
            }
            
            // If no priority source found, clear all focus
            clearAllFocus()
            return true
            
        } catch (e: Exception) {
            logger.logWithContext(
                Log.ERROR,
                "FocusManager",
                "Error resolving focus conflict",
                mapOf("error" to (e.message ?: "Unknown error")),
                e
            )
            return false
        }
    }
    
    private fun clearAllFocus() {
        logger.log(Log.DEBUG, "FocusManager", "Clearing all focus")
        
        // Clear current state
        val currentState = currentFocusState.getAndSet(null)
        
        // Clear focus from current view
        currentState?.currentView?.let { view ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                view.clearFocus()
            } else {
                mainHandler.post {
                    view.clearFocus()
                }
            }
        }
        
        // Clear all active sources
        activeFocusSources.clear()
        
        // Notify listeners
        if (currentState != null) {
            notifyFocusChanged(currentState.currentView, currentState.source, false)
        }
    }
    
    private fun restoreFocusBasedOnMode(): Boolean {
        return when (currentMode.get()) {
            FocusMode.CATEGORY_PANEL -> {
                // Try to restore focus to group adapter
                val groupView = activeFocusSources[FocusSource.GROUP_ADAPTER]
                if (groupView != null) {
                    requestFocus(groupView, FocusSource.GROUP_ADAPTER)
                } else {
                    true // No view to restore, but that's okay
                }
            }
            FocusMode.CHANNEL_PANEL -> {
                // Try to restore focus to list adapter
                val listView = activeFocusSources[FocusSource.LIST_ADAPTER]
                if (listView != null) {
                    requestFocus(listView, FocusSource.LIST_ADAPTER)
                } else {
                    true // No view to restore, but that's okay
                }
            }
            else -> true
        }
    }
    
    private fun resetToDefaultState() {
        logger.log(Log.INFO, "FocusManager", "Resetting to default state")
        
        clearAllFocus()
        currentMode.set(FocusMode.CATEGORY_PANEL)
        isTransitioning.set(false)
        isRecovering.set(false)
        recoveryAttempts.set(0)
        
        notifyFocusRecovered("Reset to default state (category panel)")
    }
    
    private fun recoverFromTransitionTimeout() {
        logger.log(Log.WARN, "FocusManager", "Recovering from transition timeout")
        
        isTransitioning.set(false)
        
        // Determine appropriate mode based on active sources
        val mode = when {
            activeFocusSources.containsKey(FocusSource.LIST_ADAPTER) -> FocusMode.CHANNEL_PANEL
            activeFocusSources.containsKey(FocusSource.GROUP_ADAPTER) -> FocusMode.CATEGORY_PANEL
            else -> FocusMode.CATEGORY_PANEL // Default fallback
        }
        
        setFocusMode(mode)
        notifyFocusRecovered("Transition timeout recovery - set mode to $mode")
    }
    
    private fun addToHistory(state: FocusState) {
        focusHistory.add(state)
        
        // Maintain history size limit
        if (focusHistory.size > maxHistorySize) {
            focusHistory.removeAt(0)
        }
    }
    
    private fun notifyFocusChanged(view: View?, source: FocusSource, hasFocus: Boolean) {
        focusListeners.values.forEach { listener ->
            try {
                listener.onFocusChanged(view, source, hasFocus)
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "FocusManager",
                    "Error notifying focus listener",
                    mapOf("listener" to listener.hashCode()),
                    e
                )
            }
        }
    }
    
    private fun notifyFocusConflict(conflictingSources: List<FocusSource>) {
        focusListeners.values.forEach { listener ->
            try {
                listener.onFocusConflict(conflictingSources)
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "FocusManager",
                    "Error notifying focus conflict listener",
                    mapOf("listener" to listener.hashCode()),
                    e
                )
            }
        }
    }
    
    private fun notifyFocusRecovered(recoveryAction: String) {
        focusListeners.values.forEach { listener ->
            try {
                listener.onFocusRecovered(recoveryAction)
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "FocusManager",
                    "Error notifying focus recovery listener",
                    mapOf("listener" to listener.hashCode()),
                    e
                )
            }
        }
    }
}

/**
 * Statistics about focus manager state for monitoring and debugging.
 */
data class FocusStatistics(
    val currentMode: FocusMode,
    val currentSource: FocusSource?,
    val isTransitioning: Boolean,
    val isRecovering: Boolean,
    val activeSources: List<FocusSource>,
    val listenerCount: Int,
    val historySize: Int,
    val recoveryAttempts: Int,
    val lastConflictTime: Long
)