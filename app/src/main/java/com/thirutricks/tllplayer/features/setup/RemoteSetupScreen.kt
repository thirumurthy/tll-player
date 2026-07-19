package com.thirutricks.tllplayer.features.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.Flow
import com.thirutricks.tllplayer.core.companion.CompanionLink
import com.thirutricks.tllplayer.core.companion.CompanionPayload
import com.thirutricks.tllplayer.core.companion.CompanionServerState
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.roundedPanel
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * The Remote add-source screen: opens a small LAN web server and shows the PIN, a QR of the URL, and
 * the URL text so a phone on the same Wi-Fi can fill the Add Source form. The phone only fills it —
 * when a submission arrives, [onPayloadReceived] hands off to the Manual form (pre-filled) where the
 * user presses Start Import. The listener stops automatically when this screen leaves composition.
 */
@Composable
fun RemoteSetupScreen(
    state: CompanionServerState,
    payloads: Flow<CompanionPayload>,
    onStartListener: (port: Int) -> Unit,
    onStopListener: () -> Unit,
    onPayloadReceived: (CompanionPayload) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val actionFocus = remember { FocusRequester() }
    LaunchedEffect(state::class) { runCatching { actionFocus.requestFocus() } }

    // A phone submission hands off to the Manual form; the host navigates away (which stops the server).
    LaunchedEffect(payloads) { payloads.collect(onPayloadReceived) }
    DisposableEffect(Unit) { onDispose { onStopListener() } }

    Box(modifier.fillMaxSize().roundedPanel()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(modifier = Modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Add from your phone", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "On a phone or laptop on the same Wi-Fi, scan the QR (or open the URL), enter this PIN, " +
                        "fill in your playlist and tap Send to TV. Its details then appear here, ready to import — " +
                        "you press Start Import with the remote.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                when (state) {
                    CompanionServerState.Idle, CompanionServerState.Starting -> {
                        val starting = state == CompanionServerState.Starting
                        OwnTVButton(
                            label = if (starting) "Opening…" else "Open server",
                            onClick = { onStartListener(CompanionLink.DEFAULT_PORT) },
                            enabled = !starting,
                            modifier = Modifier.focusRequester(actionFocus),
                        )
                    }
                    is CompanionServerState.Listening -> {
                        Text("Enter this PIN in the browser", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.pin,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            letterSpacing = 8.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        state.qr?.let { qr ->
                            Image(
                                bitmap = qr,
                                contentDescription = "QR code for the companion URL",
                                modifier = Modifier.size(188.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(9.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text("Or open this URL", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        state.urls.forEach { url ->
                            Text(url, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, textAlign = TextAlign.Center)
                        }
                        Spacer(Modifier.height(18.dp))
                        OwnTVButton("Stop server", onClick = onStopListener, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.focusRequester(actionFocus))
                    }
                    is CompanionServerState.Failed -> {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFEF4444), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(20.dp))
                        OwnTVButton("Try again", onClick = { onStartListener(CompanionLink.DEFAULT_PORT) }, modifier = Modifier.focusRequester(actionFocus))
                    }
                }
                Spacer(Modifier.height(16.dp))
                OwnTVButton("Back", onClick = onBack, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.height(24.dp)) // breathing room so Back never sits flush to the screen edge
            }
        }
    }
}
