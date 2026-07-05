package com.thirutricks.tllplayer.features.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.DownloadEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.model.DownloadStatus
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.features.shell.components.CategoryRail
import com.thirutricks.tllplayer.features.shell.components.PreviewPane
import com.thirutricks.tllplayer.features.shell.components.RailCategory
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.PosterCard
import com.thirutricks.tllplayer.ui.components.ResumeDialog
import androidx.compose.foundation.layout.width
import com.thirutricks.tllplayer.ui.components.SearchBar
import com.thirutricks.tllplayer.ui.components.SortChip
import com.thirutricks.tllplayer.ui.components.formatCount
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

@Composable
fun MoviesScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val vm: MovieViewModel = koinViewModel()
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val selectedMovie by vm.selectedMovie.collectAsStateWithLifecycle()
    val selectedProgress by vm.selectedProgress.collectAsStateWithLifecycle()
    val downloadStates by vm.downloadStates.collectAsStateWithLifecycle()
    val movies = vm.movies.collectAsLazyPagingItems()
    val resumeMode by vm.resumeMode.collectAsStateWithLifecycle()

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)

    // Resume flow: AUTO continues silently, ASK prompts (≥10s saved), NEVER starts from zero.
    val scope = rememberCoroutineScope()
    var resumePrompt by remember { mutableStateOf<Pair<MovieEntity, Long>?>(null) }
    val startMovie: (MovieEntity) -> Unit = { m ->
        scope.launch {
            val pos = vm.savedPositionMs(m)
            when {
                resumeMode == SettingsRepository.ResumeMode.ASK && pos >= 10_000 -> resumePrompt = m to pos
                resumeMode == SettingsRepository.ResumeMode.AUTO && pos > 0 -> { vm.play(m, pos); onFullscreen() }
                else -> { vm.play(m, 0); onFullscreen() }
            }
        }
    }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    // Returning from the player: scroll to and focus the movie you just played (waits for the grid to load).
    LaunchedEffect(restoreFocus, movies.itemCount) {
        if (!restoreFocus || movies.itemCount == 0) return@LaunchedEffect
        val sel = selectedMovie
        val idx = if (sel != null) movies.itemSnapshotList.items.indexOfFirst { it.id == sel.id } else -1
        if (idx >= 0) {
            runCatching { gridState.scrollToItem(idx) }
            delay(60)
            runCatching { selFocus.requestFocus() }
        }
        onRestored()
    }

    Row(modifier = modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        CategoryRail(
            categories = railItems.map { RailCategory(it.abbr, it.title, it.icon) },
            selectedIndex = selectedIndex,
            onSelect = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
        )

        Column(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxSize()
                // Entering this pane must land on a poster, never the search bar: prefer the
                // last-focused movie, else the first one. onEnter fires only for directional entry
                // from outside (internal moves don't re-trigger it).
                .focusProperties {
                    onEnter = {
                        if (runCatching { selFocus.requestFocus() }.isFailure) {
                            runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                }
                .focusGroup()
                .padding(horizontal = Dimens.ScreenPaddingH, vertical = Dimens.ScreenPaddingV),
        ) {
            Text("Movies / ${selectedItem?.title ?: "All"}", style = MaterialTheme.typography.headlineLarge, color = OwnTVTheme.colors.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(
                "${selectedItem?.abbr ?: "ALL"} (${formatCount(count)} movies)",
                style = MaterialTheme.typography.titleMedium,
                color = OwnTVTheme.colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    placeholder = "Search ${selectedItem?.title ?: "movies"}…",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                SortChip(mode = sortMode, onToggle = vm::toggleSort, playlistLabel = "Provider")
                Spacer(Modifier.width(10.dp))
                // View mode (#10): poster wall vs a compact list (more titles at once).
                com.thirutricks.tllplayer.ui.components.OwnTVButton(
                    label = viewMode.label,
                    onClick = vm::toggleViewMode,
                    icon = if (viewMode == SettingsRepository.VodViewMode.GRID) OwnTVIcon.MENU else OwnTVIcon.MOVIES,
                    style = com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle.SECONDARY,
                )
            }
            Spacer(Modifier.height(14.dp))

            if (movies.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No movies found for “${searchQuery.trim()}”" else "No movies here.",
                        style = MaterialTheme.typography.bodyLarge, color = OwnTVTheme.colors.onSurfaceVariant,
                    )
                }
            } else if (viewMode == SettingsRepository.VodViewMode.LIST) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(movies.itemCount) { index ->
                        val movie = movies[index]
                        if (movie != null) {
                            MovieListRow(
                                movie = movie,
                                isFavorite = favoriteIds.contains(movie.id),
                                modifier = when {
                                    movie.id == selectedMovie?.id -> Modifier.focusRequester(selFocus)
                                    index == 0 -> Modifier.focusRequester(firstItemFocus)
                                    else -> Modifier
                                },
                                onFocus = { vm.onMovieFocused(movie) },
                                onClick = { startMovie(movie) },
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(movies.itemCount) { index ->
                        val movie = movies[index]
                        if (movie != null) {
                            PosterCard(
                                posterUrl = movie.posterUrl,
                                title = movie.name,
                                rating = movie.rating,
                                isFavorite = favoriteIds.contains(movie.id),
                                modifier = when {
                                    movie.id == selectedMovie?.id -> Modifier.focusRequester(selFocus)
                                    index == 0 -> Modifier.focusRequester(firstItemFocus)
                                    else -> Modifier
                                },
                                onFocus = { vm.onMovieFocused(movie) },
                                onClick = { startMovie(movie) },
                            )
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize().padding(Dimens.GapLarge)) {
            MovieDetailsPane(
                movie = selectedMovie,
                isFavorite = selectedMovie?.let { favoriteIds.contains(it.id) } ?: false,
                // In "Never resume" the button should honestly say Play, not Resume.
                resumePositionMs = if (resumeMode == SettingsRepository.ResumeMode.NEVER) 0L else selectedProgress?.positionMs ?: 0L,
                download = selectedMovie?.let { downloadStates[it.id] },
                onPlay = { selectedMovie?.let { startMovie(it) } },
                onToggleFavorite = { selectedMovie?.let { vm.toggleFavorite(it) } },
                onDownload = { selectedMovie?.let { vm.download(it) } },
            )
        }
    }

    resumePrompt?.let { (m, pos) ->
        ResumeDialog(
            positionMs = pos,
            onResume = { resumePrompt = null; vm.play(m, pos); onFullscreen() },
            onStartOver = { resumePrompt = null; vm.play(m, 0); onFullscreen() },
            onDismiss = { resumePrompt = null },
        )
    }
}

@Composable
private fun MovieDetailsPane(
    movie: MovieEntity?,
    isFavorite: Boolean,
    resumePositionMs: Long,
    download: DownloadEntity?,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    if (movie == null) {
        PreviewPane(hint = "Focus a movie to see details.")
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Dimens.CardCorner))
            .background(colors.panel)
            .verticalScroll(rememberScrollState())
            .padding(Dimens.GapLarge),
    ) {
        // Tall portrait poster (like the list / a phone screen), centred in the pane.
        Box(modifier = Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                val art = movie.posterUrl ?: movie.backdropUrl
                if (!art.isNullOrBlank()) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.height(48.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(movie.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(metaLine(movie), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        val plot = movie.plot
        if (!plot.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(plot, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 6)
        }
        Spacer(Modifier.height(20.dp))
        // Stacked (one per row): the narrow detail pane can't fit Resume + Favorite side by side without
        // clipping the longer labels, so lay them out vertically and stretch each pill to the pane width.
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OwnTVButton(
                label = if (resumePositionMs > 0) "Resume" else "Play",
                onClick = onPlay,
                icon = OwnTVIcon.PLAY,
                modifier = Modifier.fillMaxWidth(),
            )
            OwnTVButton(
                label = if (isFavorite) "Favorited" else "Favorite",
                onClick = onToggleFavorite,
                style = OwnTVButtonStyle.SECONDARY,
                icon = OwnTVIcon.STAR,
                modifier = Modifier.fillMaxWidth(),
            )
            OwnTVButton(
                label = downloadLabel(download),
                onClick = onDownload,
                style = OwnTVButtonStyle.SECONDARY,
                icon = OwnTVIcon.DOWNLOADS,
                enabled = download == null || download.status == DownloadStatus.FAILED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun downloadLabel(d: DownloadEntity?): String = when (d?.status) {
    DownloadStatus.COMPLETED -> "Downloaded"
    DownloadStatus.FAILED -> "Retry download"
    DownloadStatus.QUEUED -> "Queued…"
    DownloadStatus.RUNNING, DownloadStatus.PAUSED -> {
        val pct = if (d.totalBytes > 0) (d.downloadedBytes * 100 / d.totalBytes).toInt() else 0
        "Downloading $pct%"
    }
    null -> "Download"
}

private fun metaLine(movie: MovieEntity): String {
    val parts = mutableListOf<String>()
    movie.year?.let { parts.add(it.toString()) }
    movie.rating?.takeIf { it > 0 }?.let { parts.add("★ %.1f".format(it)) }
    movie.durationSecs?.takeIf { it > 0 }?.let { secs ->
        val h = secs / 3600
        val m = (secs % 3600) / 60
        parts.add(if (h > 0) "${h}h ${m}m" else "${m}m")
    }
    return parts.joinToString("  •  ")
}

/** Compact one-line row used by the List view mode — fits many titles on screen at once (#10). */
@Composable
private fun MovieListRow(
    movie: MovieEntity,
    isFavorite: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        contentAlignment = Alignment.CenterStart,
    ) { focused ->
        LaunchedEffect(focused) { if (focused) onFocus() }
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 44.dp, height = 62.dp).clip(RoundedCornerShape(6.dp)).background(colors.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                if (!movie.posterUrl.isNullOrBlank()) {
                    AsyncImage(model = movie.posterUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
                } else {
                    OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    movie.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (focused) colors.primary else colors.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                val meta = metaLine(movie)
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
            }
            if (isFavorite) {
                OwnTVIcon(OwnTVIcon.STAR, tint = colors.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
