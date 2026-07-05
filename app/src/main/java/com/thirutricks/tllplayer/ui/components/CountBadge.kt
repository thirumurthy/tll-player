package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Small pill showing a count (e.g. total channels, favorites). Locale-grouped numbers per the
 * plan's count requirements. Used on headers and rail items.
 */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    accent: Boolean = true,
) {
    val colors = OwnTVTheme.colors
    val bg = if (accent) colors.accent.copy(alpha = 0.16f) else colors.card
    val fg = if (accent) colors.accent else colors.textSecondary
    Text(
        text = formatCount(count),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** 12,430 — grouped with thousands separators for readability at 10 feet. */
fun formatCount(count: Int): String = "%,d".format(count)
