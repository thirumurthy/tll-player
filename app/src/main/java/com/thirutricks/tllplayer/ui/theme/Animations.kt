package com.thirutricks.tllplayer.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How much UI motion to render. A performance/comfort control: lower-end Android TV boxes can feel
 * laggy when moving quickly between menus, so the user can tone the animations down (or off).
 */
enum class AnimationLevel(val label: String) {
    // On = normal motion; Off = instant (no transitions). The fixed grid (v4.0.0) removed the old reason for
    // a middle "Reduced" tier, so this is now a simple On/Off reduce-motion toggle. (Legacy "REDUCED" values
    // fall back to On via the settings store's safe parse.)
    FULL("On"), OFF("Off");

    /** Scale an animation duration to this level (OFF collapses to 0 → an instant snap). */
    fun scale(durationMs: Int): Int = when (this) {
        FULL -> durationMs
        OFF -> 0
    }
}

/** Current animation level, provided at the theme root from the user's setting. */
val LocalAnimationLevel = staticCompositionLocalOf { AnimationLevel.FULL }

/** True unless the user has turned animations fully Off — for spots that gate a transition entirely. */
val animationsOn: Boolean
    @Composable @ReadOnlyComposable get() = LocalAnimationLevel.current != AnimationLevel.OFF

/** A tween whose duration follows the user's Animations setting (Off → an instant 0 ms snap). */
@Composable
@ReadOnlyComposable
fun <T> ownTvTween(durationMs: Int = 200, easing: Easing = FastOutSlowInEasing): TweenSpec<T> =
    tween(LocalAnimationLevel.current.scale(durationMs), easing = easing)
