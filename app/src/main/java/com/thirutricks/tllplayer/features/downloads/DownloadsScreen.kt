package com.thirutricks.tllplayer.features.downloads

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.DownloadEntity
import com.thirutricks.tllplayer.core.model.DownloadStatus
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/** Phase 12 — the Downloads section: offline movies & episodes with progress and playback. */
@Composable
fun DownloadsScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: DownloadsViewModel = koinViewModel()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val lastPlayedId by vm.lastPlayedId.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Returning from the player: scroll to and focus the download you just played.
    LaunchedEffect(restoreFocus, downloads.size) {
        if (!restoreFocus || downloads.isEmpty()) return@LaunchedEffect
        val idx = lastPlayedId?.let { id -> downloads.indexOfFirst { it.id == id } } ?: -1
        if (idx >= 0) {
            runCatching { listState.scrollToItem(idx) }
            kotlinx.coroutines.delay(60)
            runCatching { selFocus.requestFocus() }
        }
        onRestored()
    }

    Column(
        modifier = modifier.fillMaxSize().background(colors.surface)
            // Route spatial D-pad entries to the first download row (entry from the sidebar would
            // otherwise land on whatever row is horizontally aligned). onEnter fires only for
            // directional entry from outside (internal moves don't re-trigger it).
            .focusProperties { onEnter = { runCatching { firstFocus.requestFocus() } } }
            .focusGroup()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
    ) {
        Text("Downloads", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text("Movies & episodes saved for offline playback.", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        if (downloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No downloads yet. Open a movie or episode and press Download.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.focusGroup()) {
                itemsIndexed(downloads, key = { _, d -> d.id }) { index, d ->
                    DownloadRow(
                        download = d,
                        focusModifier = when {
                            d.id == lastPlayedId -> Modifier.focusRequester(selFocus)
                            index == 0 -> Modifier.focusRequester(firstFocus)
                            else -> Modifier
                        },
                        onPlay = { vm.play(d); onFullscreen() },
                        onRetry = { vm.retry(d) },
                        onPause = { vm.pause(d) },
                        onResume = { vm.resume(d) },
                        onDelete = { vm.delete(d) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    download: DownloadEntity,
    onPlay: () -> Unit, onRetry: () -> Unit, onPause: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit,
    focusModifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainerHigh).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp, 78.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest), contentAlignment = Alignment.Center) {
            if (!download.posterUrl.isNullOrBlank()) AsyncImage(model = download.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            else OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(download.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            folderCrumb(download.filePath)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            StatusLine(download)
        }
        Spacer(Modifier.width(12.dp))
        when (download.status) {
            DownloadStatus.COMPLETED -> OwnTVButton("Play", onClick = onPlay, icon = OwnTVIcon.PLAY, modifier = focusModifier)
            DownloadStatus.FAILED -> OwnTVButton("Retry", onClick = onRetry, style = OwnTVButtonStyle.SECONDARY, modifier = focusModifier)
            DownloadStatus.PAUSED -> OwnTVButton("Resume", onClick = onResume, style = OwnTVButtonStyle.SECONDARY, modifier = focusModifier)
            DownloadStatus.RUNNING, DownloadStatus.QUEUED -> OwnTVButton("Pause", onClick = onPause, style = OwnTVButtonStyle.SECONDARY, modifier = focusModifier)
        }
        Spacer(Modifier.width(10.dp))
        OwnTVButton("Delete", onClick = onDelete, style = OwnTVButtonStyle.SECONDARY)
    }
}

/** Shows the folder path of a download, e.g. "Series › Game of Thrones › Season 6". */
private fun folderCrumb(filePath: String?): String? {
    val parts = filePath?.substringBeforeLast('/')?.split('/')?.filter { it.isNotBlank() } ?: return null
    val idx = parts.indexOfLast { it == "Movies" || it == "Series" }
    val rel = if (idx >= 0) parts.subList(idx, parts.size) else parts.takeLast(3)
    return rel.joinToString(" › ").ifBlank { null }
}

@Composable
private fun StatusLine(d: DownloadEntity) {
    val colors = OwnTVTheme.colors
    when (d.status) {
        DownloadStatus.COMPLETED -> Text("Downloaded · ${mb(d.totalBytes)}", style = MaterialTheme.typography.bodySmall, color = colors.primary, fontWeight = FontWeight.SemiBold)
        DownloadStatus.FAILED -> Text("Download failed", style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
        DownloadStatus.QUEUED -> Text("Queued…", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        else -> { // RUNNING / PAUSED
            val frac = if (d.totalBytes > 0) (d.downloadedBytes.toFloat() / d.totalBytes).coerceIn(0f, 1f) else 0f
            Column {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceContainerLowest)) {
                    Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (d.totalBytes > 0) "${(frac * 100).toInt()}% · ${mb(d.downloadedBytes)} / ${mb(d.totalBytes)}" else "Downloading… ${mb(d.downloadedBytes)}",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

private fun mb(bytes: Long): String = if (bytes <= 0) "—" else "%.1f MB".format(bytes / 1_048_576.0)
