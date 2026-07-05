package com.thirutricks.tllplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme

/**
 * Available OwnTV themes. Persisted via DataStore and selectable from Settings → Theme.
 * SYSTEM follows the platform dark/light setting.
 */
enum class ThemeMode { SYSTEM, AMOLED_DARK, LIGHT }

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.AMOLED_DARK }

/** Map the resolved OwnTV tokens onto a tv-material3 M3 [ColorScheme]. */
private fun schemeFrom(c: OwnTVColors): ColorScheme =
    if (c.isDark) {
        darkColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            border = c.outline,
            error = c.favorite,
        )
    } else {
        lightColorScheme(
            primary = c.primary,
            onPrimary = c.onPrimary,
            primaryContainer = c.primaryContainer,
            onPrimaryContainer = c.onPrimaryContainer,
            secondary = c.secondary,
            onSecondary = c.onSecondary,
            secondaryContainer = c.secondaryContainer,
            onSecondaryContainer = c.onSecondaryContainer,
            tertiary = c.tertiary,
            onTertiary = c.onTertiary,
            tertiaryContainer = c.tertiaryContainer,
            onTertiaryContainer = c.onTertiaryContainer,
            background = c.background,
            onBackground = c.onSurface,
            surface = c.surface,
            onSurface = c.onSurface,
            surfaceVariant = c.surfaceContainerHigh,
            onSurfaceVariant = c.onSurfaceVariant,
            border = c.outline,
            error = c.favorite,
        )
    }

@Composable
fun OwnTVTheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    systemInDarkTheme: Boolean,
    customAccent: String = "",
    animationLevel: AnimationLevel = AnimationLevel.FULL,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.AMOLED_DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemInDarkTheme
    }

    val colors = ownTvColors(isDark = useDark, accent = accent, customAccent = customAccent)

    CompositionLocalProvider(
        LocalOwnTVColors provides colors,
        LocalThemeMode provides themeMode,
        LocalAnimationLevel provides animationLevel,
    ) {
        MaterialTheme(
            colorScheme = schemeFrom(colors),
            typography = OwnTVTypography,
            content = content,
        )
    }
}

/** Convenience accessor: `OwnTVTheme.colors.focusBorder`. */
object OwnTVTheme {
    val colors: OwnTVColors
        @Composable
        @ReadOnlyComposable
        get() = LocalOwnTVColors.current
}
