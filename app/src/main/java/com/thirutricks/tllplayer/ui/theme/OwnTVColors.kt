package com.thirutricks.tllplayer.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * OwnTV's resolved Material 3 color roles for the current theme + accent. Read as `OwnTVTheme.colors`.
 *
 * Exposes the full M3 surface-container tiers and primary/secondary/tertiary roles the MD3 UI needs.
 * A few legacy aliases (`panel`/`card`/`rail`/`textPrimary`/`textSecondary`/`accent`) map onto M3
 * roles so older components keep working.
 */
@Immutable
data class OwnTVColors(
    val isDark: Boolean,
    // Surfaces
    val background: Color,
    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    // Primary
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // Secondary
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    // Tertiary
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    // Focus / status
    val focusBorder: Color,
    val focusGlow: Color,
    val favorite: Color,
) {
    // Legacy aliases used by existing components.
    val textPrimary: Color get() = onSurface
    val textSecondary: Color get() = onSurfaceVariant
    val panel: Color get() = surfaceContainerLow
    val card: Color get() = surfaceContainerHigh
    val rail: Color get() = surfaceContainer
    val accent: Color get() = primary
}

/** Parses "#RRGGBB" / "RRGGBB" (also 8-digit AARRGGBB) into a [Color]; null when invalid. */
fun parseAccentHex(hex: String): Color? {
    val s = hex.trim().removePrefix("#")
    return runCatching {
        when (s.length) {
            6 -> Color((0xFF000000L or s.toLong(16)).toInt())
            8 -> Color(s.toLong(16).toInt())
            else -> null
        }
    }.getOrNull()
}

/** The four M3 primary roles, resolved either from a preset or generated from a custom seed. */
private data class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

/** Keep the seed's hue/saturation but pin the HSL lightness — a cheap stand-in for M3 tones. */
private fun Color.withLightness(l: Float): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = l
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

/** Generate tonal primary roles from an arbitrary seed color (the custom hex accent). */
private fun rolesFrom(seed: Color, isDark: Boolean): AccentRoles = if (isDark) {
    AccentRoles(
        primary = seed.withLightness(0.70f),
        onPrimary = seed.withLightness(0.13f),
        primaryContainer = seed.withLightness(0.26f),
        onPrimaryContainer = seed.withLightness(0.90f),
    )
} else {
    AccentRoles(
        primary = seed.withLightness(0.36f),
        onPrimary = Color.White,
        primaryContainer = seed.withLightness(0.88f),
        onPrimaryContainer = seed.withLightness(0.10f),
    )
}

/**
 * Build the resolved M3 tokens for a theme (dark/light) and accent. A valid [customAccent] hex
 * overrides the preset (its tonal roles are generated from the seed color).
 */
fun ownTvColors(isDark: Boolean, accent: AccentColor, customAccent: String = ""): OwnTVColors {
    val roles = parseAccentHex(customAccent)?.let { rolesFrom(it, isDark) } ?: AccentRoles(
        primary = accent.primary(isDark),
        onPrimary = accent.onPrimary(isDark),
        primaryContainer = accent.primaryContainer(isDark),
        onPrimaryContainer = accent.onPrimaryContainer(isDark),
    )
    val primary = roles.primary
    return if (isDark) {
        OwnTVColors(
            isDark = true,
            background = DarkBackground,
            surface = DarkSurface,
            surfaceContainerLowest = DarkSurfaceContainerLowest,
            surfaceContainerLow = DarkSurfaceContainerLow,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHigh,
            surfaceContainerHighest = DarkSurfaceContainerHighest,
            onSurface = DarkOnSurface,
            onSurfaceVariant = DarkOnSurfaceVariant,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            primary = primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.primaryContainer,
            onPrimaryContainer = roles.onPrimaryContainer,
            secondary = DarkSecondary,
            onSecondary = DarkOnSecondary,
            secondaryContainer = DarkSecondaryContainer,
            onSecondaryContainer = DarkOnSecondaryContainer,
            tertiary = DarkTertiary,
            onTertiary = DarkOnTertiary,
            tertiaryContainer = DarkTertiaryContainer,
            onTertiaryContainer = DarkOnTertiaryContainer,
            focusBorder = primary,
            focusGlow = primary.copy(alpha = 0.40f),
            favorite = DarkError,
        )
    } else {
        OwnTVColors(
            isDark = false,
            background = LightBackground,
            surface = LightSurface,
            surfaceContainerLowest = LightSurfaceContainerLowest,
            surfaceContainerLow = LightSurfaceContainerLow,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            surfaceContainerHighest = LightSurfaceContainerHighest,
            onSurface = LightOnSurface,
            onSurfaceVariant = LightOnSurfaceVariant,
            outline = LightOutline,
            outlineVariant = LightOutlineVariant,
            primary = primary,
            onPrimary = roles.onPrimary,
            primaryContainer = roles.primaryContainer,
            onPrimaryContainer = roles.onPrimaryContainer,
            secondary = LightSecondary,
            onSecondary = LightOnSecondary,
            secondaryContainer = LightSecondaryContainer,
            onSecondaryContainer = LightOnSecondaryContainer,
            tertiary = LightTertiary,
            onTertiary = LightOnTertiary,
            tertiaryContainer = LightTertiaryContainer,
            onTertiaryContainer = LightOnTertiaryContainer,
            focusBorder = primary,
            focusGlow = primary.copy(alpha = 0.28f),
            favorite = LightError,
        )
    }
}

val LocalOwnTVColors = staticCompositionLocalOf { ownTvColors(isDark = true, accent = AccentColor.TEAL) }
