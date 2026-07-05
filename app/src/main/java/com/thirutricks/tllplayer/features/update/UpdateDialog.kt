package com.thirutricks.tllplayer.features.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import com.thirutricks.tllplayer.core.update.UpdateManager
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.OwnTVSpinner
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * The in-app update dialog (used both from Settings → Check for updates and the automatic prompt).
 * Binds to [UpdateManager]'s state machine: checking → up-to-date / available → downloading.
 * [checkOnOpen] makes opening the dialog trigger a fresh check (the Settings path).
 */
@Composable
fun UpdateDialog(onDismiss: () -> Unit, checkOnOpen: Boolean = false) {
    val manager: UpdateManager = koinInject()
    val state by manager.state.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (checkOnOpen) manager.check()
    }
    LaunchedEffect(state) {
        if (state is UpdateManager.State.Available || state is UpdateManager.State.UpToDate || state is UpdateManager.State.Failed) {
            runCatching { focus.requestFocus() }
        }
    }
    BackHandler { onDismiss() }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(520.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp)) {
            Text("App update", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(12.dp))

            when (val s = state) {
                UpdateManager.State.Idle, UpdateManager.State.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OwnTVSpinner(sizeDp = 28)
                        Spacer(Modifier.width(12.dp))
                        Text("Checking for updates…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                UpdateManager.State.UpToDate -> {
                    Text(
                        "You're on the latest version (v${manager.currentVersion}).",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.focusRequester(focus))
                    }
                }
                is UpdateManager.State.Available -> {
                    Text(
                        "Version ${s.info.version} is available (you have v${manager.currentVersion}).",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurface,
                    )
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("What's new", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwnTVButton("Later", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                        Spacer(Modifier.weight(1f))
                        OwnTVButton("Update now", onClick = { manager.downloadAndInstall() }, icon = OwnTVIcon.DOWNLOADS, modifier = Modifier.focusRequester(focus))
                    }
                }
                is UpdateManager.State.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OwnTVSpinner(sizeDp = 28)
                        Spacer(Modifier.width(12.dp))
                        Text("Downloading update… ${s.percent}%", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The system installer opens when the download finishes.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                    )
                }
                is UpdateManager.State.Failed -> {
                    Text(s.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                        Spacer(Modifier.weight(1f))
                        OwnTVButton("Try again", onClick = { manager.check() }, modifier = Modifier.focusRequester(focus))
                    }
                }
            }
        }
    }
}
