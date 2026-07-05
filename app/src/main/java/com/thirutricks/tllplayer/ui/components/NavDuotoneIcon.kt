package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.thirutricks.tllplayer.features.shell.MainSection

/**
 * Monochrome **duotone** nav icon: one colour, two opacities — a soft 30% "body" + crisp full-opacity key
 * shapes. Single-colour so it tints with the theme (muted when idle, accent when selected) like the old nav
 * icons, but more refined. 100-unit design grid. Drawn still — no per-frame animation on the nav.
 */
@Composable
fun NavDuotoneIcon(
    section: MainSection,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val s = size.minDimension / 100f
        val bg = color.copy(alpha = 0.30f)
        val soft = color.copy(alpha = 0.45f)
        val stroke = Stroke(width = 7f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val softStroke = Stroke(width = 6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        fun poly(close: Boolean, vararg pts: Float): Path = Path().apply {
            var i = 0
            while (i < pts.size) { if (i == 0) moveTo(pts[0] * s, pts[1] * s) else lineTo(pts[i] * s, pts[i + 1] * s); i += 2 }
            if (close) close()
        }
        fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float, c: Color, st: Stroke?) =
            drawRoundRect(c, topLeft = o(x, y), size = Size(w * s, h * s), cornerRadius = CornerRadius(r * s, r * s),
                style = st ?: androidx.compose.ui.graphics.drawscope.Fill)

        when (section) {
            MainSection.HOME -> {
                drawPath(poly(true, 50f, 20f, 84f, 48f, 84f, 84f, 16f, 84f, 16f, 48f), bg)
                drawPath(poly(false, 16f, 50f, 50f, 22f, 84f, 50f), color, style = stroke)
                drawPath(poly(true, 42f, 84f, 42f, 62f, 58f, 62f, 58f, 84f), color)
            }
            MainSection.LIVE_TV -> {
                rrect(18f, 32f, 64f, 42f, 7f, bg, null)
                rrect(18f, 32f, 64f, 42f, 7f, color, stroke)
                drawPath(poly(true, 44f, 44f, 44f, 62f, 62f, 53f), color)
                drawArc(soft, 327f, 66f, useCenter = false, topLeft = o(22f, 10f), size = Size(52f * s, 52f * s), style = softStroke)
            }
            MainSection.MOVIES -> {
                drawCircle(bg, radius = 26f * s, center = o(50f, 46f))
                drawCircle(color, radius = 26f * s, center = o(50f, 46f), style = stroke)
                drawCircle(color, radius = 6f * s, center = o(50f, 46f))
                drawCircle(color, radius = 4.5f * s, center = o(50f, 28f))
                drawCircle(color, radius = 4.5f * s, center = o(50f, 64f))
                drawCircle(color, radius = 4.5f * s, center = o(32f, 46f))
                drawCircle(color, radius = 4.5f * s, center = o(68f, 46f))
                rrect(26f, 78f, 48f, 7f, 3.5f, color, null)
            }
            MainSection.SERIES -> {
                rotate(-7f, o(50f, 50f)) { drawRoundRect(bg, topLeft = o(26f, 22f), size = Size(48f * s, 56f * s), cornerRadius = CornerRadius(7f * s, 7f * s)) }
                rotate(3f, o(50f, 50f)) { drawRoundRect(color.copy(alpha = 0.18f), topLeft = o(26f, 22f), size = Size(48f * s, 56f * s), cornerRadius = CornerRadius(7f * s, 7f * s)) }
                rrect(28f, 24f, 44f, 52f, 6f, color, stroke)
                drawPath(poly(true, 44f, 40f, 44f, 60f, 61f, 50f), color)
            }
            MainSection.SEARCH -> {
                drawCircle(bg, radius = 22f * s, center = o(42f, 42f))
                drawCircle(color, radius = 22f * s, center = o(42f, 42f), style = stroke)
                drawLine(color, o(58f, 58f), o(80f, 80f), strokeWidth = 7f * s, cap = StrokeCap.Round)
            }
            MainSection.DOWNLOADS -> {
                drawPath(poly(true, 44f, 18f, 56f, 18f, 56f, 50f, 68f, 50f, 50f, 70f, 32f, 50f, 44f, 50f), color)
                drawPath(poly(false, 26f, 76f, 26f, 84f, 74f, 84f, 74f, 76f), soft, style = softStroke)
            }
            MainSection.EPG -> {
                rrect(16f, 22f, 68f, 9f, 4.5f, bg, null)
                rrect(16f, 37f, 68f, 9f, 4.5f, bg, null)
                rrect(16f, 52f, 68f, 9f, 4.5f, bg, null)
                rrect(16f, 22f, 22f, 9f, 4.5f, color, null)
                rrect(44f, 22f, 14f, 9f, 4.5f, color, null)
                rrect(16f, 37f, 34f, 9f, 4.5f, color, null)
                rrect(16f, 52f, 12f, 9f, 4.5f, color, null)
                rrect(34f, 52f, 30f, 9f, 4.5f, color, null)
            }
            MainSection.SETTINGS -> {
                val gear = Path().apply {
                    fillType = PathFillType.EvenOdd
                    moveTo(50f * s, 16f * s); lineTo(55f * s, 30f * s); lineTo(69f * s, 26f * s); lineTo(65f * s, 40f * s)
                    lineTo(79f * s, 49f * s); lineTo(65f * s, 58f * s); lineTo(69f * s, 72f * s); lineTo(55f * s, 68f * s)
                    lineTo(50f * s, 82f * s); lineTo(45f * s, 68f * s); lineTo(31f * s, 72f * s); lineTo(35f * s, 58f * s)
                    lineTo(21f * s, 49f * s); lineTo(35f * s, 40f * s); lineTo(31f * s, 26f * s); lineTo(45f * s, 30f * s); close()
                    addOval(Rect(41f * s, 40f * s, 59f * s, 58f * s)) // centre hole (even-odd)
                }
                drawPath(gear, color)
            }
        }
    }
}
