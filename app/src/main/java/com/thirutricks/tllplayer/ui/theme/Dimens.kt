package com.thirutricks.tllplayer.ui.theme

import androidx.compose.ui.unit.dp

/** Shared spacing / sizing tokens for the 4-layer TV shell. */
object Dimens {
    val ScreenPaddingH = 32.dp
    val ScreenPaddingV = 24.dp

    // Layer 1 — MD3 navigation panel. Expands to a drawer (labels) when focused,
    // collapses to an icon rail when focus moves into a submenu.
    val SidebarWidthExpanded = 272.dp
    val SidebarWidthCollapsed = 96.dp

    // Layer 2 — category rail (expands to show full names when it holds focus)
    val RailWidth = 92.dp
    val RailWidthExpanded = 325.dp // expanded (focused overlay) width — fits long category names
    val RailWidthFixed = 240.dp    // Phase 2: fixed full-label category column (names wrap to 2 lines)
    val RailPillSize = 56.dp
    val ChannelListWidth = 400.dp  // Phase 2: fixed channel-list column (Live); preview/detail fills the rest

    // MD3 settings tonal icon tile
    val IconTileSize = 42.dp
    val IconTileCorner = 12.dp

    val GapSmall = 8.dp
    val GapMedium = 16.dp
    val GapLarge = 24.dp

    // M3 expressive shape scale (larger, rounder than the defaults).
    val CornerSmall = 12.dp
    val CornerMedium = 18.dp
    val CornerLarge = 24.dp
    val CardCorner = 20.dp

    val FocusBorderWidth = 2.5.dp

    val HomeRowPaddingH = 20.dp

    // Hero carousel
    val HeroBaseWidth = 180.dp
    val HeroMetaHeight = 84.dp
    val HeroGap = 14.dp
    val HeroCardCorner = 18.dp
    val HeroPosterCorner = 14.dp
    val HeroMaxCardHeight = 354.dp
    val HeroMinCardHeight = 200.dp
    val HeroOverlayMaxWidth = 400.dp
    val HeroProgressHeight = 3.dp
}
