package com.thirutricks.tllplayer.features.settings

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.dao.LinkedSubtitle
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.roundedPanel
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Settings → OpenSubtitles account → Delete subtitles (subtitle plan §11). A Movies/Series toggle at
 * the top, the downloaded subtitles for the selected section below (tap to delete one), and a
 * Delete all action (all / all movies / all series).
 */
@Composable
fun DeleteSubtitlesScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val vm: DeleteSubtitlesViewModel = koinViewModel()
    val section by vm.section.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val movieCount by vm.movieCount.collectAsStateWithLifecycle()
    val seriesCount by vm.seriesCount.collectAsStateWithLifecycle()

    var confirmDelete by remember { mutableStateOf<LinkedSubtitle?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    // Land D-pad focus inside the screen (Movies/Series toggle) — without this it opens unfocused.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
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
        // Title + Delete all (top-right).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Header("Delete subtitles", onBack) }
            if (movieCount + seriesCount > 0) {
                OwnTVButton("Delete all", onClick = { showDeleteAll = true }, style = OwnTVButtonStyle.SECONDARY)
            }
        }
        Spacer(Modifier.height(12.dp))

        // Movies / Series toggle (like Customize's section switch).
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DeleteSubtitlesViewModel.Section.entries.forEach { s ->
                val count = if (s == DeleteSubtitlesViewModel.Section.MOVIES) movieCount else seriesCount
                OwnTVButton(
                    "${s.label} ($count)",
                    onClick = { vm.selectSection(s) },
                    style = if (s == section) OwnTVButtonStyle.PRIMARY else OwnTVButtonStyle.SECONDARY,
                    modifier = if (s == DeleteSubtitlesViewModel.Section.MOVIES) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                "No downloaded ${section.label.lowercase()} subtitles.",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            items.forEach { item ->
                Row2(
                    icon = OwnTVIcon.SUBTITLE,
                    title = item.contentTitle,
                    desc = listOfNotNull(item.languageName ?: item.language, item.releaseName).joinToString(" · "),
                    chip = "Delete", primaryChip = false,
                    onClick = { confirmDelete = item },
                )
            }
        }
    }

    confirmDelete?.let { item ->
        ConfirmDialog(
            title = "Delete subtitle",
            message = "Delete the ${item.languageName ?: item.language ?: item.fileName} subtitle for “${item.contentTitle}”? " +
                "It can be downloaded again later.",
            onConfirm = { vm.deleteOne(item); confirmDelete = null },
            onDismiss = { confirmDelete = null },
        )
    }

    if (showDeleteAll) {
        PickerDialog(
            title = "Delete all subtitles",
            options = listOf(
                "ALL" to "Delete all",
                "MOVIES" to "Delete all movies",
                "SERIES" to "Delete all series",
            ),
            selected = "",
            onSelect = { choice ->
                when (choice) {
                    "ALL" -> vm.deleteAll()
                    "MOVIES" -> vm.deleteAllMovies()
                    "SERIES" -> vm.deleteAllSeries()
                }
                showDeleteAll = false
            },
            onDismiss = { showDeleteAll = false },
        )
    }
}
