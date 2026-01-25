package com.thirutricks.tllplayer.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.thirutricks.tllplayer.R

/**
 * Specialized focus manager for the settings page
 * Handles focus order, animations, and audio feedback for all settings elements
 */
class SettingsFocusManager(private val context: Context) {

    private val focusAnimationManager = FocusAnimationManager(context)
    private var tvUiUtils: TvUiUtils? = null
    private var isHighContrastMode = false

    /**
     * Initialize with TvUiUtils for audio feedback
     */
    fun initialize(tvUiUtils: TvUiUtils) {
        this.tvUiUtils = tvUiUtils
        focusAnimationManager.initializeWithAudio(tvUiUtils)
    }

    /**
     * Setup focus handling for the entire settings layout
     */
    fun setupSettingsFocus(rootView: ViewGroup) {
        // Setup focus for all glass cards
        setupGlassCardsFocus(rootView)
        
        // Setup focus for configuration elements
        setupConfigurationFocus(rootView)
        
        // Setup focus for preference toggles
        setupPreferencesFocus(rootView)
        
        // Setup focus for action buttons
        setupActionsFocus(rootView)
        
        // Setup logical focus order
        setupFocusNavigation(rootView)
    }

    private fun setupGlassCardsFocus(rootView: ViewGroup) {
        val glassCards = findViewsByType(rootView, GlassCard::class.java)
        glassCards.forEach { card ->
            card.initializeWithAudio(tvUiUtils!!)
            card.setupChildFocusHandling()
        }
    }

    private fun setupConfigurationFocus(rootView: ViewGroup) {
        // QR Code button
        rootView.findViewById<Button>(R.id.qrcode)?.let { button ->
            focusAnimationManager.setupFocusHandling(button, FocusAnimationManager.AnimationType.BUTTON)
        }
        
        // Config confirmation button
        rootView.findViewById<Button>(R.id.confirm_config)?.let { button ->
            focusAnimationManager.setupFocusHandling(button, FocusAnimationManager.AnimationType.BUTTON)
        }
        
        // Channel confirmation button
        rootView.findViewById<Button>(R.id.confirm_channel)?.let { button ->
            focusAnimationManager.setupFocusHandling(button, FocusAnimationManager.AnimationType.BUTTON)
        }
        
        // Config EditText
        rootView.findViewById<EditText>(R.id.config)?.let { editText ->
            focusAnimationManager.setupFocusHandling(editText, FocusAnimationManager.AnimationType.ENHANCED)
        }
        
        // Channel EditText
        rootView.findViewById<EditText>(R.id.channel)?.let { editText ->
            focusAnimationManager.setupFocusHandling(editText, FocusAnimationManager.AnimationType.ENHANCED)
        }
    }

    private fun setupPreferencesFocus(rootView: ViewGroup) {
        val toggleIds = listOf(
            R.id.switch_channel_reversal,
            R.id.switch_channel_num,
            R.id.switch_time,
            R.id.switch_watch_last,
            R.id.switch_force_high_quality,
            R.id.switch_boot_startup,
            R.id.switch_config_auto_load,
            R.id.switch_channel_check
        )
        
        toggleIds.forEach { id ->
            rootView.findViewById<ModernToggleSwitch>(id)?.let { toggle ->
                // Initialize ModernToggleSwitch with audio feedback
                tvUiUtils?.let { utils ->
                    toggle.initializeWithAudio(utils)
                }
                
                // Add explicit focus handling for consistent animations
                focusAnimationManager.setupFocusHandling(toggle, FocusAnimationManager.AnimationType.SUBTLE)
            }
        }
    }

    private fun setupActionsFocus(rootView: ViewGroup) {
        val actionButtonIds = listOf(
            R.id.clear,
            R.id.reset_order,
            R.id.appreciate,
            R.id.exit
        )
        
        actionButtonIds.forEach { id ->
            rootView.findViewById<Button>(id)?.let { button ->
                focusAnimationManager.setupFocusHandling(button, FocusAnimationManager.AnimationType.BUTTON)
            }
        }
    }

    private fun setupFocusNavigation(rootView: ViewGroup) {
        // Get all focusable views in logical order
        val focusableViews = getFocusableViewsInOrder(rootView)
        
        // Setup focus order for TV navigation
        focusAnimationManager.setupFocusOrder(focusableViews)
        
        // Set initial focus
        if (focusableViews.isNotEmpty()) {
            focusableViews.first().requestFocus()
        }
    }

    private fun getFocusableViewsInOrder(rootView: ViewGroup): List<View> {
        val focusableViews = mutableListOf<View>()
        
        // Configuration section - Top to bottom, left to right
        rootView.findViewById<Button>(R.id.qrcode)?.let { focusableViews.add(it) }
        
        rootView.findViewById<Button>(R.id.confirm_config)?.let { focusableViews.add(it) }
        rootView.findViewById<EditText>(R.id.config)?.let { focusableViews.add(it) }
        
        rootView.findViewById<Button>(R.id.confirm_channel)?.let { focusableViews.add(it) }
        rootView.findViewById<EditText>(R.id.channel)?.let { focusableViews.add(it) }
        
        // Preferences section (toggles) - sequential order
        val toggleIds = listOf(
            R.id.switch_channel_reversal,
            R.id.switch_channel_num,
            R.id.switch_time,
            R.id.switch_watch_last,
            R.id.switch_force_high_quality,
            R.id.switch_boot_startup,
            R.id.switch_config_auto_load,
            R.id.switch_channel_check
        )
        
        toggleIds.forEach { id ->
            rootView.findViewById<View>(id)?.let { focusableViews.add(it) }
        }
        
        // Actions section - left to right row
        val actionIds = listOf(R.id.clear, R.id.reset_order, R.id.appreciate, R.id.exit)
        actionIds.forEach { id ->
            rootView.findViewById<View>(id)?.let { focusableViews.add(it) }
        }
        
        return focusableViews.filter { it.visibility == View.VISIBLE && it.isFocusable }
    }

    /**
     * Handle focus changes for better navigation experience
     */
    fun handleFocusChange(view: View, hasFocus: Boolean) {
        if (hasFocus) {
            // Ensure the focused view is visible by scrolling if needed
            ensureViewVisible(view)
        }
    }

    private fun ensureViewVisible(view: View) {
        // Find parent ScrollView and scroll to make view visible
        var parent = view.parent
        while (parent != null) {
            if (parent is android.widget.ScrollView) {
                parent.smoothScrollTo(0, view.top)
                break
            }
            parent = parent.parent
        }
    }

    /**
     * Set high contrast mode for enhanced focus indicators
     */
    fun setHighContrastMode(enabled: Boolean) {
        isHighContrastMode = enabled
        focusAnimationManager.setHighContrastMode(enabled)
    }

    /**
     * Cleanup animations when settings page is destroyed
     */
    fun cleanup(rootView: ViewGroup) {
        focusAnimationManager.cancelAllAnimations(rootView)
    }

    /**
     * Utility function to find views by type
     */
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
}