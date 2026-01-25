package com.thirutricks.tllplayer.ui.glass

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.thirutricks.tllplayer.R

/**
 * Enhanced container for the glass menu system with responsive layout management,
 * smooth panel transitions, and comprehensive integration of all glass components.
 */
class GlassMenuContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var styleConfig: GlassStyleConfig = GlassEffectUtils.getOptimalGlassConfig(context)
    private var performanceManager: GlassPerformanceManager? = null
    private var animationController: MenuAnimationController? = null
    private var accessibilityManager: GlassAccessibilityManager? = null
    private var responsiveManager: GlassResponsiveManager? = null
    
    private var categoryPanel: RecyclerView? = null
    private var channelPanel: RecyclerView? = null
    private var transitionOverlay: View? = null
    private var menuBackground: View? = null
    
    private var isTransitioning = false
    private var currentFocusedPanel: PanelType = PanelType.CATEGORY
    private var isInitialized = false
    
    init {
        // Initialize managers
        initializeManagers()
        
        // Apply initial glass styling to the container
        GlassEffectUtils.applyGlassStyle(this, styleConfig, GlassType.MENU)
        
        // Enable hardware acceleration for better performance
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Set up responsive layout parameters
        setupResponsiveLayout()
    }
    
    private fun initializeManagers() {
        performanceManager = GlassPerformanceManager(context, styleConfig)
        animationController = MenuAnimationController(styleConfig, performanceManager)
        accessibilityManager = GlassAccessibilityManager(context)
        responsiveManager = GlassResponsiveManager(context)
        
        // Initialize performance monitoring
        performanceManager?.initialize(this)
    }
    
    override fun onFinishInflate() {
        super.onFinishInflate()
        
        // Find child views
        categoryPanel = findViewById(R.id.group)
        channelPanel = findViewById(R.id.list)
        transitionOverlay = findViewById(R.id.transition_overlay)
        menuBackground = findViewById(R.id.menu_background)
        
        // Complete initialization
        completeInitialization()
    }
    
    private fun completeInitialization() {
        if (isInitialized) return
        
        // Apply comprehensive glass effects to all components
        setupComprehensiveGlassStyling()
        
        // Set up advanced focus management
        setupAdvancedFocusManagement()
        
        // Apply responsive and accessibility enhancements
        applyResponsiveAndAccessibilityEnhancements()
        
        // Add micro-interactions and polish
        addMicroInteractions()
        
        isInitialized = true
    }
    
    private fun setupResponsiveLayout() {
        responsiveManager?.let { manager ->
            // Apply adaptive glass layout
            manager.applyAdaptiveGlassLayout(this)
            
            // Adjust layout based on TV characteristics
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val density = displayMetrics.density
            
            // Calculate optimal panel sizes based on screen dimensions
            val isLargeScreen = screenWidth >= 1920 && screenHeight >= 1080
            val isTelevision = manager.isTVDevice()
            
            if (isTelevision || isLargeScreen) {
                // Use television-optimized dimensions from resources (handled by XML)
                // val padding = context.resources.getDimensionPixelSize(R.dimen.glass_menu_padding)
                // setPadding(padding, padding, padding, padding)
            }
        }
    }
    
    private fun setupComprehensiveGlassStyling() {
        // Apply performance optimizations to all panels
        categoryPanel?.let { panel ->
            performanceManager?.applyPerformanceOptimizations(panel)
            GlassEffectUtils.applyGlassStyle(panel, styleConfig, GlassType.PANEL)
            
            // Set up smooth scrolling with glass effects
            panel.isNestedScrollingEnabled = true
            panel.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            
            // Add subtle glass border
            GlassEffectUtils.applyGlassBorder(panel, styleConfig)
        }
        
        channelPanel?.let { panel ->
            performanceManager?.applyPerformanceOptimizations(panel)
            GlassEffectUtils.applyGlassStyle(panel, styleConfig, GlassType.PANEL)
            
            // Set up smooth scrolling with glass effects
            panel.isNestedScrollingEnabled = true
            panel.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            
            // Add subtle glass border
            GlassEffectUtils.applyGlassBorder(panel, styleConfig)
        }
        
        // Apply comprehensive glass effect to menu background
        menuBackground?.let { background ->
            GlassEffectUtils.applyGlassBackground(background, GlassType.MENU, context)
            
            // Add subtle blur and transparency
            background.alpha = styleConfig.backgroundAlpha
            
            // Apply glass elevation
            background.elevation = styleConfig.menuElevation
        }
        
        // Style transition overlay
        transitionOverlay?.let { overlay ->
            overlay.alpha = 0f
            overlay.elevation = styleConfig.overlayElevation
            GlassEffectUtils.applyGlassStyle(overlay, styleConfig, GlassType.OVERLAY)
        }
    }
    
    private fun setupAdvancedFocusManagement() {
        // Set up focus change listeners for smooth panel transitions with error recovery
        categoryPanel?.setOnFocusChangeListener { view, hasFocus ->
            try {
                if (hasFocus && currentFocusedPanel != PanelType.CATEGORY) {
                    transitionToPanel(PanelType.CATEGORY)
                }
                
                // Apply focus animation with performance awareness
                animationController?.animateFocus(view, hasFocus)
            } catch (e: Exception) {
                // Handle focus errors gracefully
                performanceManager?.handleFocusRecovery(categoryPanel!!, 0)
            }
        }
        
        channelPanel?.setOnFocusChangeListener { view, hasFocus ->
            try {
                if (hasFocus && currentFocusedPanel != PanelType.CHANNEL) {
                    transitionToPanel(PanelType.CHANNEL)
                }
                
                // Apply focus animation with performance awareness
                animationController?.animateFocus(view, hasFocus)
            } catch (e: Exception) {
                // Handle focus errors gracefully
                performanceManager?.handleFocusRecovery(channelPanel!!, 0)
            }
        }
    }
    
    private fun applyResponsiveAndAccessibilityEnhancements() {
        // Apply accessibility enhancements
        accessibilityManager?.let { manager ->
            manager.applyGlassAccessibilityEnhancements(this)
            
            // Apply fallback styling if needed
            if (manager.hasLimitedGraphicsCapabilities()) {
                manager.applyFallbackStyling(this)
            }
        }
        
        // Apply responsive enhancements
        responsiveManager?.let { manager ->
            manager.applyAdaptiveGlassLayout(this)
        }
    }
    
    private fun addMicroInteractions() {
        // Add subtle hover effects for touch interactions
        setOnHoverListener { view, motionEvent ->
            when (motionEvent.action) {
                android.view.MotionEvent.ACTION_HOVER_ENTER -> {
                    animationController?.animateFocus(view, true)
                }
                android.view.MotionEvent.ACTION_HOVER_EXIT -> {
                    animationController?.animateFocus(view, false)
                }
            }
            false
        }
        
        // Add subtle scale animation on touch
        setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(0.98f)
                        .scaleY(0.98f)
                        .setDuration(100)
                        .start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
            }
            false
        }
    }
    
    /**
     * Smoothly transitions focus between panels with comprehensive glass effects
     */
    fun transitionToPanel(targetPanel: PanelType) {
        if (isTransitioning || currentFocusedPanel == targetPanel) return
        
        isTransitioning = true
        
        val fromPanel = when (currentFocusedPanel) {
            PanelType.CATEGORY -> categoryPanel
            PanelType.CHANNEL -> channelPanel
        }
        
        val toPanel = when (targetPanel) {
            PanelType.CATEGORY -> categoryPanel
            PanelType.CHANNEL -> channelPanel
        }
        
        val direction = when {
            currentFocusedPanel == PanelType.CATEGORY && targetPanel == PanelType.CHANNEL -> 
                TransitionDirection.LEFT_TO_RIGHT
            currentFocusedPanel == PanelType.CHANNEL && targetPanel == PanelType.CATEGORY -> 
                TransitionDirection.RIGHT_TO_LEFT
            else -> TransitionDirection.FADE_IN
        }
        
        // Show transition overlay for smooth effect
        transitionOverlay?.let { overlay ->
            overlay.visibility = View.VISIBLE
            overlay.alpha = 0f
            overlay.animate()
                .alpha(styleConfig.overlayAlpha)
                .setDuration(performanceManager?.getOptimizedAnimationDuration(150L) ?: 150L)
                .start()
        }
        
        // Perform comprehensive panel transition
        toPanel?.let { target ->
            animationController?.animatePanelTransition(
                fromPanel = fromPanel,
                toPanel = target,
                direction = direction
            ) {
                // Hide transition overlay with glass fade
                transitionOverlay?.let { overlay ->
                    overlay.animate()
                        .alpha(0f)
                        .setDuration(performanceManager?.getOptimizedAnimationDuration(100L) ?: 100L)
                        .withEndAction {
                            overlay.visibility = View.GONE
                        }
                        .start()
                }
                
                currentFocusedPanel = targetPanel
                isTransitioning = false
                
                // Add subtle panel focus glow
                addPanelFocusGlow(target)
            }
        }
    }
    
    private fun addPanelFocusGlow(panel: View) {
        // Add subtle glow effect to focused panel
        panel.animate()
            .scaleX(1.02f)
            .scaleY(1.02f)
            .setDuration(200L)
            .withEndAction {
                panel.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200L)
                    .start()
            }
            .start()
    }
    
    /**
     * Updates panel content with comprehensive smooth fade transition
     */
    fun updatePanelContent(panel: RecyclerView, updateAction: () -> Unit) {
        if (isTransitioning) return
        
        val optimizedDuration = performanceManager?.getOptimizedAnimationDuration(100L) ?: 100L
        
        // Fade out with glass effect
        panel.animate()
            .alpha(0f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(optimizedDuration)
            .withEndAction {
                updateAction()
                
                // Fade in with glass effect
                panel.animate()
                    .alpha(1f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(optimizedDuration)
                    .start()
            }
            .start()
    }
    
    /**
     * Applies comprehensive focus animation to a specific panel
     */
    fun animatePanelFocus(panel: RecyclerView, hasFocus: Boolean) {
        animationController?.animateFocus(panel, hasFocus)
        
        // Add additional glass focus effects
        if (hasFocus) {
            // Subtle elevation increase
            panel.animate()
                .translationZ(styleConfig.focusElevation)
                .setDuration(200L)
                .start()
        } else {
            // Return to normal elevation
            panel.animate()
                .translationZ(0f)
                .setDuration(200L)
                .start()
        }
    }
    
    /**
     * Gets the currently focused panel
     */
    fun getCurrentFocusedPanel(): PanelType = currentFocusedPanel
    
    /**
     * Checks if a panel transition is currently in progress
     */
    fun isTransitioning(): Boolean = isTransitioning
    
    /**
     * Updates the glass style configuration with comprehensive reapplication
     */
    fun updateStyleConfig(newConfig: GlassStyleConfig) {
        styleConfig = newConfig
        animationController = MenuAnimationController(styleConfig, performanceManager)
        
        // Reapply comprehensive styling to all components
        if (isInitialized) {
            setupComprehensiveGlassStyling()
        }
    }
    
    /**
     * Handles comprehensive accessibility adjustments
     */
    fun applyAccessibilityAdjustments(
        isHighContrastEnabled: Boolean,
        reduceMotionEnabled: Boolean
    ) {
        val accessibilityConfig = styleConfig.withAccessibilityAdjustments(
            isHighContrastEnabled = isHighContrastEnabled,
            reduceMotionEnabled = reduceMotionEnabled
        )
        
        updateStyleConfig(accessibilityConfig)
        
        // Apply additional accessibility enhancements
        accessibilityManager?.let { manager ->
            if (isHighContrastEnabled) {
                manager.applyHighContrastMode(this)
            }
        }
    }
    
    /**
     * Handles comprehensive performance adjustments based on device capabilities
     */
    fun applyPerformanceAdjustments(
        hasHardwareAcceleration: Boolean,
        isLowEndDevice: Boolean
    ) {
        val performanceConfig = styleConfig.withPerformanceAdjustments(
            hasHardwareAcceleration = hasHardwareAcceleration,
            isLowEndDevice = isLowEndDevice
        )
        
        updateStyleConfig(performanceConfig)
        
        // Adjust layer type based on hardware acceleration
        val layerType = if (hasHardwareAcceleration && !isLowEndDevice) {
            View.LAYER_TYPE_HARDWARE
        } else {
            View.LAYER_TYPE_SOFTWARE
        }
        
        setLayerType(layerType, null)
        children.forEach { child ->
            child.setLayerType(layerType, null)
        }
        
        // Apply performance optimizations to all panels
        categoryPanel?.let { performanceManager?.applyPerformanceOptimizations(it) }
        channelPanel?.let { performanceManager?.applyPerformanceOptimizations(it) }
    }
    
    /**
     * Ensures consistent visual hierarchy across all menu components
     */
    fun ensureVisualHierarchy() {
        // Apply consistent elevation levels
        menuBackground?.elevation = styleConfig.menuBackgroundElevation
        categoryPanel?.elevation = styleConfig.panelElevation
        channelPanel?.elevation = styleConfig.panelElevation
        transitionOverlay?.elevation = styleConfig.overlayElevation
        
        // Apply consistent alpha levels for depth perception
        menuBackground?.alpha = styleConfig.backgroundAlpha
        
        // Ensure consistent corner radius
        children.forEach { child ->
            if (child is ViewGroup) {
                GlassEffectUtils.applyConsistentCornerRadius(child, styleConfig.cornerRadius)
            }
        }
    }
    
    /**
     * Adds final visual polish and micro-interactions
     */
    fun addFinalPolish() {
        // Add subtle entrance animation
        alpha = 0f
        scaleX = 0.95f
        scaleY = 0.95f
        
        animate()
            .alpha(1f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(performanceManager?.getOptimizedAnimationDuration(300L) ?: 300L)
            .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
            .start()
        
        // Ensure visual hierarchy is maintained
        ensureVisualHierarchy()
        
        // Add subtle breathing animation to background
        menuBackground?.let { background ->
            val breathingAnimator = android.animation.ValueAnimator.ofFloat(
                styleConfig.backgroundAlpha,
                styleConfig.backgroundAlpha * 1.1f
            ).apply {
                duration = 3000L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                interpolator = android.view.animation.DecelerateInterpolator()
                
                addUpdateListener { animator ->
                    background.alpha = animator.animatedValue as Float
                }
            }
            
            // Only start breathing animation if performance allows
            if (performanceManager?.shouldUseAdvancedEffects() == true) {
                breathingAnimator.start()
            }
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        
        // Comprehensive cleanup
        animationController?.cancelAllAnimations()
        performanceManager?.cleanup()
        
        // Clear references
        performanceManager = null
        animationController = null
        accessibilityManager = null
        responsiveManager = null
    }
}

/**
 * Enum for panel types in the menu system
 */
enum class PanelType {
    CATEGORY,
    CHANNEL
}