package com.thirutricks.tllplayer.features.settings

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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.OwnTVTextField
import com.thirutricks.tllplayer.ui.components.dialogPanel
import com.thirutricks.tllplayer.ui.components.trapAllFocusExit
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme
import com.thirutricks.tllplayer.ui.theme.PopupFontTheme
import com.thirutricks.tllplayer.ui.components.roundedPanel

/**
 * Settings → Video Player → Subtitles → OpenSubtitles account (subtitle plan §5.2/§5.3).
 * The connection is per OwnTV profile; users sign in with their own free OpenSubtitles account.
 */
@Composable
fun OpenSubtitlesAccountScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: OpenSubtitlesViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    var showSignIn by remember { mutableStateOf(false) }
    var showDeleteSubs by remember { mutableStateOf(false) }
    // Returning from the Delete-subtitles screen should land back on the row that opened it,
    // not the first row (Sign out / Sign in).
    var returnedFromDelete by remember { mutableStateOf(false) }
    if (showDeleteSubs) {
        DeleteSubtitlesScreen(
            onBack = { showDeleteSubs = false; returnedFromDelete = true },
            modifier = modifier,
        )
        return
    }
    val firstFocus = remember { FocusRequester() }
    val deleteFocus = remember { FocusRequester() }
    LaunchedEffect(state) {
        if (showSignIn) return@LaunchedEffect
        if (returnedFromDelete) {
            returnedFromDelete = false
            runCatching { deleteFocus.requestFocus() }
        } else {
            runCatching { firstFocus.requestFocus() }
        }
    }
    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .roundedPanel()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Header("OpenSubtitles", onBack) }
            if (state is OpenSubtitlesViewModel.UiState.SignedIn) {
                OwnTVButton("Refresh", onClick = { vm.refresh() }, style = OwnTVButtonStyle.SECONDARY)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "OpenSubtitles is a free, open community subtitle service. Sign in with your own " +
                "OpenSubtitles account to search and download subtitles for movies and series " +
                "episodes. You can create a free account at opensubtitles.com. The connection " +
                "applies to the current profile only.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            is OpenSubtitlesViewModel.UiState.SignedIn -> {
                GroupLabel("Account")
                val session = s.session
                InfoRow("Connected as", session.username)
                InfoRow("Account", listOfNotNull(session.level, "VIP".takeIf { session.vip }).joinToString(" · ").ifBlank { "Free" })
                // Provider-reported values only (§5.3): remaining-only unless a total was returned.
                val remaining = session.remainingDownloads
                if (remaining != null) {
                    val total = session.allowedDownloads
                    InfoRow(
                        "Downloads",
                        if (total != null) "$remaining of $total remaining today" else "$remaining remaining today",
                    )
                }
                session.resetTime?.let { InfoRow("Resets", "in $it") }
                Spacer(Modifier.height(14.dp))
                Row2(
                    icon = OwnTVIcon.SUBTITLE, title = "Sign out",
                    desc = "Removes this profile's OpenSubtitles login from this device. Already " +
                        "downloaded subtitles stay available offline.",
                    modifier = Modifier.focusRequester(firstFocus),
                    onClick = { vm.signOut() },
                )
            }
            OpenSubtitlesViewModel.UiState.Busy -> {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Contacting OpenSubtitles…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            OpenSubtitlesViewModel.UiState.SignedOut -> {
                Row2(
                    icon = OwnTVIcon.SUBTITLE, title = "Sign in",
                    desc = "Connect this profile's OpenSubtitles account.",
                    chevron = true,
                    modifier = Modifier.focusRequester(firstFocus),
                    onClick = { showSignIn = true },
                )
            }
        }

        // Delete downloaded subtitles (available regardless of sign-in state — cached files are local).
        Spacer(Modifier.height(6.dp))
        Row2(
            icon = OwnTVIcon.SUBTITLE, title = "Delete subtitles",
            desc = "Remove subtitles you've downloaded for movies and series.",
            chevron = true,
            modifier = Modifier.focusRequester(deleteFocus),
            onClick = { showDeleteSubs = true },
        )

        // Push the credit block clearly below the actions, toward the bottom of the panel.
        // (Can't use weight() here — the column is verticalScroll'ed, so height is unbounded.)
        Spacer(Modifier.height(64.dp))
        // OpenSubtitles attribution — logo + line, mirroring the TMDB credit in Metadata settings.
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(tv.own.owntv.R.drawable.ic_opensubtitles_logo),
            contentDescription = "OpenSubtitles",
            modifier = Modifier.padding(start = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This product uses the OpenSubtitles API but is not endorsed or certified by OpenSubtitles.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
    }

    if (showSignIn) {
        OpenSubtitlesSignInDialog(
            onSubmit = { user, pass, stay ->
                showSignIn = false
                vm.signIn(user, pass, stay)
            },
            onDismiss = { showSignIn = false },
        )
    }

    error?.let { message ->
        ErrorDialog(message = message, onDismiss = { vm.dismissError() })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = OwnTVTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

/** Username + password + "Stay signed in" (review R5). TV keyboard comes from OwnTVTextField.
 *  Internal: also reused by the player's subtitle-search overlay for in-place sign-in (review R1). */
@Composable
internal fun OpenSubtitlesSignInDialog(onSubmit: (String, String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var staySignedIn by remember { mutableStateOf(true) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    BackHandler { onDismiss() }
    PopupFontTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.dialogPanel(width = 480.dp, padding = 28.dp)) {
                Text("Sign in to OpenSubtitles", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Use your opensubtitles.com account. Creating one is free.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OwnTVTextField(
                    value = username, onValueChange = { username = it },
                    label = "Username", modifier = Modifier.fillMaxWidth(), focusRequester = fieldFocus,
                )
                Spacer(Modifier.height(10.dp))
                OwnTVTextField(
                    value = password, onValueChange = { password = it },
                    label = "Password", isPassword = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Row2(
                    icon = OwnTVIcon.SUBTITLE, title = "Stay signed in",
                    desc = "Keep the login on this device so it can reconnect automatically when the " +
                        "session expires. Turn off to store only a temporary session.",
                    chip = if (staySignedIn) "On" else "Off", primaryChip = staySignedIn,
                    onClick = { staySignedIn = !staySignedIn },
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    OwnTVButton("Sign in", onClick = { onSubmit(username.trim(), password, staySignedIn) })
                }
            }
        }
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    val colors = OwnTVTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onDismiss() }
    PopupFontTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.dialogPanel(width = 420.dp, padding = 24.dp)) {
                Text("OpenSubtitles", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(10.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.focusRequester(focus))
                }
            }
        }
    }
}
