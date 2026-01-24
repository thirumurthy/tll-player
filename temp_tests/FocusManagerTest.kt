package com.thirutricks.tllplayer.infrastructure

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class FocusManagerTest {

    private lateinit var context: Context
    private lateinit var focusManager: FocusManager
    private lateinit var errorHandler: ErrorHandler
    private lateinit var logger: MenuLogger
    private lateinit var testView1: View
    private lateinit var testView2: View

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        logger = DefaultMenuLogger()
        errorHandler = DefaultErrorHandler(logger, emptyList())
        focusManager = FocusManager(errorHandler, logger)
        
        // Create test views
        testView1 = TextView(context)
        testView2 = TextView(context)
        
        // Make views focusable
        testView1.isFocusable = true
        testView1.isFocusableInTouchMode = true
        testView2.isFocusable = true
        testView2.isFocusableInTouchMode = true
    }

    @After
    fun tearDown() {
        focusManager.cleanup()
    }

    @Test
    fun `initial state should be disabled mode with no focus`() {
        assertEquals(FocusMode.DISABLED, focusManager.getCurrentMode())
        assertNull(focusManager.getCurrentFocus())
        assertNull(focusManager.getCurrentSource())
    }

    @Test
    fun `should successfully request focus for a view`() {
        // Set up main looper for focus operations
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        
        focusManager.setFocusMode(FocusMode.CATEGORY_PANEL)
        
        val result = focusManager.requestFocus(testView1, FocusSource.GROUP_ADAPTER)
        
        assertTrue(result, "Focus request should succeed")
        assertEquals(testView1, focusManager.getCurrentFocus())
        assertEquals(FocusSource.GROUP_ADAPTER, focusManager.getCurrentSource())
    }

    @Test
    fun `should clear focus from specific source`() {
        // Set up main looper for focus operations
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        
        focusManager.setFocusMode(FocusMode.CATEGORY_PANEL)
        focusManager.requestFocus(testView1, FocusSource.GROUP_ADAPTER)
        
        focusManager.clearFocus(FocusSource.GROUP_ADAPTER)
        
        assertNull(focusManager.getCurrentFocus())
        assertNull(focusManager.getCurrentSource())
    }

    @Test
    fun `should change focus mode correctly`() {
        focusManager.setFocusMode(FocusMode.CATEGORY_PANEL)
        assertEquals(FocusMode.CATEGORY_PANEL, focusManager.getCurrentMode())
        
        focusManager.setFocusMode(FocusMode.CHANNEL_PANEL)
        assertEquals(FocusMode.CHANNEL_PANEL, focusManager.getCurrentMode())
    }

    @Test
    fun `should handle transitioning mode with timeout`() {
        focusManager.setFocusMode(FocusMode.TRANSITIONING)
        assertEquals(FocusMode.TRANSITIONING, focusManager.getCurrentMode())
        
        val stats = focusManager.getFocusStatistics()
        assertTrue(stats.isTransitioning)
    }

    @Test
    fun `should register and notify focus listeners`() {
        var notificationReceived = false
        var notifiedView: View? = null
        var notifiedSource: FocusSource? = null
        var notifiedHasFocus: Boolean? = null
        
        val listener = object : FocusListener {
            override fun onFocusChanged(view: View?, source: FocusSource, hasFocus: Boolean) {
                notificationReceived = true
                notifiedView = view
                notifiedSource = source
                notifiedHasFocus = hasFocus
            }
            
            override fun onFocusConflict(conflictingSources: List<FocusSource>) {}
            override fun onFocusRecovered(recoveryAction: String) {}
        }
        
        focusManager.registerFocusListener(listener)
        focusManager.setFocusMode(FocusMode.CATEGORY_PANEL)
        
        // Set up main looper for focus operations
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        
        focusManager.requestFocus(testView1, FocusSource.GROUP_ADAPTER)
        
        assertTrue(notificationReceived, "Focus listener should be notified")
        assertEquals(testView1, notifiedView)
        assertEquals(FocusSource.GROUP_ADAPTER, notifiedSource)
        assertEquals(true, notifiedHasFocus)
    }

    @Test
    fun `should unregister focus listeners`() {
        var notificationCount = 0
        
        val listener = object : FocusListener {
            override fun onFocusChanged(view: View?, source: FocusSource, hasFocus: Boolean) {
                notificationCount++
            }
            override fun onFocusConflict(conflictingSources: List<FocusSource>) {}
            override fun onFocusRecovered(recoveryAction: String) {}
        }
        
        focusManager.registerFocusListener(listener)
        focusManager.setFocusMode(FocusMode.CATEGORY_PANEL)
        
        // Set up main looper for focus operations
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        
        focusManager.requestFocus(testView1, FocusSource.GROUP_ADAPTER)
        assertEquals(1, notificationCount)
        
        focusManager.unregisterFocusListener(listener)
        focusManager.requestFocus(testView2, FocusSource.LIST_ADAPTER)
        
        // Should still be 1 since listener was unregistered
        assertEquals(1, notificationCount)
    }

    @Test
    fun `should provide focus statistics`() {
        focusManager.setFocusMode(FocusMode.CATEGORY_PANEL)
        
        val listener = object : FocusListener {
            override fun onFocusChanged(view: View?, source: FocusSource, hasFocus: Boolean) {}
            override fun onFocusConflict(conflictingSources: List<FocusSource>) {}
            override fun onFocusRecovered(recoveryAction: String) {}
        }
        
        focusManager.registerFocusListener(listener)
        
        val stats = focusManager.getFocusStatistics()
        
        assertEquals(FocusMode.CATEGORY_PANEL, stats.currentMode)
        assertEquals(1, stats.listenerCount)
        assertFalse(stats.isRecovering)
        assertEquals(0, stats.recoveryAttempts)
    }

    @Test
    fun `should handle focus requests during recovery`() {
        // Simulate recovery state
        focusManager.recoverFromDeadlock()
        
        // Set up main looper for focus operations
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        
        val result = focusManager.requestFocus(testView1, FocusSource.GROUP_ADAPTER)
        
        // Focus requests should be ignored during recovery
        assertFalse(result, "Focus request should be ignored during recovery")
    }

    @Test
    fun `should cleanup properly`() {
        focusManager.setFocusMode(FocusMode.CATEGORY_PANEL)
        
        val listener = object : FocusListener {
            override fun onFocusChanged(view: View?, source: FocusSource, hasFocus: Boolean) {}
            override fun onFocusConflict(conflictingSources: List<FocusSource>) {}
            override fun onFocusRecovered(recoveryAction: String) {}
        }
        
        focusManager.registerFocusListener(listener)
        
        // Set up main looper for focus operations
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        
        focusManager.requestFocus(testView1, FocusSource.GROUP_ADAPTER)
        
        focusManager.cleanup()
        
        val stats = focusManager.getFocusStatistics()
        assertEquals(FocusMode.DISABLED, stats.currentMode)
        assertEquals(0, stats.listenerCount)
        assertNull(focusManager.getCurrentFocus())
    }

    @Test
    fun `should handle same source focus requests without conflict`() {
        focusManager.setFocusMode(FocusMode.CATEGORY_PANEL)
        
        // Set up main looper for focus operations
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        
        val result1 = focusManager.requestFocus(testView1, FocusSource.GROUP_ADAPTER)
        val result2 = focusManager.requestFocus(testView2, FocusSource.GROUP_ADAPTER)
        
        assertTrue(result1, "First focus request should succeed")
        assertTrue(result2, "Second focus request from same source should succeed")
        assertEquals(testView2, focusManager.getCurrentFocus())
        assertEquals(FocusSource.GROUP_ADAPTER, focusManager.getCurrentSource())
    }

    @Test
    fun `should maintain focus history`() {
        focusManager.setFocusMode(FocusMode.CATEGORY_PANEL)
        
        // Set up main looper for focus operations
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        
        focusManager.requestFocus(testView1, FocusSource.GROUP_ADAPTER)
        focusManager.setFocusMode(FocusMode.CHANNEL_PANEL)
        focusManager.requestFocus(testView2, FocusSource.LIST_ADAPTER)
        
        val stats = focusManager.getFocusStatistics()
        assertTrue(stats.historySize > 0, "Focus history should contain entries")
    }
}