package com.thirutricks.tllplayer.infrastructure

import android.animation.Animator
import android.animation.ValueAnimator
import android.os.Handler
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Base implementation of ManagedResource with common functionality.
 */
abstract class BaseManagedResource(
    private val resourceId: String,
    private val resourceType: ResourceType
) : ManagedResource {
    
    private val createdTime = System.currentTimeMillis()
    private val active = AtomicBoolean(true)
    private val lastAccessTime = AtomicLong(System.currentTimeMillis())
    private var lifecycleState = ResourceLifecycleState.CREATED
    
    override fun getResourceId(): String = resourceId
    override fun getResourceType(): ResourceType = resourceType
    override fun isActive(): Boolean = active.get()
    override fun getCreatedTime(): Long = createdTime
    override fun getLastAccessTime(): Long = lastAccessTime.get()
    override fun getLifecycleState(): ResourceLifecycleState = lifecycleState
    
    override fun updateLastAccessTime() {
        lastAccessTime.set(System.currentTimeMillis())
        if (lifecycleState == ResourceLifecycleState.CREATED) {
            lifecycleState = ResourceLifecycleState.ACTIVE
        } else if (lifecycleState == ResourceLifecycleState.IDLE) {
            lifecycleState = ResourceLifecycleState.ACTIVE
        }
    }
    
    override fun getMemoryFootprint(): Long {
        // Default implementation - subclasses can override for more accurate estimates
        return when (resourceType) {
            ResourceType.ANIMATION -> 1024L // ~1KB
            ResourceType.LISTENER -> 512L   // ~512B
            ResourceType.GLASS_EFFECT -> 2048L // ~2KB
            ResourceType.ADAPTER -> 4096L   // ~4KB
            ResourceType.HANDLER -> 256L    // ~256B
            ResourceType.VIEW -> 8192L      // ~8KB
            ResourceType.THREAD -> 16384L   // ~16KB
            ResourceType.MEMORY -> 32768L   // ~32KB
        }
    }
    
    override fun canBeCleanedUnderPressure(): Boolean {
        // Default implementation - most resources can be cleaned under pressure
        // except critical adapters and essential listeners
        return when (resourceType) {
            ResourceType.ADAPTER -> false  // Keep adapters as they're critical for UI
            ResourceType.LISTENER -> lifecycleState == ResourceLifecycleState.IDLE
            else -> true
        }
    }
    
    protected fun markInactive() {
        active.set(false)
        lifecycleState = ResourceLifecycleState.CLEANED_UP
    }
    
    protected fun markIdle() {
        if (lifecycleState == ResourceLifecycleState.ACTIVE) {
            lifecycleState = ResourceLifecycleState.IDLE
        }
    }
    
    protected fun markActive() {
        if (active.get() && lifecycleState != ResourceLifecycleState.CLEANUP_PENDING) {
            lifecycleState = ResourceLifecycleState.ACTIVE
            updateLastAccessTime()
        }
    }
}

/**
 * Managed resource for Android Animators.
 */
class ManagedAnimator(
    private val animator: Animator,
    resourceId: String
) : BaseManagedResource(resourceId, ResourceType.ANIMATION) {
    
    init {
        markActive()
    }
    
    override fun cleanup() {
        if (isActive()) {
            animator.cancel()
            animator.removeAllListeners()
            markInactive()
        }
    }
    
    override fun getMemoryFootprint(): Long {
        // More accurate estimate for animators
        return if (animator.isRunning) 2048L else 1024L
    }
    
    override fun canBeCleanedUnderPressure(): Boolean {
        // Running animations are more critical than idle ones
        return !animator.isRunning || getLifecycleState() == ResourceLifecycleState.IDLE
    }
}

/**
 * Managed resource for ValueAnimators.
 */
class ManagedValueAnimator(
    private val animator: ValueAnimator,
    resourceId: String
) : BaseManagedResource(resourceId, ResourceType.ANIMATION) {
    
    override fun cleanup() {
        if (isActive()) {
            animator.cancel()
            animator.removeAllUpdateListeners()
            animator.removeAllListeners()
            markInactive()
        }
    }
}

/**
 * Managed resource for event listeners.
 */
class ManagedListener(
    private val cleanupAction: () -> Unit,
    resourceId: String,
    private val description: String = "Generic Listener"
) : BaseManagedResource(resourceId, ResourceType.LISTENER) {
    
    override fun cleanup() {
        if (isActive()) {
            try {
                cleanupAction()
            } catch (e: Exception) {
                // Log but don't throw - cleanup should be resilient
                android.util.Log.w("ManagedListener", "Error cleaning up $description: ${e.message}")
            }
            markInactive()
        }
    }
}

/**
 * Managed resource for RecyclerView adapters.
 */
class ManagedAdapter(
    private val adapter: RecyclerView.Adapter<*>,
    private val recyclerView: RecyclerView?,
    resourceId: String
) : BaseManagedResource(resourceId, ResourceType.ADAPTER) {
    
    init {
        markActive()
    }
    
    override fun cleanup() {
        if (isActive()) {
            // Detach adapter from RecyclerView
            recyclerView?.adapter = null
            
            // Clear any observers or listeners if the adapter supports it
            try {
                adapter.unregisterAdapterDataObserver(adapter.adapterDataObserver)
            } catch (e: Exception) {
                // Some adapters might not have observers
            }
            
            markInactive()
        }
    }
    
    override fun getMemoryFootprint(): Long {
        // Adapters can be memory-intensive depending on item count
        val itemCount = try { adapter.itemCount } catch (e: Exception) { 0 }
        return 4096L + (itemCount * 256L) // Base + per-item estimate
    }
    
    override fun canBeCleanedUnderPressure(): Boolean {
        // Adapters are critical for UI functionality - only clean if truly idle
        return getLifecycleState() == ResourceLifecycleState.IDLE && 
               System.currentTimeMillis() - getLastAccessTime() > 30000L // 30 seconds idle
    }
    
    private val RecyclerView.Adapter<*>.adapterDataObserver: RecyclerView.AdapterDataObserver
        get() = object : RecyclerView.AdapterDataObserver() {}
}

/**
 * Managed resource for Handlers and Runnables.
 */
class ManagedHandler(
    private val handler: Handler,
    private val runnables: MutableSet<Runnable> = mutableSetOf(),
    resourceId: String
) : BaseManagedResource(resourceId, ResourceType.HANDLER) {
    
    fun addRunnable(runnable: Runnable) {
        runnables.add(runnable)
    }
    
    fun removeRunnable(runnable: Runnable) {
        runnables.remove(runnable)
        handler.removeCallbacks(runnable)
    }
    
    override fun cleanup() {
        if (isActive()) {
            // Remove all tracked runnables
            runnables.forEach { runnable ->
                handler.removeCallbacks(runnable)
            }
            runnables.clear()
            
            // Remove all callbacks and messages
            handler.removeCallbacksAndMessages(null)
            
            markInactive()
        }
    }
}

/**
 * Managed resource for Views with cleanup of listeners and animations.
 */
class ManagedView(
    private val view: View,
    resourceId: String
) : BaseManagedResource(resourceId, ResourceType.VIEW) {
    
    override fun cleanup() {
        if (isActive()) {
            // Clear animations
            view.clearAnimation()
            view.animate().cancel()
            
            // Clear listeners
            view.setOnClickListener(null)
            view.setOnFocusChangeListener(null)
            view.setOnTouchListener(null)
            view.setOnKeyListener(null)
            
            // Clear any background drawable callbacks
            view.background?.callback = null
            
            markInactive()
        }
    }
}

/**
 * Managed resource for glass effects and related UI resources.
 */
class ManagedGlassEffect(
    private val cleanupAction: () -> Unit,
    resourceId: String,
    private val effectType: String = "Unknown"
) : BaseManagedResource(resourceId, ResourceType.GLASS_EFFECT) {
    
    init {
        markActive()
    }
    
    override fun cleanup() {
        if (isActive()) {
            try {
                cleanupAction()
            } catch (e: Exception) {
                android.util.Log.w("ManagedGlassEffect", "Error cleaning up $effectType effect: ${e.message}")
            }
            markInactive()
        }
    }
    
    override fun getMemoryFootprint(): Long {
        // Glass effects can be memory-intensive due to graphics resources
        return when (effectType.lowercase()) {
            "blur" -> 4096L
            "shadow" -> 2048L
            "gradient" -> 1536L
            else -> 2048L
        }
    }
    
    override fun canBeCleanedUnderPressure(): Boolean {
        // Glass effects are visual enhancements - can be cleaned under any pressure
        return true
    }
}

/**
 * Managed resource for background threads and executors.
 */
class ManagedThread(
    private val thread: Thread,
    resourceId: String
) : BaseManagedResource(resourceId, ResourceType.THREAD) {
    
    override fun cleanup() {
        if (isActive()) {
            if (thread.isAlive && !thread.isInterrupted) {
                thread.interrupt()
                
                // Give thread a chance to stop gracefully
                try {
                    thread.join(1000) // Wait up to 1 second
                } catch (e: InterruptedException) {
                    // Current thread was interrupted while waiting
                    Thread.currentThread().interrupt()
                }
            }
            markInactive()
        }
    }
}

/**
 * Managed resource for memory-intensive objects that need explicit cleanup.
 */
class ManagedMemoryResource(
    private val cleanupAction: () -> Unit,
    resourceId: String,
    private val description: String = "Memory Resource",
    private val estimatedSizeBytes: Long = 32768L
) : BaseManagedResource(resourceId, ResourceType.MEMORY) {
    
    init {
        markActive()
    }
    
    override fun cleanup() {
        if (isActive()) {
            try {
                cleanupAction()
                // Suggest garbage collection for memory resources
                System.gc()
            } catch (e: Exception) {
                android.util.Log.w("ManagedMemoryResource", "Error cleaning up $description: ${e.message}")
            }
            markInactive()
        }
    }
    
    override fun getMemoryFootprint(): Long {
        return estimatedSizeBytes
    }
    
    override fun canBeCleanedUnderPressure(): Boolean {
        // Memory resources should be cleaned under moderate pressure
        return true
    }
}

/**
 * Factory for creating managed resources with automatic ID generation.
 */
object ManagedResourceFactory {
    
    private var idCounter = 0
    
    private fun generateId(prefix: String): String {
        return "${prefix}_${++idCounter}_${System.currentTimeMillis()}"
    }
    
    fun createManagedAnimator(animator: Animator): ManagedAnimator {
        return ManagedAnimator(animator, generateId("animator"))
    }
    
    fun createManagedValueAnimator(animator: ValueAnimator): ManagedValueAnimator {
        return ManagedValueAnimator(animator, generateId("value_animator"))
    }
    
    fun createManagedListener(cleanupAction: () -> Unit, description: String = "Listener"): ManagedListener {
        return ManagedListener(cleanupAction, generateId("listener"), description)
    }
    
    fun createManagedAdapter(adapter: RecyclerView.Adapter<*>, recyclerView: RecyclerView?): ManagedAdapter {
        return ManagedAdapter(adapter, recyclerView, generateId("adapter"))
    }
    
    fun createManagedHandler(handler: Handler): ManagedHandler {
        return ManagedHandler(handler, mutableSetOf(), generateId("handler"))
    }
    
    fun createManagedView(view: View): ManagedView {
        return ManagedView(view, generateId("view"))
    }
    
    fun createManagedGlassEffect(cleanupAction: () -> Unit, effectType: String): ManagedGlassEffect {
        return ManagedGlassEffect(cleanupAction, generateId("glass_effect"), effectType)
    }
    
    fun createManagedThread(thread: Thread): ManagedThread {
        return ManagedThread(thread, generateId("thread"))
    }
    
    fun createManagedMemoryResource(
        cleanupAction: () -> Unit, 
        description: String,
        estimatedSizeBytes: Long = 32768L
    ): ManagedMemoryResource {
        return ManagedMemoryResource(cleanupAction, generateId("memory"), description, estimatedSizeBytes)
    }
}