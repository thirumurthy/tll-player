package com.thirutricks.tllplayer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.util.TypedValue
import android.view.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.thirutricks.tllplayer.databinding.SettingBinding
import com.thirutricks.tllplayer.models.TVList
import com.thirutricks.tllplayer.ui.TvUiUtils
import com.thirutricks.tllplayer.ui.ModernToggleSwitch
import com.thirutricks.tllplayer.ui.GlassCard
import com.thirutricks.tllplayer.ui.GlassyBackgroundView
import com.thirutricks.tllplayer.ui.SettingsFocusManager
import com.thirutricks.tllplayer.ui.ResponsiveLayoutManager
import com.thirutricks.tllplayer.ui.SettingsAccessibilityManager
import com.thirutricks.tllplayer.ui.PerformanceOptimizationManager
import com.thirutricks.tllplayer.ui.SettingsResourceManager
import com.thirutricks.tllplayer.ui.ResourceValidator
import com.thirutricks.tllplayer.ui.RecoveryAction
import com.thirutricks.tllplayer.ui.CrashDiagnosticManager
import com.thirutricks.tllplayer.ui.SafeFragmentManager
import com.thirutricks.tllplayer.ui.SettingsErrorRecovery
import com.thirutricks.tllplayer.OrderPreferenceManager
import com.thirutricks.tllplayer.R
import com.thirutricks.tllplayer.ui.glass.GlassEffectUtils
import com.thirutricks.tllplayer.ui.glass.GlassType
import android.widget.Toast

class SettingFragment : Fragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var uri: Uri
    private lateinit var updateManager: UpdateManager
    private var tvUiUtils: TvUiUtils? = null
    private lateinit var settingsFocusManager: SettingsFocusManager
    private lateinit var responsiveLayoutManager: ResponsiveLayoutManager
    private lateinit var accessibilityManager: SettingsAccessibilityManager
    private lateinit var performanceManager: PerformanceOptimizationManager
    private lateinit var settingsResourceManager: SettingsResourceManager
    private lateinit var crashDiagnosticManager: CrashDiagnosticManager
    private lateinit var safeFragmentManager: SafeFragmentManager
    private lateinit var settingsErrorRecovery: SettingsErrorRecovery

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = SettingBinding.inflate(inflater, container, false)

        // Initialize crash prevention systems first
        initializeCrashPreventionSystems()

        // Initialize SettingsResourceManager first for pre-flight validation
        settingsResourceManager = SettingsResourceManager(requireContext())
        
        // Perform comprehensive resource validation before proceeding
        val validationPassed = performPreFlightValidation()
        if (!validationPassed) {
            Log.e(TAG, "Pre-flight validation failed, using emergency fallback UI")
            return createEmergencySettingsUI(container)
        }

        tvUiUtils = TvUiUtils(requireContext())
        tvUiUtils?.initSounds(R.raw.focus, R.raw.click)  // SOUND FEEDBACK

        // Initialize ResponsiveLayoutManager
        responsiveLayoutManager = ResponsiveLayoutManager(requireContext())

        // Initialize AccessibilityManager
        accessibilityManager = SettingsAccessibilityManager(requireContext())

        // Initialize PerformanceOptimizationManager
        performanceManager = PerformanceOptimizationManager(requireContext())

        // Initialize SettingsFocusManager
        settingsFocusManager = SettingsFocusManager(requireContext())
        settingsFocusManager.initialize(tvUiUtils!!)

        setupUI()
        setupListeners()
        setupGlassyBackground()
        setupFocusAnimations()  // ⭐ ADD TIVIMATE STYLE FOCUS EFFECTS
        setupResponsiveLayout() // ⭐ ADD RESPONSIVE LAYOUT OPTIMIZATION
        setupAccessibility()    // ⭐ ADD ACCESSIBILITY COMPLIANCE
        setupPerformanceOptimization() // ⭐ ADD PERFORMANCE OPTIMIZATION
        setupKeyNavigation()    // ⭐ ADD PROPER KEY NAVIGATION

        updateManager = UpdateManager(requireContext(), requireContext().appVersionCode)
        (activity as MainActivity).ready(TAG)
        // SP.config = "https://tllapp.dpdns.org/tvnexa/v1/admin/channel-pllayer"
        return binding.root
    }

    // ------------------------------------------------------------
    //  CRASH PREVENTION SYSTEMS INITIALIZATION
    // ------------------------------------------------------------
    
    /**
     * Initialize all crash prevention systems before any UI operations
     */
    private fun initializeCrashPreventionSystems() {
        try {
            Log.i(TAG, "Initializing comprehensive crash prevention systems")
            
            // Initialize ResourceValidator first
            val resourceValidator = ResourceValidator(requireContext())
            
            // Initialize CrashDiagnosticManager first
            crashDiagnosticManager = CrashDiagnosticManager(requireContext(), resourceValidator)
            
            // Initialize SafeFragmentManager with diagnostic support
            safeFragmentManager = SafeFragmentManager(requireActivity(), crashDiagnosticManager)
            
            // Initialize SettingsErrorRecovery
            settingsErrorRecovery = SettingsErrorRecovery(requireContext(), resourceValidator, crashDiagnosticManager)
            
            // Capture initial fragment state for diagnostics
            val fragmentState = crashDiagnosticManager.captureFragmentState(fragment = this)
            Log.d(TAG, "Initial fragment state captured: isAttached=${fragmentState.isFragmentAttached}")
            
            // Validate settings resources comprehensively
            val resourceValidation = crashDiagnosticManager.validateSettingsResources()
            Log.d(TAG, "Resource validation completed: ${resourceValidation.customComponentsAvailable}")
            
            if (!resourceValidation.customComponentsAvailable) {
                Log.w(TAG, "Custom components not available, fallback mode will be used")
                Log.d(TAG, "Missing drawables: ${resourceValidation.missingDrawables}")
            }
            
            Log.i(TAG, "Crash prevention systems initialized successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error initializing crash prevention systems", e)
            
            // Try to initialize minimal systems
            try {
                val resourceValidator = ResourceValidator(requireContext())
                crashDiagnosticManager = CrashDiagnosticManager(requireContext(), resourceValidator)
                crashDiagnosticManager.logCrashDetails(e, "initializeCrashPreventionSystems")
            } catch (e2: Exception) {
                Log.e(TAG, "Cannot initialize even basic crash prevention", e2)
            }
        }
    }

    // ------------------------------------------------------------
    //  PRE-FLIGHT VALIDATION AND CRASH PREVENTION
    // ------------------------------------------------------------
    
    /**
     * Perform comprehensive pre-flight validation of all settings resources
     */
    private fun performPreFlightValidation(): Boolean {
        return try {
            Log.i(TAG, "Starting settings pre-flight validation")
            
            // Use crash diagnostic manager for comprehensive validation
            val resourceValidation = if (::crashDiagnosticManager.isInitialized) {
                crashDiagnosticManager.validateSettingsResources()
            } else {
                Log.w(TAG, "CrashDiagnosticManager not initialized, using basic validation")
                null
            }
            
            val validationPassed = settingsResourceManager.performPreFlightValidation()
            val report = settingsResourceManager.getValidationReport()
            
            if (validationPassed && report != null) {
                Log.i(TAG, "Pre-flight validation completed successfully")
                Log.d(TAG, "Validation report: All resources available = ${report.allResourcesAvailable}")
                
                if (!report.allResourcesAvailable) {
                    Log.w(TAG, "Some resources missing, but fallbacks available")
                    Log.d(TAG, "Missing resources: ${report.missingResources}")
                    Log.d(TAG, "Recommended action: ${report.recommendedAction}")
                    
                    // Log additional diagnostic information if available
                    resourceValidation?.let { rv ->
                        Log.d(TAG, "Diagnostic validation - Custom components: ${rv.customComponentsAvailable}")
                        Log.d(TAG, "Missing drawables: ${rv.missingDrawables}")
                    }
                }
                
                // Log diagnostic information
                settingsResourceManager.logDiagnostics()
                
                // Generate comprehensive diagnostic report
                if (::crashDiagnosticManager.isInitialized) {
                    val diagnosticReport = crashDiagnosticManager.generateDiagnosticReport()
                    Log.d(TAG, "Diagnostic report generated with ${diagnosticReport.componentStates.size} components")
                }
                
                true
            } else {
                Log.e(TAG, "Pre-flight validation failed")
                
                // Log failure details using crash diagnostic manager
                if (::crashDiagnosticManager.isInitialized) {
                    crashDiagnosticManager.logCrashDetails(
                        RuntimeException("Pre-flight validation failed"),
                        "performPreFlightValidation",
                        "ValidationFailed"
                    )
                }
                
                false
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error during pre-flight validation", e)
            
            // Use crash diagnostic manager to log the error
            if (::crashDiagnosticManager.isInitialized) {
                crashDiagnosticManager.logCrashDetails(e, "performPreFlightValidation")
            }
            
            false
        }
    }

    /**
     * Create emergency settings UI when normal initialization fails
     */
    private fun createEmergencySettingsUI(container: ViewGroup?): View {
        return try {
            Log.w(TAG, "Creating emergency settings UI")
            
            // Use SettingsErrorRecovery if available
            val emergencyView = if (::settingsErrorRecovery.isInitialized) {
                settingsErrorRecovery.createEmergencySettingsUI()
            } else {
                createBasicEmergencyUI()
            }
            
            // Log emergency UI creation
            if (::crashDiagnosticManager.isInitialized) {
                crashDiagnosticManager.logCrashDetails(
                    RuntimeException("Emergency UI created due to initialization failure"),
                    "createEmergencySettingsUI",
                    "EmergencyMode"
                )
            }
            
            Log.i(TAG, "Emergency settings UI created successfully")
            emergencyView
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error creating emergency UI", e)
            
            // Log the error if possible
            if (::crashDiagnosticManager.isInitialized) {
                crashDiagnosticManager.logCrashDetails(e, "createEmergencySettingsUI")
            }
            
            // Return absolute minimal view
            createBasicEmergencyUI()
        }
    }

    /**
     * Create basic emergency UI as last resort
     */
    private fun createBasicEmergencyUI(): View {
        return try {
            // Create a simple LinearLayout with basic settings
            val emergencyLayout = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            
            // Add title
            val titleText = android.widget.TextView(requireContext()).apply {
                text = "Settings (Safe Mode)"
                textSize = 24f
                setPadding(0, 0, 0, 32)
            }
            emergencyLayout.addView(titleText)
            
            // Add basic message
            val messageText = android.widget.TextView(requireContext()).apply {
                text = "Settings are running in safe mode due to missing resources. Basic functionality is available."
                textSize = 16f
                setPadding(0, 0, 0, 32)
            }
            emergencyLayout.addView(messageText)
            
            // Add basic exit button with safe fragment management
            val exitButton = android.widget.Button(requireContext()).apply {
                text = "Exit Settings"
                setOnClickListener {
                    safeExitSettings()
                }
            }
            emergencyLayout.addView(exitButton)
            
            emergencyLayout
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error creating basic emergency UI", e)
            // Return absolute minimal view
            android.widget.TextView(requireContext()).apply {
                text = "Settings unavailable. Press BACK to exit."
                textSize = 18f
                setPadding(32, 32, 32, 32)
            }
        }
    }

    /**
     * Safely exit settings using SafeFragmentManager
     */
    private fun safeExitSettings() {
        try {
            if (::safeFragmentManager.isInitialized) {
                val success = safeFragmentManager.safeHideFragment(this)
                if (success) {
                    (activity as? MainActivity)?.showTime()
                } else {
                    Log.w(TAG, "Safe fragment hide failed, using fallback")
                    fallbackExitSettings()
                }
            } else {
                fallbackExitSettings()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in safe exit settings", e)
            if (::crashDiagnosticManager.isInitialized) {
                crashDiagnosticManager.logCrashDetails(e, "safeExitSettings")
            }
            fallbackExitSettings()
        }
    }

    /**
     * Fallback exit settings method
     */
    private fun fallbackExitSettings() {
        try {
            requireActivity().supportFragmentManager.beginTransaction()
                .hide(this@SettingFragment)
                .commitAllowingStateLoss()
            (activity as? MainActivity)?.showTime()
        } catch (e: Exception) {
            Log.e(TAG, "Error in fallback exit settings", e)
        }
    }


    // ------------------------------------------------------------
    //  KEY NAVIGATION SETUP
    // ------------------------------------------------------------
    private fun setupKeyNavigation() {
        try {
            Log.d(TAG, "Setting up key navigation for settings")
            
            // Focus management is now primarily handled by settingsFocusManager
            // We just ensure the root is focusable and request initial focus
            binding.content.apply {
                isFocusable = true
                isFocusableInTouchMode = true
            }
            
            Log.i(TAG, "Key navigation setup delegated to SettingsFocusManager")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up key navigation", e)
        }
    }
    
    // Removing manual setupFocusOrder as it's handled by SettingsFocusManager


    // ------------------------------------------------------------
    //  MODERN UI SETUP
    // ------------------------------------------------------------
    private fun setupUI() {
        val ctx = requireContext()

        binding.name.text = getString(R.string.app_name)
        binding.version.text = "Version 1.0.22"  // Placeholder, updated via setVersionName

        binding.switchChannelReversal.isChecked = SP.channelReversal
        binding.switchChannelNum.isChecked = SP.channelNum
        binding.switchTime.isChecked = SP.time
        binding.switchBootStartup.isChecked = SP.bootStartup
        binding.switchConfigAutoLoad.isChecked = SP.configAutoLoad
        binding.switchChannelCheck.isChecked = SP.channelCheck
        binding.switchWatchLast.isChecked = SP.watchLast
        binding.switchForceHighQuality.isChecked = SP.forceHighQuality

        // Add content descriptions for accessibility
        binding.switchChannelReversal.contentDescription = "Toggle channel reversal"
        binding.switchChannelNum.contentDescription = "Toggle channel numbering"
        binding.switchTime.contentDescription = "Toggle time display"
        binding.switchBootStartup.contentDescription = "Toggle boot startup"
        binding.switchConfigAutoLoad.contentDescription = "Toggle config auto load"
        binding.switchChannelCheck.contentDescription = "Toggle channel checking"
        binding.confirmConfig.contentDescription = "Confirm config URL"
        binding.confirmChannel.contentDescription = "Confirm default channel"
        binding.clear.contentDescription = "Clear all settings"
        binding.resetOrder.contentDescription = "Reset channel and category order"
        binding.appreciate.contentDescription = "Show appreciation message"
        binding.exit.contentDescription = "Exit the application"

        // Focus on Default Channel input as requested
        binding.channel.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
    }
    


    // ------------------------------------------------------------
    //  GLASSMORPHISM BACKGROUND SETUP
    // ------------------------------------------------------------
    private fun setupGlassyBackground() {
        try {
            val menuBackground = binding.root.findViewById<View>(R.id.menu_background)
            menuBackground?.let {
                // Apply look that matches MenuFragment using the centralized utility
                GlassEffectUtils.applyGlassBackground(
                    it, 
                    GlassType.MENU, 
                    requireContext()
                )
                
                // Match the transparency levels from MenuFragment's GlassStyleConfig
                it.alpha = 0.85f // Matching the visible intensity users expect
                it.elevation = 4f
            }
            
            // Initialize glass cards with consistent audio feedback
            val headerCard = binding.root.findViewById<GlassCard>(R.id.header_card)
            val configCard = binding.root.findViewById<GlassCard>(R.id.configuration_card)
            val preferencesCard = binding.root.findViewById<GlassCard>(R.id.preferences_card)
            val actionsCard = binding.root.findViewById<GlassCard>(R.id.actions_card)
            
            listOf(headerCard, configCard, preferencesCard, actionsCard).forEach { card ->
                card?.initializeWithAudio(tvUiUtils!!)
            }
            
            Log.i(TAG, "Unified glass background setup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up unified background", e)
        }
    }

    // ------------------------------------------------------------
    //  MODERN FOCUS ANIMATION SYSTEM
    // ------------------------------------------------------------
    private fun setupFocusAnimations() {
        try {
            // Setup focus handling for the entire settings layout
            settingsFocusManager.setupSettingsFocus(binding.content)
            
            // Use SettingsResourceManager to safely initialize toggle switches
            val initializedCount = settingsResourceManager.safeInitializeToggleSwitches(
                binding.content, 
                tvUiUtils
            )
            
            Log.i(TAG, "Successfully initialized $initializedCount toggle switches")
            
            // Validate that critical switches are working
            validateToggleSwitchInitialization()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in setupFocusAnimations, using fallback", e)
            setupFallbackFocusAnimations()
        }
    }

    /**
     * Validate that toggle switches were properly initialized
     */
    private fun validateToggleSwitchInitialization() {
        try {
            val toggleSwitches = listOf(
                binding.switchChannelReversal,
                binding.switchChannelNum,
                binding.switchTime,
                binding.switchBootStartup,
                binding.switchConfigAutoLoad,
                binding.switchChannelCheck,
                binding.switchWatchLast,
                binding.switchForceHighQuality
            )
            
            var validSwitches = 0
            toggleSwitches.forEach { switch ->
                try {
                    // Test basic functionality
                    val currentState = switch.isChecked
                    switch.isEnabled = true
                    
                    if (switch is ModernToggleSwitch) {
                        val isInFallbackMode = switch.isInFallbackMode()
                        Log.d(TAG, "ModernToggleSwitch validation - Fallback mode: $isInFallbackMode")
                    }
                    
                    validSwitches++
                } catch (e: Exception) {
                    Log.w(TAG, "Toggle switch validation failed", e)
                }
            }
            
            Log.i(TAG, "Toggle switch validation completed: $validSwitches/${toggleSwitches.size} switches valid")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during toggle switch validation", e)
        }
    }

    /**
     * Fallback focus animations when full system fails
     */
    private fun setupFallbackFocusAnimations() {
        try {
            Log.w(TAG, "Setting up fallback focus animations")
            
            val toggleSwitches = listOf(
                binding.switchChannelReversal,
                binding.switchChannelNum,
                binding.switchTime,
                binding.switchBootStartup,
                binding.switchConfigAutoLoad,
                binding.switchChannelCheck,
                binding.switchWatchLast,
                binding.switchForceHighQuality
            )
            
            toggleSwitches.forEach { switch ->
                try {
                    // Basic focus animation
                    switch.setOnFocusChangeListener { view, hasFocus ->
                        try {
                            val scale = if (hasFocus) 1.05f else 1.0f
                            view.animate()
                                .scaleX(scale)
                                .scaleY(scale)
                                .setDuration(200)
                                .start()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error in fallback focus animation", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error setting up fallback animation for switch", e)
                }
            }
            
            Log.i(TAG, "Fallback focus animations setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in fallback focus animations", e)
        }
    }


    // ------------------------------------------------------------
    //  PERFORMANCE OPTIMIZATION AND ADAPTIVE RENDERING
    // ------------------------------------------------------------
    private fun setupPerformanceOptimization() {
        val content = binding.content
        val menuBackground = binding.root.findViewById<View>(R.id.menu_background)
        
        // Start performance monitoring
        performanceManager.startMonitoring(content)
        
        // Apply performance optimizations based on device capabilities
        performanceManager.applyPerformanceOptimizations(content)
        
        // Optimize GPU usage for background effects (formerly glassyBackground)
        // If it's a simple View, the optimizer might need adjustments, but for now we pass it
        if (menuBackground is GlassyBackgroundView) {
            performanceManager.optimizeGpuUsage(menuBackground)
        }
        
        // Create fallback rendering for lower-end devices
        performanceManager.createFallbackRendering(content)
        
        // Log performance status
        val isLowEnd = performanceManager.isLowEndDevice()
        Log.d(TAG, "Performance optimization applied. Low-end device: $isLowEnd")
    }


    // ------------------------------------------------------------
    //  ACCESSIBILITY AND COMPLIANCE
    // ------------------------------------------------------------
    private fun setupAccessibility() {
        val content = binding.content
        
        // Apply comprehensive accessibility enhancements
        accessibilityManager.applyAccessibilityEnhancements(content)
        
        // Setup keyboard navigation support
        accessibilityManager.setupKeyboardNavigation(content)
        
        // Log accessibility status for debugging
        val isAccessibilityEnabled = accessibilityManager.isAccessibilityEnabled()
        val isHighContrast = accessibilityManager.isHighContrastMode()
        
        Log.d(TAG, "Accessibility enabled: $isAccessibilityEnabled, High contrast: $isHighContrast")
        
        // Apply additional enhancements based on accessibility state
        if (isAccessibilityEnabled) {
            enhanceForScreenReaders()
        }
        
        if (isHighContrast) {
            applyHighContrastOptimizations()
        }
    }

    private fun enhanceForScreenReaders() {
        // Add comprehensive content descriptions
        binding.name.contentDescription = "Application name: ${binding.name.text}"
        binding.version.contentDescription = "Application version: ${binding.version.text}"
        
        // Enhance input field descriptions
        binding.config.contentDescription = "Channel configuration URL input field"
        binding.channel.contentDescription = "Default channel number input field"
        binding.server.contentDescription = "Server address display"
        
        // Add section announcements
        binding.root.announceForAccessibility("Settings page loaded with configuration, preferences, and action sections")
    }

    private fun applyHighContrastOptimizations() {
        // Additional high contrast optimizations beyond the AccessibilityManager
        val resources = requireContext().resources
        
        // Increase focus indicator prominence
        settingsFocusManager.setHighContrastMode(true)
        
        // Ensure glass effects don't interfere with high contrast
        val menuBackground = binding.root.findViewById<View>(R.id.menu_background)
        if (menuBackground is GlassyBackgroundView) {
            menuBackground.setBlurEnabled(false) // Disable blur in high contrast mode
        }
    }


    // ------------------------------------------------------------
    //  RESPONSIVE LAYOUT OPTIMIZATION
    // ------------------------------------------------------------
    private fun setupResponsiveLayout() {
        val container = binding.container
        val content = binding.content
        
        // Apply TV-specific container constraints
        responsiveLayoutManager.applyTVContainerConstraints(container)
        
        // Apply adaptive layout based on screen characteristics
        responsiveLayoutManager.applyAdaptiveLayout(content)
        
        // Apply density-specific optimizations
        responsiveLayoutManager.applyDensityOptimizations(content)
        
        // Log screen characteristics for debugging
        val screenCategory = responsiveLayoutManager.getScreenSizeCategory()
        val scaleFactor = responsiveLayoutManager.calculateScaleFactor()
        val isTv = responsiveLayoutManager.isTVDevice()
        
        Log.d(TAG, "Screen category: $screenCategory, Scale factor: $scaleFactor, Is TV: $isTv")
    }


    // ------------------------------------------------------------
    //  LISTENERS (UNCUT)
    // ------------------------------------------------------------
    private fun setupListeners() {

        binding.switchChannelReversal.setOnCheckedChangeListener { _, b ->
            SP.channelReversal = b
            (activity as MainActivity).settingActive()
        }

        binding.switchChannelNum.setOnCheckedChangeListener { _, b ->
            SP.channelNum = b
            (activity as MainActivity).settingActive()
        }

        binding.switchTime.setOnCheckedChangeListener { _, b ->
            SP.time = b
            (activity as MainActivity).settingActive()
        }

        binding.switchBootStartup.setOnCheckedChangeListener { _, b ->
            SP.bootStartup = b
            (activity as MainActivity).settingActive()
        }

        binding.switchConfigAutoLoad.setOnCheckedChangeListener { _, b ->
            SP.configAutoLoad = b
            (activity as MainActivity).settingActive()
        }

        binding.switchChannelCheck.setOnCheckedChangeListener { _, b ->
            SP.channelCheck = b
            (activity as MainActivity).settingActive()
        }

         binding.switchWatchLast.setOnCheckedChangeListener { _, b ->
            SP.watchLast = b
            (activity as MainActivity).settingActive()
        }

        binding.switchForceHighQuality.setOnCheckedChangeListener { _, b ->
            SP.forceHighQuality = b
            (activity as MainActivity).settingActive()
        }

        binding.qrcode.setOnClickListener {
            val imageModalFragment = ModalFragment()
            val size = Utils.dpToPx(200)
            val img = QrCodeUtil().createQRCodeBitmap(binding.server.text.toString(), size, size)
            
            if (img == null) {
                android.widget.Toast.makeText(requireContext(), "No content to generate QR Code", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val args = Bundle()
            args.putParcelable("bitmap", img);
            imageModalFragment.arguments = args

            imageModalFragment.show(requireFragmentManager(), ModalFragment.TAG)
            (activity as MainActivity).settingActive()
        }

        binding.checkVersion.setOnClickListener {
            requestInstallPermissions()
            (activity as MainActivity).settingActive()
        }

        val config = binding.config
        config.text = SP.config?.let { Editable.Factory.getInstance().newEditable(it) }
            ?: Editable.Factory.getInstance().newEditable("")


        binding.confirmConfig.setOnClickListener {
            tvUiUtils?.playClickSound()

            val text = binding.config.text.toString().trim()
            val url = Utils.formatUrl(text)
            uri = Uri.parse(url)

            if (uri.scheme.isNullOrEmpty()) {
                uri = uri.buildUpon().scheme("http").build()
            }

            if (uri.isAbsolute) {
                if (uri.scheme == "file") requestReadPermissions()
                else TVList.parseUri(uri)
                Toast.makeText(requireContext(), "Config updated", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Invalid address", Toast.LENGTH_SHORT).show()
            }

            (activity as MainActivity).settingActive()
        }

        binding.confirmChannel.setOnClickListener {
            tvUiUtils?.playClickSound()

            val num = binding.channel.text.toString().toIntOrNull()
            if (num != null && num > 0 && num <= TVList.listModel.size) {
                SP.channel = num
                Toast.makeText(requireContext(), "Channel set to $num", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Invalid channel number", Toast.LENGTH_SHORT).show()
            }

            (activity as MainActivity).settingActive()
        }

        binding.clear.setOnClickListener {
            tvUiUtils?.playClickSound()

            SP.channel = 0
            SP.position = 0

            binding.config.text = Editable.Factory.getInstance().newEditable("")
            binding.channel.text = Editable.Factory.getInstance().newEditable("")

            requireContext().deleteFile(TVList.FILE_NAME)
            SP.deleteLike()
            Toast.makeText(requireContext(), "Settings cleared", Toast.LENGTH_SHORT).show()
        }

        binding.resetOrder.setOnClickListener {
            tvUiUtils?.playClickSound()
            
            android.app.AlertDialog.Builder(requireContext())
                .setTitle("Reset Order & Renames")
                .setMessage("This will reset all category and channel order and rename settings. Continue?")
                .setPositiveButton("Reset") { _, _ ->
                    OrderPreferenceManager.resetAll()
                    Toast.makeText(requireContext(), "Order and renames reset. Please refresh the channel list.", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
            
            (activity as MainActivity).settingActive()
        }

        binding.appreciate.setOnClickListener {
            tvUiUtils?.playClickSound()
            val modal = ModalFragment()
            val args = Bundle()
            args.putInt(ModalFragment.KEY_DRAWABLE_ID, R.drawable.appreciate)
            modal.arguments = args
            modal.show(requireFragmentManager(), ModalFragment.TAG)
            (activity as MainActivity).settingActive()
        }

        binding.exit.setOnClickListener {
            //tvUiUtils?.playClickSound()
            requireActivity().finishAffinity()
        }
    }

    // ------------------------------------------------------------
    //  PERMISSIONS
    // ------------------------------------------------------------
    private fun requestReadPermissions() {
        val ctx = requireContext()
        val list = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (list.isEmpty()) {
            TVList.parseUri(uri)
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(), list.toTypedArray(), PERMISSION_READ
            )
        }
    }

    fun setServer(server: String) {
        binding.server.text = "http://$server"
    }

    fun setVersionName(versionName: String) {
        binding.version.text = versionName
    }

    private fun hideSelf() {
        requireActivity().supportFragmentManager.beginTransaction()
            .hide(this)
            .commit()
        (activity as MainActivity).showTime()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            val config = binding.config
            config.text = SP.config?.let { Editable.Factory.getInstance().newEditable(it) }
                ?: Editable.Factory.getInstance().newEditable("")
            
            // Ensure proper focus when settings becomes visible
            binding.content.post {
                binding.switchChannelReversal.requestFocus()
            }
        }
    }

    private fun requestInstallPermissions() {
        val context = requireContext()
        val permissionsList: MutableList<String> = mutableListOf()

        // Check for "Request Install Packages" permission
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
//            !context.packageManager.canRequestPackageInstalls()
//        ) {
//            permissionsList.add(Manifest.permission.REQUEST_INSTALL_PACKAGES)
//        }

        // Check for "Read External Storage" permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        // Optional: Handle scoped storage for Android 13 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsList.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Check for "Write External Storage" permission (deprecated after Android 10)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // Request permissions if the list is not empty
        if (permissionsList.isNotEmpty()) {
            try {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    permissionsList.toTypedArray(),
                    PERMISSIONS_REQUEST_CODE
                )
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Fragment is not attached to an activity: ${e.message}")
            }
        } else {
            // All permissions are granted; proceed with the update manager
            updateManager.checkAndUpdate()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)

        if (requestCode == PERMISSION_READ &&
            results.isNotEmpty() &&
            results[0] == PackageManager.PERMISSION_GRANTED
        ) {
            TVList.parseUri(uri)
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        
        try {
            Log.d(TAG, "Starting SettingFragment cleanup")
            
            // Use SafeFragmentManager to cancel pending operations
            if (::safeFragmentManager.isInitialized) {
                safeFragmentManager.cancelPendingOperations()
                Log.d(TAG, "SafeFragmentManager cleanup completed")
            }
            
            // Cleanup focus animations
            if (::settingsFocusManager.isInitialized) {
                settingsFocusManager.cleanup(binding.content)
                Log.d(TAG, "Focus manager cleanup completed")
            }
            
            // Stop performance monitoring
            if (::performanceManager.isInitialized) {
                performanceManager.stopMonitoring()
                Log.d(TAG, "Performance manager cleanup completed")
            }
            
            // Log final diagnostics if resource manager is available
            if (::settingsResourceManager.isInitialized) {
                Log.d(TAG, "Final resource manager diagnostics:")
                settingsResourceManager.logDiagnostics()
            }
            
            // Generate final diagnostic report
            if (::crashDiagnosticManager.isInitialized) {
                val finalReport = crashDiagnosticManager.generateDiagnosticReport()
                Log.d(TAG, "Final diagnostic report: ${finalReport.componentStates.size} components tracked")
                
                // Log cleanup completion
                crashDiagnosticManager.logCrashDetails(
                    RuntimeException("SettingFragment cleanup completed"),
                    "onDestroyView",
                    "CleanupCompleted"
                )
            }
            
            Log.i(TAG, "SettingFragment cleanup completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
            
            // Log cleanup error if possible
            if (::crashDiagnosticManager.isInitialized) {
                crashDiagnosticManager.logCrashDetails(e, "onDestroyView")
            }
        } finally {
            _binding = null
        }
    }

    companion object {
        const val TAG = "SettingFragment"
        const val PERMISSION_READ = 30
        const val PERMISSIONS_REQUEST_CODE = 1
        const val PERMISSION_READ_EXTERNAL_STORAGE_REQUEST_CODE = 2
    }
}
