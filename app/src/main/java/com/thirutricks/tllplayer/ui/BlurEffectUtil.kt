package com.thirutricks.tllplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import androidx.annotation.RequiresApi

/**
 * Utility class for creating blur effects with API level fallbacks
 * Supports modern RenderEffect (API 31+) and legacy RenderScript (API 17-30)
 */
class BlurEffectUtil(private val context: Context) {

    companion object {
        private const val DEFAULT_BLUR_RADIUS = 25f
        private const val MAX_BLUR_RADIUS = 25f
        private const val MIN_BLUR_RADIUS = 1f
    }

    /**
     * Apply blur effect to a view based on available API level
     */
    fun applyBlurToView(view: View, radius: Float = DEFAULT_BLUR_RADIUS) {
        val clampedRadius = radius.coerceIn(MIN_BLUR_RADIUS, MAX_BLUR_RADIUS)
        
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                applyRenderEffectBlur(view, clampedRadius)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 -> {
                // RenderScript is deprecated but still works for older devices
                // For production, consider using alternative blur libraries
                applyFallbackBlur(view)
            }
            else -> {
                // Very old devices - use simple transparency
                applySimpleTransparency(view)
            }
        }
    }

    /**
     * Modern blur effect using RenderEffect (API 31+)
     */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun applyRenderEffectBlur(view: View, radius: Float) {
        try {
            val blurEffect = RenderEffect.createBlurEffect(
                radius, radius, Shader.TileMode.CLAMP
            )
            view.setRenderEffect(blurEffect)
        } catch (e: Exception) {
            // Fallback if RenderEffect fails
            applyFallbackBlur(view)
        }
    }

    /**
     * Legacy blur effect using RenderScript (API 17-30)
     * Note: RenderScript is deprecated, but still functional
     */
    @Suppress("DEPRECATION")
    private fun applyRenderScriptBlur(bitmap: Bitmap, radius: Float): Bitmap? {
        return try {
            val renderScript = RenderScript.create(context)
            val input = Allocation.createFromBitmap(renderScript, bitmap)
            val output = Allocation.createTyped(renderScript, input.type)
            
            val script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
            script.setRadius(radius)
            script.setInput(input)
            script.forEach(output)
            
            val blurredBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
            output.copyTo(blurredBitmap)
            
            // Cleanup
            renderScript.destroy()
            input.destroy()
            output.destroy()
            script.destroy()
            
            blurredBitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fallback blur effect for older devices or when other methods fail
     */
    private fun applyFallbackBlur(view: View) {
        // Use a simple semi-transparent overlay as fallback
        view.alpha = 0.9f
        view.setBackgroundResource(android.R.color.transparent)
    }

    /**
     * Simple transparency for very old devices
     */
    private fun applySimpleTransparency(view: View) {
        view.alpha = 0.85f
    }

    /**
     * Create a blurred bitmap from a view
     */
    fun createBlurredBitmap(view: View, radius: Float = DEFAULT_BLUR_RADIUS): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(
                view.width, view.height, Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 -> {
                    applyRenderScriptBlur(bitmap, radius)
                }
                else -> {
                    // Return original bitmap with reduced alpha for old devices
                    val paint = Paint().apply { alpha = 200 }
                    val blurredBitmap = Bitmap.createBitmap(
                        bitmap.width, bitmap.height, bitmap.config
                    )
                    Canvas(blurredBitmap).drawBitmap(bitmap, 0f, 0f, paint)
                    blurredBitmap
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if blur effects are supported on current device
     */
    fun isBlurSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
    }

    /**
     * Get recommended blur radius based on device capabilities
     */
    fun getRecommendedBlurRadius(): Float {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> DEFAULT_BLUR_RADIUS
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 -> DEFAULT_BLUR_RADIUS * 0.8f
            else -> 0f
        }
    }
}