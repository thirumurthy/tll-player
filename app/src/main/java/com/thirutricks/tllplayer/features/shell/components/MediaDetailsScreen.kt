package com.thirutricks.tllplayer.features.shell.components

import androidx.compose.runtime.Immutable

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.trapAllFocusExit
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Read-only, already-merged data for the [MediaDetailsScreen] window. The caller applies the §7.1/§4.1
 * provider/TMDB merge and builds image.tmdb.org URLs, so this window is source-agnostic and reused for
 * movie / series / episode.
 */
@Immutable
data class MediaDetailsUi(
    val title: String,
    val subtitle: String? = null,       // e.g. "S2 · E5 · aired 2019-04-14"
    val backdropUrl: String? = null,    // 16:9 hero
    val posterUrl: String? = null,      // 2:3 poster (or 16:9 still for episodes)
    val metaLine: String = "",          // "2026 · ★ 7.6 · 2h 10m"
    val genres: List<String> = emptyList(),
    val plot: String? = null,
    val cast: List<String> = emptyList(),
)

/**
 * Windowed TMDB details popup (plan §11.1) — a centred ~60%-of-screen card over a dimmed scrim, like the
 * subtitle/track pickers. A 16:9 backdrop hero, then poster + complete info. Purely for reading:
 * **D-pad scrolls, Back exits, nothing is selectable.** The scrolling card owns focus (focusable) and
 * traps it, so D-pad drives the scroll and can't leak to the screen behind.
 */
@Composable
fun MediaDetailsScreen(details: MediaDetailsUi, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OwnTVTheme.colors
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler { onExit() }

    // A scroll-only window has no inner focus targets, so D-pad Up/Down would otherwise do nothing. The
    // focusable card owns focus and we translate Up/Down key presses into a programmatic scroll.
    val step = 260f
    val onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = onKey@{ e ->
        if (e.type != KeyEventType.KeyDown) return@onKey false
        when (e.key) {
            Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(step) }; true }
            Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-step) }; true }
            else -> false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .trapAllFocusExit()
            .focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        // The scrolling card IS the focusable: focus + verticalScroll on the same node so the D-pad
        // scrolls it directly (no inner focus targets, per the "scroll-only" requirement).
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.82f)
                .clip(RoundedCornerShape(20.dp))
                .background(colors.surfaceContainerHigh)
                .focusRequester(focus)
                .onKeyEvent(onKey)
                .focusable()
                .verticalScroll(scroll),
        ) {
            // Backdrop hero (16:9) with a bottom fade into the card background.
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(colors.surfaceContainerLowest)) {
                if (!details.backdropUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = details.backdropUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to colors.surfaceContainerHigh),
                    ),
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!details.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = details.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.height(36.dp))
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(details.title, style = MaterialTheme.typography.titleLarge, color = colors.onSurface, fontWeight = FontWeight.Bold)
                    if (!details.subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(details.subtitle, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                    }
                    if (details.metaLine.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(details.metaLine, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    if (details.genres.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(details.genres.joinToString(" · "), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!details.plot.isNullOrBlank()) {
                    Column {
                        Text("Overview", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(details.plot, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    }
                }
                if (details.cast.isNotEmpty()) {
                    Column {
                        Text("Cast", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(details.cast.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                Text("Press Back to close", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}
