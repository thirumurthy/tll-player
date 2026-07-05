package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill

/**
 * An object-themed avatar drawn entirely with Canvas — no image assets, fully scalable. There are
 * exactly [OwnTVAvatars.COUNT] presets (lightning, sword, tree, car, football, rocket, crown, heart,
 * music, gamepad); pass an id and the matching icon-on-color tile is drawn deterministically.
 */
object OwnTVAvatars {
    const val COUNT = 10
}

@Composable
fun OwnTVAvatar(avatarId: Int, modifier: Modifier = Modifier) {
    val i = ((avatarId % OwnTVAvatars.COUNT) + OwnTVAvatars.COUNT) % OwnTVAvatars.COUNT
    Canvas(modifier = modifier) { drawAvatar(i) }
}

private val BG = listOf(
    Color(0xFFF59E0B), // 0 lightning — amber
    Color(0xFF64748B), // 1 sword — slate
    Color(0xFF22C55E), // 2 tree — green
    Color(0xFFEF4444), // 3 car — red
    Color(0xFF059669), // 4 football — emerald
    Color(0xFF6366F1), // 5 rocket — indigo
    Color(0xFFD97706), // 6 crown — gold
    Color(0xFFEC4899), // 7 heart — pink
    Color(0xFF8B5CF6), // 8 music — violet
    Color(0xFF06B6D4), // 9 gamepad — cyan
)

private fun DrawScope.drawAvatar(i: Int) {
    val s = size.minDimension
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    val w = Color.White
    val ink = Color(0xFF10181C)
    val bg = BG[i]

    drawRoundRect(bg, size = Size(s, s), cornerRadius = CornerRadius(s * 0.30f))

    when (i) {
        0 -> { // Lightning bolt
            drawPath(poly(listOf(p(0.57f, 0.13f), p(0.31f, 0.55f), p(0.47f, 0.55f), p(0.42f, 0.87f), p(0.71f, 0.41f), p(0.53f, 0.41f), p(0.61f, 0.13f))), w, style = Fill)
        }
        1 -> { // Sword
            drawPath(poly(listOf(p(0.5f, 0.12f), p(0.57f, 0.22f), p(0.57f, 0.60f), p(0.43f, 0.60f), p(0.43f, 0.22f))), w, style = Fill)
            drawRoundRect(w, topLeft = p(0.33f, 0.59f), size = Size(0.34f * s, 0.06f * s), cornerRadius = CornerRadius(0.02f * s))
            drawRoundRect(w, topLeft = p(0.455f, 0.65f), size = Size(0.09f * s, 0.18f * s), cornerRadius = CornerRadius(0.02f * s))
            drawCircle(w, 0.045f * s, p(0.5f, 0.86f))
        }
        2 -> { // Tree
            drawRoundRect(w, topLeft = p(0.45f, 0.54f), size = Size(0.10f * s, 0.31f * s), cornerRadius = CornerRadius(0.02f * s))
            drawCircle(w, 0.20f * s, p(0.5f, 0.38f))
            drawCircle(w, 0.145f * s, p(0.33f, 0.47f))
            drawCircle(w, 0.145f * s, p(0.67f, 0.47f))
        }
        3 -> { // Car
            drawRoundRect(w, topLeft = p(0.32f, 0.35f), size = Size(0.36f * s, 0.18f * s), cornerRadius = CornerRadius(0.05f * s))
            drawRoundRect(w, topLeft = p(0.15f, 0.49f), size = Size(0.70f * s, 0.16f * s), cornerRadius = CornerRadius(0.07f * s))
            drawCircle(ink, 0.075f * s, p(0.34f, 0.66f)); drawCircle(w, 0.034f * s, p(0.34f, 0.66f))
            drawCircle(ink, 0.075f * s, p(0.66f, 0.66f)); drawCircle(w, 0.034f * s, p(0.66f, 0.66f))
        }
        4 -> { // Football (soccer ball)
            val c = p(0.5f, 0.5f)
            drawCircle(w, 0.30f * s, c, style = Fill)
            drawPath(pentagon(c, 0.10f * s), ink, style = Fill)
            for (k in 0 until 5) {
                val a = -Math.PI / 2 + k * 2 * Math.PI / 5
                val v = Offset(c.x + (0.10f * s * Math.cos(a)).toFloat(), c.y + (0.10f * s * Math.sin(a)).toFloat())
                val outer = Offset(c.x + (0.27f * s * Math.cos(a)).toFloat(), c.y + (0.27f * s * Math.sin(a)).toFloat())
                drawLine(ink, v, outer, strokeWidth = 0.035f * s, cap = StrokeCap.Round)
            }
        }
        5 -> { // Rocket
            drawRoundRect(w, topLeft = p(0.40f, 0.32f), size = Size(0.20f * s, 0.34f * s), cornerRadius = CornerRadius(0.10f * s))
            drawPath(poly(listOf(p(0.40f, 0.34f), p(0.50f, 0.14f), p(0.60f, 0.34f))), w, style = Fill)
            drawCircle(bg, 0.06f * s, p(0.50f, 0.41f))
            drawPath(poly(listOf(p(0.40f, 0.54f), p(0.29f, 0.68f), p(0.40f, 0.63f))), w, style = Fill)
            drawPath(poly(listOf(p(0.60f, 0.54f), p(0.71f, 0.68f), p(0.60f, 0.63f))), w, style = Fill)
            drawPath(poly(listOf(p(0.44f, 0.66f), p(0.50f, 0.84f), p(0.56f, 0.66f))), Color(0xFFFBBF24), style = Fill)
        }
        6 -> { // Crown
            drawPath(poly(listOf(p(0.26f, 0.64f), p(0.32f, 0.40f), p(0.42f, 0.56f), p(0.50f, 0.34f), p(0.58f, 0.56f), p(0.68f, 0.40f), p(0.74f, 0.64f))), w, style = Fill)
            drawRoundRect(w, topLeft = p(0.26f, 0.62f), size = Size(0.48f * s, 0.10f * s), cornerRadius = CornerRadius(0.02f * s))
        }
        7 -> { // Heart
            drawCircle(w, 0.155f * s, p(0.37f, 0.41f))
            drawCircle(w, 0.155f * s, p(0.63f, 0.41f))
            drawPath(poly(listOf(p(0.225f, 0.45f), p(0.775f, 0.45f), p(0.50f, 0.82f))), w, style = Fill)
        }
        8 -> { // Music note
            drawCircle(w, 0.085f * s, p(0.36f, 0.66f))
            drawCircle(w, 0.085f * s, p(0.64f, 0.60f))
            drawRoundRect(w, topLeft = p(0.435f, 0.30f), size = Size(0.035f * s, 0.36f * s), cornerRadius = CornerRadius(0.01f * s))
            drawRoundRect(w, topLeft = p(0.715f, 0.26f), size = Size(0.035f * s, 0.34f * s), cornerRadius = CornerRadius(0.01f * s))
            drawPath(poly(listOf(p(0.435f, 0.27f), p(0.75f, 0.23f), p(0.75f, 0.33f), p(0.435f, 0.37f))), w, style = Fill)
        }
        9 -> { // Gamepad
            drawRoundRect(w, topLeft = p(0.16f, 0.41f), size = Size(0.68f * s, 0.22f * s), cornerRadius = CornerRadius(0.11f * s))
            drawRoundRect(bg, topLeft = p(0.26f, 0.50f), size = Size(0.13f * s, 0.04f * s), cornerRadius = CornerRadius(0.01f * s))
            drawRoundRect(bg, topLeft = p(0.305f, 0.455f), size = Size(0.04f * s, 0.13f * s), cornerRadius = CornerRadius(0.01f * s))
            drawCircle(bg, 0.03f * s, p(0.64f, 0.49f))
            drawCircle(bg, 0.03f * s, p(0.71f, 0.55f))
        }
    }
}

private fun poly(pts: List<Offset>): Path = Path().apply {
    pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
    close()
}

private fun pentagon(center: Offset, r: Float): Path = Path().apply {
    for (i in 0 until 5) {
        val a = -Math.PI / 2 + i * 2 * Math.PI / 5
        val x = center.x + (r * Math.cos(a)).toFloat()
        val y = center.y + (r * Math.sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
