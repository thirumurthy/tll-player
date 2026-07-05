package com.thirutricks.tllplayer.ui.theme

/**
 * Global UI scale as a percentage, applied by overriding [androidx.compose.ui.platform.LocalDensity]
 * so every dp and sp grows/shrinks uniformly. Users fine-tune the 10-foot layout anywhere in
 * [MIN]..[MAX] in [STEP] increments. Persisted via DataStore and adjusted from Settings.
 */
object UiZoom {
    const val MIN = 65
    const val MAX = 140
    const val DEFAULT = 100
    const val STEP = 5

    fun clamp(percent: Int): Int = percent.coerceIn(MIN, MAX)
    fun label(percent: Int): String = "$percent%"
    fun factor(percent: Int): Float = percent / 100f
}
