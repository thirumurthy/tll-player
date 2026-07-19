package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * A small in-app toast: a transient, themed message pinned to the bottom-center of the screen — nicer than a
 * system [android.widget.Toast] on a TV (legible app font, OwnTV colors). Auto-dismisses after ~2.2s.
 *
 * Usage: `val toast = rememberInAppToast()` near the top of a screen; call `toast.show("…")` from a click
 * lambda; render [InAppToast] once as a sibling overlay (it stacks like the long-press menus). Emits nothing
 * while idle, so it costs nothing and never intercepts D-pad focus when there's no message.
 */
@Stable
class InAppToastState {
    internal var message by mutableStateOf<String?>(null)
        private set
    internal var tick by mutableStateOf(0L)
        private set

    fun show(text: String) {
        message = text
        tick++
    }

    internal fun clear() {
        message = null
    }
}

@Composable
fun rememberInAppToast(): InAppToastState = remember { InAppToastState() }

@Composable
fun InAppToast(state: InAppToastState) {
    val msg = state.message ?: return
    // Re-run the timer every time show() bumps the tick (so repeated identical messages re-trigger).
    LaunchedEffect(state.tick) {
        delay(2200)
        state.clear()
    }
    Box(
        modifier = Modifier.fillMaxSize().wrapContentSize(Alignment.BottomCenter).padding(bottom = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            msg,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(OwnTVTheme.colors.surfaceContainerHigh)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            color = OwnTVTheme.colors.onSurface,
        )
    }
}
