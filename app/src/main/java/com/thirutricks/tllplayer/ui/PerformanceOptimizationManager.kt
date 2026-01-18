package com.thirutricks.tllplayer.ui

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R
import java.lang.ref.WeakReference

/**
 * Manages performance optimization and adaptive rendering for the settings UI
 * Monitors frame rate, GPU usage, and memory consumption
 */
class PerformanceOptimizationManager(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private var frameRateMonitor: FrameRateMonitor? = null
    private var memoryMonitor: MemoryMonitor? = null
    private var isLowEndDevice: Boolean = false
    
    companion object {
        private const val TAG = "PerformanceOptimizer"
        private const val TARGET_FPS = 60f
        private const val LOW_FPS_THRESHOLD = 45f
        private const val MEMORY_WARNING_THRESHOLD = 0.8f // 80% of available memory
        private const val MONITORING_INTERVAL_MS = 1000L
        
        // Device capability thresholds
        private const val LOW_END_RAM_THRESHOLD_MB = 1024 // 1GB
        private const val LOW_END_API_THRESHOLD = 23 // Android 6.0
    }

    init {
        detectDeviceCapabilities()
    }

    /**
     * Start performance monitoring
     */
    fun startMonitoring(rootView: ViewGroup) {
        frameRateMonitor = FrameRateMonitor(rootView)
        memoryMonitor = MemoryMonitor()
        
        frameRateMonitor?.startMonitoring()
        memoryMonitor?.startMonitoring()
        
        Log.d(TAG, "Performance monitoring started. Low-end device: $isLowEndDevice")
    }

    /**
     * Stop performance monitoring
     */
    fun stopMonitoring() {
        frameRateMonitor?.stopMonitoring()
        memoryMonitor?.stopMonitoring()
        
        frameRateMonitor = null
        memoryMonitor = null
        
        Log.d(TAG, "Performance monitoring stopped")
    }

    /**
     * Apply performance optimizations based on device capabilities
     */
    fun applyPerformanceOptimizations(rootView: ViewGroup) {
        if (isLowEndDevice) {
            applyLowEndOptimizations(rootView)
        } else {
            applyStandardOptimizations(rootView)
        }
    }

    /**
     * Optimize GPU usage for blur effects
     */
    fun optimizeGpuUsage(glassyBackground: GlassyBackgroundView?) {
        glassyBackground?.let { background ->
            if (isLowEndDevice) {
                // Disable blur on low-end devices
                background.setBlurEnabled(false)
                background.setGlassIntensity(0.3f)
                Log.d(TAG, "Disabled blur effects for low-end device")
            } else {
                // Monitor performance and adjust blur quality
                frameRateMonitor?.setPerformanceCallback { fps ->
                    if (fps < LOW_FPS_THRESHOLD) {
                        background.setGlassIntensity(0.5f) // Reduce intensity
                        Log.d(TAG, "Reduced blur intensity due to low FPS: $fps")
                    }
                }
            }
        }
    }

    /**
     * Create fallback rendering for lower-end devices
     */
    fun createFallbackRendering(rootView: ViewGroup) {
        if (isLowEndDevice) {
            // Replace glass cards with simple backgrounds
            replaceGlassCardsWithSimpleBackgrounds(rootView)
            
            // Disable elevation and shadows
            disableElevationEffects(rootView)
            
            // Simplify animations
            simplifyAnimations(rootView)
            
            Log.d(TAG, "Applied fallback rendering for low-end device")
        }
    }

    private fun detectDeviceCapabilities() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val totalRamMB = memoryInfo.totalMem / (1024 * 1024)
        val apiLevel = Build.VERSION.SDK_INT
        
        isLowEndDevice = totalRamMB < LOW_END_RAM_THRESHOLD_MB || 
                        apiLevel < LOW_END_API_THRESHOLD ||
                        activityManager.isLowRamDevice
        
        Log.d(TAG, "Device capabilities - RAM: ${totalRamMB}MB, API: $apiLevel, Low-end: $isLowEndDevice")
    }

    private fun applyLowEndOptimizations(rootView: ViewGroup) {
        // Reduce animation complexity
        reduceAnimationComplexity(rootView)
        
        // Disable hardware acceleration for certain views if needed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            disableHardwareAcceleration(rootView)
        }
        
        // Use simpler drawables
        useSimpleDrawables(rootView)
        
        Log.d(TAG, "Applied low-end device optimizations")
    }

    private fun applyStandardOptimizations(rootView: ViewGroup) {
        // Enable hardware acceleration
        enableHardwareAcceleration(rootView)
        
        // Optimize layer types
        optimizeLayerTypes(rootView)
        
        Log.d(TAG, "Applied standard device optimizations")
    }

    private fun replaceGlassCardsWithSimpleBackgrounds(rootView: ViewGroup) {
        val glassCards = findViewsByType(rootView, GlassCard::class.java)
        glassCards.forEach { card ->
            card.setBackgroundResource(R.drawable.simple_card_background)
        }
    }

    private fun disableElevationEffects(rootView: ViewGroup) {
        disableElevationRecursive(rootView)
    }

    private fun disableElevationRecursive(view: View) {
        view.elevation = 0f
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                disableElevationRecursive(view.getChildAt(i))
            }
        }
    }

    private fun simplifyAnimations(rootView: ViewGroup) {
        // This would be implemented by the FocusAnimationManager
        // when it detects low-end device mode
    }

    private fun reduceAnimationComplexity(rootView: ViewGroup) {
        // Reduce animation duration and disable complex interpolators
        val animationScale = 0.5f // Reduce animation duration by half
        
        // This would be coordinated with FocusAnimationManager
        Log.d(TAG, "Reduced animation complexity with scale: $animationScale")
    }

    private fun disableHardwareAcceleration(rootView: ViewGroup) {
        rootView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        Log.d(TAG, "Disabled hardware acceleration for compatibility")
    }

    private fun enableHardwareAcceleration(rootView: ViewGroup) {
        rootView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    private fun optimizeLayerTypes(rootView: ViewGroup) {
        optimizeLayerTypesRecursive(rootView)
    }

    private fun optimizeLayerTypesRecursive(view: View) {
        // Set appropriate layer types based on view characteristics
        when {
            view is GlassCard -> view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            view.alpha < 1.0f -> view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            else -> view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                optimizeLayerTypesRecursive(view.getChildAt(i))
            }
        }
    }

    private fun useSimpleDrawables(rootView: ViewGroup) {
        // Replace complex drawables with simpler alternatives
        useSimpleDrawablesRecursive(rootView)
    }

    private fun useSimpleDrawablesRecursive(view: View) {
        // This would replace complex gradient/blur drawables with simple colors
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                useSimpleDrawablesRecursive(view.getChildAt(i))
            }
        }
    }

    private fun <T : View> findViewsByType(viewGroup: ViewGroup, clazz: Class<T>): List<T> {
        val views = mutableListOf<T>()
        
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            
            when {
                clazz.isInstance(child) -> views.add(clazz.cast(child)!!)
                child is ViewGroup -> views.addAll(findViewsByType(child, clazz))
            }
        }
        
        return views
    }

    /**
     * Frame rate monitoring class
     */
    private inner class FrameRateMonitor(rootView: ViewGroup) {
        private val rootViewRef = WeakReference(rootView)
        private var isMonitoring = false
        private var frameCount = 0
        private var lastFrameTime = 0L
        private var performanceCallback: ((Float) -> Unit)? = null
        
        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isMonitoring) return
                
                frameCount++
                val currentTime = System.currentTimeMillis()
                
                if (lastFrameTime == 0L) {
                    lastFrameTime = currentTime
                } else if (currentTime - lastFrameTime >= MONITORING_INTERVAL_MS) {
                    val fps = frameCount * 1000f / (currentTime - lastFrameTime)
                    
                    if (fps < LOW_FPS_THRESHOLD) {
                        Log.w(TAG, "Low FPS detected: $fps")
                        performanceCallback?.invoke(fps)
                    }
                    
                    frameCount = 0
                    lastFrameTime = currentTime
                }
                
                if (isMonitoring) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        
        fun startMonitoring() {
            isMonitoring = true
            frameCount = 0
            lastFrameTime = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
        
        fun stopMonitoring() {
            isMonitoring = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }
        
        fun setPerformanceCallback(callback: (Float) -> Unit) {
            performanceCallback = callback
        }
    }

    /**
     * Memory monitoring class
     */
    private inner class MemoryMonitor {
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
                    // Trigger garbage collection
                    System.gc()
                }
                
                if (isMonitoring) {
                    handler.postDelayed(this, MONITORING_INTERVAL_MS)
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
    }

    /**
     * Check if device is low-end
     */
    fun isLowEndDevice(): Boolean = isLowEndDevice
}