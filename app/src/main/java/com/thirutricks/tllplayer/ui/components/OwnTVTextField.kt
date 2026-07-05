package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * A remote-friendly single-line text field, TV-style and two-stage: D-pad focus only *highlights*
 * the field (no keyboard), so you can move past it to other controls / a Save button freely. Press
 * **OK** to start editing (the keyboard appears); **Back** or the IME's Done returns to the field
 * without leaving the form. This mirrors the search bars and fixes the "keyboard pops up on every
 * field and traps you" problem on Fire TV / Android TV.
 */
@Composable
fun OwnTVTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val colors = OwnTVTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val fieldFocused by interaction.collectIsFocusedAsState()
    var editing by remember { mutableStateOf(false) }
    val pillFocus = remember { FocusRequester() }
    val innerFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    val focused = fieldFocused || editing

    LaunchedEffect(editing) {
        if (editing) runCatching { innerFocus.requestFocus(); keyboard?.show() }
    }

    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(colors.surfaceContainerHigh)
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) colors.primary else colors.outlineVariant,
                    shape = shape,
                )
                // The outer node takes D-pad focus (so the form can be traversed without the IME);
                // a passed-in requester focuses the field, not the inner editor.
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .focusRequester(pillFocus)
                .clickable(interactionSource = interaction, indication = null) { editing = true },
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .focusRequester(innerFocus)
                    .focusProperties { canFocus = editing } // not D-pad reachable until OK opens it
                    .onFocusChanged { if (editing && !it.isFocused) editing = false }
                    .onPreviewKeyEvent {
                        if (it.key == Key.Back) {
                            if (it.type == KeyEventType.KeyUp) {
                                editing = false
                                runCatching { pillFocus.requestFocus() }
                            }
                            true
                        } else {
                            false
                        }
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { editing = false; runCatching { pillFocus.requestFocus() } }),
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
        }
    }
}
