package com.thirutricks.tllplayer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import com.thirutricks.tllplayer.R

/**
 * Lora — a free, open-licensed serif (SIL OFL) chosen for OwnTV's popup menus; the rest of the
 * app keeps the sans-serif [OwnTVTypography]. Lora ships as a variable font (weight axis 400–700),
 * so each weight is a variation setting off the single upright/italic files (minSdk 26 supports
 * variable fonts).
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun loraUpright(weight: FontWeight) =
    Font(R.font.lora_variable, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun loraItalic(weight: FontWeight) =
    Font(R.font.lora_italic_variable, weight, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

val PopupFontFamily = FontFamily(
    loraUpright(FontWeight.Normal),
    loraUpright(FontWeight.Medium),
    loraUpright(FontWeight.SemiBold),
    loraUpright(FontWeight.Bold),
    loraItalic(FontWeight.Normal),
    loraItalic(FontWeight.Bold),
)

/**
 * Wraps popup-menu content so every text style inside uses [PopupFontFamily]. [fontScale] shrinks
 * (or grows) all font sizes and line heights — 1f keeps the design sizes; the EPG match/review
 * popups pass 0.75f for a denser look.
 */
@Composable
fun PopupFontTheme(fontScale: Float = 1f, content: @Composable () -> Unit) {
    val t = MaterialTheme.typography
    fun androidx.compose.ui.text.TextStyle.popup() = copy(
        fontFamily = PopupFontFamily,
        fontSize = if (fontScale == 1f) fontSize else fontSize * fontScale,
        lineHeight = if (fontScale == 1f || lineHeight.isUnspecified) lineHeight else lineHeight * fontScale,
    )
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        shapes = MaterialTheme.shapes,
        typography = Typography(
            displayLarge = t.displayLarge.popup(),
            displayMedium = t.displayMedium.popup(),
            displaySmall = t.displaySmall.popup(),
            headlineLarge = t.headlineLarge.popup(),
            headlineMedium = t.headlineMedium.popup(),
            headlineSmall = t.headlineSmall.popup(),
            titleLarge = t.titleLarge.popup(),
            titleMedium = t.titleMedium.popup(),
            titleSmall = t.titleSmall.popup(),
            bodyLarge = t.bodyLarge.popup(),
            bodyMedium = t.bodyMedium.popup(),
            bodySmall = t.bodySmall.popup(),
            labelLarge = t.labelLarge.popup(),
            labelMedium = t.labelMedium.popup(),
            labelSmall = t.labelSmall.popup(),
        ),
        content = content,
    )
}
