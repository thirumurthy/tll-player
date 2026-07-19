package com.thirutricks.tllplayer.features.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.longPressMenuGuard
import com.thirutricks.tllplayer.ui.format.rememberSystemTimeFormatter
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

internal object GuideGridDefaults {
    val ChannelCol = 176.dp
    val RowHeight = 64.dp
    val PxPerMin = 4.dp
    const val SlotMin = 30
}

@Composable
internal fun ProgrammeStripCanvas(
    programmes: List<EpgProgrammeEntity>,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    highlightTime: Long?,
    catchupIds: Set<Long>,
    hScroll: androidx.compose.foundation.ScrollState,
) {
    val colors = OwnTVTheme.colors
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)

    // Pre-computed once — nothing here is allocated inside the draw loop.
    val pxPerMin = with(density) { GuideGridDefaults.PxPerMin.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val padPx = with(density) { 10.dp.toPx() }
    val borderPx = with(density) { Dimens.FocusBorderWidth.toPx() }
    val corner = with(density) { CornerRadius(10.dp.toPx(), 10.dp.toPx()) }
    val titleStyle = MaterialTheme.typography.titleSmall.copy(color = colors.onSurface)
    val titleNowStyle = MaterialTheme.typography.titleSmall.copy(color = colors.onPrimaryContainer)
    val timeStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val timeNowStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onPrimaryContainer)
    val formatTime = rememberSystemTimeFormatter()
    // Time labels built once (string formatting kept out of the per-frame draw loop).
    val labels = remember(programmes, now, formatTime) {
        programmes.map { p ->
            val t = "${formatTime(p.startMs)} – ${formatTime(p.stopMs)}"
            if (now in p.startMs until p.stopMs) "NOW · $t" else t
        }
    }
    // Vertical "now" marker + catch-up glyph — measured once, reused each frame.
    val nowColor = Color(0xFFFF5C5C)
    val nowLinePx = with(density) { 2.dp.toPx() }
    val catchupStyle = MaterialTheme.typography.labelSmall.copy(color = colors.primary)
    val catchupGlyph = remember(catchupStyle) { measurer.measure("↻", catchupStyle) }

    val scrollPx = hScroll.value.toFloat() // read in composable scope so Canvas redraws on scroll
    Canvas(Modifier.fillMaxSize()) {
        val viewW = size.width
        val h = size.height
        programmes.forEachIndexed { i, p ->
            val s = p.startMs.coerceIn(windowStart, windowEnd)
            val e = p.stopMs.coerceIn(windowStart, windowEnd)
            if (e <= s) return@forEachIndexed
            val x = ((s - windowStart) / 60_000f) * pxPerMin - scrollPx
            val w = (((e - s) / 60_000f) * pxPerMin - gapPx).coerceAtLeast(0f)
            if (x + w <= 0f || x >= viewW) return@forEachIndexed // cull off-screen programmes
            val isNow = now in p.startMs until p.stopMs
            val hi = highlightTime != null && highlightTime in p.startMs until p.stopMs
            val bg = when { hi -> colors.card; isNow -> colors.primaryContainer; else -> colors.surfaceContainerHigh }
            drawRoundRect(color = bg, topLeft = Offset(x, 0f), size = Size(w, h), cornerRadius = corner)
            if (hi) drawRoundRect(color = colors.focusBorder, topLeft = Offset(x, 0f), size = Size(w, h), cornerRadius = corner, style = Stroke(borderPx))
            val textW = (w - padPx * 2f).toInt()
            if (textW > 8) {
                val tStyle = if (isNow && !hi) titleNowStyle else titleStyle
                val mStyle = if (isNow && !hi) timeNowStyle else timeStyle
                val title = measurer.measure(p.title, tStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = textW))
                val time = measurer.measure(labels[i], mStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = textW))
                val top = (h - (title.size.height + time.size.height + 2)) / 2f
                drawText(title, topLeft = Offset(x + padPx, top))
                drawText(time, topLeft = Offset(x + padPx, top + title.size.height + 2))
            }
            // Catch-up badge (↻) at the cell's top-right — only on programmes this channel can rewind from.
            if (p.id in catchupIds && w > 50f) {
                drawText(catchupGlyph, topLeft = Offset(x + w - catchupGlyph.size.width - 4f, 3f))
            }
        }
        // Vertical "now" marker — drawn on every row so it reads as one continuous line down the grid.
        if (now in windowStart..windowEnd) {
            val nowX = ((now - windowStart) / 60_000f) * pxPerMin - scrollPx
            if (nowX in 0f..viewW) {
                drawLine(color = nowColor, start = Offset(nowX, 0f), end = Offset(nowX, h), strokeWidth = nowLinePx)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ProgrammeDetailDialog(
    channelName: String,
    programme: EpgProgrammeEntity,
    loadDescription: suspend (Long) -> String?,
    canCatchup: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onWatch: () -> Unit,
    onPlayCatchup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val formatTime = rememberSystemTimeFormatter()
    // The grid load drops `description` to stay under the CursorWindow limit, so fetch it on demand
    // here (fall back to the row's own value when it was loaded by the lazy per-row path).
    val description by produceState(programme.description, programme.id) {
        value = programme.description ?: loadDescription(programme.id)
    }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        BackHandler { onDismiss() }
        Box(
            // The dialog can be opened by a long-press on the programme cell; the OK key is often still
            // held when it appears, which would instantly fire the focused action. Swallow OK until it's
            // released once so the held long-press only reveals the dialog, then the user chooses.
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).longPressMenuGuard(),
            contentAlignment = Alignment.Center,
        ) {
            // Scrollable: long XMLTV descriptions can exceed a small screen's height.
            Column(
                Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh)
                    .verticalScroll(rememberScrollState()).padding(28.dp),
            ) {
                Text(channelName.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(programme.title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                Spacer(Modifier.height(8.dp))
                Text("${formatTime(programme.startMs)} – ${formatTime(programme.stopMs)}", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(description.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(24.dp))
                // FlowRow so the actions wrap to a second line on narrower screens instead of the last
                // button being clipped off the dialog edge (4 buttons don't fit one row when catch-up adds
                // "Watch from start" + "Watch channel").
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Catch-up channels: replay this programme from its start (seekable archive playback).
                    if (canCatchup) {
                        OwnTVButton("Watch from start", onClick = onPlayCatchup, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
                        OwnTVButton("Watch channel", onClick = onWatch, style = OwnTVButtonStyle.SECONDARY)
                    } else {
                        OwnTVButton("Watch channel", onClick = onWatch, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
                    }
                    // Favourite the channel without leaving the guide; the label flips in place.
                    OwnTVButton(
                        if (isFavorite) "Unfavourite" else "Favourite",
                        onClick = onToggleFavorite,
                        style = OwnTVButtonStyle.SECONDARY,
                        icon = OwnTVIcon.FAVORITE,
                    )
                    OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                }
            }
        }
    }
}
