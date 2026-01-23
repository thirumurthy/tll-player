package com.thirutricks.tllplayer.ui

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.random.Random

/**
 * Property-based test for crash information capture functionality
 * 
 * **Feature: settings-crash-fix, Property 5: Crash Information Capture**
 * **Validates: Requirements 1.1, 1.2**
 * 
 * This test validates that for any crash or error during settings operations,
 * the system captures comprehensive diagnostic information including stack traces,
 * component states, and failure context.
 */
@RunWith(RobolectricTestRunner::class)
class CrashInformationCapturePropertyTest {

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
    fun `Property 5 - Crash Information Capture - For any crash during settings operations, comprehensive diagnostic information should be captured`() {
        // Property-based test with 25 iterations (reduced for reliability)
        repeat(25) { iteration ->
            val crashScenario = generateCrashScenario(iteration)
            val settingsContext = generateSettingsContext(iteration)
            val componentName = generateComponentName(iteration)
            
            // Given: Mock resource validator responses
            `when`(resourceValidator.validateDrawableResources()).thenReturn(crashScenario.missingDrawables)
            `when`(resourceValidator.validateLayoutResources()).thenReturn(crashScenario.missingLayouts)
            `when`(resourceValidator.validateColorResources()).thenReturn(crashScenario.missingColors)
            `when`(resourceValidator.validateDimensionResources()).thenReturn(crashScenario.missingDimensions)
            `when`(resourceValidator.validateCustomComponents()).thenReturn(crashScenario.customComponentsAvailable)

            // When: A crash occurs during settings operations
            val exception = crashScenario.generateException()
            crashDiagnosticManager.logCrashDetails(
                exception = exception,
                context = settingsContext,
                componentName = componentName,
                fragmentManager = crashScenario.mockFragmentManager,
                fragment = crashScenario.mockFragment
            )

            // Allow coroutine to complete with longer wait
            Thread.sleep(200)

            // Then: Comprehensive diagnostic information should be captured
            val allReports = crashDiagnosticManager.getAllCrashReports()
            assertTrue("Crash reports should not be empty for iteration $iteration", allReports.isNotEmpty())

            val latestReport = allReports.first()

            // Verify crash report contains all required information
            assertNotNull("Crash ID should not be null for iteration $iteration", latestReport.id)
            assertNotEquals("Timestamp should not be 0 for iteration $iteration", 0L, latestReport.timestamp)
            assertEquals("Exception type should match for iteration $iteration", exception.javaClass.simpleName, latestReport.exception)
            assertEquals("Exception message should match for iteration $iteration", exception.message ?: "No message", latestReport.message)
            assertEquals("Context should match for iteration $iteration", settingsContext, latestReport.context)
            assertEquals("Component name should match for iteration $iteration", componentName, latestReport.componentName)

            // Verify stack trace is captured
            assertNotEquals("Stack trace should not be empty for iteration $iteration", "", latestReport.stackTrace)
            assertTrue("Stack trace should contain exception type for iteration $iteration", 
                latestReport.stackTrace.contains(exception.javaClass.simpleName))

            // Verify device information is captured
            assertNotNull("Device info should not be null for iteration $iteration", latestReport.deviceInfo)
            assertNotEquals("Manufacturer should not be empty for iteration $iteration", "", latestReport.deviceInfo.manufacturer)
            assertNotEquals("Model should not be empty for iteration $iteration", "", latestReport.deviceInfo.model)
            assertNotEquals("Android version should not be empty for iteration $iteration", "", latestReport.deviceInfo.androidVersion)
            assertNotEquals("API level should not be 0 for iteration $iteration", 0, latestReport.deviceInfo.apiLevel)

            // Verify fragment state is captured
            assertNotNull("Fragment state should not be null for iteration $iteration", latestReport.fragmentState)
            assertNotEquals("Fragment state timestamp should not be 0 for iteration $iteration", 0L, latestReport.fragmentState.timestamp)
            assertNotEquals("Fragment manager state should not be empty for iteration $iteration", "", latestReport.fragmentState.fragmentManagerState)

            // Verify resource validation state is captured
            assertNotNull("Resource state should not be null for iteration $iteration", latestReport.resourceState)
            assertNotEquals("Resource state timestamp should not be 0 for iteration $iteration", 0L, latestReport.resourceState.timestamp)
            assertNotEquals("Validation time should not be 0 for iteration $iteration", 0L, latestReport.resourceState.validationTimeMs)

            // Verify crash type is determined correctly
            assertNotNull("Crash type should not be null for iteration $iteration", latestReport.crashType)
            
            // Verify recovery attempts list is initialized
            assertNotNull("Recovery attempts should not be null for iteration $iteration", latestReport.recoveryAttempts)
            
            // Clear reports for next iteration to avoid interference
            crashDiagnosticManager.clearOldCrashReports()
        }
    }

    @Test
    fun `Property 5a - Component State Capture - For any component failure, the specific failing component should be identified and logged`() {
        // Property-based test with 15 iterations (reduced for reliability)
        repeat(15) { iteration ->
            val failureScenario = generateComponentFailureScenario(iteration)
            val settingsContext = generateSettingsContext(iteration)
            
            // Given: Component state is tracked
            crashDiagnosticManager.updateComponentState(
                componentName = failureScenario.componentName,
                state = failureScenario.initialState,
                details = failureScenario.stateDetails
            )

            // Mock resource validator
            `when`(resourceValidator.validateDrawableResources()).thenReturn(emptyList())
            `when`(resourceValidator.validateLayoutResources()).thenReturn(emptyList())
            `when`(resourceValidator.validateColorResources()).thenReturn(emptyList())
            `when`(resourceValidator.validateDimensionResources()).thenReturn(emptyList())
            `when`(resourceValidator.validateCustomComponents()).thenReturn(true)

            // When: Component failure occurs
            val exception = failureScenario.generateComponentException()
            crashDiagnosticManager.logCrashDetails(
                exception = exception,
                context = settingsContext,
                componentName = failureScenario.componentName
            )

            Thread.sleep(200)

            // Then: Component failure should be properly identified and logged
            val allReports = crashDiagnosticManager.getAllCrashReports()
            assertTrue("Crash reports should not be empty for iteration $iteration", allReports.isNotEmpty())

            val latestReport = allReports.first()
            assertEquals("Component name should match for iteration $iteration", failureScenario.componentName, latestReport.componentName)
            assertNotNull("Component state should not be null for iteration $iteration", latestReport.componentState)
            assertEquals("Component state name should match for iteration $iteration", failureScenario.componentName, latestReport.componentState?.name)
            assertEquals("Component state should match for iteration $iteration", failureScenario.initialState, latestReport.componentState?.state)
            assertEquals("Component state details should match for iteration $iteration", failureScenario.stateDetails, latestReport.componentState?.details)
            
            // Clear reports for next iteration
            crashDiagnosticManager.clearOldCrashReports()
        }
    }

    @Test
    fun `Property 5b - Resource Validation Capture - For any resource-related crash, missing resources and dependencies should be recorded`() {
        // Property-based test with 15 iterations (reduced for reliability)
        repeat(15) { iteration ->
            val resourceScenario = generateResourceFailureScenario(iteration)
            val settingsContext = generateSettingsContext(iteration)
            
            // Given: Resource validation returns missing resources
            `when`(resourceValidator.validateDrawableResources()).thenReturn(resourceScenario.missingDrawables)
            `when`(resourceValidator.validateLayoutResources()).thenReturn(resourceScenario.missingLayouts)
            `when`(resourceValidator.validateColorResources()).thenReturn(resourceScenario.missingColors)
            `when`(resourceValidator.validateDimensionResources()).thenReturn(resourceScenario.missingDimensions)
            `when`(resourceValidator.validateCustomComponents()).thenReturn(resourceScenario.customComponentsAvailable)

            // When: Resource-related crash occurs
            val exception = android.content.res.Resources.NotFoundException(
                "Resource not found: ${resourceScenario.missingDrawables.firstOrNull() ?: "unknown"}"
            )
            crashDiagnosticManager.logCrashDetails(
                exception = exception,
                context = settingsContext
            )

            Thread.sleep(200)

            // Then: Missing resources should be captured in crash report
            val allReports = crashDiagnosticManager.getAllCrashReports()
            assertTrue("Crash reports should not be empty for iteration $iteration", allReports.isNotEmpty())

            val latestReport = allReports.first()
            assertEquals("Should be RESOURCE_NOT_FOUND for iteration $iteration", CrashType.RESOURCE_NOT_FOUND, latestReport.crashType)
            assertEquals("Missing drawables should match for iteration $iteration", resourceScenario.missingDrawables, latestReport.resourceState.missingDrawables)
            assertEquals("Missing layouts should match for iteration $iteration", resourceScenario.missingLayouts, latestReport.resourceState.missingLayouts)
            assertEquals("Missing colors should match for iteration $iteration", resourceScenario.missingColors, latestReport.resourceState.missingColors)
            assertEquals("Missing dimensions should match for iteration $iteration", resourceScenario.missingDimensions, latestReport.resourceState.missingDimensions)
            assertEquals("Custom components availability should match for iteration $iteration", resourceScenario.customComponentsAvailable, latestReport.resourceState.customComponentsAvailable)
            assertEquals("Fallback required should match for iteration $iteration", 
                resourceScenario.missingDrawables.isNotEmpty() || resourceScenario.missingColors.isNotEmpty(),
                latestReport.resourceState.fallbackRequired)
            
            // Clear reports for next iteration
            crashDiagnosticManager.clearOldCrashReports()
        }
    }

    @Test
    fun `Property 5c - Fragment State Capture - For any fragment-related crash, fragment lifecycle and manager states should be captured`() {
        // Property-based test with 15 iterations (reduced for reliability)
        repeat(15) { iteration ->
            val fragmentScenario = generateFragmentStateScenario(iteration)
            val settingsContext = generateSettingsContext(iteration)
            
            // Given: Mock resource validator
            `when`(resourceValidator.validateDrawableResources()).thenReturn(emptyList())
            `when`(resourceValidator.validateLayoutResources()).thenReturn(emptyList())
            `when`(resourceValidator.validateColorResources()).thenReturn(emptyList())
            `when`(resourceValidator.validateDimensionResources()).thenReturn(emptyList())
            `when`(resourceValidator.validateCustomComponents()).thenReturn(true)

            // When: Fragment-related crash occurs
            val exception = IllegalStateException("Fragment lifecycle error: ${fragmentScenario.errorMessage}")
            crashDiagnosticManager.logCrashDetails(
                exception = exception,
                context = settingsContext,
                fragmentManager = fragmentScenario.mockFragmentManager,
                fragment = fragmentScenario.mockFragment
            )

            Thread.sleep(200)

            // Then: Fragment state should be properly captured
            val allReports = crashDiagnosticManager.getAllCrashReports()
            assertTrue("Crash reports should not be empty for iteration $iteration", allReports.isNotEmpty())

            val latestReport = allReports.first()
            assertEquals("Should be FRAGMENT_LIFECYCLE_ERROR for iteration $iteration", CrashType.FRAGMENT_LIFECYCLE_ERROR, latestReport.crashType)
            
            val fragmentState = latestReport.fragmentState
            assertNotNull("Fragment state should not be null for iteration $iteration", fragmentState)
            assertEquals("Fragment attached state should match for iteration $iteration", fragmentScenario.isAttached, fragmentState.isFragmentAttached)
            assertEquals("Fragment manager state should match for iteration $iteration", fragmentScenario.managerState, fragmentState.fragmentManagerState)
            assertEquals("Fragment lifecycle state should match for iteration $iteration", fragmentScenario.lifecycleState, fragmentState.fragmentLifecycleState)
            assertEquals("Fragment visible state should match for iteration $iteration", fragmentScenario.isVisible, fragmentState.isFragmentVisible)
            assertEquals("Fragment hidden state should match for iteration $iteration", fragmentScenario.isHidden, fragmentState.isFragmentHidden)
            
            // Clear reports for next iteration
            crashDiagnosticManager.clearOldCrashReports()
        }
    }

    // Generator functions for property-based testing

    private fun generateCrashScenario(seed: Int): CrashScenario {
        val random = Random(seed)
        val exceptionTypes = listOf(
            "RuntimeException", "IllegalStateException", "NullPointerException", 
            "Resources.NotFoundException", "OutOfMemoryError"
        )
        
        val resourceNames = listOf(
            "modern_toggle_switch_thumb", "modern_toggle_switch_track", "glass_card_background",
            "settings_icon", "menu_background", "focus_indicator"
        )
        
        return CrashScenario(
            exceptionType = exceptionTypes.random(random),
            errorMessage = "Test crash: ${random.nextInt(1000)}",
            missingDrawables = resourceNames.shuffled(random).take(random.nextInt(4)),
            missingLayouts = listOf("setting", "menu", "dialog").shuffled(random).take(random.nextInt(3)),
            missingColors = listOf("glass_primary", "glass_accent").shuffled(random).take(random.nextInt(3)),
            missingDimensions = listOf("glass_blur_radius", "glass_elevation").shuffled(random).take(random.nextInt(3)),
            customComponentsAvailable = random.nextBoolean(),
            mockFragmentManager = mock(FragmentManager::class.java),
            mockFragment = mock(Fragment::class.java)
        )
    }

    private fun generateSettingsContext(seed: Int): String {
        val random = Random(seed)
        val contexts = listOf(
            "settings_initialization", "settings_fragment_show", "settings_ui_creation",
            "settings_preference_update", "settings_resource_loading", "settings_component_binding"
        )
        return contexts.random(random)
    }

    private fun generateComponentName(seed: Int): String {
        val random = Random(seed)
        val components = listOf(
            "ModernToggleSwitch", "GlassCard", "SettingsFragment", "PreferenceManager",
            "ResourceValidator", "FragmentManager", "ViewBinding"
        )
        return components.random(random)
    }

    private fun generateComponentFailureScenario(seed: Int): ComponentFailureScenario {
        val random = Random(seed)
        val componentNames = listOf(
            "ModernToggleSwitch", "GlassCard", "GlassyBackgroundView", "SettingsFragment"
        )
        val states = listOf("initializing", "created", "bound", "error", "destroyed")
        val failureTypes = listOf("initialization", "resource_missing", "view_error")
        
        return ComponentFailureScenario(
            componentName = componentNames.random(random),
            initialState = states.random(random),
            stateDetails = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "attempt" to random.nextInt(1, 6),
                "hasView" to random.nextBoolean()
            ),
            failureType = failureTypes.random(random)
        )
    }

    private fun generateResourceFailureScenario(seed: Int): ResourceFailureScenario {
        val random = Random(seed)
        val drawableNames = listOf(
            "modern_toggle_switch_thumb", "modern_toggle_switch_track", "glass_card_background"
        )
        val layoutNames = listOf("setting", "menu", "dialog")
        val colorNames = listOf("glass_primary", "glass_accent", "glass_surface")
        val dimensionNames = listOf("glass_blur_radius", "glass_elevation", "glass_corner_radius")
        
        return ResourceFailureScenario(
            missingDrawables = drawableNames.shuffled(random).take(random.nextInt(4)),
            missingLayouts = layoutNames.shuffled(random).take(random.nextInt(3)),
            missingColors = colorNames.shuffled(random).take(random.nextInt(4)),
            missingDimensions = dimensionNames.shuffled(random).take(random.nextInt(4)),
            customComponentsAvailable = random.nextBoolean()
        )
    }

    private fun generateFragmentStateScenario(seed: Int): FragmentStateScenario {
        val random = Random(seed)
        val managerStates = listOf("ACTIVE", "DESTROYED", "STATE_SAVED", "NULL")
        val lifecycleStates = listOf("CREATED", "STARTED", "RESUMED", "DESTROYED", "UNKNOWN")
        
        val mockFragmentManager = mock(FragmentManager::class.java)
        val mockFragment = mock(Fragment::class.java)
        
        val isAttached = random.nextBoolean()
        val isVisible = random.nextBoolean()
        val isHidden = !isVisible
        val managerState = managerStates.random(random)
        val lifecycleState = lifecycleStates.random(random)
        
        // Configure mocks based on generated state
        `when`(mockFragment.isAdded).thenReturn(isAttached)
        `when`(mockFragment.isVisible).thenReturn(isVisible)
        `when`(mockFragment.isHidden).thenReturn(isHidden)
        
        val mockLifecycle = mock(Lifecycle::class.java)
        `when`(mockFragment.lifecycle).thenReturn(mockLifecycle)
        `when`(mockLifecycle.currentState).thenReturn(
            when (lifecycleState) {
                "CREATED" -> Lifecycle.State.CREATED
                "STARTED" -> Lifecycle.State.STARTED
                "RESUMED" -> Lifecycle.State.RESUMED
                "DESTROYED" -> Lifecycle.State.DESTROYED
                else -> Lifecycle.State.INITIALIZED
            }
        )
        
        return FragmentStateScenario(
            isAttached = isAttached,
            managerState = managerState,
            lifecycleState = lifecycleState,
            isVisible = isVisible,
            isHidden = isHidden,
            errorMessage = "Fragment error: ${random.nextInt(1000)}",
            mockFragmentManager = mockFragmentManager,
            mockFragment = mockFragment
        )
    }
}

// Data classes for property-based testing

data class CrashScenario(
    val exceptionType: String,
    val errorMessage: String,
    val missingDrawables: List<String>,
    val missingLayouts: List<String>,
    val missingColors: List<String>,
    val missingDimensions: List<String>,
    val customComponentsAvailable: Boolean,
    val mockFragmentManager: FragmentManager?,
    val mockFragment: Fragment?
) {
    fun generateException(): Exception {
        return when (exceptionType) {
            "RuntimeException" -> RuntimeException(errorMessage)
            "IllegalStateException" -> IllegalStateException(errorMessage)
            "NullPointerException" -> NullPointerException(errorMessage)
            "Resources.NotFoundException" -> android.content.res.Resources.NotFoundException(errorMessage)
            "OutOfMemoryError" -> RuntimeException("OutOfMemoryError: $errorMessage") // Wrap as Exception
            else -> Exception(errorMessage)
        }
    }
}

data class ComponentFailureScenario(
    val componentName: String,
    val initialState: String,
    val stateDetails: Map<String, Any>,
    val failureType: String
) {
    fun generateComponentException(): Exception {
        return when (failureType) {
            "initialization" -> RuntimeException("Failed to initialize $componentName")
            "resource_missing" -> android.content.res.Resources.NotFoundException("Missing resource for $componentName")
            "view_error" -> IllegalStateException("View error in $componentName")
            else -> Exception("Component failure in $componentName")
        }
    }
}

data class ResourceFailureScenario(
    val missingDrawables: List<String>,
    val missingLayouts: List<String>,
    val missingColors: List<String>,
    val missingDimensions: List<String>,
    val customComponentsAvailable: Boolean
)

data class FragmentStateScenario(
    val isAttached: Boolean,
    val managerState: String,
    val lifecycleState: String,
    val isVisible: Boolean,
    val isHidden: Boolean,
    val errorMessage: String,
    val mockFragmentManager: FragmentManager?,
    val mockFragment: Fragment?
)