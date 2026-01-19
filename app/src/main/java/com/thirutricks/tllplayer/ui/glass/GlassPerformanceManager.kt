package com.thirutricks.tllplayer.ui.glass

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.lang.ref.WeakReference
import kotlin.math.max

/**
 * Manages performance optimization and error handling for glass menu components.
 * Provides animation performance monitoring, fallback mechanisms, and memory management.
 */
class GlassPerformanceManager(
    private val context: Context,
    private val styleConfig: GlassStyleConfig = GlassEffectUtils.getOptimalGlassConfig(context)
) {
    
    private val handler = Handler(Looper.getMainLooper())
    private var frameRateMonitor: GlassFrameRateMonitor? = null
    private var memoryManager: GlassMemoryManager? = null
    private var focusRecoveryManager: FocusRecoveryManager? = null
    
    private val activeAnimations = mutableSetOf<WeakReference<ValueAnimator>>()
    private var performanceMode = PerformanceMode.STANDARD
    private var hasHardwareAcceleration = true
    
    companion object {
        private const val TAG = "GlassPerformanceManager"
        
        // Performance thresholds
        private const val TARGET_FPS = 60f
        private const val LOW_FPS_THRESHOLD = 45f
        private const val CRITICAL_FPS_THRESHOLD = 30f
        private const val MEMORY_WARNING_THRESHOLD = 0.85f
        
        // Animation performance parameters
        private const val MAX_CONCURRENT_ANIMATIONS = 8
        private const val ANIMATION_TIMEOUT_MS = 5000L
        private const val FRAME_DROP_TOLERANCE = 3
        
        // Recovery parameters
        private const val FOCUS_RECOVERY_DELAY_MS = 100L
        private const val MAX_RECOVERY_ATTEMPTS = 3
    }
    
    enum class PerformanceMode {
        STANDARD,
        OPTIMIZED,
        FALLBACK
    }
    
    /**
     * Initialize performance monitoring and error handling
     */
    fun initialize(rootView: ViewGroup) {
        detectHardwareCapabilities()
        
        frameRateMonitor = GlassFrameRateMonitor()
        memoryManager = GlassMemoryManager()
        focusRecoveryManager = FocusRecoveryManager()
        
        frameRateMonitor?.startMonitoring { fps ->
            handlePerformanceChange(fps)
        }
        
        memoryManager?.startMonitoring()
        
        Log.d(TAG, "Glass performance manager initialized. Hardware acceleration: $hasHardwareAcceleration")
    }
    
    /**
     * Clean up resources and stop monitoring
     */
    fun cleanup() {
        frameRateMonitor?.stopMonitoring()
        memoryManager?.stopMonitoring()
        focusRecoveryManager?.cleanup()
        
        // Cancel all active animations
        cancelAllAnimations()
        
        frameRateMonitor = null
        memoryManager = null
        focusRecoveryManager = null
        
        Log.d(TAG, "Glass performance manager cleaned up")
    }
    
    /**
     * Register an animation for performance monitoring
     */
    fun registerAnimation(animator: ValueAnimator) {
        // Clean up dead references
        activeAnimations.removeAll { it.get() == null }
        
        // Check if we're at the animation limit
        if (activeAnimations.size >= MAX_CONCURRENT_ANIMATIONS) {
            Log.w(TAG, "Animation limit reached, canceling oldest animation")
            activeAnimations.firstOrNull()?.get()?.cancel()
        }
        
        activeAnimations.add(WeakReference(animator))
        
        // Add timeout protection
        handler.postDelayed({
            if (animator.isRunning) {
                Log.w(TAG, "Animation timeout, canceling animation")
                animator.cancel()
            }
        }, ANIMATION_TIMEOUT_MS)
        
        // Add completion listener to clean up reference
        animator.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {
                activeAnimations.removeAll { it.get() == animation }
            }
            override fun onAnimationEnd(animation: Animator) {
                activeAnimations.removeAll { it.get() == animation }
            }
        })
    }
    
    /**
     * Cancel all active animations
     */
    fun cancelAllAnimations() {
        val animationsToCancel = activeAnimations.mapNotNull { it.get() }
        animationsToCancel.forEach { animator ->
            try {
                animator.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error canceling animation: ${e.message}")
            }
        }
        activeAnimations.clear()
        Log.d(TAG, "Canceled ${animationsToCancel.size} active animations")
    }
    
    /**
     * Apply performance optimizations based on current mode
     */
    fun applyPerformanceOptimizations(view: View) {
        when (performanceMode) {
            PerformanceMode.STANDARD -> applyStandardOptimizations(view)
            PerformanceMode.OPTIMIZED -> applyOptimizedMode(view)
            PerformanceMode.FALLBACK -> applyFallbackMode(view)
        }
    }
    
    /**
     * Handle focus recovery in case of errors
     */
    fun handleFocusRecovery(recyclerView: RecyclerView, lastKnownPosition: Int = -1) {
        focusRecoveryManager?.recoverFocus(recyclerView, lastKnownPosition)
    }
    
    /**
     * Get optimized animation duration based on performance mode
     */
    fun getOptimizedAnimationDuration(baseDuration: Long): Long {
        return when (performanceMode) {
            PerformanceMode.STANDARD -> baseDuration
            PerformanceMode.OPTIMIZED -> (baseDuration * 0.75f).toLong()
            PerformanceMode.FALLBACK -> (baseDuration * 0.5f).toLong()
        }
    }
    
    /**
     * Check if advanced effects should be enabled
     */
    fun shouldUseAdvancedEffects(): Boolean {
        return hasHardwareAcceleration && performanceMode != PerformanceMode.FALLBACK
    }
    
    /**
     * Get current performance mode
     */
    fun getPerformanceMode(): PerformanceMode = performanceMode
    
    private fun detectHardwareCapabilities() {
        hasHardwareAcceleration = GlassEffectUtils.supportsAdvancedEffects(context)
        
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val isLowEndDevice = activityManager.isLowRamDevice
        
        if (!hasHardwareAcceleration || isLowEndDevice) {
            performanceMode = PerformanceMode.FALLBACK
        }
        
        Log.d(TAG, "Hardware capabilities - Acceleration: $hasHardwareAcceleration, Low-end: $isLowEndDevice")
    }
    
    private fun handlePerformanceChange(fps: Float) {
        val newMode = when {
            fps >= TARGET_FPS -> PerformanceMode.STANDARD
            fps >= LOW_FPS_THRESHOLD -> PerformanceMode.OPTIMIZED
            else -> PerformanceMode.FALLBACK
        }
        
        if (newMode != performanceMode) {
            Log.i(TAG, "Performance mode changed from $performanceMode to $newMode (FPS: $fps)")
            performanceMode = newMode
            
            // Trigger memory cleanup if performance is poor
            if (newMode == PerformanceMode.FALLBACK) {
                memoryManager?.triggerCleanup()
            }
        }
    }
    
    private fun applyStandardOptimizations(view: View) {
        // Enable hardware acceleration
        if (hasHardwareAcceleration) {
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // Full glass effects enabled
        if (view is ViewGroup) {
            GlassEffectUtils.applyGlassStyle(view, styleConfig, GlassType.PANEL)
        }
    }
    
    private fun applyOptimizedMode(view: View) {
        // Reduce animation complexity
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Use reduced glass effects
        if (view is ViewGroup) {
            val optimizedConfig = styleConfig.copy(
                blurRadius = styleConfig.blurRadius * 0.7f,
                backgroundAlpha = styleConfig.backgroundAlpha * 1.2f
            )
            GlassEffectUtils.applyGlassStyle(view, optimizedConfig, GlassType.PANEL)
        }
    }
    
    private fun applyFallbackMode(view: View) {
        // Disable hardware acceleration if causing issues
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        
        // Use minimal glass effects
        if (view is ViewGroup) {
            val fallbackConfig = styleConfig.copy(
                blurRadius = 0f,
                backgroundAlpha = max(0.3f, styleConfig.backgroundAlpha),
                borderAlpha = styleConfig.borderAlpha * 0.5f
            )
            GlassEffectUtils.applyGlassStyle(view, fallbackConfig, GlassType.PANEL)
        }
    }
    
    /**
     * Frame rate monitoring for glass animations
     */
    private inner class GlassFrameRateMonitor {
        private var isMonitoring = false
        private var frameCount = 0
        private var droppedFrames = 0
        private var lastFrameTime = 0L
        private var performanceCallback: ((Float) -> Unit)? = null
        
        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isMonitoring) return
                
                val currentTime = System.currentTimeMillis()
                
                if (lastFrameTime > 0) {
                    val frameDuration = currentTime - lastFrameTime
                    val expectedFrameDuration = 1000f / TARGET_FPS
                    
                    if (frameDuration > expectedFrameDuration * 1.5f) {
                        droppedFrames++
                    }
                }
                
                frameCount++
                
                if (frameCount >= TARGET_FPS) { // Check every second
                    val fps = frameCount * 1000f / (currentTime - (lastFrameTime - (frameCount - 1) * (1000f / TARGET_FPS)).toLong())
                    
                    if (droppedFrames > FRAME_DROP_TOLERANCE) {
                        Log.w(TAG, "Frame drops detected: $droppedFrames in last second")
                    }
                    
                    performanceCallback?.invoke(fps)
                    
                    frameCount = 0
                    droppedFrames = 0
                }
                
                lastFrameTime = currentTime
                
                if (isMonitoring) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        
        fun startMonitoring(callback: (Float) -> Unit) {
            performanceCallback = callback
            isMonitoring = true
            frameCount = 0
            droppedFrames = 0
            lastFrameTime = System.currentTimeMillis()
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
        
        fun stopMonitoring() {
            isMonitoring = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
    }
    
    /**
     * Memory management for glass effects
     */
    private inner class GlassMemoryManager {
        private var isMonitoring = false
        private val monitoringRunnable = object : Runnable {
            override fun run() {
                if (!isMonitoring) return
                
                val runtime = Runtime.getRuntime()
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val maxMemory = runtime.maxMemory()
                val memoryUsageRatio = usedMemory.toFloat() / maxMemory.toFloat()
                
                if (memoryUsageRatio > MEMORY_WARNING_THRESHOLD) {
                    Log.w(TAG, "High memory usage: ${(memoryUsageRatio * 100).toInt()}%")
                    triggerCleanup()
                }
                
                if (isMonitoring) {
                    handler.postDelayed(this, 2000L) // Check every 2 seconds
                }
            }
        }
        
        fun startMonitoring() {
            isMonitoring = true
            handler.post(monitoringRunnable)
        }
        
        fun stopMonitoring() {
            isMonitoring = false
            handler.removeCallbacks(monitoringRunnable)
        }
        
        fun triggerCleanup() {
            // Cancel non-essential animations
            val nonEssentialAnimations = activeAnimations.filter { ref ->
                ref.get()?.let { animator ->
                    // Consider focus animations as essential, others as non-essential
                    animator.duration > 500L // Long animations are likely non-essential
                } ?: false
            }
            
            nonEssentialAnimations.forEach { ref ->
                ref.get()?.cancel()
            }
            
            // Trigger garbage collection
            System.gc()
            
            Log.d(TAG, "Memory cleanup triggered, canceled ${nonEssentialAnimations.size} animations")
        }
    }
    
    /**
     * Focus recovery manager for error handling
     */
    private inner class FocusRecoveryManager {
        private val recoveryAttempts = mutableMapOf<Int, Int>()
        
        fun recoverFocus(recyclerView: RecyclerView, lastKnownPosition: Int) {
            val viewId = recyclerView.id
            val attempts = recoveryAttempts.getOrDefault(viewId, 0)
            
            if (attempts >= MAX_RECOVERY_ATTEMPTS) {
                Log.w(TAG, "Max recovery attempts reached for RecyclerView $viewId")
                return
            }
            
            recoveryAttempts[viewId] = attempts + 1
            
            handler.postDelayed({
                try {
                    val targetPosition = if (lastKnownPosition >= 0 && lastKnownPosition < recyclerView.adapter?.itemCount ?: 0) {
                        lastKnownPosition
                    } else {
                        0 // Fallback to first item
                    }
                    
                    val viewHolder = recyclerView.findViewHolderForAdapterPosition(targetPosition)
                    if (viewHolder != null) {
                        viewHolder.itemView.requestFocus()
                        Log.d(TAG, "Focus recovered to position $targetPosition")
                        recoveryAttempts.remove(viewId) // Reset attempts on success
                    } else {
                        // Scroll to position and try again
                        recyclerView.scrollToPosition(targetPosition)
                        handler.postDelayed({
                            recyclerView.findViewHolderForAdapterPosition(targetPosition)?.itemView?.requestFocus()
                        }, FOCUS_RECOVERY_DELAY_MS)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Focus recovery failed: ${e.message}")
                }
            }, FOCUS_RECOVERY_DELAY_MS)
        }
        
        fun cleanup() {
            recoveryAttempts.clear()
        }
    }
}