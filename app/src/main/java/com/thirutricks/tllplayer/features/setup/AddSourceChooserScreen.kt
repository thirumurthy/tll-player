package com.thirutricks.tllplayer.features.setup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.roundedPanel
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * First step of "Add source": pick **Remote** (fill the form on a phone over the LAN) or **Manual**
 * (type Xtream / M3U / Stalker here with the remote). Shared by the setup wizard and Settings → Manage
 * sources so both entry points offer the same choice.
 */
@Composable
fun AddSourceChooserScreen(
    onRemote: () -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffectRequestFocus(firstFocus)
    BackHandler { onBack() }

    Box(modifier.fillMaxSize().roundedPanel().background(colors.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Add a source", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Fill the form on your phone, or type the details here with the remote.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ChooserCard(
                    icon = OwnTVIcon.PLAYLIST,
                    title = "Remote",
                    subtitle = "Use a phone on the same Wi-Fi",
                    onClick = onRemote,
                    modifier = Modifier.focusRequester(firstFocus),
                )
                ChooserCard(
                    icon = OwnTVIcon.ADD,
                    title = "Manual",
                    subtitle = "Type Xtream / M3U / Stalker here",
                    onClick = onManual,
                )
            }
            Spacer(Modifier.height(24.dp))
            OwnTVButton("Back", onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun ChooserCard(
    icon: OwnTVIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.size(width = 224.dp, height = 174.dp),
        shape = RoundedCornerShape(22.dp),
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                OwnTVIcon(icon, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = if (focused) colors.primary else colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun LaunchedEffectRequestFocus(fr: FocusRequester) {
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
}
