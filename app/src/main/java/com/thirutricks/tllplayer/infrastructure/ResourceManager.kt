package com.thirutricks.tllplayer.infrastructure

import android.animation.Animator
import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Interface for managing UI resources and ensuring proper cleanup.
 */
interface ResourceManager {
    fun registerResource(resource: ManagedResource)
    fun unregisterResource(resource: ManagedResource)
    fun cleanupAll()
    fun cleanupByType(type: ResourceType)
    fun isCleanupInProgress(): Boolean
    fun getResourceCount(): Int
    fun getResourcesByType(type: ResourceType): List<ManagedResource>
    fun forceCleanup(resourceId: String): Boolean
    
    // Lifecycle tracking enhancements
    fun getResourceLifecycleInfo(resourceId: String): ResourceLifecycleInfo?
    fun getResourcesOlderThan(ageMs: Long): List<ManagedResource>
    fun getResourcesInState(state: ResourceLifecycleState): List<ManagedResource>
    
    // Memory pressure detection and management
    fun checkMemoryPressure(): MemoryPressureLevel
    fun handleMemoryPressure(level: MemoryPressureLevel): Boolean
    fun enableMemoryPressureMonitoring(context: Context)
    fun disableMemoryPressureMonitoring()
    
    // Graceful degradation
    fun enterDegradedMode()
    fun exitDegradedMode()
    fun isDegradedMode(): Boolean
    fun setDegradationStrategy(strategy: DegradationStrategy)
}

/**
 * Interface for resources that can be managed and cleaned up.
 */
interface ManagedResource {
    fun cleanup()
    fun getResourceType(): ResourceType
    fun getResourceId(): String
    fun isActive(): Boolean
    fun getCreatedTime(): Long
    
    // Lifecycle tracking enhancements
    fun getLifecycleState(): ResourceLifecycleState
    fun getLastAccessTime(): Long
    fun updateLastAccessTime()
    fun getMemoryFootprint(): Long
    fun canBeCleanedUnderPressure(): Boolean
}

/**
 * Types of resources that can be managed.
 */
enum class ResourceType {
    ANIMATION,      // Animations and animators
    LISTENER,       // Event listeners and observers
    GLASS_EFFECT,   // Glass UI effects and related resources
    ADAPTER,        // RecyclerView adapters and related resources
    HANDLER,        // Handlers and runnables
    VIEW,           // Views and UI components
    THREAD,         // Background threads and executors
    MEMORY          // Memory-intensive resources
}

/**
 * Lifecycle states for managed resources.
 */
enum class ResourceLifecycleState {
    CREATED,        // Resource just created
    ACTIVE,         // Resource actively being used
    IDLE,           // Resource not recently accessed but still valid
    CLEANUP_PENDING,// Resource marked for cleanup
    CLEANED_UP      // Resource has been cleaned up
}

/**
 * Memory pressure levels for adaptive resource management.
 */
enum class MemoryPressureLevel {
    NORMAL,         // Normal memory usage
    MODERATE,       // Moderate memory pressure - start cleanup of idle resources
    HIGH,           // High memory pressure - aggressive cleanup
    CRITICAL        // Critical memory pressure - emergency cleanup and degradation
}

/**
 * Strategies for handling resource degradation under memory pressure.
 */
interface DegradationStrategy {
    fun shouldDegradeResource(resource: ManagedResource, pressureLevel: MemoryPressureLevel): Boolean
    fun getDegradedAlternative(resource: ManagedResource): ManagedResource?
    fun getPriorityOrder(): List<ResourceType>
}

/**
 * Default degradation strategy that prioritizes core functionality.
 */
class DefaultDegradationStrategy : DegradationStrategy {
    override fun shouldDegradeResource(resource: ManagedResource, pressureLevel: MemoryPressureLevel): Boolean {
        return when (pressureLevel) {
            MemoryPressureLevel.NORMAL -> false
            MemoryPressureLevel.MODERATE -> {
                // Clean up idle glass effects and animations
                resource.getResourceType() in listOf(ResourceType.GLASS_EFFECT, ResourceType.ANIMATION) &&
                resource.getLifecycleState() == ResourceLifecycleState.IDLE
            }
            MemoryPressureLevel.HIGH -> {
                // Clean up all non-essential resources
                resource.getResourceType() in listOf(
                    ResourceType.GLASS_EFFECT, 
                    ResourceType.ANIMATION,
                    ResourceType.MEMORY
                ) || resource.canBeCleanedUnderPressure()
            }
            MemoryPressureLevel.CRITICAL -> {
                // Clean up everything except core adapters and essential listeners
                resource.getResourceType() !in listOf(ResourceType.ADAPTER, ResourceType.LISTENER) ||
                resource.canBeCleanedUnderPressure()
            }
        }
    }
    
    override fun getDegradedAlternative(resource: ManagedResource): ManagedResource? {
        // For now, return null - could implement lightweight alternatives
        return null
    }
    
    override fun getPriorityOrder(): List<ResourceType> {
        return listOf(
            ResourceType.GLASS_EFFECT,  // Clean up visual effects first
            ResourceType.ANIMATION,     // Then animations
            ResourceType.MEMORY,        // Then memory-intensive resources
            ResourceType.VIEW,          // Then views
            ResourceType.HANDLER,       // Then handlers
            ResourceType.THREAD,        // Then threads
            ResourceType.LISTENER,      // Then listeners
            ResourceType.ADAPTER        // Keep adapters last as they're most critical
        )
    }
}

/**
 * Information about a resource's lifecycle for monitoring and debugging.
 */
data class ResourceLifecycleInfo(
    val resourceId: String,
    val resourceType: ResourceType,
    val state: ResourceLifecycleState,
    val createdTime: Long,
    val lastAccessTime: Long,
    val memoryFootprint: Long,
    val canBeCleanedUnderPressure: Boolean,
    val age: Long = System.currentTimeMillis() - createdTime,
    val idleTime: Long = System.currentTimeMillis() - lastAccessTime
)

/**
 * Default implementation of ResourceManager with comprehensive resource tracking.
 */
class DefaultResourceManager(
    private val logger: MenuLogger,
    private val errorHandler: ErrorHandler
) : ResourceManager {
    
    private val resources = ConcurrentHashMap<String, ManagedResource>()
    private val resourcesByType = ConcurrentHashMap<ResourceType, MutableSet<String>>()
    private val resourceLifecycleInfo = ConcurrentHashMap<String, ResourceLifecycleInfo>()
    private val cleanupInProgress = AtomicBoolean(false)
    private val resourceIdCounter = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Memory pressure monitoring
    private var activityManager: ActivityManager? = null
    private var memoryPressureMonitoringEnabled = false
    private val lastMemoryCheck = AtomicLong(0)
    private val memoryCheckInterval = 5000L // Check every 5 seconds
    
    // Degradation management
    private val degradedMode = AtomicBoolean(false)
    private var degradationStrategy: DegradationStrategy = DefaultDegradationStrategy()
    
    // Memory thresholds (in MB)
    private val memoryThresholds = mapOf(
        MemoryPressureLevel.MODERATE to 50L,
        MemoryPressureLevel.HIGH to 25L,
        MemoryPressureLevel.CRITICAL to 10L
    )
    
    override fun registerResource(resource: ManagedResource) {
        val resourceId = resource.getResourceId()
        
        logger.logWithContext(
            android.util.Log.DEBUG,
            "ResourceManager",
            "Registering resource: $resourceId",
            mapOf(
                "type" to resource.getResourceType().name,
                "active" to resource.isActive(),
                "created_time" to resource.getCreatedTime()
            )
        )
        
        resources[resourceId] = resource
        
        // Track by type
        resourcesByType.computeIfAbsent(resource.getResourceType()) { mutableSetOf() }
            .add(resourceId)
        
        // Track lifecycle info
        resourceLifecycleInfo[resourceId] = ResourceLifecycleInfo(
            resourceId = resourceId,
            resourceType = resource.getResourceType(),
            state = resource.getLifecycleState(),
            createdTime = resource.getCreatedTime(),
            lastAccessTime = resource.getLastAccessTime(),
            memoryFootprint = resource.getMemoryFootprint(),
            canBeCleanedUnderPressure = resource.canBeCleanedUnderPressure()
        )
        
        // Check memory pressure if monitoring is enabled
        if (memoryPressureMonitoringEnabled) {
            checkAndHandleMemoryPressure()
        }
        
        // Log resource statistics
        logResourceStatistics()
    }
    
    override fun unregisterResource(resource: ManagedResource) {
        val resourceId = resource.getResourceId()
        
        logger.logWithContext(
            android.util.Log.DEBUG,
            "ResourceManager",
            "Unregistering resource: $resourceId",
            mapOf(
                "type" to resource.getResourceType().name,
                "was_active" to resource.isActive()
            )
        )
        
        resources.remove(resourceId)
        resourcesByType[resource.getResourceType()]?.remove(resourceId)
        resourceLifecycleInfo.remove(resourceId)
        
        // Clean up empty type sets
        resourcesByType.entries.removeAll { it.value.isEmpty() }
    }
    
    override fun cleanupAll() {
        if (!cleanupInProgress.compareAndSet(false, true)) {
            logger.log(android.util.Log.WARN, "ResourceManager", "Cleanup already in progress")
            return
        }
        
        try {
            logger.logWithContext(
                android.util.Log.INFO,
                "ResourceManager",
                "Starting cleanup of all resources",
                mapOf("total_resources" to resources.size)
            )
            
            val startTime = System.currentTimeMillis()
            val failedCleanups = mutableListOf<String>()
            
            // Cleanup resources in priority order
            val priorityOrder = listOf(
                ResourceType.THREAD,      // Stop threads first
                ResourceType.ANIMATION,   // Stop animations
                ResourceType.HANDLER,     // Cancel handlers
                ResourceType.LISTENER,    // Remove listeners
                ResourceType.GLASS_EFFECT,// Cleanup glass effects
                ResourceType.ADAPTER,     // Cleanup adapters
                ResourceType.VIEW,        // Cleanup views
                ResourceType.MEMORY       // Free memory resources last
            )
            
            for (type in priorityOrder) {
                val typeFailures = cleanupResourcesOfType(type)
                failedCleanups.addAll(typeFailures)
            }
            
            // Cleanup any remaining resources not in priority list
            val remainingResources = resources.values.toList()
            for (resource in remainingResources) {
                if (!cleanupResource(resource)) {
                    failedCleanups.add(resource.getResourceId())
                }
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            if (failedCleanups.isNotEmpty()) {
                val error = MenuError.ResourceCleanupPartialFailure(failedCleanups)
                errorHandler.handleError(error)
            }
            
            logger.logWithContext(
                android.util.Log.INFO,
                "ResourceManager",
                "Cleanup completed",
                mapOf(
                    "duration_ms" to duration,
                    "failed_cleanups" to failedCleanups.size,
                    "remaining_resources" to resources.size
                )
            )
            
        } finally {
            cleanupInProgress.set(false)
        }
    }
    
    override fun cleanupByType(type: ResourceType) {
        logger.logWithContext(
            android.util.Log.INFO,
            "ResourceManager",
            "Cleaning up resources by type: ${type.name}",
            mapOf("resource_count" to (resourcesByType[type]?.size ?: 0))
        )
        
        val failures = cleanupResourcesOfType(type)
        
        if (failures.isNotEmpty()) {
            val error = MenuError.ResourceCleanupPartialFailure(failures)
            errorHandler.handleError(error)
        }
    }
    
    override fun isCleanupInProgress(): Boolean = cleanupInProgress.get()
    
    override fun getResourceCount(): Int = resources.size
    
    override fun getResourcesByType(type: ResourceType): List<ManagedResource> {
        val resourceIds = resourcesByType[type] ?: return emptyList()
        return resourceIds.mapNotNull { resources[it] }
    }
    
    override fun forceCleanup(resourceId: String): Boolean {
        val resource = resources[resourceId] ?: return false
        
        logger.logWithContext(
            android.util.Log.WARN,
            "ResourceManager",
            "Force cleanup requested for resource: $resourceId",
            mapOf("type" to resource.getResourceType().name)
        )
        
        return cleanupResource(resource)
    }
    
    // Lifecycle tracking methods
    override fun getResourceLifecycleInfo(resourceId: String): ResourceLifecycleInfo? {
        return resourceLifecycleInfo[resourceId]
    }
    
    override fun getResourcesOlderThan(ageMs: Long): List<ManagedResource> {
        val currentTime = System.currentTimeMillis()
        return resources.values.filter { resource ->
            currentTime - resource.getCreatedTime() > ageMs
        }
    }
    
    override fun getResourcesInState(state: ResourceLifecycleState): List<ManagedResource> {
        return resources.values.filter { resource ->
            resource.getLifecycleState() == state
        }
    }
    
    // Memory pressure detection and management
    override fun checkMemoryPressure(): MemoryPressureLevel {
        val currentTime = System.currentTimeMillis()
        
        // Throttle memory checks to avoid excessive overhead
        if (currentTime - lastMemoryCheck.get() < memoryCheckInterval) {
            return getLastKnownMemoryPressure()
        }
        
        lastMemoryCheck.set(currentTime)
        
        val activityManager = this.activityManager ?: return MemoryPressureLevel.NORMAL
        
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val availableMemoryMB = memInfo.availMem / 1024 / 1024
        
        val pressureLevel = when {
            availableMemoryMB <= memoryThresholds[MemoryPressureLevel.CRITICAL]!! -> MemoryPressureLevel.CRITICAL
            availableMemoryMB <= memoryThresholds[MemoryPressureLevel.HIGH]!! -> MemoryPressureLevel.HIGH
            availableMemoryMB <= memoryThresholds[MemoryPressureLevel.MODERATE]!! -> MemoryPressureLevel.MODERATE
            else -> MemoryPressureLevel.NORMAL
        }
        
        logger.logWithContext(
            android.util.Log.DEBUG,
            "ResourceManager",
            "Memory pressure check completed",
            mapOf(
                "available_memory_mb" to availableMemoryMB,
                "pressure_level" to pressureLevel.name,
                "low_memory" to memInfo.lowMemory
            )
        )
        
        return pressureLevel
    }
    
    override fun handleMemoryPressure(level: MemoryPressureLevel): Boolean {
        if (level == MemoryPressureLevel.NORMAL) {
            return true
        }
        
        logger.logWithContext(
            android.util.Log.INFO,
            "ResourceManager",
            "Handling memory pressure",
            mapOf(
                "pressure_level" to level.name,
                "total_resources" to resources.size,
                "degraded_mode" to degradedMode.get()
            )
        )
        
        val startTime = System.currentTimeMillis()
        var cleanedResources = 0
        
        try {
            // Get resources that should be cleaned up based on degradation strategy
            val resourcesToCleanup = resources.values.filter { resource ->
                degradationStrategy.shouldDegradeResource(resource, level)
            }
            
            // Sort by priority order from degradation strategy
            val priorityOrder = degradationStrategy.getPriorityOrder()
            val sortedResources = resourcesToCleanup.sortedBy { resource ->
                priorityOrder.indexOf(resource.getResourceType()).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            
            // Clean up resources in priority order
            for (resource in sortedResources) {
                if (cleanupResource(resource)) {
                    cleanedResources++
                }
                
                // Check if we've freed enough memory for high/critical pressure
                if (level in listOf(MemoryPressureLevel.HIGH, MemoryPressureLevel.CRITICAL)) {
                    val currentPressure = checkMemoryPressure()
                    if (currentPressure < level) {
                        logger.log(
                            android.util.Log.INFO,
                            "ResourceManager",
                            "Memory pressure reduced after cleaning $cleanedResources resources"
                        )
                        break
                    }
                }
            }
            
            // Enter degraded mode for high/critical pressure
            if (level in listOf(MemoryPressureLevel.HIGH, MemoryPressureLevel.CRITICAL)) {
                enterDegradedMode()
            }
            
            val duration = System.currentTimeMillis() - startTime
            
            logger.logWithContext(
                android.util.Log.INFO,
                "ResourceManager",
                "Memory pressure handling completed",
                mapOf(
                    "cleaned_resources" to cleanedResources,
                    "duration_ms" to duration,
                    "remaining_resources" to resources.size
                )
            )
            
            return true
            
        } catch (e: Exception) {
            logger.logWithContext(
                android.util.Log.ERROR,
                "ResourceManager",
                "Error handling memory pressure",
                mapOf("pressure_level" to level.name),
                e
            )
            
            val error = MenuError.ResourceCleanupPartialFailure(listOf("memory_pressure_handling"))
            errorHandler.handleError(error)
            
            return false
        }
    }
    
    override fun enableMemoryPressureMonitoring(context: Context) {
        activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        memoryPressureMonitoringEnabled = true
        
        logger.log(
            android.util.Log.INFO,
            "ResourceManager",
            "Memory pressure monitoring enabled"
        )
    }
    
    override fun disableMemoryPressureMonitoring() {
        memoryPressureMonitoringEnabled = false
        activityManager = null
        
        logger.log(
            android.util.Log.INFO,
            "ResourceManager",
            "Memory pressure monitoring disabled"
        )
    }
    
    // Graceful degradation methods
    override fun enterDegradedMode() {
        if (degradedMode.compareAndSet(false, true)) {
            logger.log(
                android.util.Log.WARN,
                "ResourceManager",
                "Entering degraded mode due to memory pressure"
            )
            
            // Notify error handler about degraded mode
            val error = MenuError.PerformanceWarning("memory_pressure", 0.0, 1.0)
            errorHandler.handleError(error)
        }
    }
    
    override fun exitDegradedMode() {
        if (degradedMode.compareAndSet(true, false)) {
            logger.log(
                android.util.Log.INFO,
                "ResourceManager",
                "Exiting degraded mode - memory pressure resolved"
            )
        }
    }
    
    override fun isDegradedMode(): Boolean = degradedMode.get()
    
    override fun setDegradationStrategy(strategy: DegradationStrategy) {
        this.degradationStrategy = strategy
        logger.log(
            android.util.Log.DEBUG,
            "ResourceManager",
            "Degradation strategy updated: ${strategy::class.simpleName}"
        )
    }
    
    private fun cleanupResourcesOfType(type: ResourceType): List<String> {
        val resourceIds = resourcesByType[type]?.toList() ?: return emptyList()
        val failures = mutableListOf<String>()
        
        for (resourceId in resourceIds) {
            val resource = resources[resourceId]
            if (resource != null && !cleanupResource(resource)) {
                failures.add(resourceId)
            }
        }
        
        return failures
    }
    
    private fun cleanupResource(resource: ManagedResource): Boolean {
        return try {
            val startTime = System.currentTimeMillis()
            
            // Update lifecycle info before cleanup
            resourceLifecycleInfo[resource.getResourceId()]?.let { info ->
                resourceLifecycleInfo[resource.getResourceId()] = info.copy(
                    state = ResourceLifecycleState.CLEANUP_PENDING
                )
            }
            
            // Ensure cleanup happens on appropriate thread
            when (resource.getResourceType()) {
                ResourceType.ANIMATION,
                ResourceType.VIEW,
                ResourceType.GLASS_EFFECT -> {
                    // UI resources must be cleaned up on main thread
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        resource.cleanup()
                    } else {
                        mainHandler.post { resource.cleanup() }
                    }
                }
                else -> {
                    // Other resources can be cleaned up on current thread
                    resource.cleanup()
                }
            }
            
            unregisterResource(resource)
            
            val duration = System.currentTimeMillis() - startTime
            
            logger.logWithContext(
                android.util.Log.DEBUG,
                "ResourceManager",
                "Resource cleaned up successfully: ${resource.getResourceId()}",
                mapOf(
                    "type" to resource.getResourceType().name,
                    "cleanup_duration_ms" to duration
                )
            )
            
            true
        } catch (e: Exception) {
            logger.logWithContext(
                android.util.Log.ERROR,
                "ResourceManager",
                "Failed to cleanup resource: ${resource.getResourceId()}",
                mapOf(
                    "type" to resource.getResourceType().name,
                    "error" to (e.message ?: "Unknown error")
                ),
                e
            )
            false
        }
    }
    
    private fun checkAndHandleMemoryPressure() {
        if (!memoryPressureMonitoringEnabled) return
        
        val pressureLevel = checkMemoryPressure()
        if (pressureLevel != MemoryPressureLevel.NORMAL) {
            handleMemoryPressure(pressureLevel)
        }
    }
    
    private fun getLastKnownMemoryPressure(): MemoryPressureLevel {
        // Simple heuristic based on current resource count and degraded mode
        return when {
            degradedMode.get() -> MemoryPressureLevel.HIGH
            resources.size > 100 -> MemoryPressureLevel.MODERATE
            else -> MemoryPressureLevel.NORMAL
        }
    }
    
    private fun logResourceStatistics() {
        if (logger.isDebugMode()) {
            val stats = getResourceStatistics()
            logger.logWithContext(
                android.util.Log.DEBUG,
                "ResourceManager",
                "Resource statistics",
                mapOf(
                    "total" to stats.totalResources,
                    "active" to stats.activeResources,
                    "idle" to stats.idleResources,
                    "by_type" to stats.resourcesByType,
                    "by_state" to stats.resourcesByState,
                    "memory_usage_mb" to stats.estimatedMemoryUsageMB,
                    "memory_pressure" to stats.memoryPressureLevel.name,
                    "degraded_mode" to stats.isDegradedMode,
                    "average_age_ms" to stats.averageResourceAge
                )
            )
        }
    }
    
    /**
     * Get comprehensive resource statistics.
     */
    fun getResourceStatistics(): ResourceStatistics {
        val activeResources = resources.values.count { it.isActive() }
        val resourcesByType = resourcesByType.mapValues { it.value.size }
        val resourcesByState = resources.values.groupingBy { it.getLifecycleState() }.eachCount()
        val oldestResource = resources.values.minByOrNull { it.getCreatedTime() }
        val newestResource = resources.values.maxByOrNull { it.getCreatedTime() }
        val currentTime = System.currentTimeMillis()
        val averageAge = if (resources.isNotEmpty()) {
            resources.values.map { currentTime - it.getCreatedTime() }.average().toLong()
        } else 0L
        val idleResources = resources.values.count { 
            it.getLifecycleState() == ResourceLifecycleState.IDLE 
        }
        
        return ResourceStatistics(
            totalResources = resources.size,
            activeResources = activeResources,
            resourcesByType = resourcesByType,
            resourcesByState = resourcesByState,
            oldestResourceAge = oldestResource?.let { currentTime - it.getCreatedTime() } ?: 0,
            newestResourceAge = newestResource?.let { currentTime - it.getCreatedTime() } ?: 0,
            estimatedMemoryUsageMB = estimateMemoryUsage(),
            memoryPressureLevel = if (memoryPressureMonitoringEnabled) checkMemoryPressure() else MemoryPressureLevel.NORMAL,
            isDegradedMode = degradedMode.get(),
            averageResourceAge = averageAge,
            idleResources = idleResources
        )
    }
    
    private fun estimateMemoryUsage(): Long {
        // Rough estimation based on resource types and counts
        var estimatedBytes = 0L
        
        resourcesByType.forEach { (type, resourceIds) ->
            val count = resourceIds.size
            estimatedBytes += when (type) {
                ResourceType.ANIMATION -> count * 1024L // ~1KB per animation
                ResourceType.LISTENER -> count * 512L   // ~512B per listener
                ResourceType.GLASS_EFFECT -> count * 2048L // ~2KB per glass effect
                ResourceType.ADAPTER -> count * 4096L   // ~4KB per adapter
                ResourceType.HANDLER -> count * 256L    // ~256B per handler
                ResourceType.VIEW -> count * 8192L      // ~8KB per view
                ResourceType.THREAD -> count * 16384L   // ~16KB per thread
                ResourceType.MEMORY -> count * 32768L   // ~32KB per memory resource
            }
        }
        
        return estimatedBytes / 1024 / 1024 // Convert to MB
    }
    
    /**
     * Generate a unique resource ID.
     */
    fun generateResourceId(prefix: String = "resource"): String {
        return "${prefix}_${resourceIdCounter.incrementAndGet()}_${System.currentTimeMillis()}"
    }
}

/**
 * Statistics about resource usage for monitoring and debugging.
 */
data class ResourceStatistics(
    val totalResources: Int,
    val activeResources: Int,
    val resourcesByType: Map<ResourceType, Int>,
    val resourcesByState: Map<ResourceLifecycleState, Int>,
    val oldestResourceAge: Long,
    val newestResourceAge: Long,
    val estimatedMemoryUsageMB: Long,
    val memoryPressureLevel: MemoryPressureLevel,
    val isDegradedMode: Boolean,
    val averageResourceAge: Long,
    val idleResources: Int
)

/**
 * Information about a specific resource for debugging.
 */
data class ResourceInfo(
    val id: String,
    val type: ResourceType,
    val createdAt: Long,
    val isActive: Boolean,
    val age: Long = System.currentTimeMillis() - createdAt
)