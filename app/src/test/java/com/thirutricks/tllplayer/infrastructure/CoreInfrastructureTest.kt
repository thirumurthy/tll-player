package com.thirutricks.tllplayer.infrastructure

import org.junit.Test
import org.junit.Assert.*

/**
 * Core infrastructure tests that don't depend on Android context or TVList initialization.
 * These tests focus on the basic functionality of infrastructure components.
 */
class CoreInfrastructureTest {
    
    @Test
    fun testMenuErrorCreation() {
        val error = MenuError.DataNotInitialized
        assertEquals(ErrorSeverity.HIGH, error.severity)
        assertTrue(error.recoverable)
    }
    
    @Test
    fun testMenuErrorHierarchy() {
        val criticalError = MenuError.DataCorruption
        val highError = MenuError.AdapterSetupFailed
        val mediumError = MenuError.GlassEffectFailure("blur")
        val lowError = MenuError.PerformanceWarning("memory", 100.0, 150.0)
        
        assertEquals(ErrorSeverity.CRITICAL, criticalError.severity)
        assertEquals(ErrorSeverity.HIGH, highError.severity)
        assertEquals(ErrorSeverity.MEDIUM, mediumError.severity)
        assertEquals(ErrorSeverity.LOW, lowError.severity)
        
        assertFalse(criticalError.recoverable)
        assertTrue(highError.recoverable)
        assertTrue(mediumError.recoverable)
        assertTrue(lowError.recoverable)
    }
    
    @Test
    fun testErrorResultTypes() {
        val success = ErrorResult.Success
        val recovered = ErrorResult.Recovered("Fixed the issue")
        val partial = ErrorResult.PartialRecovery("Partially fixed", listOf("remaining issue"))
        val failed = ErrorResult.Failed("Could not fix")
        
        assertTrue(success is ErrorResult.Success)
        assertTrue(recovered is ErrorResult.Recovered)
        assertTrue(partial is ErrorResult.PartialRecovery)
        assertTrue(failed is ErrorResult.Failed)
    }
    
    @Test
    fun testRecoveryResultTypes() {
        val success = RecoveryResult.Success
        val partialSuccess = RecoveryResult.PartialSuccess("Partially recovered")
        val retry = RecoveryResult.Retry(simplifiedConfig = true)
        val cannotRecover = RecoveryResult.CannotRecover
        
        assertTrue(success is RecoveryResult.Success)
        assertTrue(partialSuccess is RecoveryResult.PartialSuccess)
        assertTrue(retry is RecoveryResult.Retry)
        assertTrue(cannotRecover is RecoveryResult.CannotRecover)
        
        assertTrue(retry.simplifiedConfig)
    }
    
    @Test
    fun testFocusSourceEnum() {
        val sources = FocusSource.values()
        assertTrue(sources.contains(FocusSource.GROUP_ADAPTER))
        assertTrue(sources.contains(FocusSource.LIST_ADAPTER))
        assertTrue(sources.contains(FocusSource.MENU_FRAGMENT))
        assertTrue(sources.contains(FocusSource.EXTERNAL))
    }
    
    @Test
    fun testResourceTypeEnum() {
        val types = ResourceType.values()
        assertTrue(types.contains(ResourceType.ANIMATION))
        assertTrue(types.contains(ResourceType.LISTENER))
        assertTrue(types.contains(ResourceType.GLASS_EFFECT))
        assertTrue(types.contains(ResourceType.ADAPTER))
        assertTrue(types.contains(ResourceType.HANDLER))
        assertTrue(types.contains(ResourceType.VIEW))
        assertTrue(types.contains(ResourceType.THREAD))
        assertTrue(types.contains(ResourceType.MEMORY))
    }
    
    @Test
    fun testResourceLifecycleStateEnum() {
        val states = ResourceLifecycleState.values()
        assertTrue(states.contains(ResourceLifecycleState.CREATED))
        assertTrue(states.contains(ResourceLifecycleState.ACTIVE))
        assertTrue(states.contains(ResourceLifecycleState.IDLE))
        assertTrue(states.contains(ResourceLifecycleState.CLEANUP_PENDING))
        assertTrue(states.contains(ResourceLifecycleState.CLEANED_UP))
    }
    
    @Test
    fun testMemoryPressureLevelEnum() {
        val levels = MemoryPressureLevel.values()
        assertTrue(levels.contains(MemoryPressureLevel.NORMAL))
        assertTrue(levels.contains(MemoryPressureLevel.MODERATE))
        assertTrue(levels.contains(MemoryPressureLevel.HIGH))
        assertTrue(levels.contains(MemoryPressureLevel.CRITICAL))
    }
    
    @Test
    fun testFocusModeEnum() {
        val modes = FocusMode.values()
        assertTrue(modes.contains(FocusMode.CATEGORY_PANEL))
        assertTrue(modes.contains(FocusMode.CHANNEL_PANEL))
        assertTrue(modes.contains(FocusMode.TRANSITIONING))
        assertTrue(modes.contains(FocusMode.DISABLED))
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
        assertNotNull(resource.getResourceId())
        assertTrue(resource.getCreatedTime() > 0)
        assertTrue(resource.getLastAccessTime() > 0)
        assertEquals(ResourceLifecycleState.CREATED, resource.getLifecycleState())
        assertTrue(resource.getMemoryFootprint() > 0)
        
        resource.cleanup()
        assertFalse(resource.isActive())
        assertEquals(ResourceLifecycleState.CLEANED_UP, resource.getLifecycleState())
        assertEquals(1, cleanupCalled.size)
    }
    
    @Test
    fun testManagedGlassEffect() {
        val cleanupCalled = mutableListOf<Boolean>()
        
        val glassEffect = ManagedResourceFactory.createManagedGlassEffect(
            cleanupAction = { cleanupCalled.add(true) },
            effectType = "blur"
        )
        
        assertEquals(ResourceType.GLASS_EFFECT, glassEffect.getResourceType())
        assertTrue(glassEffect.isActive())
        assertTrue(glassEffect.canBeCleanedUnderPressure())
        
        glassEffect.cleanup()
        assertFalse(glassEffect.isActive())
        assertEquals(1, cleanupCalled.size)
    }
    
    @Test
    fun testDefaultDegradationStrategy() {
        val strategy = DefaultDegradationStrategy()
        
        val glassEffect = ManagedResourceFactory.createManagedGlassEffect({ }, "test_effect")
        val listener = ManagedResourceFactory.createManagedListener({ }, "test_listener")
        
        // Fresh glass effects are ACTIVE, so they should NOT be degraded under moderate pressure
        // (only IDLE glass effects are degraded under moderate pressure)
        assertEquals(ResourceLifecycleState.ACTIVE, glassEffect.getLifecycleState())
        assertFalse(strategy.shouldDegradeResource(glassEffect, MemoryPressureLevel.MODERATE))
        
        // But glass effects should be degraded under HIGH pressure regardless of state
        assertTrue(strategy.shouldDegradeResource(glassEffect, MemoryPressureLevel.HIGH))
        
        // Listeners should not be degraded under moderate pressure unless idle
        // Fresh listener should not be degraded
        assertFalse(strategy.shouldDegradeResource(listener, MemoryPressureLevel.MODERATE))
        
        // Both should be degraded under critical pressure if they can be cleaned
        if (glassEffect.canBeCleanedUnderPressure()) {
            assertTrue(strategy.shouldDegradeResource(glassEffect, MemoryPressureLevel.CRITICAL))
        }
        
        // Test with listener that can be cleaned under pressure
        if (listener.canBeCleanedUnderPressure()) {
            assertTrue(strategy.shouldDegradeResource(listener, MemoryPressureLevel.CRITICAL))
        }
        
        val priorityOrder = strategy.getPriorityOrder()
        assertTrue(priorityOrder.isNotEmpty())
        assertTrue(priorityOrder.contains(ResourceType.GLASS_EFFECT))
        assertTrue(priorityOrder.contains(ResourceType.ADAPTER))
        
        // Glass effects should have higher priority (earlier in list) than adapters
        assertTrue(priorityOrder.indexOf(ResourceType.GLASS_EFFECT) < priorityOrder.indexOf(ResourceType.ADAPTER))
    }
    
    @Test
    fun testRecoveryStrategies() {
        val dataRecovery = DataInitializationRecovery()
        val focusRecovery = FocusRecovery()
        val glassRecovery = GlassEffectRecovery()
        
        // Test data recovery
        assertTrue(dataRecovery.canHandle(MenuError.DataNotInitialized))
        assertFalse(dataRecovery.canHandle(MenuError.FocusDeadlock(emptyList())))
        
        // Test focus recovery
        assertTrue(focusRecovery.canHandle(MenuError.FocusDeadlock(emptyList())))
        assertFalse(focusRecovery.canHandle(MenuError.DataNotInitialized))
        
        // Test glass effect recovery
        assertTrue(glassRecovery.canHandle(MenuError.GlassEffectFailure("blur")))
        assertFalse(glassRecovery.canHandle(MenuError.DataNotInitialized))
        
        // Test priority ordering
        assertTrue(dataRecovery.getPriority() > glassRecovery.getPriority())
        assertTrue(focusRecovery.getPriority() > glassRecovery.getPriority())
    }
    
    @Test
    fun testRecoveryStrategyFactory() {
        val defaultStrategies = RecoveryStrategyFactory.createDefaultStrategies()
        val minimalStrategies = RecoveryStrategyFactory.createMinimalStrategies()
        
        assertTrue(defaultStrategies.size > minimalStrategies.size)
        assertTrue(defaultStrategies.isNotEmpty())
        assertTrue(minimalStrategies.isNotEmpty())
        
        // Strategies should be sorted by priority (highest first)
        for (i in 0 until defaultStrategies.size - 1) {
            assertTrue(defaultStrategies[i].getPriority() >= defaultStrategies[i + 1].getPriority())
        }
    }
    
    @Test
    fun testMenuContext() {
        val context = MenuContext(
            fragmentState = "active",
            adapterStates = mapOf("group" to "ready", "list" to "updating"),
            focusState = "category_panel",
            resourceCount = 10,
            memoryUsage = 1024 * 1024 // 1MB
        )
        
        assertEquals("active", context.fragmentState)
        assertEquals(2, context.adapterStates.size)
        assertEquals("ready", context.adapterStates["group"])
        assertEquals("updating", context.adapterStates["list"])
        assertEquals("category_panel", context.focusState)
        assertEquals(10, context.resourceCount)
        assertEquals(1024 * 1024, context.memoryUsage)
        assertTrue(context.timestamp > 0)
    }
    
    @Test
    fun testLogEntry() {
        val context = mapOf("test" to "value", "number" to 42)
        val exception = RuntimeException("Test exception")
        
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = android.util.Log.ERROR,
            tag = "TestTag",
            message = "Test message",
            context = context,
            throwable = exception,
            threadName = Thread.currentThread().name
        )
        
        assertEquals("ERROR", entry.getLevelName())
        assertEquals("TestTag", entry.tag)
        assertEquals("Test message", entry.message)
        assertEquals(context, entry.context)
        assertEquals(exception, entry.throwable)
        assertNotNull(entry.getFormattedTimestamp())
        assertTrue(entry.getFormattedTimestamp().contains(":"))
    }
}