package com.thirutricks.tllplayer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Material You-style accent presets. OwnTV can't rely on true wallpaper-based dynamic color (a phone
 * feature that isn't dependable on Android TV), so instead the user picks an accent and the M3 color
 * scheme is seeded from it. Each preset carries its tonal `primary` / `primaryContainer` roles for
 * both dark and light themes (M3 uses lighter tones on dark surfaces, darker tones on light).
 *
 * Neutrals (background, surface containers, text, outline) are theme-only and live in [OwnTVColors].
 */
enum class AccentColor(
    val label: String,
    private val primaryDark: Color,
    private val onPrimaryDark: Color,
    private val primaryContainerDark: Color,
    private val onPrimaryContainerDark: Color,
    private val primaryLight: Color,
    private val onPrimaryLight: Color,
    private val primaryContainerLight: Color,
    private val onPrimaryContainerLight: Color,
) {
    TEAL(
        "Teal",
        primaryDark = Color(0xFF52DBC8), onPrimaryDark = Color(0xFF003730),
        primaryContainerDark = Color(0xFF004F46), onPrimaryContainerDark = Color(0xFF6FF8E4),
        primaryLight = Color(0xFF006B5E), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFF6FF8E4), onPrimaryContainerLight = Color(0xFF00201B),
    ),
    BLUE(
        "Blue",
        primaryDark = Color(0xFF6FB0FF), onPrimaryDark = Color(0xFF00315C),
        primaryContainerDark = Color(0xFF134A7C), onPrimaryContainerDark = Color(0xFFD3E4FF),
        primaryLight = Color(0xFF1565C0), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFD6E3FF), onPrimaryContainerLight = Color(0xFF001C3A),
    ),
    VIOLET(
        "Violet",
        primaryDark = Color(0xFFCBBEFF), onPrimaryDark = Color(0xFF312170),
        primaryContainerDark = Color(0xFF483A88), onPrimaryContainerDark = Color(0xFFE7DEFF),
        primaryLight = Color(0xFF5B45C9), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFE5DEFF), onPrimaryContainerLight = Color(0xFF190066),
    ),
    GREEN(
        "Green",
        primaryDark = Color(0xFF6FDB94), onPrimaryDark = Color(0xFF00391C),
        primaryContainerDark = Color(0xFF1F5135), onPrimaryContainerDark = Color(0xFF8BF8AF),
        primaryLight = Color(0xFF1B6B3F), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFA6F2C0), onPrimaryContainerLight = Color(0xFF00210F),
    ),
    AMBER(
        "Amber",
        primaryDark = Color(0xFFFFB95C), onPrimaryDark = Color(0xFF452B00),
        primaryContainerDark = Color(0xFF624000), onPrimaryContainerDark = Color(0xFFFFDDB3),
        primaryLight = Color(0xFF8A5100), onPrimaryLight = Color(0xFFFFFFFF),
        primaryContainerLight = Color(0xFFFFDDB3), onPrimaryContainerLight = Color(0xFF2C1600),
    );

    fun primary(isDark: Boolean) = if (isDark) primaryDark else primaryLight
    fun onPrimary(isDark: Boolean) = if (isDark) onPrimaryDark else onPrimaryLight
    fun primaryContainer(isDark: Boolean) = if (isDark) primaryContainerDark else primaryContainerLight
    fun onPrimaryContainer(isDark: Boolean) = if (isDark) onPrimaryContainerDark else onPrimaryContainerLight
}
