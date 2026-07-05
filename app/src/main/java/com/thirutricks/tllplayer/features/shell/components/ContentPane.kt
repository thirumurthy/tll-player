package com.thirutricks.tllplayer.features.shell.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.components.EmptyState
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Layer 3 — content list/grid area. Phase 1/2 render the header plus a reusable [EmptyState]; the
 * real Paging list/grid arrives in the media-section phases (7–9).
 *
 * Per the plan's "Total Count Requirements", the count sits on the subtitle line beneath the title
 * as `ABBR (N unit)` — e.g. `UK (50 channels)` — rather than a separate top-right number.
 */
@Composable
fun ContentPane(
    sectionTitle: String,
    categoryName: String,
    categoryAbbr: String,
    countLabel: String,
    emptyIcon: OwnTVIcon,
    emptyMessage: String,
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
    ) {
        Text(
            text = "$sectionTitle / $categoryName",
            style = MaterialTheme.typography.headlineLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.padding(top = 4.dp))
        Text(
            text = "$categoryAbbr ($countLabel)",
            style = MaterialTheme.typography.titleMedium,
            color = colors.accent,
            fontWeight = FontWeight.Bold,
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                icon = emptyIcon,
                title = "Nothing here yet",
                message = emptyMessage,
                actionLabel = "Add a source",
                onAction = onAddSource,
            )
        }
    }
}
