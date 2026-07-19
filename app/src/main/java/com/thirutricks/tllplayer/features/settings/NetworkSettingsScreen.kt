package com.thirutricks.tllplayer.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.OwnTVTextField
import com.thirutricks.tllplayer.ui.components.roundedPanel
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Network → Proxy (Approach 1): one app-wide HTTP proxy. Enabling it routes all app traffic (playlist,
 * Xtream API, EPG, images, downloads, updates, ExoPlayer) and mpv playback through the proxy. SOCKS and
 * per-playlist overrides are not part of this version (see extras/PROXY_SUPPORT_PLAN.md).
 */
@Composable
fun NetworkSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: SettingsViewModel = koinViewModel()
    val config by vm.proxyConfig.collectAsStateWithLifecycle()
    val testState by vm.proxyTest.collectAsStateWithLifecycle()

    // Seed the editable form from the stored config once it's loaded (and whenever it changes from
    // outside, e.g. first composition). Local state lets the user edit freely, then Save persists.
    var seeded by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    LaunchedEffect(config) {
        if (!seeded) {
            enabled = config.enabled
            host = config.host
            port = if (config.port > 0) config.port.toString() else ""
            user = config.username
            pass = config.password
            seeded = true
        }
    }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler { onBack() }

    val portInt = port.trim().toIntOrNull() ?: 0
    val save = {
        vm.saveProxy(enabled, host, portInt, user, pass)
        vm.resetProxyTest()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Header("Proxy", onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel("HTTP Proxy")
        Row2(
            icon = OwnTVIcon.SHARE,
            title = "Use proxy",
            desc = "Send all OwnTV traffic and playback through an HTTP proxy.",
            chip = if (enabled) "On" else "Off", primaryChip = enabled,
            modifier = Modifier.focusRequester(firstFocus),
            onClick = { enabled = !enabled; save() },
        )

        Spacer(Modifier.height(12.dp))
        OwnTVTextField(
            value = host,
            onValueChange = { host = it },
            label = "Host",
            placeholder = "proxy.example.com",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OwnTVTextField(
            value = port,
            onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
            label = "Port",
            placeholder = "8080",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.width(220.dp),
        )
        Spacer(Modifier.height(12.dp))
        OwnTVTextField(
            value = user,
            onValueChange = { user = it },
            label = "Username (optional)",
            placeholder = "",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OwnTVTextField(
            value = pass,
            onValueChange = { pass = it },
            label = "Password (optional)",
            placeholder = "",
            isPassword = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OwnTVButton("Save", onClick = { save() })
            OwnTVButton(
                label = if (testState is SettingsViewModel.ProxyTestState.Testing) "Testing…" else "Test proxy",
                onClick = { vm.testProxy(host, portInt, user, pass) },
                style = OwnTVButtonStyle.SECONDARY,
            )
            ProxyTestLabel(testState)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Your proxy provider can see which servers you connect to, and the contents of any non-HTTPS traffic.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "HTTP proxy only for now — SOCKS and per-playlist proxies aren't supported yet.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProxyTestLabel(state: SettingsViewModel.ProxyTestState) {
    val colors = OwnTVTheme.colors
    val (text, color) = when (state) {
        is SettingsViewModel.ProxyTestState.Ok -> "Connected ✓ (${state.millis} ms)" to colors.primary
        is SettingsViewModel.ProxyTestState.Fail -> state.message to androidx.compose.ui.graphics.Color(0xFFEF4444)
        else -> null to colors.onSurfaceVariant
    }
    if (text != null) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
