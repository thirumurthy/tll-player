package com.thirutricks.tllplayer.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.parser.XtEpgEntry
import com.thirutricks.tllplayer.features.live.EpgNowNext
import com.thirutricks.tllplayer.ui.format.rememberSystemTimeFormatter
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Guide card for the PLAYING live channel — Before / Now playing / Next — shown on the right edge
 * whenever the player controls are visible (part of the HUD, like the top-bar channel card; not
 * tied to the channel-list overlay). Informational only: never focusable, and renders NOTHING when
 * the channel has no guide data (a permanent "no info" card would just be noise on every unhide).
 */
@Composable
fun LiveEpgCard(epg: EpgNowNext?, modifier: Modifier = Modifier) {
    if (epg == null || (epg.now == null && epg.next == null && epg.previous == null)) return
    val formatTime = rememberSystemTimeFormatter()
    Column(
        modifier = modifier
            .width(340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        epg.previous?.let { EpgSlot("Before", it, formatTime, emphasized = false) }
        epg.now?.let { EpgSlot("Now playing", it, formatTime, emphasized = true) }
        epg.next?.let { EpgSlot("Next", it, formatTime, emphasized = false) }
    }
}

@Composable
private fun EpgSlot(
    label: String,
    entry: XtEpgEntry,
    formatTime: (Long) -> String,
    emphasized: Boolean,
) {
    val colors = OwnTVTheme.colors
    Column {
        Text(
            "$label · ${formatTime(entry.startMs)} – ${formatTime(entry.stopMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasized) colors.primary else colors.onSurfaceVariant,
        )
        Text(
            entry.title,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = if (emphasized) Color.White else colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (emphasized && !entry.description.isNullOrBlank()) {
            Text(
                entry.description.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
