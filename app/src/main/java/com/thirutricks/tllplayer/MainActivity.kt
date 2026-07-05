package com.thirutricks.tllplayer

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import com.thirutricks.tllplayer.core.launcher.LauncherDeepLink
import com.thirutricks.tllplayer.core.util.NetworkSecurity
import com.thirutricks.tllplayer.features.profiles.ProfileGate
import com.thirutricks.tllplayer.features.profiles.ProfilesViewModel
import com.thirutricks.tllplayer.features.setup.Onboarding
import com.thirutricks.tllplayer.features.shell.OwnTVShell
import com.thirutricks.tllplayer.features.shell.ShellViewModel
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme
import com.thirutricks.tllplayer.ui.theme.UiZoom

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "OwnTVHome"
        @Volatile var keyInterceptor: ((android.view.KeyEvent) -> Boolean)? = null
    }

    private val player: com.thirutricks.tllplayer.player.OwnTVPlayer by inject()
    private val previewEngine: com.thirutricks.tllplayer.player.LivePreviewEngine by inject()
    private val heroPreviewEngine: com.thirutricks.tllplayer.player.HeroPreviewEngine by inject()
    private var pendingDeepLink by mutableStateOf<LauncherDeepLink?>(null)

    // --- VPN guard: the production path (this activity) must enforce the same block the legacy app did.
    // Detecting on every network change (and on resume) catches a VPN toggled on while the app is running.
    private val connectivityManager: ConnectivityManager? by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    private val vpnNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            if (NetworkSecurity.isVpnActive(this@MainActivity)) {
                runOnUiThread {
                    Log.d(TAG, "VPN detected via network callback. Exiting app.")
                    Toast.makeText(this@MainActivity, "VPN detected. Exiting app.", Toast.LENGTH_LONG).show()
                    finishAffinity()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = LauncherDeepLink.parse(intent.data)
        Log.d(TAG, "onNewIntent deepLinkHost=${intent.data?.host} deepLinkType=${pendingDeepLink?.javaClass?.simpleName ?: "none"}")
    }

    override fun onStop() {
        super.onStop()
        // Backgrounded (Home / another app), exited, or logged out: stop playback and free the demuxer
        // cache + decoder buffers — holding them while invisible got the process LMK-killed on real TVs.
        if (!isChangingConfigurations) {
            player.onAppBackgrounded()
            // Live runs on ExoPlayer — remember the channel and free the stream (its audio must stop too).
            previewEngine.onAppBackgrounded()
            heroPreviewEngine.stop()
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onStart() {
        super.onStart()
        // Paired with onStop: bring back what was freed while backgrounded (notably the TV screensaver, which
        // kicks in during a long pause) — a VOD restored paused at its position, and a live channel re-tuned
        // to the live edge — so Play resumes instead of sitting on a dead/empty stream. No-op on fresh launch.
        player.onAppForegrounded()
        previewEngine.onAppForegrounded()
    }

    override fun onResume() {
        super.onResume()
        // Re-check the VPN guard on resume — catches a VPN enabled while the app was paused/backgrounded
        // (the network callback alone wouldn't fire if no new network came up while we were away).
        if (NetworkSecurity.isVpnActive(this)) {
            Toast.makeText(this, "VPN detected. Exiting app.", Toast.LENGTH_LONG).show()
            finishAffinity()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { connectivityManager?.unregisterNetworkCallback(vpnNetworkCallback) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // VPN guard — enforce immediately on launch and on every network change (mirrors the legacy app).
        if (NetworkSecurity.isVpnActive(this)) {
            Toast.makeText(this, "VPN detected. Exiting app.", Toast.LENGTH_LONG).show()
            finishAffinity()
            return
        }
        connectivityManager?.registerDefaultNetworkCallback(vpnNetworkCallback)
        pendingDeepLink = LauncherDeepLink.parse(intent.data)
        Log.d(TAG, "onCreate deepLinkHost=${intent.data?.host} deepLinkType=${pendingDeepLink?.javaClass?.simpleName ?: "none"}")
        setContent {
            // Hold the screen on while video is actually playing, so the TV screensaver doesn't
            // start mid-channel/episode; released when paused/stopped (then the screensaver is fine).
            val playing by player.isPlaying.collectAsStateWithLifecycle()
            LaunchedEffect(playing) {
                if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }


            val viewModel: ShellViewModel = koinViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            val customAccent by viewModel.customAccent.collectAsStateWithLifecycle()
            val uiZoomPercent by viewModel.uiZoomPercent.collectAsStateWithLifecycle()
            val animationLevel by viewModel.animationLevel.collectAsStateWithLifecycle()
            val avatarId by viewModel.avatarId.collectAsStateWithLifecycle()
            val profileName by viewModel.profileName.collectAsStateWithLifecycle()
            val sourceSummary by viewModel.sourceSummary.collectAsStateWithLifecycle()
            val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
            val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
            val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

            val profilesVm: ProfilesViewModel = koinViewModel()
            val profiles by profilesVm.profiles.collectAsStateWithLifecycle()
            var gatePassed by remember { mutableStateOf(false) }
            var addingProfile by remember { mutableStateOf(false) }
            // A backup restore deletes-then-reinserts profiles, so the list is briefly EMPTY while
            // the shell is showing — without this, the shell unmounts and remounts, dumping the user
            // out of Settings → Backup & Restore mid-restore. Only the cold start waits for the load.
            var everHadProfiles by remember { mutableStateOf(false) }
            LaunchedEffect(profiles) { if (profiles.isNotEmpty()) everHadProfiles = true }
            val shouldShowProfileGate = profiles.size > 1 || profiles.singleOrNull()?.pinHash != null

            // "Refresh on startup" — re-sync sources once the active profile is known.
            LaunchedEffect(activeProfileId) {
                if ((activeProfileId ?: -1L) >= 0L) viewModel.refreshOnStartIfEnabled()
            }



            OwnTVTheme(themeMode = themeMode, accent = accent, systemInDarkTheme = isSystemInDarkTheme(), customAccent = customAccent, animationLevel = animationLevel) {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density * UiZoom.factor(uiZoomPercent), base.fontScale),
                ) {
                    Box(modifier = Modifier.fillMaxSize().background(OwnTVTheme.colors.background)) {
                        val profile = activeProfileId
                        when {
                            profile == null -> Unit // loading
                            // Adding a profile from the gate → onboard the new profile.
                            addingProfile -> Onboarding(
                                firstRun = false,
                                onDone = { addingProfile = false; gatePassed = true },
                                onCancel = { addingProfile = false },
                                modifier = Modifier.fillMaxSize(),
                            )
                            // First run (no profile yet) → full onboarding.
                            profile < 0L -> Onboarding(
                                firstRun = true,
                                onDone = { gatePassed = true },
                                onCancel = {},
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Profiles still loading (≥0 means at least one exists) — avoid a gate/shell flicker.
                            profiles.isEmpty() && !everHadProfiles -> Unit
                            // Run 2+ (or a single locked profile): "Who's watching?" — choose a profile or add one.
                            shouldShowProfileGate && !gatePassed -> ProfileGate(
                                onEnter = { gatePassed = true },
                                onAddProfile = { addingProfile = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                            else -> OwnTVShell(
                                selectedSection = selectedSection,
                                onSelectSection = viewModel::selectSection,
                                themeMode = themeMode,
                                uiZoomPercent = uiZoomPercent,
                                onSetZoom = viewModel::setUiZoom,
                                avatarId = avatarId,
                                onSetAvatar = viewModel::setAvatar,
                                profileName = profileName,
                                sourceSummary = sourceSummary,
                                activeProfileId = activeProfileId,
                                pendingDeepLink = pendingDeepLink,
                                onDeepLinkConsumed = { pendingDeepLink = null },
                                isOffline = !isOnline,
                                onExitApp = { finish() },
                                onSwitchProfile = {
                                    // Stop playback and return to the "Who's watching?" gate — no app restart.
                                    player.onAppBackgrounded(); player.discardBackgroundRestore(); previewEngine.stop(); previewEngine.discardBackgroundRestore(); heroPreviewEngine.stop(); gatePassed = false
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (keyInterceptor?.invoke(event) == true) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
