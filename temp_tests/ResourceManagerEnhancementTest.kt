package com.thirutricks.tllplayer.infrastructure

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thirutricks.tllplayer.models.TVList
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Tests for the enhanced ResourceManager with lifecycle tracking and memory pressure detection.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class ResourceManagerEnhancementTest {
    
    private lateinit var infrastructure: MinimalInfrastructure
    private lateinit var resourceManager: ResourceManager
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Initialize TVList for testing
        TVList.init(context)
        
        infrastructure = InfrastructureFactory.createMinimalInfrastructure(debugMode = true)
        resourceManager = infrastructure.resourceManager
    }
    
    @Test
    fun testLifecycleTracking() {
        val cleanupCalled = mutableListOf<Boolean>()
        
        val resource = ManagedResourceFactory.createManagedListener(
            cleanupAction = { cleanupCalled.add(true) },
            description = "Test Lifecycle Listener"
        )
        
        // Test initial state
        assertEquals(ResourceLifecycleState.CREATED, resource.getLifecycleState())
        assertTrue(resource.isActive())
        
        // Test access time update
        val initialAccessTime = resource.getLastAccessTime()
        Thread.sleep(10) // Small delay to ensure time difference
        resource.updateLastAccessTime()
        assertTrue(resource.getLastAccessTime() > initialAccessTime)
        assertEquals(ResourceLifecycleState.ACTIVE, resource.getLifecycleState())
        
        // Test memory footprint
        assertTrue(resource.getMemoryFootprint() > 0)
        
        // Test cleanup
        resource.cleanup()
        assertFalse(resource.isActive())
        assertEquals(ResourceLifecycleState.CLEANED_UP, resource.getLifecycleState())
        assertEquals(1, cleanupCalled.size)
    }
    
    @Test
    fun testResourceRegistrationWithLifecycleInfo() {
        val resource = ManagedResourceFactory.createManagedListener(
            cleanupAction = { },
            description = "Test Registration"
        )
        
        val initialCount = resourceManager.getResourceCount()
        resourceManager.registerResource(resource)
        
        assertEquals(initialCount + 1, resourceManager.getResourceCount())
        
        // Test lifecycle info retrieval
        val lifecycleInfo = resourceManager.getResourceLifecycleInfo(resource.getResourceId())
        assertNotNull(lifecycleInfo)
        assertEquals(resource.getResourceId(), lifecycleInfo!!.resourceId)
        assertEquals(ResourceType.LISTENER, lifecycleInfo.resourceType)
        assertEquals(resource.getLifecycleState(), lifecycleInfo.state)
        
        resourceManager.unregisterResource(resource)
        assertEquals(initialCount, resourceManager.getResourceCount())
        assertNull(resourceManager.getResourceLifecycleInfo(resource.getResourceId()))
    }
    
    @Test
    fun testResourcesByState() {
        val resource1 = ManagedResourceFactory.createManagedListener({ }, "Listener 1")
        val resource2 = ManagedResourceFactory.createManagedListener({ }, "Listener 2")
        
        resourceManager.registerResource(resource1)
        resourceManager.registerResource(resource2)
        
        // Both should be in CREATED state initially
        val createdResources = resourceManager.getResourcesInState(ResourceLifecycleState.CREATED)
        assertTrue(createdResources.size >= 2)
        assertTrue(createdResources.contains(resource1))
        assertTrue(createdResources.contains(resource2))
        
        // Update one to ACTIVE
        resource1.updateLastAccessTime()
        val activeResources = resourceManager.getResourcesInState(ResourceLifecycleState.ACTIVE)
        assertTrue(activeResources.contains(resource1))
        assertFalse(activeResources.contains(resource2))
        
        resourceManager.unregisterResource(resource1)
        resourceManager.unregisterResource(resource2)
    }
    
    @Test
    fun testResourcesOlderThan() {
        val resource = ManagedResourceFactory.createManagedListener({ }, "Old Resource")
        resourceManager.registerResource(resource)
        
        // Should not be old immediately
        val oldResources = resourceManager.getResourcesOlderThan(1000L)
        assertFalse(oldResources.contains(resource))
        
        // Should be old after 0ms threshold
        val allResources = resourceManager.getResourcesOlderThan(0L)
        assertTrue(allResources.contains(resource))
        
        resourceManager.unregisterResource(resource)
    }
    
    @Test
    fun testMemoryPressureDetection() {
        // Test memory pressure levels
        val pressureLevel = resourceManager.checkMemoryPressure()
        assertNotNull(pressureLevel)
        assertTrue(pressureLevel in MemoryPressureLevel.values())
    }
    
    @Test
    fun testDegradedMode() {
        assertFalse(resourceManager.isDegradedMode())
        
        resourceManager.enterDegradedMode()
        assertTrue(resourceManager.isDegradedMode())
        
        resourceManager.exitDegradedMode()
        assertFalse(resourceManager.isDegradedMode())
    }
    
    @Test
    fun testResourceStatisticsEnhancement() {
        val resource1 = ManagedResourceFactory.createManagedListener({ }, "Stats Test 1")
        val resource2 = ManagedResourceFactory.createManagedGlassEffect({ }, "blur")
        
        resourceManager.registerResource(resource1)
        resourceManager.registerResource(resource2)
        
        val stats = (resourceManager as DefaultResourceManager).getResourceStatistics()
        
        assertTrue(stats.totalResources >= 2)
        assertTrue(stats.resourcesByType.containsKey(ResourceType.LISTENER))
        assertTrue(stats.resourcesByType.containsKey(ResourceType.GLASS_EFFECT))
        assertTrue(stats.resourcesByState.isNotEmpty())
        assertTrue(stats.estimatedMemoryUsageMB >= 0)
        assertNotNull(stats.memoryPressureLevel)
        assertEquals(resourceManager.isDegradedMode(), stats.isDegradedMode)
        assertTrue(stats.averageResourceAge >= 0)
        assertTrue(stats.idleResources >= 0)
        
        resourceManager.unregisterResource(resource1)
        resourceManager.unregisterResource(resource2)
    }
    
    @Test
    fun testMemoryPressureHandling() {
        // Create some resources that can be cleaned under pressure
        val glassEffect = ManagedResourceFactory.createManagedGlassEffect({ }, "test_effect")
        val listener = ManagedResourceFactory.createManagedListener({ }, "test_listener")
        
        resourceManager.registerResource(glassEffect)
        resourceManager.registerResource(listener)
        
        val initialCount = resourceManager.getResourceCount()
        
        // Test handling moderate pressure
        val result = resourceManager.handleMemoryPressure(MemoryPressureLevel.MODERATE)
        assertTrue(result)
        
        // Some resources might have been cleaned up depending on their state
        assertTrue(resourceManager.getResourceCount() <= initialCount)
        
        resourceManager.cleanupAll()
    }
    
    @Test
    fun testDegradationStrategy() {
        val strategy = DefaultDegradationStrategy()
        
        val glassEffect = ManagedResourceFactory.createManagedGlassEffect({ }, "test_effect")
        val adapter = ManagedResourceFactory.createManagedAdapter(
            object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                    TODO("Not implemented for test")
                }
                override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                    TODO("Not implemented for test")
                }
                override fun getItemCount(): Int = 0
            },
            null
        )
        
        // Glass effects should be degraded under moderate pressure
        assertTrue(strategy.shouldDegradeResource(glassEffect, MemoryPressureLevel.MODERATE))
        
        // Adapters should not be degraded under moderate pressure
        assertFalse(strategy.shouldDegradeResource(adapter, MemoryPressureLevel.MODERATE))
        
        // Both should be degraded under critical pressure if they can be cleaned
        if (glassEffect.canBeCleanedUnderPressure()) {
            assertTrue(strategy.shouldDegradeResource(glassEffect, MemoryPressureLevel.CRITICAL))
        }
        
        val priorityOrder = strategy.getPriorityOrder()
        assertTrue(priorityOrder.isNotEmpty())
        assertTrue(priorityOrder.contains(ResourceType.GLASS_EFFECT))
        assertTrue(priorityOrder.contains(ResourceType.ADAPTER))
        
        // Glass effects should have higher priority (earlier in list) than adapters
        assertTrue(priorityOrder.indexOf(ResourceType.GLASS_EFFECT) < priorityOrder.indexOf(ResourceType.ADAPTER))
    }
}