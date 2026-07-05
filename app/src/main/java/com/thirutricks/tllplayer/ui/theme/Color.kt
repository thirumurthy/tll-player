package com.thirutricks.tllplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material 3 tonal palette for OwnTV (teal-seeded). NEUTRAL + secondary/tertiary roles are
 * theme-only; the `primary` roles are seeded per [AccentColor] (default teal == these values).
 *
 * Dark keeps a true-AMOLED black [DarkBackground] behind the M3 tonal surface containers.
 */

// Brand mark color (the OwnTV play logo) — constant.
val AccentCyan = Color(0xFF52DBC8)

// ---------------- DARK (M3 dark over AMOLED black) ----------------
val DarkBackground = Color(0xFF000000)               // AMOLED root
val DarkSurface = Color(0xFF0E1513)
val DarkSurfaceContainerLowest = Color(0xFF090F0E)
val DarkSurfaceContainerLow = Color(0xFF161D1B)
val DarkSurfaceContainer = Color(0xFF1B211F)
val DarkSurfaceContainerHigh = Color(0xFF252B29)
val DarkSurfaceContainerHighest = Color(0xFF303634)
val DarkOnSurface = Color(0xFFDEE4E1)
val DarkOnSurfaceVariant = Color(0xFFBFC9C4)
val DarkOutline = Color(0xFF89938F)
val DarkOutlineVariant = Color(0xFF3F4945)
val DarkSecondary = Color(0xFFB1CCC3)
val DarkOnSecondary = Color(0xFF1C352E)
val DarkSecondaryContainer = Color(0xFF334B44)
val DarkOnSecondaryContainer = Color(0xFFCDE8DF)
val DarkTertiary = Color(0xFFA9CBE4)
val DarkOnTertiary = Color(0xFF0B3445)
val DarkTertiaryContainer = Color(0xFF294B5D)
val DarkOnTertiaryContainer = Color(0xFFC5E7FF)
val DarkError = Color(0xFFFFB4AB)

// ---------------- LIGHT (M3 light) ----------------
val LightBackground = Color(0xFFF5FBF8)
val LightSurface = Color(0xFFF5FBF8)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFEFF5F2)
val LightSurfaceContainer = Color(0xFFE9EFEC)
val LightSurfaceContainerHigh = Color(0xFFE3EAE6)
val LightSurfaceContainerHighest = Color(0xFFDEE4E1)
val LightOnSurface = Color(0xFF171D1B)
val LightOnSurfaceVariant = Color(0xFF3F4945)
val LightOutline = Color(0xFF6F7975)
val LightOutlineVariant = Color(0xFFBFC9C4)
val LightSecondary = Color(0xFF4B635C)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCDE8DF)
val LightOnSecondaryContainer = Color(0xFF07201A)
val LightTertiary = Color(0xFF416278)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFC5E7FF)
val LightOnTertiaryContainer = Color(0xFF001E2F)
val LightError = Color(0xFFBA1A1A)
