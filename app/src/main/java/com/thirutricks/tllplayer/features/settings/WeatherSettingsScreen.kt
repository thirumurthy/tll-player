package com.thirutricks.tllplayer.features.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.TextInputDialog
import com.thirutricks.tllplayer.ui.components.roundedPanel

/**
 * Weather settings — the top-bar weather chip: show/hide, manual location override, and °C/°F.
 * (Grew out of two loose rows on the Settings root; grouped here as its own submenu.)
 */
@Composable
fun WeatherSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val enabled by vm.weatherEnabled.collectAsStateWithLifecycle()
    val location by vm.weatherLocation.collectAsStateWithLifecycle()
    val fahrenheit by vm.weatherFahrenheit.collectAsStateWithLifecycle()

    var showLocation by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val locationRowFocus = remember { FocusRequester() }
    var dialogReturn by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    LaunchedEffect(showLocation) {
        if (showLocation) {
            dialogReturn = locationRowFocus
        } else {
            dialogReturn?.let { row ->
                kotlinx.coroutines.delay(80)
                runCatching { row.requestFocus() }
            }
        }
    }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            // onEnter fires for any entry from outside the group — including the dialog-close
            // restore (the dialog lives outside it) — so it must prefer the pending return row.
            .focusProperties {
                onEnter = {
                    val target = dialogReturn ?: firstFocus
                    dialogReturn = null
                    runCatching { target.requestFocus() }
                }
            }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Header("Weather", onBack)
        Spacer(Modifier.height(8.dp))

        GroupLabel("Top bar weather")
        Row2(
            icon = OwnTVIcon.EPG, title = "Show weather",
            desc = "Display the current weather in the top bar.",
            chip = if (enabled) "On" else "Off", primaryChip = enabled,
            modifier = Modifier.focusRequester(firstFocus),
            onClick = { vm.setWeatherEnabled(!enabled) },
        )
        Row2(
            icon = OwnTVIcon.EPG, title = "Custom location",
            desc = "Override the city used for weather. Leave blank to auto-detect, or enter a city " +
                "(e.g. London) or \"lat,lon\" (e.g. 51.5,-0.12). Useful on a VPN, where auto-detect " +
                "resolves to the server's city.",
            chip = location.ifBlank { "Auto" }, primaryChip = false, chevron = true,
            modifier = Modifier.focusRequester(locationRowFocus),
            onClick = { showLocation = true },
        )
        Row2(
            icon = OwnTVIcon.EPG, title = "Temperature unit",
            desc = "Show the temperature in Celsius or Fahrenheit.",
            chip = if (fahrenheit) "°F" else "°C", primaryChip = true,
            onClick = { vm.setWeatherFahrenheit(!fahrenheit) },
        )
    }

    if (showLocation) {
        TextInputDialog(
            title = "Custom location",
            initial = location,
            label = "City or lat,lon",
            hint = "Leave blank to auto-detect from your public IP. Enter a city (e.g. London) or \"lat,lon\" (e.g. 51.5,-0.12).",
            onConfirm = { vm.setWeatherLocation(it); showLocation = false },
            onDismiss = { showLocation = false },
        )
    }
}
