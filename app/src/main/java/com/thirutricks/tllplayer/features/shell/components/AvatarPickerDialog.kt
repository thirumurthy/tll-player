package com.thirutricks.tllplayer.features.shell.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVAvatar
import com.thirutricks.tllplayer.ui.components.OwnTVAvatars
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/** Full-screen avatar picker: a grid of the preset cartoon avatars. Picking one applies & closes. */
@Composable
fun AvatarPickerDialog(
    selectedId: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { selectedFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surfaceContainerHigh)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Choose your avatar",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))

            val ids = (0 until OwnTVAvatars.COUNT).toList()
            ids.chunked(4).forEach { rowIds ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    rowIds.forEach { id ->
                        val isSelected = id == selectedId
                        FocusableSurface(
                            onClick = { onSelect(id); onDismiss() },
                            modifier = (if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier)
                                .size(88.dp),
                            selected = isSelected,
                            shape = RoundedCornerShape(22.dp),
                            focusedScale = 1.08f,
                            focusedContainerColor = colors.surfaceContainerHighest,
                            unfocusedContainerColor = colors.surfaceContainer,
                            selectedContainerColor = colors.primaryContainer,
                            contentAlignment = Alignment.Center,
                        ) { _ ->
                            OwnTVAvatar(avatarId = id, modifier = Modifier.size(64.dp))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}
