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
import com.thirutricks.tllplayer.OrderPreferenceManager
import com.thirutricks.tllplayer.R
import android.widget.Toast

class SettingFragment : Fragment() {

    private var _binding: SettingBinding? = null
    private val binding get() = _binding!!

    private lateinit var uri: Uri
    private lateinit var updateManager: UpdateManager
    private var tvUiUtils: TvUiUtils? = null
    private lateinit var settingsFocusManager: SettingsFocusManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = SettingBinding.inflate(inflater, container, false)

        tvUiUtils = TvUiUtils(requireContext())
        tvUiUtils?.initSounds(R.raw.focus, R.raw.click)  // SOUND FEEDBACK

        // Initialize SettingsFocusManager
        settingsFocusManager = SettingsFocusManager(requireContext())
        settingsFocusManager.initialize(tvUiUtils!!)

        setupUI()
        setupListeners()
        setupGlassyBackground()
        setupFocusAnimations()  // ⭐ ADD TIVIMATE STYLE FOCUS EFFECTS

        updateManager = UpdateManager(requireContext(), requireContext().appVersionCode)
        (activity as MainActivity).ready(TAG)
        // SP.config = "https://besttllapp.online/tvnexa/v1/admin/channel-pllayer"
        return binding.root
    }

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
        val glassyBackground = binding.root.findViewById<GlassyBackgroundView>(R.id.glassy_background)
        glassyBackground?.let {
            it.setGlassIntensity(0.8f)
            it.setBlurEnabled(true)
        }
        
        // Initialize glass cards with audio feedback
        val headerCard = binding.root.findViewById<GlassCard>(R.id.header_card)
        val configCard = binding.root.findViewById<GlassCard>(R.id.configuration_card)
        val preferencesCard = binding.root.findViewById<GlassCard>(R.id.preferences_card)
        val actionsCard = binding.root.findViewById<GlassCard>(R.id.actions_card)
        
        listOf(headerCard, configCard, preferencesCard, actionsCard).forEach { card ->
            card?.initializeWithAudio(tvUiUtils!!)
        }
    }

    // ------------------------------------------------------------
    //  MODERN FOCUS ANIMATION SYSTEM
    // ------------------------------------------------------------
    private fun setupFocusAnimations() {
        // Setup focus handling for the entire settings layout
        settingsFocusManager.setupSettingsFocus(binding.content)
        
        // Initialize modern toggle switches with audio feedback
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
            if (switch is ModernToggleSwitch) {
                switch.initializeWithAudio(tvUiUtils!!)
            }
        }
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
        
        // Cleanup focus animations
        settingsFocusManager.cleanup(binding.content)
        
        _binding = null
    }

    companion object {
        const val TAG = "SettingFragment"
        const val PERMISSION_READ = 30
        const val PERMISSIONS_REQUEST_CODE = 1
        const val PERMISSION_READ_EXTERNAL_STORAGE_REQUEST_CODE = 2
    }
}
