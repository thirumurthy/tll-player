package com.thirutricks.tllplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/** A focusable poster tile for the Movies/Series grids: poster, title, rating, resume bar, fav star. */
@Composable
fun PosterCard(
    posterUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    rating: Double? = null,
    progressFraction: Float? = null,
    isFavorite: Boolean = false,
    selected: Boolean = false,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.hasFocus) onFocus() },
        selected = selected,
        shape = RoundedCornerShape(14.dp),
        focusedScale = 1.06f,
        glowElevation = 14,
        focusedContainerColor = colors.surfaceContainerHigh,
        unfocusedContainerColor = colors.surfaceContainerHigh,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.Center,
    ) { focused ->
        Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // Taller, phone-screen-like poster. Crop (not Fit) so a standard 2:3 poster fills the
                    // slightly taller box instead of letterboxing.
                    .aspectRatio(2f / 3.2f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerLowest),
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(model = posterUrl, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.size(36.dp))
                    }
                }

                if (rating != null && rating > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OwnTVIcon(OwnTVIcon.STAR, tint = colors.accent, filled = true, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(formatRating(rating), style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }

                if (isFavorite) {
                    OwnTVIcon(
                        OwnTVIcon.STAR,
                        tint = colors.favorite,
                        filled = true,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                    )
                }

                if (progressFraction != null && progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.Black.copy(alpha = 0.4f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(colors.primary),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (focused) colors.primary else colors.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun formatRating(rating: Double): String = "%.1f".format(rating)
