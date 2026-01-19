package com.thirutricks.tllplayer.ui.glass

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/**
 * Centralized animation controller for consistent menu animations.
 * Manages focus, transition, and move mode animations with proper timing and easing.
 * Integrates with GlassPerformanceManager for optimized performance.
 */
class MenuAnimationController(
    private val styleConfig: GlassStyleConfig = GlassStyleConfig.DEFAULT,
    private val performanceManager: GlassPerformanceManager? = null
) {
    
    companion object {
        // Animation interpolators
        val FOCUS_INTERPOLATOR = DecelerateInterpolator(1.5f)
        val TRANSITION_INTERPOLATOR = FastOutSlowInInterpolator()
        val MOVE_INTERPOLATOR = DecelerateInterpolator(2.0f)
        
        // Animation property names
        private const val SCALE_X = "scaleX"
        private const val SCALE_Y = "scaleY"
        private const val ELEVATION = "elevation"
        private const val ALPHA = "alpha"
        private const val TRANSLATION_Z = "translationZ"
    }
    
    private val activeAnimations = mutableMapOf<View, AnimatorSet>()
    
    /**
     * Animates focus state change with scale, elevation, and glow effects
     */
    fun animateFocus(
        view: View, 
        hasFocus: Boolean,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        // Cancel any existing animation for this view
        cancelAnimation(view)
        
        val targetScale = if (hasFocus) styleConfig.focusScale else 1.0f
        val targetElevation = if (hasFocus) styleConfig.focusElevation else 0f
        val targetAlpha = if (hasFocus) 1.0f else 0.9f
        
        // Get optimized duration from performance manager
        val baseDuration = styleConfig.focusAnimationDuration
        val optimizedDuration = performanceManager?.getOptimizedAnimationDuration(baseDuration) ?: baseDuration
        
        // Create animation set with error handling
        val animatorSet = createSafeAnimatorSet(view) {
            val scaleXAnimator = ObjectAnimator.ofFloat(view, SCALE_X, view.scaleX, targetScale)
            val scaleYAnimator = ObjectAnimator.ofFloat(view, SCALE_Y, view.scaleY, targetScale)
            val elevationAnimator = ObjectAnimator.ofFloat(view, ELEVATION, view.elevation, targetElevation)
            val alphaAnimator = ObjectAnimator.ofFloat(view, ALPHA, view.alpha, targetAlpha)
            
            listOf(scaleXAnimator, scaleYAnimator, elevationAnimator, alphaAnimator).forEach { animator ->
                animator.duration = optimizedDuration
                animator.interpolator = FOCUS_INTERPOLATOR
                
                // Register with performance manager
                performanceManager?.registerAnimation(animator)
            }
            
            AnimatorSet().apply {
                playTogether(scaleXAnimator, scaleYAnimator, elevationAnimator, alphaAnimator)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimations.remove(view)
                        onAnimationEnd?.invoke()
                    }
                    override fun onAnimationCancel(animation: Animator) {
                        activeAnimations.remove(view)
                    }
                })
            }
        }
        
        activeAnimations[view] = animatorSet
        animatorSet.start()
    }
    
    /**
     * Creates a safe animator set with error handling
     */
    private fun createSafeAnimatorSet(view: View, animatorFactory: () -> AnimatorSet): AnimatorSet {
        return try {
            animatorFactory()
        } catch (e: Exception) {
            android.util.Log.w("MenuAnimationController", "Animation creation failed: ${e.message}")
            // Return a no-op animator set
            AnimatorSet().apply {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimations.remove(view)
                    }
                })
            }
        }
    }
    
    /**
     * Animates panel transition with fade and slide effects
     */
    fun animatePanelTransition(
        fromPanel: View?,
        toPanel: View,
        direction: TransitionDirection = TransitionDirection.FADE_IN,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        val animators = mutableListOf<Animator>()
        val baseDuration = 150L
        val optimizedDuration = performanceManager?.getOptimizedAnimationDuration(baseDuration) ?: baseDuration
        
        // Animate out the from panel if provided
        fromPanel?.let { panel ->
            val fadeOut = ObjectAnimator.ofFloat(panel, ALPHA, panel.alpha, 0f)
            val slideOut = when (direction) {
                TransitionDirection.LEFT_TO_RIGHT -> 
                    ObjectAnimator.ofFloat(panel, "translationX", 0f, -panel.width * 0.1f)
                TransitionDirection.RIGHT_TO_LEFT -> 
                    ObjectAnimator.ofFloat(panel, "translationX", 0f, panel.width * 0.1f)
                else -> null
            }
            
            animators.add(fadeOut)
            slideOut?.let { animators.add(it) }
        }
        
        // Animate in the to panel
        val fadeIn = ObjectAnimator.ofFloat(toPanel, ALPHA, 0f, 1f)
        val slideIn = when (direction) {
            TransitionDirection.LEFT_TO_RIGHT -> {
                toPanel.translationX = toPanel.width * 0.1f
                ObjectAnimator.ofFloat(toPanel, "translationX", toPanel.translationX, 0f)
            }
            TransitionDirection.RIGHT_TO_LEFT -> {
                toPanel.translationX = -toPanel.width * 0.1f
                ObjectAnimator.ofFloat(toPanel, "translationX", toPanel.translationX, 0f)
            }
            else -> null
        }
        
        animators.add(fadeIn)
        slideIn?.let { animators.add(it) }
        
        // Register animations with performance manager
        animators.forEach { animator ->
            if (animator is ValueAnimator) {
                performanceManager?.registerAnimation(animator)
            }
        }
        
        val animatorSet = AnimatorSet().apply {
            playTogether(animators)
            duration = optimizedDuration
            interpolator = TRANSITION_INTERPOLATOR
            
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onAnimationEnd?.invoke()
                }
            })
        }
        
        animatorSet.start()
    }
    
    /**
     * Animates move mode state with enhanced scale and glow effects
     */
    fun animateMoveMode(
        view: View,
        isMoving: Boolean,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        cancelAnimation(view)
        
        val targetScale = if (isMoving) styleConfig.moveScale else 1.0f
        val targetElevation = if (isMoving) styleConfig.moveElevation else styleConfig.focusElevation
        val targetAlpha = if (isMoving) 1.0f else 0.95f
        
        val baseDuration = styleConfig.moveAnimationDuration
        val optimizedDuration = performanceManager?.getOptimizedAnimationDuration(baseDuration) ?: baseDuration
        
        val animatorSet = createSafeAnimatorSet(view) {
            val scaleXAnimator = ObjectAnimator.ofFloat(view, SCALE_X, view.scaleX, targetScale)
            val scaleYAnimator = ObjectAnimator.ofFloat(view, SCALE_Y, view.scaleY, targetScale)
            val elevationAnimator = ObjectAnimator.ofFloat(view, ELEVATION, view.elevation, targetElevation)
            val alphaAnimator = ObjectAnimator.ofFloat(view, ALPHA, view.alpha, targetAlpha)
            
            listOf(scaleXAnimator, scaleYAnimator, elevationAnimator, alphaAnimator).forEach { animator ->
                animator.duration = optimizedDuration
                animator.interpolator = MOVE_INTERPOLATOR
                performanceManager?.registerAnimation(animator)
            }
            
            AnimatorSet().apply {
                playTogether(scaleXAnimator, scaleYAnimator, elevationAnimator, alphaAnimator)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        activeAnimations.remove(view)
                        onAnimationEnd?.invoke()
                    }
                    override fun onAnimationCancel(animation: Animator) {
                        activeAnimations.remove(view)
                    }
                })
            }
        }
        
        activeAnimations[view] = animatorSet
        animatorSet.start()
    }
    
    /**
     * Animates content fade transition for smooth panel content changes
     */
    fun animateContentFade(
        view: View,
        fadeOut: Boolean = true,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        val targetAlpha = if (fadeOut) 0f else 1f
        val startAlpha = if (fadeOut) 1f else 0f
        
        view.alpha = startAlpha
        
        val baseDuration = 100L
        val optimizedDuration = performanceManager?.getOptimizedAnimationDuration(baseDuration) ?: baseDuration
        
        val fadeAnimator = ObjectAnimator.ofFloat(view, ALPHA, startAlpha, targetAlpha).apply {
            duration = optimizedDuration
            interpolator = TRANSITION_INTERPOLATOR
            
            performanceManager?.registerAnimation(this)
            
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onAnimationEnd?.invoke()
                }
            })
        }
        
        fadeAnimator.start()
    }
    
    /**
     * Animates heart/favorite button with enhanced glass styling
     */
    fun animateHeartLike(
        view: View,
        isLiked: Boolean,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        cancelAnimation(view)
        
        if (isLiked) {
            // Animate heart like with bounce and glow effect
            val animatorSet = createSafeAnimatorSet(view) {
                val scaleUpX = ObjectAnimator.ofFloat(view, SCALE_X, 1.0f, 1.3f)
                val scaleUpY = ObjectAnimator.ofFloat(view, SCALE_Y, 1.0f, 1.3f)
                val scaleDownX = ObjectAnimator.ofFloat(view, SCALE_X, 1.3f, 1.0f)
                val scaleDownY = ObjectAnimator.ofFloat(view, SCALE_Y, 1.3f, 1.0f)
                
                listOf(scaleUpX, scaleUpY, scaleDownX, scaleDownY).forEach { animator ->
                    performanceManager?.registerAnimation(animator)
                }
                
                AnimatorSet().apply {
                    play(AnimatorSet().apply { 
                        playTogether(scaleUpX, scaleUpY)
                        duration = 150L
                    }).before(AnimatorSet().apply {
                        playTogether(scaleDownX, scaleDownY)
                        duration = 200L
                    })
                    
                    interpolator = DecelerateInterpolator()
                    
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            activeAnimations.remove(view)
                            onAnimationEnd?.invoke()
                        }
                        override fun onAnimationCancel(animation: Animator) {
                            activeAnimations.remove(view)
                        }
                    })
                }
            }
            
            activeAnimations[view] = animatorSet
            animatorSet.start()
        } else {
            // Simple fade animation for unlike
            val fadeAnimator = ObjectAnimator.ofFloat(view, ALPHA, 1.0f, 0.7f, 1.0f).apply {
                duration = performanceManager?.getOptimizedAnimationDuration(200L) ?: 200L
                interpolator = TRANSITION_INTERPOLATOR
                
                performanceManager?.registerAnimation(this)
                
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onAnimationEnd?.invoke()
                    }
                })
            }
            
            fadeAnimator.start()
        }
    }
    
    /**
     * Cancels any active animation for the specified view
     */
    fun cancelAnimation(view: View) {
        activeAnimations[view]?.cancel()
        activeAnimations.remove(view)
    }
    
    /**
     * Cancels all active animations
     */
    fun cancelAllAnimations() {
        activeAnimations.values.forEach { it.cancel() }
        activeAnimations.clear()
    }
    
    /**
     * Checks if an animation is currently running for the specified view
     */
    fun isAnimating(view: View): Boolean {
        return activeAnimations[view]?.isRunning == true
    }
}