package com.thirutricks.tllplayer.features.search

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.PosterCard
import com.thirutricks.tllplayer.ui.components.SearchBar
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/**
 * Phase 11 — global search across channels, movies and series. Channels & movies play straight to
 * fullscreen; a series result opens that series (its episode list) in the Series section.
 */
@Composable
fun SearchScreen(
    onFullscreen: () -> Unit,
    onOpenSeries: (com.thirutricks.tllplayer.core.database.entity.SeriesEntity) -> Unit,
    onChildFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SearchViewModel = koinViewModel()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteChannelIds.collectAsStateWithLifecycle()
    val colors = OwnTVTheme.colors

    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
    ) {
        Text("Search", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
        Spacer(Modifier.height(14.dp))
        SearchBar(
            query = query,
            onQueryChange = vm::setQuery,
            placeholder = "Search channels, movies & series…",
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
        )
        Spacer(Modifier.height(20.dp))

        when {
            query.trim().length < 2 -> CenterHint("Type at least 2 characters to search across everything.")
            results.isEmpty -> CenterHint("No results for “${query.trim()}”.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusGroup(),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                if (results.channels.isNotEmpty()) {
                    item {
                        ResultSection("Channels", hint = "Long-press to favorite") {
                            items(results.channels, key = { "c${it.channel.id}" }) { row ->
                                ChannelChip(
                                    channel = row.channel,
                                    categoryName = row.categoryName,
                                    isFavorite = row.channel.id in favoriteIds,
                                    onClick = { vm.playChannel(row.channel); onFullscreen() },
                                    onToggleFavorite = { vm.toggleFavoriteChannel(row.channel) },
                                )
                            }
                        }
                    }
                }
                if (results.movies.isNotEmpty()) {
                    item {
                        ResultSection("Movies") {
                            items(results.movies, key = { "m${it.id}" }) { movie ->
                                Box(Modifier.width(150.dp)) {
                                    PosterCard(
                                        posterUrl = movie.posterUrl,
                                        title = movie.name,
                                        rating = movie.rating,
                                        onClick = { vm.playMovie(movie); onFullscreen() },
                                    )
                                }
                            }
                        }
                    }
                }
                if (results.series.isNotEmpty()) {
                    item {
                        ResultSection("Series") {
                            items(results.series, key = { "s${it.id}" }) { s ->
                                Box(Modifier.width(150.dp)) {
                                    PosterCard(
                                        posterUrl = s.posterUrl,
                                        title = s.name,
                                        rating = s.rating,
                                        onClick = { onOpenSeries(s) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultSection(title: String, hint: String? = null, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, color = OwnTVTheme.colors.primary, fontWeight = FontWeight.Bold)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.labelSmall, color = OwnTVTheme.colors.onSurfaceVariant)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun ChannelChip(
    channel: ChannelEntity,
    categoryName: String?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    // "category · #number" so near-identical feeds (e.g. several "ABC") are distinguishable.
    val detail = listOfNotNull(categoryName?.takeIf { it.isNotBlank() }, channel.number?.let { "#$it" })
        .joinToString(" · ").takeIf { it.isNotBlank() }
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        onLongClick = onToggleFavorite,
        modifier = Modifier.width(220.dp),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(model = channel.logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) colors.primary else colors.onSurface,
                    maxLines = 2,
                )
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.STAR, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant)
    }
}
