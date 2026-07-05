package com.thirutricks.tllplayer.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Apply to the root of a menu/dialog that was opened by a **long-press**. The OK key is usually still held
 * (or auto-repeating) when the menu appears, which would instantly "click" its focused item before the user
 * can choose. This swallows OK/Enter until the key has been **released once** — so the held long-press can't
 * trigger anything, and the next deliberate press works normally.
 */
fun Modifier.longPressMenuGuard(): Modifier = composed {
    var armed by remember { mutableStateOf(false) }
    onPreviewKeyEvent { e ->
        if (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter) {
            if (e.type == KeyEventType.KeyUp) armed = true
            !armed // consume OK while still "held" from the long-press; let it through after the first release
        } else {
            false
        }
    }
}
