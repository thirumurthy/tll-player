package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Brand icon set drawn directly with Canvas so we don't depend on which glyphs ship in
 * `material-icons-core` (and to keep the APK lean). All icons are designed on a normalized 24×24
 * grid and scaled to fit. Line style by default; some support [filled].
 */
enum class OwnTVIcon {
    LIVE_TV, MOVIES, SERIES, DOWNLOADS, MENU, STAR, PLAY, SEARCH, HOME, HISTORY,
    PERSON, ADD, SETTINGS, PALETTE, THEME, ZOOM, PLAYLIST, EPG, VIDEO, SHARE, CHEVRON, FAVORITE,
    PAUSE, REWIND, FORWARD, AUDIO, SUBTITLE, SKIP_NEXT, SKIP_PREVIOUS,
    BACK, VOLUME_HIGH, VOLUME_LOW, VOLUME_MUTE, ASPECT, FULLSCREEN, FULLSCREEN_EXIT, PIP, CLOSE,
    SORT,
}

@Composable
fun OwnTVIcon(
    icon: OwnTVIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 24f // scale: 24-unit grid -> px
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val stroke = Stroke(width = 2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (icon) {
            OwnTVIcon.LIVE_TV -> {
                drawRoundRectStroke(p(3f, 8f), p(21f, 21f), 2.5f * s, tint, stroke)
                drawLineStroke(p(8f, 8f), p(12f, 3f), tint, stroke)
                drawLineStroke(p(16f, 8f), p(12f, 3f), tint, stroke)
            }
            OwnTVIcon.MOVIES -> {
                drawRoundRectStroke(p(3f, 9f), p(21f, 20f), 2f * s, tint, stroke)
                drawLineStroke(p(3f, 9f), p(21f, 6.5f), tint, stroke)
                drawLineStroke(p(7.5f, 9f), p(9f, 6.2f), tint, stroke)
                drawLineStroke(p(12f, 9f), p(13.5f, 5.8f), tint, stroke)
                drawLineStroke(p(16.5f, 9f), p(18f, 5.5f), tint, stroke)
            }
            OwnTVIcon.SERIES -> {
                drawRoundRectStroke(p(6f, 4f), p(21f, 14f), 2f * s, tint, stroke)
                drawLineStroke(p(3f, 8f), p(3f, 20f), tint, stroke)
                drawLineStroke(p(3f, 20f), p(18f, 20f), tint, stroke)
            }
            OwnTVIcon.DOWNLOADS -> {
                drawLineStroke(p(12f, 3f), p(12f, 15f), tint, stroke)
                drawLineStroke(p(7f, 10f), p(12f, 15f), tint, stroke)
                drawLineStroke(p(17f, 10f), p(12f, 15f), tint, stroke)
                drawLineStroke(p(5f, 20f), p(19f, 20f), tint, stroke)
            }
            OwnTVIcon.MENU -> {
                drawLineStroke(p(4f, 7f), p(20f, 7f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(20f, 12f), tint, stroke)
                drawLineStroke(p(4f, 17f), p(20f, 17f), tint, stroke)
            }
            OwnTVIcon.SORT -> { // descending bars — classic sort glyph
                drawLineStroke(p(4f, 7f), p(20f, 7f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(14f, 12f), tint, stroke)
                drawLineStroke(p(4f, 17f), p(9f, 17f), tint, stroke)
            }
            OwnTVIcon.HISTORY -> {
                drawCircleStroke(p(12f, 12f), 9f * s, tint, stroke)
                drawLineStroke(p(12f, 7f), p(12f, 12f), tint, stroke)
                drawLineStroke(p(12f, 12f), p(16f, 14f), tint, stroke)
            }
            OwnTVIcon.SEARCH -> {
                drawCircleStroke(p(10.5f, 10.5f), 6.5f * s, tint, stroke)
                drawLineStroke(p(15.5f, 15.5f), p(20f, 20f), tint, stroke)
            }
            OwnTVIcon.HOME -> {
                val roof = Path().apply {
                    moveTo(p(12f, 3f).x, p(12f, 3f).y)
                    lineTo(p(3f, 12f).x, p(3f, 12f).y)
                    lineTo(p(21f, 12f).x, p(21f, 12f).y)
                    close()
                }
                drawPath(roof, tint, style = stroke)
                drawRoundRectStroke(p(5f, 12f), p(19f, 21f), 2f * s, tint, stroke)
                drawLineStroke(p(10f, 21f), p(10f, 15f), tint, stroke)
                drawLineStroke(p(14f, 21f), p(14f, 15f), tint, stroke)
                drawLineStroke(p(10f, 15f), p(14f, 15f), tint, stroke)
            }
            OwnTVIcon.STAR -> {
                val star = starPath(center = p(12f, 12f), outer = 9f * s, inner = 3.7f * s)
                if (filled) drawPath(star, tint, style = Fill) else drawPath(star, tint, style = stroke)
            }
            OwnTVIcon.FAVORITE -> {
                // bookmark
                val bm = Path().apply {
                    moveTo(p(7f, 4f).x, p(7f, 4f).y)
                    lineTo(p(17f, 4f).x, p(17f, 4f).y)
                    lineTo(p(17f, 20f).x, p(17f, 20f).y)
                    lineTo(p(12f, 16f).x, p(12f, 16f).y)
                    lineTo(p(7f, 20f).x, p(7f, 20f).y)
                    close()
                }
                if (filled) drawPath(bm, tint, style = Fill) else drawPath(bm, tint, style = stroke)
            }
            OwnTVIcon.PLAY -> {
                val tri = Path().apply {
                    moveTo(p(8f, 5f).x, p(8f, 5f).y)
                    lineTo(p(19f, 12f).x, p(19f, 12f).y)
                    lineTo(p(8f, 19f).x, p(8f, 19f).y)
                    close()
                }
                if (filled) drawPath(tri, tint, style = Fill) else drawPath(tri, tint, style = stroke)
            }
            OwnTVIcon.PERSON -> {
                drawCircleStroke(p(12f, 8f), 3.6f * s, tint, if (filled) stroke else stroke)
                if (filled) drawCircle(tint, 3.6f * s, p(12f, 8f), style = Fill)
                // shoulders
                drawArc(
                    color = tint,
                    startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = p(5f, 13f), size = Size(14f * s, 14f * s), style = stroke,
                )
            }
            OwnTVIcon.ADD -> {
                drawLineStroke(p(12f, 5f), p(12f, 19f), tint, stroke)
                drawLineStroke(p(5f, 12f), p(19f, 12f), tint, stroke)
            }
            OwnTVIcon.SETTINGS -> {
                // "tune" sliders — clearer than a gear at small sizes
                drawLineStroke(p(4f, 8f), p(20f, 8f), tint, stroke)
                drawLineStroke(p(4f, 16f), p(20f, 16f), tint, stroke)
                drawCircle(tint, 2.6f * s, p(9f, 8f), style = Fill)
                drawCircle(tint, 2.6f * s, p(15f, 16f), style = Fill)
            }
            OwnTVIcon.PALETTE -> {
                drawArc(
                    color = tint, startAngle = 110f, sweepAngle = 320f, useCenter = false,
                    topLeft = p(3f, 3f), size = Size(18f * s, 18f * s), style = stroke,
                )
                drawCircle(tint, 1.3f * s, p(8.5f, 8f), style = Fill)
                drawCircle(tint, 1.3f * s, p(13f, 6.5f), style = Fill)
                drawCircle(tint, 1.3f * s, p(16.5f, 9.5f), style = Fill)
            }
            OwnTVIcon.THEME -> {
                // half-filled circle — classic dark-mode glyph
                drawCircleStroke(p(12f, 12f), 8f * s, tint, stroke)
                drawArc(
                    color = tint,
                    startAngle = -90f, sweepAngle = 180f, useCenter = true,
                    topLeft = p(4f, 4f), size = Size(16f * s, 16f * s), style = Fill,
                )
            }
            OwnTVIcon.ZOOM -> {
                drawRoundRectStroke(p(4f, 5f), p(20f, 19f), 2f * s, tint, stroke)
                drawLineStroke(p(7f, 9f), p(7f, 7f), tint, stroke)
                drawLineStroke(p(7f, 7f), p(9f, 7f), tint, stroke)
                drawLineStroke(p(17f, 15f), p(17f, 17f), tint, stroke)
                drawLineStroke(p(17f, 17f), p(15f, 17f), tint, stroke)
            }
            OwnTVIcon.PLAYLIST -> {
                drawLineStroke(p(4f, 7f), p(16f, 7f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(16f, 12f), tint, stroke)
                drawLineStroke(p(4f, 17f), p(11f, 17f), tint, stroke)
                val tri = Path().apply {
                    moveTo(p(15f, 14f).x, p(15f, 14f).y)
                    lineTo(p(21f, 17f).x, p(21f, 17f).y)
                    lineTo(p(15f, 20f).x, p(15f, 20f).y)
                    close()
                }
                drawPath(tri, tint, style = Fill)
            }
            OwnTVIcon.EPG -> {
                drawRoundRectStroke(p(3f, 4f), p(21f, 20f), 2f * s, tint, stroke)
                drawLineStroke(p(3f, 9f), p(21f, 9f), tint, stroke)
                drawLineStroke(p(9f, 9f), p(9f, 20f), tint, stroke)
                drawLineStroke(p(15f, 9f), p(15f, 20f), tint, stroke)
            }
            OwnTVIcon.VIDEO -> {
                drawRoundRectStroke(p(3f, 6f), p(21f, 18f), 2.5f * s, tint, stroke)
                val tri = Path().apply {
                    moveTo(p(10f, 9f).x, p(10f, 9f).y)
                    lineTo(p(15f, 12f).x, p(15f, 12f).y)
                    lineTo(p(10f, 15f).x, p(10f, 15f).y)
                    close()
                }
                drawPath(tri, tint, style = Fill)
            }
            OwnTVIcon.SHARE -> {
                drawCircleStroke(p(6f, 12f), 2.4f * s, tint, stroke)
                drawCircleStroke(p(18f, 6f), 2.4f * s, tint, stroke)
                drawCircleStroke(p(18f, 18f), 2.4f * s, tint, stroke)
                drawLineStroke(p(8f, 11f), p(16f, 7f), tint, stroke)
                drawLineStroke(p(8f, 13f), p(16f, 17f), tint, stroke)
            }
            OwnTVIcon.CHEVRON -> {
                drawLineStroke(p(9f, 5f), p(16f, 12f), tint, stroke)
                drawLineStroke(p(16f, 12f), p(9f, 19f), tint, stroke)
            }
            OwnTVIcon.PAUSE -> {
                drawRect(tint, topLeft = p(8f, 5f), size = Size(2.6f * s, 14f * s))
                drawRect(tint, topLeft = p(13.4f, 5f), size = Size(2.6f * s, 14f * s))
            }
            OwnTVIcon.REWIND -> {
                drawPath(triangle(p(11f, 6f), p(4f, 12f), p(11f, 18f)), tint, style = Fill)
                drawPath(triangle(p(20f, 6f), p(13f, 12f), p(20f, 18f)), tint, style = Fill)
            }
            OwnTVIcon.FORWARD -> {
                drawPath(triangle(p(4f, 6f), p(11f, 12f), p(4f, 18f)), tint, style = Fill)
                drawPath(triangle(p(13f, 6f), p(20f, 12f), p(13f, 18f)), tint, style = Fill)
            }
            OwnTVIcon.SKIP_NEXT -> {
                // play-to-bar: ▶|
                drawPath(triangle(p(5f, 6f), p(14f, 12f), p(5f, 18f)), tint, style = Fill)
                drawRect(tint, topLeft = p(15.6f, 6f), size = Size(2.6f * s, 12f * s))
            }
            OwnTVIcon.SKIP_PREVIOUS -> {
                // bar-to-play: |◀
                drawRect(tint, topLeft = p(5.8f, 6f), size = Size(2.6f * s, 12f * s))
                drawPath(triangle(p(19f, 6f), p(10f, 12f), p(19f, 18f)), tint, style = Fill)
            }
            OwnTVIcon.AUDIO -> {
                // Music note (audio track) — clearly distinct from the speaker/volume icon.
                drawCircle(tint, radius = 3f * s, center = p(8.5f, 17.5f))    // filled note head
                drawLineStroke(p(11.5f, 17.5f), p(11.5f, 5f), tint, stroke)   // stem
                drawLineStroke(p(11.5f, 5f), p(16.5f, 7f), tint, stroke)      // upper flag
                drawLineStroke(p(11.5f, 8.5f), p(16.5f, 10.5f), tint, stroke) // lower flag
            }
            OwnTVIcon.SUBTITLE -> {
                drawRoundRectStroke(p(3f, 5f), p(21f, 19f), 2.5f * s, tint, stroke)
                drawLineStroke(p(6f, 14f), p(11f, 14f), tint, stroke)
                drawLineStroke(p(13f, 14f), p(18f, 14f), tint, stroke)
            }
            OwnTVIcon.BACK -> {
                drawLineStroke(p(20f, 12f), p(4f, 12f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(10f, 6f), tint, stroke)
                drawLineStroke(p(4f, 12f), p(10f, 18f), tint, stroke)
            }
            OwnTVIcon.VOLUME_HIGH -> {
                drawPath(speaker(::p), tint, style = Fill)
                drawArc(tint, -52f, 104f, false, topLeft = p(11.5f, 8.5f), size = Size(5f * s, 7f * s), style = stroke)
                drawArc(tint, -52f, 104f, false, topLeft = p(12.5f, 6f), size = Size(8f * s, 12f * s), style = stroke)
            }
            OwnTVIcon.VOLUME_LOW -> {
                drawPath(speaker(::p), tint, style = Fill)
                drawArc(tint, -52f, 104f, false, topLeft = p(11.5f, 8.5f), size = Size(5f * s, 7f * s), style = stroke)
            }
            OwnTVIcon.VOLUME_MUTE -> {
                drawPath(speaker(::p), tint, style = Fill)
                drawLineStroke(p(14f, 9f), p(20f, 15f), tint, stroke)
                drawLineStroke(p(20f, 9f), p(14f, 15f), tint, stroke)
            }
            OwnTVIcon.ASPECT -> {
                drawRoundRectStroke(p(3f, 5f), p(21f, 19f), 2.5f * s, tint, stroke)
                drawLineStroke(p(7f, 11f), p(7f, 9f), tint, stroke)
                drawLineStroke(p(7f, 9f), p(9f, 9f), tint, stroke)
                drawLineStroke(p(17f, 13f), p(17f, 15f), tint, stroke)
                drawLineStroke(p(17f, 15f), p(15f, 15f), tint, stroke)
            }
            OwnTVIcon.FULLSCREEN -> {
                drawLineStroke(p(4f, 9f), p(4f, 4f), tint, stroke); drawLineStroke(p(4f, 4f), p(9f, 4f), tint, stroke)
                drawLineStroke(p(20f, 9f), p(20f, 4f), tint, stroke); drawLineStroke(p(20f, 4f), p(15f, 4f), tint, stroke)
                drawLineStroke(p(4f, 15f), p(4f, 20f), tint, stroke); drawLineStroke(p(4f, 20f), p(9f, 20f), tint, stroke)
                drawLineStroke(p(20f, 15f), p(20f, 20f), tint, stroke); drawLineStroke(p(20f, 20f), p(15f, 20f), tint, stroke)
            }
            OwnTVIcon.FULLSCREEN_EXIT -> {
                drawLineStroke(p(9f, 4f), p(9f, 9f), tint, stroke); drawLineStroke(p(9f, 9f), p(4f, 9f), tint, stroke)
                drawLineStroke(p(15f, 4f), p(15f, 9f), tint, stroke); drawLineStroke(p(15f, 9f), p(20f, 9f), tint, stroke)
                drawLineStroke(p(9f, 20f), p(9f, 15f), tint, stroke); drawLineStroke(p(9f, 15f), p(4f, 15f), tint, stroke)
                drawLineStroke(p(15f, 20f), p(15f, 15f), tint, stroke); drawLineStroke(p(15f, 15f), p(20f, 15f), tint, stroke)
            }
            OwnTVIcon.PIP -> {
                drawRoundRectStroke(p(3f, 5f), p(21f, 19f), 2.5f * s, tint, stroke)
                drawRect(tint, topLeft = p(12.5f, 12f), size = Size(6.5f * s, 5f * s))
            }
            OwnTVIcon.CLOSE -> {
                drawLineStroke(p(6f, 6f), p(18f, 18f), tint, stroke)
                drawLineStroke(p(18f, 6f), p(6f, 18f), tint, stroke)
            }
        }
    }
}

private fun triangle(a: Offset, b: Offset, c: Offset): Path = Path().apply {
    moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
}

/** Speaker body (left part of the volume glyphs), drawn on the shared 24-grid via [p]. */
private fun speaker(p: (Float, Float) -> Offset): Path = Path().apply {
    moveTo(p(3f, 9f).x, p(3f, 9f).y)
    lineTo(p(7f, 9f).x, p(7f, 9f).y)
    lineTo(p(11f, 5f).x, p(11f, 5f).y)
    lineTo(p(11f, 19f).x, p(11f, 19f).y)
    lineTo(p(7f, 15f).x, p(7f, 15f).y)
    lineTo(p(3f, 15f).x, p(3f, 15f).y)
    close()
}

private fun DrawScope.drawLineStroke(a: Offset, b: Offset, color: Color, stroke: Stroke) {
    drawLine(color, a, b, strokeWidth = stroke.width, cap = stroke.cap)
}

private fun DrawScope.drawRoundRectStroke(topLeft: Offset, bottomRight: Offset, radius: Float, color: Color, stroke: Stroke) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = stroke,
    )
}

private fun DrawScope.drawCircleStroke(center: Offset, radius: Float, color: Color, stroke: Stroke) {
    drawCircle(color = color, radius = radius, center = center, style = stroke)
}

private fun starPath(center: Offset, outer: Float, inner: Float): Path {
    val path = Path()
    val points = 5
    for (i in 0 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        val angle = Math.PI / points * i - Math.PI / 2
        val x = center.x + (r * Math.cos(angle)).toFloat()
        val y = center.y + (r * Math.sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
