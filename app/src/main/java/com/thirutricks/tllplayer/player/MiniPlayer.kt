package com.thirutricks.tllplayer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVIcon

/**
 * The docked mini-player (dock-to-corner model). The mpv surface is rendered behind this by the shell;
 * this just overlays a title and a small focusable control row (play/pause · expand · close). Navigate
 * to it with the D-pad to use it; expand returns to fullscreen.
 */
@Composable
fun MiniPlayer(player: PlaybackEngine, onExpand: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    Box(modifier = modifier) {
        // Title (top, on a slight scrim).
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(meta.title ?: "", style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 1)
        }

        // Controls (bottom).
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(8.dp).focusGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            MiniBtn(if (isPlaying) OwnTVIcon.PAUSE else OwnTVIcon.PLAY) { player.togglePlayPause() }
            Spacer(Modifier.weight(1f))
            MiniBtn(OwnTVIcon.FULLSCREEN, onClick = onExpand)
            MiniBtn(OwnTVIcon.CLOSE, onClick = onClose)
        }
    }
}

@Composable
private fun MiniBtn(icon: OwnTVIcon, onClick: () -> Unit) {
    FocusableSurface(
        onClick = onClick,
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        focusedScale = 1.12f,
        focusedContainerColor = Color.White.copy(alpha = 0.28f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
        selectedContainerColor = Color.White.copy(alpha = 0.12f),
        contentAlignment = Alignment.Center,
    ) { _ ->
        OwnTVIcon(icon, tint = Color.White, filled = true, modifier = Modifier.size(18.dp))
    }
}
