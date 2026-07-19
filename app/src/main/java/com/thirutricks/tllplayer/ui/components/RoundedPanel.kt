package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

// Phase 6 — per-region panel fill colours (owner-specified, 2026-06-27).
// Each returns a dark-green tint in dark mode, and a light-grey-green tint in light mode,
// so the theme toggle actually changes the panels.
// Option A — Clean + Premium (owntv_panel_color_concepts_vertical.html)
val RailPanelFill: Color
    @Composable @ReadOnlyComposable get() =
        if (OwnTVTheme.colors.isDark) Color(0xFF141B19) else Color(0xFFE4ECE8)

val ContentPanelFill: Color
    @Composable @ReadOnlyComposable get() =
        if (OwnTVTheme.colors.isDark) Color(0xFF0C1312) else Color(0xFFF0F6F3)

val PreviewPanelFill: Color
    @Composable @ReadOnlyComposable get() =
        if (OwnTVTheme.colors.isDark) Color(0xFF1B2320) else Color(0xFFDDE7E2)

/**
 * Phase 6 — a rounded visual container matching the new-shell mockup's "panel 2/3/4" look: large rounded
 * corners, a subtle surface fill, and a hairline [outlineVariant] border. Content is clipped to the
 * rounded shape.
 *
 * This is a VISUAL wrapper only — a plain [Box], no `clickable`/`selectable`/focus of its own.
 *
 * @param fillColor the panel surface colour, or null for the theme default.
 * @param radius corner radius (≈24px on the mockup; 22dp reads well at TV distance).
 * @param innerPadding inset between the rounded edge and the content.
 */
@Composable
fun RoundedPanel(
    modifier: Modifier = Modifier,
    radius: Dp = 22.dp,
    fillColor: Color? = null,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val bg = fillColor ?: colors.surfaceContainerLowest
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .border(width = 1.dp, color = colors.outlineVariant, shape = shape)
            .padding(innerPadding),
    ) {
        content()
    }
}

/**
 * Phase 6 — the rounded-panel look as a [Modifier], for applying to an EXISTING container.
 * Same spec as [RoundedPanel].
 */
@Composable
fun Modifier.roundedPanel(
    radius: Dp = 22.dp,
    fillColor: Color? = null,
): Modifier {
    val colors = OwnTVTheme.colors
    val bg = fillColor ?: colors.surfaceContainerLowest
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(bg, shape)
        .border(width = 1.dp, color = colors.outlineVariant, shape = shape)
}
