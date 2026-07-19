package com.thirutricks.tllplayer.features.subtitles

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVSpinner
import com.thirutricks.tllplayer.ui.components.OwnTVTextField
import com.thirutricks.tllplayer.ui.components.dialogPanel
import com.thirutricks.tllplayer.ui.components.trapAllFocusExit
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme
import com.thirutricks.tllplayer.ui.theme.PopupFontTheme

/**
 * OpenSubtitles search overlay, opened from the player HUD's ADD SUBTITLES entry (subtitle plan §6).
 * Shows results for the playing movie/episode; selecting one downloads, attaches, and remembers it.
 */
@Composable
fun SubtitleSearchScreen(
    onDismiss: () -> Unit,
    // §14/R1 escape hatch: close the search and open the local subtitle-file picker instead.
    onSelectLocalFile: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val vm: SubtitleSearchViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val applying by vm.applying.collectAsStateWithLifecycle()
    val quotaNote by vm.quotaNote.collectAsStateWithLifecycle()
    val signInError by vm.signInError.collectAsStateWithLifecycle()

    // Fresh search each time the overlay opens (the ViewModel is reused across opens), and close on
    // the one-shot "applied" event.
    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(Unit) { vm.applied.collect { onDismiss() } }

    var editing by remember { mutableStateOf(false) }
    var showSignIn by remember { mutableStateOf(false) }
    BackHandler { if (editing) editing = false else onDismiss() }

    PopupFontTheme {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .trapAllFocusExit()
                .focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.dialogPanel(width = 620.dp, padding = 24.dp)) {
                Text("Search OpenSubtitles", style = MaterialTheme.typography.titleLarge, color = OwnTVTheme.colors.onSurface)
                Spacer(Modifier.height(4.dp))
                quotaNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = OwnTVTheme.colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))

                if (editing) {
                    EditSearchField(
                        initial = vm.initialQuery,
                        onSubmit = { q -> editing = false; vm.editSearch(q) },
                        onCancel = { editing = false },
                    )
                } else {
                    when (val s = state) {
                        // R1 "account needed" / §14 "session expired" dialog — friendly explanation,
                        // in-place sign-in, and the no-account local-file path always open.
                        is SubtitleSearchViewModel.UiState.SignedOut -> Message(
                            if (s.sessionExpired) {
                                "Your OpenSubtitles session has expired. Sign in again to keep searching " +
                                    "and downloading subtitles."
                            } else {
                                "OpenSubtitles account needed\n\nOpenSubtitles is a free, open community " +
                                    "subtitle service — anyone can create a free account at opensubtitles.com. " +
                                    "Sign in with that account to search and download subtitles for movies " +
                                    "and series episodes.\n\nOr skip for now and pick a local subtitle file instead."
                            },
                            primary = if (s.sessionExpired) "Sign in again" else "Add account",
                            onPrimary = { showSignIn = true },
                            secondary = onSelectLocalFile?.let { "Select local file" },
                            onSecondary = onSelectLocalFile,
                            tertiary = "Skip", onTertiary = onDismiss,
                        )
                        SubtitleSearchViewModel.UiState.Loading ->
                            Centered { OwnTVSpinner(); Spacer(Modifier.height(12.dp)); Text("Working…", color = OwnTVTheme.colors.onSurfaceVariant) }
                        SubtitleSearchViewModel.UiState.Empty -> Message(
                            "No matching subtitles were found.",
                            primary = "Edit search", onPrimary = { editing = true },
                            secondary = "Show all languages", onSecondary = vm::showAllLanguages,
                            tertiary = "Close", onTertiary = onDismiss,
                        )
                        is SubtitleSearchViewModel.UiState.Error -> Message(
                            s.message,
                            primary = "Try again", onPrimary = vm::retry,
                            secondary = if (s.offerLocalFile && onSelectLocalFile != null) "Select local file" else null,
                            onSecondary = onSelectLocalFile,
                            tertiary = "Close", onTertiary = onDismiss,
                        )
                        is SubtitleSearchViewModel.UiState.Results -> ResultsList(
                            results = s.results,
                            applyingFileId = applying,
                            onSelect = vm::select,
                            onEdit = { editing = true },
                            onShowAll = if (!s.showingAllLanguages) vm::showAllLanguages else null,
                            onClose = onDismiss,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    OpenSubtitlesAttribution()
                }
            }
        }
    }

    // In-place sign-in (review R1): success re-runs the search that started the flow (§5.2).
    if (showSignIn) {
        tv.own.owntv.features.settings.OpenSubtitlesSignInDialog(
            onSubmit = { user, pass, stay ->
                showSignIn = false
                vm.signIn(user, pass, stay)
            },
            onDismiss = { showSignIn = false },
        )
    }
    // §14 "Sign-in failed": [Try again] reopens the sign-in dialog, [Cancel] returns to the message.
    signInError?.let { err ->
        SignInFailedDialog(
            message = err,
            onTryAgain = { vm.dismissSignInError(); showSignIn = true },
            onCancel = vm::dismissSignInError,
        )
    }
}

@Composable
private fun SignInFailedDialog(message: String, onTryAgain: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onCancel() }
    PopupFontTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.dialogPanel(width = 460.dp, padding = 24.dp)) {
                Text("OpenSubtitles", style = MaterialTheme.typography.titleLarge, color = OwnTVTheme.colors.onSurface)
                Spacer(Modifier.height(10.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OwnTVButton("Cancel", onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
                    Spacer(Modifier.weight(1f))
                    OwnTVButton("Try again", onClick = onTryAgain, modifier = Modifier.focusRequester(focus))
                }
            }
        }
    }
}

/** Logo + credit line, mirroring the TMDB attribution in Metadata settings. */
@Composable
private fun OpenSubtitlesAttribution() {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(tv.own.owntv.R.drawable.ic_opensubtitles_logo),
        contentDescription = "OpenSubtitles",
        modifier = Modifier.height(28.dp),
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "This product uses the OpenSubtitles API but is not endorsed or certified by OpenSubtitles.",
        style = MaterialTheme.typography.bodySmall,
        color = OwnTVTheme.colors.onSurfaceVariant,
    )
}

@Composable
private fun ResultsList(
    results: List<SubtitleSearchViewModel.Result>,
    applyingFileId: Long?,
    onSelect: (SubtitleSearchViewModel.Result) -> Unit,
    onEdit: () -> Unit,
    onShowAll: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Column {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.fileId }) { r ->
                val isApplying = applyingFileId == r.fileId
                FocusableSurface(
                    onClick = { onSelect(r) },
                    enabled = applyingFileId == null,
                    modifier = if (r == results.first()) Modifier.fillMaxWidth().focusRequester(firstFocus) else Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) { _ ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                r.languageName ?: r.language ?: "Subtitle",
                                style = MaterialTheme.typography.titleSmall, color = colors.onSurface, fontWeight = FontWeight.SemiBold,
                            )
                            Text(r.releaseName, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                            val tags = buildList {
                                if (r.fromTrusted) add("Trusted")
                                if (r.hearingImpaired) add("SDH")
                                if (r.aiTranslated) add("AI")
                                if (r.downloads > 0) add("${r.downloads} downloads")
                            }
                            if (tags.isNotEmpty()) {
                                Text(tags.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = colors.primary)
                            }
                        }
                        if (isApplying) OwnTVSpinner()
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton("Edit search", onClick = onEdit, style = OwnTVButtonStyle.SECONDARY)
            onShowAll?.let { OwnTVButton("All languages", onClick = it, style = OwnTVButtonStyle.SECONDARY) }
            Spacer(Modifier.weight(1f))
            OwnTVButton("Close", onClick = onClose, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

@Composable
private fun EditSearchField(initial: String, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
    Column {
        Text("Edit search", style = MaterialTheme.typography.titleSmall, color = OwnTVTheme.colors.onSurface)
        Spacer(Modifier.height(10.dp))
        OwnTVTextField(value = value, onValueChange = { value = it }, label = "Title", modifier = Modifier.fillMaxWidth(), focusRequester = fieldFocus)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton("Cancel", onClick = onCancel, style = OwnTVButtonStyle.SECONDARY)
            Spacer(Modifier.weight(1f))
            OwnTVButton("Search", onClick = { onSubmit(value.trim()) })
        }
    }
}

@Composable
private fun Message(
    text: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiary: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = OwnTVTheme.colors.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            secondary?.let { OwnTVButton(it, onClick = { onSecondary?.invoke() }, style = OwnTVButtonStyle.SECONDARY) }
            tertiary?.let { OwnTVButton(it, onClick = { onTertiary?.invoke() }, style = OwnTVButtonStyle.SECONDARY) }
            Spacer(Modifier.weight(1f))
            OwnTVButton(primary, onClick = onPrimary, modifier = Modifier.focusRequester(focus))
        }
    }
}

@Composable
private fun Centered(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}
