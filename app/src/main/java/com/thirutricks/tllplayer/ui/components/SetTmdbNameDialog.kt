package com.thirutricks.tllplayer.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * "Set TMDB name" dialog (plan §11.2 U5b): the user types the exact title (+ optional year) to search TMDB
 * under, overriding the auto-normalized provider title. This is the escape hatch when a match is wrong or
 * was negative-cached for 7 days — saving forces a fresh re-resolve. Mirrors [TextInputDialog]'s TV-friendly
 * layout (two-stage fields, Back exits, focus trapped inside).
 *
 * - **Save**: writes the override (caller re-resolves). Disabled while the title is blank.
 * - **Clear** ([hasOverride] only): removes the override (caller re-resolves with the cleaned title).
 * - **Cancel**: close without changes.
 */
@Composable
fun SetTmdbNameDialog(
    initialTitle: String,
    initialYear: Int?,
    hasOverride: Boolean,
    onSave: (title: String, year: Int?) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var title by remember { mutableStateOf(initialTitle) }
    var year by remember { mutableStateOf(initialYear?.toString() ?: "") }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { titleFocus.requestFocus() } }
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).trapAllFocusExit().focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.dialogPanel(width = 480.dp, padding = 28.dp),
        ) {
            Text("Set TMDB name", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(6.dp))
            Text(
                "Type the exact TMDB title to match. Year is optional but helps when several titles share a name.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OwnTVTextField(
                value = title,
                onValueChange = { title = it },
                label = "Title",
                modifier = Modifier.fillMaxWidth(),
                focusRequester = titleFocus,
            )
            Spacer(Modifier.height(14.dp))
            OwnTVTextField(
                value = year,
                onValueChange = { s -> year = s.filter { it.isDigit() }.take(4) },
                label = "Year (optional)",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                if (hasOverride) OwnTVButton("Clear", onClick = onClear, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton(
                    "Save",
                    onClick = { onSave(title.trim(), year.trim().toIntOrNull()) },
                    enabled = title.trim().isNotEmpty(),
                )
            }
        }
    }
}
