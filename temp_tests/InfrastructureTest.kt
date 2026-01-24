package com.thirutricks.tllplayer.infrastructure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thirutricks.tllplayer.models.TVList
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Basic tests to verify the infrastructure components compile and work correctly.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class InfrastructureTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Initialize TVList for testing
        TVList.init(context)
    }
    
    @Test
    fun testMenuErrorCreation() {
        val error = MenuError.DataNotInitialized
        assertEquals(ErrorSeverity.HIGH, error.severity)
        assertTrue(error.recoverable)
    }
    
    @Test
    fun testInfrastructureFactoryCreation() {
        val infrastructure = InfrastructureFactory.createMinimalInfrastructure(debugMode = true)
        
        assertNotNull(infrastructure.logger)
        assertNotNull(infrastructure.errorHandler)
        assertNotNull(infrastructure.resourceManager)
        assertNotNull(infrastructure.dataManager)
        assertNotNull(infrastructure.focusManager)
    }
    
    @Test
    fun testErrorHandling() {
        val infrastructure = InfrastructureFactory.createMinimalInfrastructure()
        val error = MenuError.DataNotInitialized
        
        val result = infrastructure.errorHandler.handleError(error)
        assertNotNull(result)
    }
    
    @Test
    fun testResourceManagerBasics() {
        val infrastructure = InfrastructureFactory.createMinimalInfrastructure()
        val resourceManager = infrastructure.resourceManager
        
        assertEquals(0, resourceManager.getResourceCount())
        assertFalse(resourceManager.isCleanupInProgress())
    }
    
    @Test
    fun testManagedResourceFactory() {
        val cleanupCalled = mutableListOf<Boolean>()
        
        val resource = ManagedResourceFactory.createManagedListener(
            cleanupAction = { cleanupCalled.add(true) },
            description = "Test Listener"
        )
        
        assertTrue(resource.isActive())
        assertEquals(ResourceType.LISTENER, resource.getResourceType())
        
        resource.cleanup()
        assertFalse(resource.isActive())
        assertEquals(1, cleanupCalled.size)
    }
    
    @Test
    fun testLoggingBasics() {
        val logger = InfrastructureFactory.createLogger()
        
        logger.log(android.util.Log.INFO, "Test", "Test message")
        
        // Basic test - just ensure logger works without errors
        assertNotNull(logger)
    }
}