package com.thirutricks.tllplayer.ui.components

import androidx.compose.runtime.Immutable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.DownloadEntity
import com.thirutricks.tllplayer.core.model.DownloadStatus
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/** Display-only download state for the poster-panel strip (no actions — mirrors the Downloads screen). */
@Immutable
data class DownloadStripState(
    val label: String,
    /** 0f..1f when a size is known; null = indeterminate (queued / unknown total). */
    val progress: Float?,
    val isError: Boolean = false,
)

/**
 * Builds a strip state from the download rows that belong to one item (a single movie/episode, or all
 * of a series' episodes). Returns null when nothing is in flight — i.e. no rows, or every row already
 * COMPLETED — so the caller can hide the strip. FAILED rows still surface so the user isn't left guessing.
 */
fun downloadStripFor(rows: List<DownloadEntity>): DownloadStripState? {
    val active = rows.filter { it.status != DownloadStatus.COMPLETED }
    if (active.isEmpty()) return null

    val running = active.filter { it.status == DownloadStatus.RUNNING }
    val paused = active.filter { it.status == DownloadStatus.PAUSED }
    val failed = active.filter { it.status == DownloadStatus.FAILED }
    val queued = active.filter { it.status == DownloadStatus.QUEUED }

    val downloaded = active.sumOf { it.downloadedBytes }
    val total = active.sumOf { it.totalBytes }
    val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
    val many = active.size > 1
    val suffix = if (many) " ${active.size} items" else ""

    return when {
        running.isNotEmpty() -> DownloadStripState("Downloading$suffix", fraction)
        queued.isNotEmpty() && paused.isEmpty() && failed.isEmpty() -> DownloadStripState("Queued$suffix", null)
        paused.isNotEmpty() && running.isEmpty() && failed.isEmpty() -> DownloadStripState("Paused$suffix", fraction)
        failed.isNotEmpty() && running.isEmpty() && queued.isEmpty() && paused.isEmpty() ->
            DownloadStripState(if (many) "${failed.size} downloads failed" else "Download failed", null, isError = true)
        // Mixed states (some queued/paused/failed together) → report the in-progress framing.
        else -> DownloadStripState("Downloading$suffix", fraction)
    }
}

/** A compact, non-focusable status strip: icon + label + a thin progress bar. */
@Composable
fun DownloadStatusStrip(state: DownloadStripState, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val accent = if (state.isError) Color(0xFFEF4444) else colors.primary
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OwnTVIcon(OwnTVIcon.DOWNLOADS, tint = accent, modifier = Modifier.size(16.dp))
            Text(state.label, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            state.progress?.let {
                Text("${(it * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!state.isError) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.surfaceContainerLowest)) {
                // Determinate when a size is known; otherwise a modest fixed sliver so the user still
                // sees an active bar for a queued item.
                Box(Modifier.fillMaxWidth(state.progress ?: 0.15f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            }
        }
    }
}
