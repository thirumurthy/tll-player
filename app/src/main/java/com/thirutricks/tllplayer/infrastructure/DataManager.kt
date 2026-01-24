package com.thirutricks.tllplayer.infrastructure

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.thirutricks.tllplayer.models.TVGroupModel
import com.thirutricks.tllplayer.models.TVList
import com.thirutricks.tllplayer.models.TVListModel
import com.thirutricks.tllplayer.models.TVModel
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Interface for observing data changes and errors.
 */
interface DataObserver {
    fun onDataReady()
    fun onDataError(error: DataError)
    fun onDataUpdated()
}

/**
 * Represents data validation results.
 */
data class DataValidationResult(
    val isValid: Boolean,
    val errors: List<ValidationError>,
    val warnings: List<ValidationWarning>
)

/**
 * Sealed class for validation errors.
 */
sealed class ValidationError {
    object GroupModelNull : ValidationError()
    object GroupModelEmpty : ValidationError()
    data class ListModelNull(val position: Int) : ValidationError()
    data class InvalidPosition(val position: Int, val maxSize: Int) : ValidationError()
    data class DataCorruption(val details: String) : ValidationError()
}

/**
 * Sealed class for validation warnings.
 */
sealed class ValidationWarning {
    data class EmptyCategory(val categoryName: String) : ValidationWarning()
    data class MissingFavorites(val count: Int) : ValidationWarning()
    data class PerformanceWarning(val message: String) : ValidationWarning()
}

/**
 * Sealed class for data errors.
 */
sealed class DataError {
    object InitializationFailed : DataError()
    object ValidationFailed : DataError()
    data class UpdateFailed(val cause: Throwable) : DataError()
    data class ThreadSafetyViolation(val operation: String) : DataError()
}

/**
 * Central data manager for safe data initialization, validation, and thread-safe updates.
 * Implements Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 7.1, 7.2, 7.3, 7.4, 7.5
 */
class DataManager(
    private val errorHandler: ErrorHandler,
    private val logger: MenuLogger
) {
    
    private val dataLock = ReentrantReadWriteLock()
    private val observers = ConcurrentHashMap<String, DataObserver>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dataScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // State tracking
    private val isInitialized = AtomicBoolean(false)
    private val isUpdating = AtomicBoolean(false)
    private val lastValidationTime = AtomicBoolean(false)
    
    // Cached validation results
    @Volatile
    private var lastValidationResult: DataValidationResult? = null
    private var lastValidationTimestamp = 0L
    private val validationCacheTimeout = 5000L // 5 seconds
    
    /**
     * Check if data is ready for use.
     * Requirement 1.1: Ensure all required data models are initialized before adapter setup
     */
    fun isDataReady(): Boolean {
        return dataLock.read {
            try {
                val groupModel = TVList.groupModel
                val listModel = TVList.listModel
                
                val ready = isInitialized.get() && 
                           groupModel.size() > 0 && 
                           listModel.isNotEmpty()
                
                logger.logWithContext(
                    Log.DEBUG,
                    "DataManager",
                    "Data readiness check",
                    mapOf(
                        "ready" to ready,
                        "initialized" to isInitialized.get(),
                        "group_size" to groupModel.size(),
                        "list_size" to listModel.size
                    )
                )
                
                ready
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "DataManager",
                    "Error checking data readiness",
                    mapOf("error" to (e.message ?: "Unknown error")),
                    e
                )
                false
            }
        }
    }
    
    /**
     * Validate data integrity and structure.
     * Requirements 1.2, 1.4: Provide safe fallback states and validate data integrity
     */
    fun validateData(): DataValidationResult {
        return dataLock.read {
            try {
                // Check cache first
                val now = System.currentTimeMillis()
                lastValidationResult?.let { cached ->
                    if (now - lastValidationTimestamp < validationCacheTimeout) {
                        return@read cached
                    }
                }
                
                val errors = mutableListOf<ValidationError>()
                val warnings = mutableListOf<ValidationWarning>()
                
                val groupModel = TVList.groupModel
                val listModel = TVList.listModel
                
                // Validate group model
                if (groupModel.size() == 0) {
                    errors.add(ValidationError.GroupModelEmpty)
                }
                
                // Validate essential categories exist (My Collection, Favourites, All channels)
                if (groupModel.size() < 3) {
                    errors.add(ValidationError.DataCorruption("Missing essential categories"))
                }
                
                // Validate list models
                for (i in 0 until groupModel.size()) {
                    val tvListModel = groupModel.getTVListModel(i)
                    if (tvListModel == null) {
                        errors.add(ValidationError.ListModelNull(i))
                        continue
                    }
                    
                    // Check for empty categories (warning only)
                    if (tvListModel.size() == 0 && i >= 3) { // Skip built-in categories
                        warnings.add(ValidationWarning.EmptyCategory(tvListModel.getName()))
                    }
                }
                
                // Validate global list model consistency
                if (listModel.isEmpty() && groupModel.size() > 3) {
                    errors.add(ValidationError.DataCorruption("Global list model is empty but categories exist"))
                }
                
                // Check for data consistency between global list and "All channels" category
                val allChannelsModel = groupModel.getTVListModel(2)
                if (allChannelsModel != null) {
                    val allChannelsSize = allChannelsModel.size()
                    if (allChannelsSize != listModel.size) {
                        warnings.add(ValidationWarning.PerformanceWarning(
                            "Inconsistency between global list (${listModel.size}) and All channels ($allChannelsSize)"
                        ))
                    }
                }
                
                // Performance warnings
                if (listModel.size > 10000) {
                    warnings.add(ValidationWarning.PerformanceWarning(
                        "Large channel list may impact performance: ${listModel.size} channels"
                    ))
                }
                
                val result = DataValidationResult(
                    isValid = errors.isEmpty(),
                    errors = errors,
                    warnings = warnings
                )
                
                // Cache the result
                lastValidationResult = result
                lastValidationTimestamp = now
                
                logger.logWithContext(
                    if (result.isValid) Log.DEBUG else Log.WARN,
                    "DataManager",
                    "Data validation completed",
                    mapOf(
                        "valid" to result.isValid,
                        "errors" to result.errors.size,
                        "warnings" to result.warnings.size,
                        "group_size" to groupModel.size(),
                        "list_size" to listModel.size
                    )
                )
                
                result
            } catch (e: Exception) {
                val errorResult = DataValidationResult(
                    isValid = false,
                    errors = listOf(ValidationError.DataCorruption("Validation failed: ${e.message}")),
                    warnings = emptyList()
                )
                
                logger.logWithContext(
                    Log.ERROR,
                    "DataManager",
                    "Data validation error",
                    mapOf("error" to (e.message ?: "Unknown error")),
                    e
                )
                
                errorResult
            }
        }
    }
    
    /**
     * Safely get the group model with validation.
     * Requirement 1.2: Provide safe fallback states and prevent unsafe operations
     */
    fun safeGetGroupModel(): TVGroupModel? {
        return dataLock.read {
            try {
                if (!isDataReady()) {
                    logger.log(Log.WARN, "DataManager", "Attempted to get group model when data not ready")
                    return@read null
                }
                
                val groupModel = TVList.groupModel
                if (groupModel.size() == 0) {
                    logger.log(Log.WARN, "DataManager", "Group model is empty")
                    return@read null
                }
                
                groupModel
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "DataManager",
                    "Error getting group model",
                    mapOf("error" to (e.message ?: "Unknown error")),
                    e
                )
                null
            }
        }
    }
    
    /**
     * Safely get a list model at the specified position.
     * Requirement 1.2: Provide safe fallback states and prevent unsafe operations
     */
    fun safeGetListModel(position: Int): TVListModel? {
        return dataLock.read {
            try {
                if (!isDataReady()) {
                    logger.logWithContext(
                        Log.WARN,
                        "DataManager",
                        "Attempted to get list model when data not ready",
                        mapOf("position" to position)
                    )
                    return@read null
                }
                
                val groupModel = TVList.groupModel
                if (position < 0 || position >= groupModel.size()) {
                    logger.logWithContext(
                        Log.WARN,
                        "DataManager",
                        "Invalid position for list model",
                        mapOf(
                            "position" to position,
                            "max_size" to groupModel.size()
                        )
                    )
                    return@read null
                }
                
                groupModel.getTVListModel(position)
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "DataManager",
                    "Error getting list model",
                    mapOf(
                        "position" to position,
                        "error" to (e.message ?: "Unknown error")
                    ),
                    e
                )
                null
            }
        }
    }
    
    /**
     * Register a data observer.
     * Requirement 7.3: Coordinate updates to prevent race conditions
     */
    fun registerDataObserver(observer: DataObserver) {
        val observerId = observer.hashCode().toString()
        observers[observerId] = observer
        
        logger.logWithContext(
            Log.DEBUG,
            "DataManager",
            "Data observer registered",
            mapOf(
                "observer_id" to observerId,
                "total_observers" to observers.size
            )
        )
        
        // Notify immediately if data is ready
        if (isDataReady()) {
            try {
                observer.onDataReady()
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "DataManager",
                    "Error notifying observer on registration",
                    mapOf("observer_id" to observerId),
                    e
                )
            }
        }
    }
    
    /**
     * Unregister a data observer.
     */
    fun unregisterDataObserver(observer: DataObserver) {
        val observerId = observer.hashCode().toString()
        observers.remove(observerId)
        
        logger.logWithContext(
            Log.DEBUG,
            "DataManager",
            "Data observer unregistered",
            mapOf(
                "observer_id" to observerId,
                "remaining_observers" to observers.size
            )
        )
    }
    
    /**
     * Perform a safe data update operation with proper synchronization.
     * Requirements 1.3, 7.1, 7.2, 7.4, 7.5: Thread-safe coordination and proper main thread execution
     */
    fun performSafeUpdate(operation: suspend () -> Unit): Boolean {
        if (!isUpdating.compareAndSet(false, true)) {
            logger.log(Log.WARN, "DataManager", "Update already in progress, skipping")
            return false
        }
        
        return try {
            dataScope.launch {
                try {
                    logger.log(Log.DEBUG, "DataManager", "Starting safe data update")
                    val startTime = System.currentTimeMillis()
                    
                    // Perform the update operation
                    operation()
                    
                    // Validate data after update
                    val validationResult = validateData()
                    if (!validationResult.isValid) {
                        val error = DataError.ValidationFailed
                        notifyObserversError(error)
                        errorHandler.handleError(MenuError.ValidationWarning(
                            "data_validation",
                            "Data validation failed after update: ${validationResult.errors}"
                        ))
                    } else {
                        // Mark as initialized if this is the first successful update
                        if (!isInitialized.get()) {
                            isInitialized.set(true)
                        }
                        
                        // Notify observers on main thread
                        withContext(Dispatchers.Main) {
                            notifyObserversUpdated()
                        }
                    }
                    
                    val duration = System.currentTimeMillis() - startTime
                    logger.logWithContext(
                        Log.DEBUG,
                        "DataManager",
                        "Safe data update completed",
                        mapOf(
                            "duration_ms" to duration,
                            "validation_passed" to validationResult.isValid
                        )
                    )
                    
                } catch (e: Exception) {
                    logger.logWithContext(
                        Log.ERROR,
                        "DataManager",
                        "Error during safe data update",
                        mapOf("error" to (e.message ?: "Unknown error")),
                        e
                    )
                    
                    val error = DataError.UpdateFailed(e)
                    notifyObserversError(error)
                    errorHandler.handleError(MenuError.ThreadSynchronizationError(
                        "data_update",
                        e
                    ))
                } finally {
                    isUpdating.set(false)
                }
            }
            true
        } catch (e: Exception) {
            isUpdating.set(false)
            logger.logWithContext(
                Log.ERROR,
                "DataManager",
                "Failed to start safe data update",
                mapOf("error" to (e.message ?: "Unknown error")),
                e
            )
            false
        }
    }
    
    /**
     * Initialize the data manager and validate initial state.
     * Requirement 1.1: Ensure all required data models are initialized before adapter setup
     */
    fun initialize(): Boolean {
        return dataLock.write {
            try {
                logger.log(Log.INFO, "DataManager", "Initializing data manager")
                
                // Validate initial data state
                val validationResult = validateData()
                
                if (validationResult.isValid) {
                    isInitialized.set(true)
                    
                    // Notify observers on main thread
                    mainHandler.post {
                        notifyObserversReady()
                    }
                    
                    logger.logWithContext(
                        Log.INFO,
                        "DataManager",
                        "Data manager initialized successfully",
                        mapOf(
                            "group_size" to TVList.groupModel.size(),
                            "list_size" to TVList.listModel.size,
                            "warnings" to validationResult.warnings.size
                        )
                    )
                    
                    true
                } else {
                    logger.logWithContext(
                        Log.ERROR,
                        "DataManager",
                        "Data manager initialization failed - validation errors",
                        mapOf(
                            "errors" to validationResult.errors.size,
                            "error_details" to validationResult.errors.toString()
                        )
                    )
                    
                    val error = DataError.InitializationFailed
                    mainHandler.post {
                        notifyObserversError(error)
                    }
                    
                    errorHandler.handleError(MenuError.DataNotInitialized)
                    false
                }
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "DataManager",
                    "Exception during data manager initialization",
                    mapOf("error" to (e.message ?: "Unknown error")),
                    e
                )
                
                val error = DataError.InitializationFailed
                mainHandler.post {
                    notifyObserversError(error)
                }
                
                errorHandler.handleError(MenuError.DataNotInitialized)
                false
            }
        }
    }
    
    /**
     * Cleanup resources and observers.
     */
    fun cleanup() {
        logger.log(Log.INFO, "DataManager", "Cleaning up data manager")
        
        // Cancel any ongoing operations
        dataScope.cancel()
        
        // Clear observers
        observers.clear()
        
        // Reset state
        isInitialized.set(false)
        isUpdating.set(false)
        lastValidationResult = null
        lastValidationTimestamp = 0L
        
        logger.log(Log.INFO, "DataManager", "Data manager cleanup completed")
    }
    
    /**
     * Get current data statistics for monitoring.
     */
    fun getDataStatistics(): DataStatistics {
        return dataLock.read {
            try {
                val groupModel = TVList.groupModel
                val listModel = TVList.listModel
                val validationResult = validateData()
                
                DataStatistics(
                    isInitialized = isInitialized.get(),
                    isUpdating = isUpdating.get(),
                    groupModelSize = groupModel.size(),
                    listModelSize = listModel.size,
                    observerCount = observers.size,
                    validationErrors = validationResult.errors.size,
                    validationWarnings = validationResult.warnings.size,
                    lastValidationTime = lastValidationTimestamp
                )
            } catch (e: Exception) {
                DataStatistics(
                    isInitialized = false,
                    isUpdating = false,
                    groupModelSize = 0,
                    listModelSize = 0,
                    observerCount = observers.size,
                    validationErrors = 1,
                    validationWarnings = 0,
                    lastValidationTime = 0L
                )
            }
        }
    }
    
    private fun notifyObserversReady() {
        observers.values.forEach { observer ->
            try {
                observer.onDataReady()
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "DataManager",
                    "Error notifying observer of data ready",
                    mapOf("observer" to observer.hashCode()),
                    e
                )
            }
        }
    }
    
    private fun notifyObserversUpdated() {
        observers.values.forEach { observer ->
            try {
                observer.onDataUpdated()
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "DataManager",
                    "Error notifying observer of data update",
                    mapOf("observer" to observer.hashCode()),
                    e
                )
            }
        }
    }
    
    private fun notifyObserversError(error: DataError) {
        observers.values.forEach { observer ->
            try {
                observer.onDataError(error)
            } catch (e: Exception) {
                logger.logWithContext(
                    Log.ERROR,
                    "DataManager",
                    "Error notifying observer of data error",
                    mapOf(
                        "observer" to observer.hashCode(),
                        "original_error" to error.toString()
                    ),
                    e
                )
            }
        }
    }
}

/**
 * Statistics about data manager state for monitoring and debugging.
 */
data class DataStatistics(
    val isInitialized: Boolean,
    val isUpdating: Boolean,
    val groupModelSize: Int,
    val listModelSize: Int,
    val observerCount: Int,
    val validationErrors: Int,
    val validationWarnings: Int,
    val lastValidationTime: Long
)