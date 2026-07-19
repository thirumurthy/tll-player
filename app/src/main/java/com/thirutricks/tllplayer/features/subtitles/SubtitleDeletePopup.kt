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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.dao.LinkedSubtitle
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.dialogPanel
import com.thirutricks.tllplayer.ui.components.trapAllFocusExit
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme
import com.thirutricks.tllplayer.ui.theme.PopupFontTheme

/**
 * Per-item "Delete subtitles" popup opened from a movie/episode long-press (subtitle plan §11).
 * Lists that item's downloaded subtitles; tapping one deletes it individually. Shares the app's Lora
 * popup styling. [items] is re-supplied by the caller after each delete (empty → the caller closes).
 */
@Composable
fun SubtitleDeletePopup(
    contentTitle: String,
    items: List<LinkedSubtitle>,
    onDelete: (LinkedSubtitle) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val firstFocus = remember { FocusRequester() }
    // Re-run after each delete: the focused row is disposed with the deletion, so land on the first
    // remaining subtitle (when none remain, the caller closes the popup).
    LaunchedEffect(items.size) {
        androidx.compose.runtime.withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }
    BackHandler { onDismiss() }
    PopupFontTheme {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.dialogPanel(width = 480.dp, padding = 24.dp)) {
                Text("Delete OpenSub subtitles", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(contentTitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(14.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.cacheId }) { item ->
                        FocusableSurface(
                            onClick = { onDelete(item) },
                            modifier = if (item == items.first()) Modifier.fillMaxWidth().focusRequester(firstFocus) else Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { _ ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.languageName ?: item.language ?: "Subtitle",
                                        style = MaterialTheme.typography.titleSmall, color = colors.onSurface, fontWeight = FontWeight.SemiBold,
                                    )
                                    item.releaseName?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                                    }
                                }
                                Text("Delete", style = MaterialTheme.typography.labelMedium, color = colors.favorite, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                }
            }
        }
    }
}
