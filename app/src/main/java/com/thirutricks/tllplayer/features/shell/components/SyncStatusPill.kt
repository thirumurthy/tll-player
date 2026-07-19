package com.thirutricks.tllplayer.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.sync.EpgActivityTracker
import com.thirutricks.tllplayer.core.sync.SyncActivityTracker
import com.thirutricks.tllplayer.ui.components.OwnTVSpinner
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Unobtrusive "sync running" pill (shell overlay, bottom middle). Reflects BOTH catalog syncs (a
 * backgrounded first import, the movies/series remainder worker, auto refresh — all funnel through
 * SyncManager → [SyncActivityTracker]) AND EPG/guide syncs (manual resync from Settings, auto startup
 * refresh — [EpgSyncWorker] → [EpgActivityTracker]). Semi-transparent, never focusable, renders nothing
 * when no sync is active. The shell hides it during fullscreen playback.
 */
@Composable
fun SyncStatusPill(modifier: Modifier = Modifier) {
    val catalogTracker: SyncActivityTracker = koinInject()
    val epgTracker: EpgActivityTracker = koinInject()
    val activeCatalog by catalogTracker.active.collectAsStateWithLifecycle()
    val activeEpg by epgTracker.active.collectAsStateWithLifecycle()

    val colors = OwnTVTheme.colors

    // Catalog syncs take the primary slot; EPG shows when no catalog sync is running.
    val catalogSync = activeCatalog.values.firstOrNull()
    val epgSync = activeEpg.values.firstOrNull()
    val label = when {
        catalogSync != null -> {
            val count = catalogSync.stage?.totalProcessed ?: 0
            val others = activeCatalog.size - 1
            buildString {
                append("Syncing ")
                append(catalogSync.sourceName)
                if (count > 0) append(" · ").append(String.format(java.util.Locale.getDefault(), "%,d", count)).append(" items")
                if (others > 0) append(" (+$others more)")
                if (epgSync != null) append(" · EPG too")
            }
        }
        epgSync != null -> {
            val others = activeEpg.size - 1
            buildString {
                append("Updating guide · ")
                append(epgSync.sourceName)
                if (epgSync.programmes > 0) {
                    append(" · ").append(String.format(java.util.Locale.getDefault(), "%,d", epgSync.programmes)).append(" programmes")
                }
                if (others > 0) append(" (+$others more)")
            }
        }
        else -> return // nothing running → render nothing
    }

    Row(
        modifier = modifier
            .padding(bottom = 14.dp)
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainerHigh.copy(alpha = 0.72f))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OwnTVSpinner(sizeDp = 14)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
