package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Shared panel chrome for centered popup dialogs: fixed width, rounded clip, surface fill —
 * and crucially a [verticalScroll], so a dialog taller than the screen (small/low-resolution
 * TVs, large interface zoom) scrolls instead of clipping its lower controls out of reach.
 * D-pad focus automatically brings off-screen children into view inside the scroll area.
 *
 * Do NOT use this on dialogs whose column contains a LazyColumn or `weight()` children —
 * nested same-direction scrolling is not allowed; cap the inner list's height instead.
 */
@Composable
fun Modifier.dialogPanel(
    width: Dp = 440.dp,
    corner: Dp = 20.dp,
    padding: Dp = 24.dp,
    fill: androidx.compose.ui.graphics.Color? = null,
): Modifier = this
    .width(width)
    .clip(RoundedCornerShape(corner))
    .background(fill ?: OwnTVTheme.colors.surfaceContainerHigh)
    .verticalScroll(rememberScrollState())
    .padding(padding)
