package com.thirutricks.tllplayer.infrastructure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thirutricks.tllplayer.models.TVList
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class DataManagerTest {

    private lateinit var dataManager: DataManager
    private lateinit var mockErrorHandler: ErrorHandler
    private lateinit var mockLogger: MenuLogger
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Initialize TVList for testing
        TVList.init(context)
        
        mockErrorHandler = object : ErrorHandler {
            override fun handleError(error: MenuError): ErrorResult = ErrorResult.Success
            override fun registerErrorListener(listener: ErrorListener) {}
            override fun unregisterErrorListener(listener: ErrorListener) {}
            override fun canRecover(error: MenuError): Boolean = true
            override fun attemptRecovery(error: MenuError, context: MenuContext): RecoveryResult = RecoveryResult.Success
            override fun logError(error: MenuError, context: String, additionalInfo: Map<String, Any>) {}
        }
        
        mockLogger = object : MenuLogger {
            override fun log(level: Int, tag: String, message: String, throwable: Throwable?) {}
            override fun logWithContext(level: Int, tag: String, message: String, context: Map<String, Any>, throwable: Throwable?) {}
            override fun setDebugMode(enabled: Boolean) {}
            override fun isDebugMode(): Boolean = false
            override fun getLogHistory(maxEntries: Int): List<LogEntry> = emptyList()
            override fun clearHistory() {}
        }
        
        dataManager = DataManager(mockErrorHandler, mockLogger)
    }

    @After
    fun tearDown() {
        dataManager.cleanup()
    }

    @Test
    fun `validateData returns errors for empty data`() {
        // When
        val result = dataManager.validateData()
        
        // Then - should have validation errors due to empty data
        assertFalse("Validation should fail for empty data", result.isValid)
        assertTrue("Should have validation errors", result.errors.isNotEmpty())
    }

    @Test
    fun `isDataReady returns false when not initialized`() {
        // When
        val result = dataManager.isDataReady()
        
        // Then
        assertFalse("Data should not be ready when not initialized", result)
    }

    @Test
    fun `safeGetGroupModel returns null when data not ready`() {
        // When
        val result = dataManager.safeGetGroupModel()
        
        // Then
        assertNull("Should return null when data not ready", result)
    }

    @Test
    fun `safeGetListModel returns null for any position when data not ready`() {
        // When
        val result = dataManager.safeGetListModel(0)
        
        // Then
        assertNull("Should return null when data not ready", result)
    }

    @Test
    fun `getDataStatistics returns correct initial state`() {
        // When
        val stats = dataManager.getDataStatistics()
        
        // Then
        assertFalse("Should not be initialized initially", stats.isInitialized)
        assertFalse("Should not be updating initially", stats.isUpdating)
        assertEquals("Should have no observers initially", 0, stats.observerCount)
    }

    @Test
    fun `cleanup resets state correctly`() {
        // Given - register an observer first
        val observer = object : DataObserver {
            override fun onDataReady() {}
            override fun onDataError(error: DataError) {}
            override fun onDataUpdated() {}
        }
        dataManager.registerDataObserver(observer)
        
        // When
        dataManager.cleanup()
        
        // Then
        val stats = dataManager.getDataStatistics()
        assertFalse("Should not be initialized after cleanup", stats.isInitialized)
        assertEquals("Should have no observers after cleanup", 0, stats.observerCount)
    }

    @Test
    fun `registerDataObserver increases observer count`() {
        // Given
        val observer = object : DataObserver {
            override fun onDataReady() {}
            override fun onDataError(error: DataError) {}
            override fun onDataUpdated() {}
        }
        
        // When
        dataManager.registerDataObserver(observer)
        
        // Then
        val stats = dataManager.getDataStatistics()
        assertEquals("Should have one observer", 1, stats.observerCount)
    }

    @Test
    fun `unregisterDataObserver decreases observer count`() {
        // Given
        val observer = object : DataObserver {
            override fun onDataReady() {}
            override fun onDataError(error: DataError) {}
            override fun onDataUpdated() {}
        }
        dataManager.registerDataObserver(observer)
        
        // When
        dataManager.unregisterDataObserver(observer)
        
        // Then
        val stats = dataManager.getDataStatistics()
        assertEquals("Should have no observers after unregister", 0, stats.observerCount)
    }
}