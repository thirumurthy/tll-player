package com.thirutricks.tllplayer.features.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.ProfileEntity
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVAvatar
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Phase 6.5 — the "Who's watching?" launch gate. Shown when more than one profile exists. Picking a
 * profile makes it active (and prompts for a PIN if it's locked); [onEnter] proceeds into the shell.
 */
@Composable
fun ProfileGate(onEnter: () -> Unit, onAddProfile: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ProfilesViewModel = koinViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(profiles.isNotEmpty()) {
        if (profiles.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }

    fun choose(p: ProfileEntity) {
        if (p.pinHash != null) { pinError = false; pinFor = p } else { vm.switchTo(p, onEnter) }
    }

    Box(
        modifier = modifier.fillMaxSize().background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Who's watching?", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            Spacer(Modifier.height(36.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.focusGroup().padding(horizontal = 48.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    ProfileTile(
                        profile = p,
                        modifier = if (p.id == profiles.first().id) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = { choose(p) },
                    )
                }
                item { AddTile(onClick = onAddProfile) }
            }
        }
    }

    pinFor?.let { p ->
        PinDialog(
            title = if (pinError) "Wrong PIN — try again" else "Enter PIN for ${p.name}",
            onSubmit = { pin ->
                if (vm.verifyPin(p, pin)) { vm.switchTo(p, onEnter) } else pinError = true
            },
            onDismiss = { pinFor = null },
        )
    }
}

@Composable
private fun ProfileTile(profile: ProfileEntity, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusableSurface(
            onClick = onClick,
            modifier = modifier.size(132.dp),
            shape = RoundedCornerShape(24.dp),
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(contentAlignment = Alignment.TopEnd) {
                OwnTVAvatar(avatarId = profile.avatarId, modifier = Modifier.size(104.dp))
                if (profile.pinHash != null) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        OwnTVIcon(OwnTVIcon.SETTINGS, tint = colors.onSurface, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(profile.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, textAlign = TextAlign.Center)
        if (profile.isKids) {
            Text("KIDS", style = MaterialTheme.typography.labelSmall, color = colors.primary)
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val colors = OwnTVTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusableSurface(
            onClick = onClick,
            modifier = Modifier.size(132.dp),
            shape = RoundedCornerShape(24.dp),
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(
                modifier = Modifier.size(104.dp).clip(CircleShape).background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                OwnTVIcon(OwnTVIcon.ADD, tint = colors.onSurfaceVariant, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Add profile", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
    }
}
