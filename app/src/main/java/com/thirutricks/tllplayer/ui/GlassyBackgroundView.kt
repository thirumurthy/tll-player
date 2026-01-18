package com.thirutricks.tllplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.thirutricks.tllplayer.R

/**
 * Custom view that creates a glassmorphism background effect
 * with gradient overlays and blur effects
 */
class GlassyBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val blurUtil = BlurEffectUtil(context)
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private var gradientShader: LinearGradient? = null
    private var isInitialized = false

    init {
        setupPaints()
        applyBlurEffect()
    }

    private fun setupPaints() {
        // Setup overlay paint for additional depth
        overlayPaint.apply {
            color = ContextCompat.getColor(context, R.color.surface_glass_alpha)
            alpha = 180 // 70% transparency
        }
    }

    private fun applyBlurEffect() {
        if (blurUtil.isBlurSupported()) {
            val radius = blurUtil.getRecommendedBlurRadius()
            blurUtil.applyBlurToView(this, radius)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            createGradientShader(w, h)
            isInitialized = true
        }
    }

    private fun createGradientShader(width: Int, height: Int) {
        // Create diagonal gradient for depth effect
        gradientShader = LinearGradient(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            intArrayOf(
                0x1A1A1A1A, // Very subtle start
                0x33000000, // Darker middle
                0x4D000000  // Darker end
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        gradientPaint.shader = gradientShader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!isInitialized) return

        // Draw gradient overlay
        gradientShader?.let {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
        }

        // Draw additional overlay for glass effect
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
    }

    /**
     * Update the glass effect intensity
     */
    fun setGlassIntensity(intensity: Float) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        overlayPaint.alpha = (180 * clampedIntensity).toInt()
        invalidate()
    }

    /**
     * Enable or disable the blur effect
     */
    fun setBlurEnabled(enabled: Boolean) {
        if (enabled && blurUtil.isBlurSupported()) {
            applyBlurEffect()
        } else {
            // Remove blur effect
            setRenderEffect(null)
        }
    }
}