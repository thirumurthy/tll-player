package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/** A circular progress ring (0..1). Used for inline download progress. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = OwnTVTheme.colors.primary,
    trackColor: Color = OwnTVTheme.colors.surfaceContainerHighest,
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.13f
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
        val arc = Size(d, d)
        drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
