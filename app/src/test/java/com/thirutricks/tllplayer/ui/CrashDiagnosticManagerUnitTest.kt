package com.thirutricks.tllplayer.ui

import android.content.Context
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Unit test for CrashDiagnosticManager using Robolectric to handle Android dependencies
 */
@RunWith(RobolectricTestRunner::class)
class CrashDiagnosticManagerUnitTest {

    @Mock
    private lateinit var resourceValidator: ResourceValidator

    private lateinit var context: Context
    private lateinit var crashDiagnosticManager: CrashDiagnosticManager

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        context = RuntimeEnvironment.getApplication()
        crashDiagnosticManager = CrashDiagnosticManager(context, resourceValidator)
    }

    @Test
    fun `CrashDiagnosticManager should initialize successfully`() {
        // Given - setup is done in @Before
        
        // When - manager is created
        
        // Then - it should not be null
        assertNotNull(crashDiagnosticManager)
    }

    @Test
    fun `validateSettingsResources should call ResourceValidator methods`() {
        // Given
        `when`(resourceValidator.validateDrawableResources()).thenReturn(emptyList())
        `when`(resourceValidator.validateLayoutResources()).thenReturn(emptyList())
        `when`(resourceValidator.validateColorResources()).thenReturn(emptyList())
        `when`(resourceValidator.validateDimensionResources()).thenReturn(emptyList())
        `when`(resourceValidator.validateCustomComponents()).thenReturn(true)

        // When
        val result = crashDiagnosticManager.validateSettingsResources()

        // Then
        assertNotNull(result)
        assertFalse(result.fallbackRequired)
        assertTrue(result.customComponentsAvailable)
        assertEquals(0, result.totalMissingResources)
        
        // Verify all validation methods were called
        verify(resourceValidator).validateDrawableResources()
        verify(resourceValidator).validateLayoutResources()
        verify(resourceValidator).validateColorResources()
        verify(resourceValidator).validateDimensionResources()
        verify(resourceValidator).validateCustomComponents()
    }

    @Test
    fun `validateSettingsResources should handle missing resources`() {
        // Given
        `when`(resourceValidator.validateDrawableResources()).thenReturn(listOf("missing_drawable"))
        `when`(resourceValidator.validateLayoutResources()).thenReturn(listOf("missing_layout"))
        `when`(resourceValidator.validateColorResources()).thenReturn(emptyList())
        `when`(resourceValidator.validateDimensionResources()).thenReturn(emptyList())
        `when`(resourceValidator.validateCustomComponents()).thenReturn(false)

        // When
        val result = crashDiagnosticManager.validateSettingsResources()

        // Then
        assertNotNull(result)
        assertTrue(result.fallbackRequired)
        assertFalse(result.customComponentsAvailable)
        assertEquals(2, result.totalMissingResources)
        assertEquals(listOf("missing_drawable"), result.missingDrawables)
        assertEquals(listOf("missing_layout"), result.missingLayouts)
    }

    @Test
    fun `captureFragmentState should handle null inputs gracefully`() {
        // When
        val snapshot = crashDiagnosticManager.captureFragmentState(null, null)

        // Then
        assertNotNull(snapshot)
        assertFalse(snapshot.isFragmentAttached)
        assertEquals("NULL", snapshot.fragmentManagerState)
        assertEquals("NO_FRAGMENT", snapshot.viewBindingState)
        assertEquals("UNKNOWN", snapshot.fragmentLifecycleState)
        assertTrue(snapshot.timestamp > 0)
    }

    @Test
    fun `updateComponentState should track component information`() {
        // Given
        val componentName = "TestComponent"
        val state = "initialized"
        val details = mapOf("test" to "value")

        // When
        crashDiagnosticManager.updateComponentState(componentName, state, details)

        // Then - should not throw exception
        // We can verify this worked by generating a diagnostic report
        val report = crashDiagnosticManager.generateDiagnosticReport()
        assertNotNull(report)
        assertTrue(report.componentStates.any { it.name == componentName })
        
        val componentState = report.componentStates.find { it.name == componentName }
        assertNotNull(componentState)
        assertEquals(state, componentState!!.state)
        assertEquals(details, componentState.details)
    }

    @Test
    fun `generateDiagnosticReport should create valid report`() {
        // Given
        `when`(resourceValidator.validateDrawableResources()).thenReturn(listOf("missing_drawable"))
        `when`(resourceValidator.validateLayoutResources()).thenReturn(emptyList())
        `when`(resourceValidator.validateColorResources()).thenReturn(emptyList())
        `when`(resourceValidator.validateDimensionResources()).thenReturn(emptyList())
        `when`(resourceValidator.validateCustomComponents()).thenReturn(false)

        // When
        val report = crashDiagnosticManager.generateDiagnosticReport()

        // Then
        assertNotNull(report)
        assertNotNull(report.deviceInfo)
        assertNotNull(report.resourceState)
        assertNotNull(report.crashSummary)
        assertNotNull(report.recommendations)
        assertEquals(1, report.resourceState.totalMissingResources)
        assertFalse(report.resourceState.customComponentsAvailable)
        assertTrue(report.recommendations.isNotEmpty())
    }

    @Test
    fun `logCrashDetails should handle exceptions gracefully`() {
        // Given
        val exception = RuntimeException("Test crash")
        val context = "settings_initialization"
        val componentName = "TestComponent"

        // When - should not throw exception
        crashDiagnosticManager.logCrashDetails(exception, context, componentName)

        // Then - verify crash was logged (after allowing coroutine to complete)
        Thread.sleep(100)
        val reports = crashDiagnosticManager.getAllCrashReports()
        assertTrue(reports.isNotEmpty())
        
        val report = reports.first()
        assertEquals("RuntimeException", report.exception)
        assertEquals("Test crash", report.message)
        assertEquals(context, report.context)
        assertEquals(componentName, report.componentName)
    }

    @Test
    fun `recordRecoveryAttempt should track recovery attempts`() {
        // Given - create a crash first
        val exception = RuntimeException("Test crash")
        crashDiagnosticManager.logCrashDetails(exception, "test_context")
        Thread.sleep(100)

        val crashId = crashDiagnosticManager.getAllCrashReports().first().id

        // When
        crashDiagnosticManager.recordRecoveryAttempt(crashId, "fallback_ui", true)

        // Then
        val report = crashDiagnosticManager.getCrashReport(crashId)
        assertNotNull(report)
        assertEquals(1, report!!.recoveryAttempts.size)

        val attempt = report.recoveryAttempts.first()
        assertEquals("fallback_ui", attempt.strategy)
        assertTrue(attempt.success)
        assertNull(attempt.errorMessage)
    }

    @Test
    fun `getCrashReport should return correct report by ID`() {
        // Given
        val exception = RuntimeException("Specific crash")
        crashDiagnosticManager.logCrashDetails(exception, "specific_context")
        Thread.sleep(100)

        val allReports = crashDiagnosticManager.getAllCrashReports()
        val crashId = allReports.first().id

        // When
        val report = crashDiagnosticManager.getCrashReport(crashId)

        // Then
        assertNotNull(report)
        assertEquals(crashId, report!!.id)
        assertEquals("Specific crash", report.message)
        assertEquals("specific_context", report.context)
    }

    @Test
    fun `getCrashReport should return null for non-existent ID`() {
        // When
        val report = crashDiagnosticManager.getCrashReport("non_existent_id")

        // Then
        assertNull(report)
    }
}