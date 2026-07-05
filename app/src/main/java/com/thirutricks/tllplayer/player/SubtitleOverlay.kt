package com.thirutricks.tllplayer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.compose.koinInject
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository

/**
 * App-drawn subtitles for the direct render path: the decoder owns the video surface there, so mpv
 * can't draw its OSD — instead the player polls the active subtitle line ([OwnTVPlayer.subText])
 * and this overlay renders it Netflix-style. Inactive (empty) in GL mode, where mpv draws its own.
 */
@Composable
fun SubtitleOverlay(player: OwnTVPlayer, modifier: Modifier = Modifier) {
    val text by player.subText.collectAsStateWithLifecycle()
    val settings = koinInject<SettingsRepository>()
    val scale by settings.subtitleScale.collectAsStateWithLifecycle(initialValue = 1.0f)
    val line = text ?: return

    Box(modifier = modifier.fillMaxSize().padding(bottom = 56.dp), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = line,
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = Color.White,
                fontSize = (24 * scale).sp,
                lineHeight = (30 * scale).sp,
                fontWeight = FontWeight.Medium,
                shadow = Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 6f),
            ),
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}
