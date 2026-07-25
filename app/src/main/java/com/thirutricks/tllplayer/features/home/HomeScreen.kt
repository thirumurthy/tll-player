package com.thirutricks.tllplayer.features.home

import android.content.Context
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.BlurMaskFilter
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.launcher.LauncherContinuationItem
import com.thirutricks.tllplayer.core.launcher.LauncherWatchNextType
import com.thirutricks.tllplayer.player.HeroPreviewEngine
import com.thirutricks.tllplayer.ui.components.BrandLockup
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.OwnTVSpinner
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.foundation.layout.widthIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onPlayMovie: (movieId: Long, positionMs: Long) -> Unit,
    onPlayEpisode: (seriesId: Long, episodeId: Long, positionMs: Long) -> Unit,
    onPlayChannel: (channelId: Long, zapChannels: List<ChannelEntity>) -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    previewEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val state by vm.uiState.collectAsStateWithLifecycle()
    val heroPreviewEngine = koinInject<HeroPreviewEngine>()
    val engineState by heroPreviewEngine.state.collectAsStateWithLifecycle()
    val isPreviewActive by vm.isPreviewActive.collectAsStateWithLifecycle()
    val lastInteractionMs by vm.lastHeroInteractionMs.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val heroFocus = remember { FocusRequester() }
    val fallbackFocus = remember { FocusRequester() }
    val firstRowFocus = remember { FocusRequester() }
    // The SELECTED hero is always shown wide (with its poster) — default the first one, even before the row
    // is focused. Video only plays once the row is focused (see the preview effect below). Expansion is
    // decoupled from playback, so the wide card never collapses just because the video hasn't started.
    var expandedHeroIndex by remember { mutableStateOf(0) }
    var focusedHeroIndex by remember { mutableStateOf(-1) }

    val onNonHeroFocused = remember(vm, heroPreviewEngine) {
        {
            vm.setHeroFocused(false)
            heroPreviewEngine.stop() // stop the video, but keep the selected hero expanded (poster)
            onChildFocused()
        }
    }

    LaunchedEffect(focusedHeroIndex) {
        if (focusedHeroIndex != -1) return@LaunchedEffect
        // Focus moves between hero cards very quickly (old loses focus before new gains). Debounce the
        // "left hero row" signal so we don't flap preview state while navigating within the row.
        kotlinx.coroutines.delay(40L)
        if (focusedHeroIndex != -1) return@LaunchedEffect
        vm.setHeroFocused(false)
        heroPreviewEngine.stop() // keep the selected hero expanded (poster); just stop the video
    }

    LaunchedEffect(previewEnabled) {
        vm.setPreviewEnabled(previewEnabled)
        if (!previewEnabled) {
            vm.stopPreview() // keep the hero expanded (poster); just stop the video
        }
    }

    LaunchedEffect(state.heroItems, state.continueMovies, state.continueSeries, state.favoriteLive, restoreFocus) {
        if (state.isEmpty) {
            if (restoreFocus) onRestored()
            return@LaunchedEffect
        }
        runCatching { listState.scrollToItem(0) }
        // Only pull focus INTO the Home content when returning from the player (restoreFocus). On a cold
        // start or a tab switch, leave focus on the sidebar's Home item so the nav is immediately navigable.
        if (restoreFocus) {
            kotlinx.coroutines.delay(60)
            if (state.heroItems.isNotEmpty()) {
                runCatching { heroFocus.requestFocus() }
            } else {
                runCatching { fallbackFocus.requestFocus() }
            }
            onRestored()
        }
    }

    if (state.isEmpty) {
        EmptyHomeState(
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    val hero = state.heroItems.getOrNull(state.activeHeroIndex)
    val hasMovies = state.continueMovies.isNotEmpty()
    val hasSeries = state.continueSeries.isNotEmpty()
    val hasFavorites = state.favoriteLive.isNotEmpty()
    val firstRowKind = when {
        hasFavorites -> RowKind.FAVORITES
        hasMovies -> RowKind.MOVIES
        hasSeries -> RowKind.SERIES
        else -> null
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.background,
                        colors.surface,
                        colors.background,
                    ),
                ),
            )
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .focusGroup(),
        state = listState,
        contentPadding = PaddingValues(top = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item {
            if (state.heroItems.isNotEmpty()) {
                HeroRowSection(
                    items = state.heroItems,
                    expandedIndex = expandedHeroIndex,
                    heroPreviewEngine = heroPreviewEngine,
                    engineState = engineState,
                    heroFocusRequester = heroFocus,
                    onHeroFocusChanged = { index, hasFocus ->
                        if (hasFocus) {
                            if (expandedHeroIndex != index) {
                                heroPreviewEngine.stop() // stop the previous hero's video before switching
                            }
                            expandedHeroIndex = index // expand the focused hero immediately (poster shows first)
                            focusedHeroIndex = index
                            vm.onHeroUserNavigate(index)
                            vm.setHeroFocused(true)
                            onChildFocused()
                        } else if (focusedHeroIndex == index) {
                            focusedHeroIndex = -1
                        }
                    },
                    onPlay = { item ->
                        when (item) {
                            is HeroItem.MovieHero -> onPlayMovie(item.movie.id, item.positionMs)
                            is HeroItem.SeriesHero -> onPlayEpisode(item.series.id, item.episode.id, item.positionMs)
                            is HeroItem.LiveHero -> onPlayChannel(item.channel.id, state.recentLive)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                HeroFallbackPane(
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = fallbackFocus,
                    onChildFocused = onNonHeroFocused,
                )
            }
        }

        if (state.recentLive.isNotEmpty()) {
            item {
                ChannelRailRow(
                    title = "Recently Watched Live",
                    channels = state.recentLive,
                    onChannelClick = { id -> onPlayChannel(id, state.recentLive) },
                    onFocus = onNonHeroFocused,
                )
            }
        }

        if (hasFavorites) {
            item {
                ChannelRailRow(
                    title = "Favourite Channels",
                    channels = state.favoriteLive,
                    onChannelClick = { id -> onPlayChannel(id, state.favoriteLive) },
                    onFocus = onNonHeroFocused,
                    firstItemFocusRequester = if (firstRowKind == RowKind.FAVORITES) firstRowFocus else null,
                )
            }
        }
        if (hasMovies) {
            item {
                ContinueWatchingRow(
                    title = "Continue Watching Movies",
                    items = state.continueMovies,
                    onItemClick = { onPlayMovie(it.sourceItemId, it.positionMs) },
                    onFocus = onNonHeroFocused,
                    firstItemFocusRequester = if (firstRowKind == RowKind.MOVIES) firstRowFocus else null,
                )
            }
        }
        if (hasSeries) {
            item {
                ContinueWatchingRow(
                    title = "Continue Watching Series",
                    items = state.continueSeries,
                    onItemClick = { onPlayEpisode(0L, it.targetItemId, it.positionMs) },
                    onFocus = onNonHeroFocused,
                    firstItemFocusRequester = if (firstRowKind == RowKind.SERIES) firstRowFocus else null,
                )
            }
        }
    }

    // Video preview: only the EXPANSION is immediate (on navigation, above) — the muted video starts a
    // moment later, on top of the already-wide card's poster, and only while the row is focused.
    LaunchedEffect(isPreviewActive, hero, lastInteractionMs) {
        if (!isPreviewActive || hero == null) {
            heroPreviewEngine.stop()
            return@LaunchedEffect
        }

        val scheduledHero = hero
        val interactionStamp = lastInteractionMs

        heroPreviewEngine.stop()
        kotlinx.coroutines.delay(3_000L)
        if (!isPreviewActive || interactionStamp != lastInteractionMs) return@LaunchedEffect
        if (scheduledHero != state.heroItems.getOrNull(state.activeHeroIndex)) return@LaunchedEffect

        heroPreviewEngine.play(scheduledHero.streamUrl, scheduledHero.seekToMs)
    }
}

private enum class RowKind { FAVORITES, MOVIES, SERIES }

@Composable
private fun HomeSectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.HomeRowPaddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            OwnTVIcon(OwnTVIcon.PLAY, tint = colors.primary, modifier = Modifier.size(15.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroRowSection(
    items: List<HeroItem>,
    expandedIndex: Int,
    heroPreviewEngine: HeroPreviewEngine,
    engineState: HeroPreviewEngine.State,
    heroFocusRequester: FocusRequester,
    onHeroFocusChanged: (index: Int, hasFocus: Boolean) -> Unit,
    onPlay: (HeroItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    val approxRowWidth = screenWidthDp - Dimens.SidebarWidthCollapsed - Dimens.HomeRowPaddingH
    val maxCardHeight = (approxRowWidth - Dimens.HeroBaseWidth - Dimens.HeroGap) * 9f / 16f
    // Keep the hero cinematic without letting it consume the whole TV viewport. The first content
    // rail remains visible below it, making Home feel like a browsable hub instead of a splash page.
    val cardHeight = maxCardHeight.coerceIn(220.dp, 260.dp)
    val posterHeight = cardHeight - Dimens.HeroMetaHeight
    val expandedWidth = cardHeight * 16f / 9f
    val cardShape = RoundedCornerShape(Dimens.HeroCardCorner)
    val posterClip = RoundedCornerShape(Dimens.HeroPosterCorner)

    var rowTopLeftInRoot by remember { mutableStateOf(Offset.Zero) }
    var previewRectInRowPx by remember { mutableStateOf<Rect?>(null) }
    var localFocusedIndex by remember { mutableStateOf(-1) }
    var rowWidthDp by remember { mutableStateOf(0.dp) }
    val heroRowState = rememberLazyListState()

    LaunchedEffect(localFocusedIndex) {
        if (localFocusedIndex >= 0) {
            heroRowState.animateScrollToItem(localFocusedIndex)
        }
    }

    val endPadding = (rowWidthDp - Dimens.HeroBaseWidth - Dimens.HomeRowPaddingH).coerceAtLeast(Dimens.HomeRowPaddingH)

    Column(modifier = modifier) {
        HomeSectionHeader(
            title = "Up Next",
            subtitle = "Continue where you left off",
            count = items.size,
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .onGloballyPositioned {
                    rowTopLeftInRoot = it.positionInRoot()
                    rowWidthDp = with(density) { it.size.width.toDp() }
                },
        ) {
            LazyRow(
                state = heroRowState,
                horizontalArrangement = Arrangement.spacedBy(Dimens.HeroGap),
                contentPadding = PaddingValues(start = Dimens.HomeRowPaddingH, end = endPadding),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(
                    items,
                    key = { _, item ->
                        when (item) {
                            is HeroItem.MovieHero -> "movie:${item.movie.id}"
                            is HeroItem.SeriesHero -> "episode:${item.episode.id}"
                            is HeroItem.LiveHero -> "live:${item.channel.id}"
                        }
                    },
                ) { index, item ->
                    val isExpanded = index == expandedIndex
                    val targetWidth = if (isExpanded) expandedWidth else Dimens.HeroBaseWidth
                    val width by animateDpAsState(
                        targetValue = targetWidth,
                        animationSpec = tween(durationMillis = if (isExpanded) 500 else 150),
                        label = "heroCardWidth",
                    )

                    val imageUrl = when (item) {
                        is HeroItem.MovieHero -> item.movie.posterUrl
                        is HeroItem.SeriesHero -> item.series.posterUrl
                        is HeroItem.LiveHero -> item.channel.logoUrl
                    }

                    val heroGlowColor = colors.focusGlow
                    Box(
                        modifier = Modifier
                            .height(cardHeight)
                            .width(width)
                            .then(if (isExpanded) Modifier.zIndex(1f) else Modifier)
                            .then(
                                if (isExpanded) Modifier.onGloballyPositioned { coords ->
                                    val b = coords.boundsInRoot()
                                    previewRectInRowPx = Rect(
                                        left = b.left - rowTopLeftInRoot.x,
                                        top = b.top - rowTopLeftInRoot.y,
                                        right = b.right - rowTopLeftInRoot.x,
                                        bottom = b.bottom - rowTopLeftInRoot.y,
                                    )
                                } else Modifier
                            ),
                    ) {
                        FocusableSurface(
                            onClick = { onPlay(item) },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .width(width)
                                .height(cardHeight)
                                .then(if (index == 0) Modifier.focusRequester(heroFocusRequester) else Modifier)
                                .onFocusChanged { fs ->
                                    if (fs.hasFocus) localFocusedIndex = index
                                    onHeroFocusChanged(index, fs.hasFocus)
                                }
                                .then(
                                    if (isExpanded) Modifier.drawBehind {
                                        val radius = 18.dp.toPx()
                                        drawIntoCanvas { canvas ->
                                            val paint = android.graphics.Paint().apply {
                                                isAntiAlias = true
                                                color = heroGlowColor.toArgb()
                                                maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
                                            }
                                            canvas.nativeCanvas.drawRoundRect(
                                                0f, 0f, size.width, size.height,
                                                Dimens.HeroCardCorner.toPx(), Dimens.HeroCardCorner.toPx(),
                                                paint,
                                            )
                                        }
                                    } else Modifier
                                ),
                            shape = cardShape,
                            focusedScale = 1f,
                            glowElevation = if (isExpanded) 0 else 10,
                            focusedContainerColor = colors.surfaceContainerHigh,
                            unfocusedContainerColor = colors.surfaceContainerHigh,
                            selectedContainerColor = colors.surfaceContainerHigh,
                            contentAlignment = Alignment.Center,
                        ) { focused ->
                            if (isExpanded) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    colors.surfaceContainerLowest,
                                                    colors.primaryContainer.copy(alpha = 0.28f),
                                                    colors.surfaceContainerLowest,
                                                ),
                                            ),
                                        ),
                                ) {
                                    if (!imageUrl.isNullOrBlank()) {
                                        if (item is HeroItem.LiveHero) {
                                            AsyncImage(
                                                model = imageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                alpha = 0.24f,
                                                modifier = Modifier.fillMaxSize().blur(26.dp),
                                            )
                                            AsyncImage(
                                                model = imageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 54.dp).size(176.dp),
                                            )
                                        } else {
                                            AsyncImage(
                                                model = imageUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    } else {
                                        Box(
                                            Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            val fallback = when (item) {
                                                is HeroItem.MovieHero -> OwnTVIcon.MOVIES
                                                is HeroItem.SeriesHero -> OwnTVIcon.SERIES
                                                is HeroItem.LiveHero -> OwnTVIcon.LIVE_TV
                                            }
                                            OwnTVIcon(fallback, tint = colors.onSurfaceVariant, modifier = Modifier.size(64.dp))
                                        }
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(posterHeight)
                                            .clip(posterClip)
                                            .background(colors.surfaceContainerLowest),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (!imageUrl.isNullOrBlank()) {
                                            if (item is HeroItem.LiveHero) {
                                                AsyncImage(
                                                    model = imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize().blur(20.dp),
                                                    contentScale = ContentScale.Crop,
                                                    alpha = 0.5f,
                                                )
                                                AsyncImage(
                                                    model = imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(80.dp),
                                                    contentScale = ContentScale.Fit,
                                                )
                                            } else {
                                                AsyncImage(
                                                    model = imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                )
                                            }
                                        } else {
                                            val fallback = when (item) {
                                                is HeroItem.MovieHero -> OwnTVIcon.MOVIES
                                                is HeroItem.SeriesHero -> OwnTVIcon.SERIES
                                                is HeroItem.LiveHero -> OwnTVIcon.LIVE_TV
                                            }
                                            OwnTVIcon(fallback, tint = colors.onSurfaceVariant, modifier = Modifier.size(42.dp))
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    val title = when (item) {
                                        is HeroItem.MovieHero -> item.item.title
                                        is HeroItem.SeriesHero -> item.item.title
                                        is HeroItem.LiveHero -> item.channel.name
                                    }
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (focused) colors.primary else colors.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )

                                    if (item.durationMs > 0) {
                                        Spacer(Modifier.height(6.dp))
                                        val fraction = (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .clip(RoundedCornerShape(100))
                                                .background(Color.Black.copy(alpha = 0.25f)),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction)
                                                    .height(4.dp)
                                                    .background(colors.primary),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val rect = previewRectInRowPx
            if (rect != null && expandedIndex >= 0) {
                val expandedItem = items.getOrNull(expandedIndex)
                if (expandedItem != null) {
                    val ox = with(density) { rect.left.toDp() }
                    val oy = with(density) { rect.top.toDp() }
                    val ow = with(density) { rect.width.toDp() }
                    val oh = with(density) { rect.height.toDp() }

                    Box(
                        modifier = Modifier
                            .focusProperties { canFocus = false }
                            .offset(x = ox, y = oy)
                            .width(ow)
                            .height(oh)
                            .clip(cardShape),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            colors.surfaceContainerLowest,
                                            colors.primaryContainer.copy(alpha = 0.28f),
                                            colors.surfaceContainerLowest,
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            HeroPreviewSurface(
                                engine = heroPreviewEngine,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (engineState != HeroPreviewEngine.State.PLAYING) {
                                val artUrl = when (expandedItem) {
                                    is HeroItem.MovieHero -> expandedItem.movie.posterUrl
                                    is HeroItem.SeriesHero -> expandedItem.series.posterUrl
                                    is HeroItem.LiveHero -> expandedItem.channel.logoUrl
                                }
                                if (!artUrl.isNullOrBlank()) {
                                    if (expandedItem is HeroItem.LiveHero) {
                                        AsyncImage(
                                            model = artUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            alpha = 0.24f,
                                            modifier = Modifier.fillMaxSize().blur(26.dp),
                                        )
                                        AsyncImage(
                                            model = artUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 54.dp).size(176.dp),
                                        )
                                    } else {
                                        AsyncImage(
                                            model = artUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                } else {
                                    val fallback = when (expandedItem) {
                                        is HeroItem.MovieHero -> OwnTVIcon.MOVIES
                                        is HeroItem.SeriesHero -> OwnTVIcon.SERIES
                                        is HeroItem.LiveHero -> OwnTVIcon.LIVE_TV
                                    }
                                    OwnTVIcon(fallback, tint = colors.onSurfaceVariant, modifier = Modifier.size(64.dp))
                                }
                            }
                        }

                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        0f to Color.Black.copy(alpha = 0.88f),
                                        0.58f to Color.Black.copy(alpha = 0.18f),
                                        1f to Color.Transparent,
                                    ),
                                )
                                .background(
                                    Brush.verticalGradient(
                                        0.42f to Color.Transparent,
                                        1f to Color.Black.copy(alpha = 0.76f),
                                    ),
                                ),
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                .widthIn(max = Dimens.HeroOverlayMaxWidth),
                        ) {
                            val typeIcon = when (expandedItem) {
                                is HeroItem.MovieHero -> OwnTVIcon.MOVIES
                                is HeroItem.SeriesHero -> OwnTVIcon.SERIES
                                is HeroItem.LiveHero -> OwnTVIcon.LIVE_TV
                            }
                            val typeLabel = when (expandedItem) {
                                is HeroItem.MovieHero -> "MOVIE"
                                is HeroItem.SeriesHero -> "SERIES"
                                is HeroItem.LiveHero -> "LIVE CHANNEL"
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.Black.copy(alpha = 0.48f))
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                OwnTVIcon(typeIcon, tint = colors.primary, modifier = Modifier.size(12.dp))
                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            val title = when (expandedItem) {
                                is HeroItem.MovieHero -> expandedItem.item.title
                                is HeroItem.SeriesHero -> expandedItem.item.title
                                is HeroItem.LiveHero -> expandedItem.channel.name
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                            )

                            val subtitle = when (expandedItem) {
                                is HeroItem.MovieHero ->
                                    expandedItem.item.subtitle ?: expandedItem.movie.year?.toString().orEmpty()
                                is HeroItem.SeriesHero ->
                                    expandedItem.item.subtitle.orEmpty()
                                is HeroItem.LiveHero -> "Recently watched live"
                            }
                            if (subtitle.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            val statText = heroStatLabel(context, expandedItem, System.currentTimeMillis())
                            if (statText != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = statText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            OwnTVButton(
                                label = when (expandedItem.watchNextType) {
                                    LauncherWatchNextType.NEXT -> "Play Next"
                                    LauncherWatchNextType.CONTINUE ->
                                        if (expandedItem is HeroItem.LiveHero) "Tune In" else "Resume"
                                },
                                onClick = { onPlay(expandedItem) },
                                modifier = Modifier.focusProperties { canFocus = false },
                                style = OwnTVButtonStyle.PRIMARY,
                                icon = OwnTVIcon.PLAY,
                                enabled = true,
                            )
                        }

                        if (expandedItem.durationMs > 0) {
                            val fraction = (expandedItem.positionMs.toFloat() / expandedItem.durationMs.toFloat()).coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .height(Dimens.HeroProgressHeight)
                                    .background(Color.Black.copy(alpha = 0.35f)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(Dimens.HeroProgressHeight)
                                        .background(colors.primary),
                                )
                            }
                        }

                        if (engineState == HeroPreviewEngine.State.LOADING) {
                            OwnTVSpinner(
                                sizeDp = 18,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 16.dp)
                                    .alpha(0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun heroStatLabel(context: Context, item: HeroItem, nowMs: Long): String? =
    when (item) {
        is HeroItem.MovieHero,
        is HeroItem.SeriesHero -> finishByLabel(context, item.positionMs, item.durationMs, nowMs)
        is HeroItem.LiveHero -> relativeLastWatchedLabel(item.lastEngagementAt, nowMs)
    }

private fun finishByLabel(context: Context, positionMs: Long, durationMs: Long, nowMs: Long): String? {
    if (durationMs <= 0) return null

    val safePosition = positionMs.coerceIn(0L, durationMs)
    val remainingMs = durationMs - safePosition
    if (remainingMs <= 0L) return null

    val finishMs = roundUpToNextQuarterHour(nowMs + remainingMs)
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
    val time = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(finishMs))
    return "Finish by $time"
}

private fun roundUpToNextQuarterHour(ms: Long): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = ms
    }
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    val millisecond = calendar.get(Calendar.MILLISECOND)
    val remainder = minute % 15
    val shouldAdvance = remainder != 0 || second != 0 || millisecond != 0

    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    if (shouldAdvance) {
        val minutesToAdd = if (remainder == 0) 15 else 15 - remainder
        calendar.add(Calendar.MINUTE, minutesToAdd)
    }

    return calendar.timeInMillis
}

private fun relativeLastWatchedLabel(lastEngagementAt: Long, nowMs: Long): String {
    val elapsedMs = nowMs - lastEngagementAt
    if (elapsedMs < 60_000L) return "Last watched just now"

    val elapsedMinutes = elapsedMs / 60_000L
    if (elapsedMinutes < 60L) {
        return "Last watched ${elapsedMinutes} ${if (elapsedMinutes == 1L) "minute" else "minutes"} ago"
    }

    val elapsedHours = elapsedMinutes / 60L
    if (elapsedHours < 24L) {
        return "Last watched ${elapsedHours} ${if (elapsedHours == 1L) "hour" else "hours"} ago"
    }

    val elapsedDays = elapsedHours / 24L
    return "Last watched ${elapsedDays} ${if (elapsedDays == 1L) "day" else "days"} ago"
}

@Composable
private fun HeroPreviewSurface(
    engine: HeroPreviewEngine,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            android.view.SurfaceView(ctx).apply {
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) = engine.setSurface(holder.surface)
                    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}
                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) = engine.setSurface(null)
                })
            }
        },
        update = { it.keepScreenOn = false },
    )
}

@Composable
private fun ContinueWatchingRow(
    title: String,
    items: List<LauncherContinuationItem>,
    onItemClick: (LauncherContinuationItem) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var focusedIndex by remember { mutableStateOf(-1) }

    Column(modifier = modifier.fillMaxWidth()) {
        HomeSectionHeader(
            title = title,
            subtitle = "Ready when you are",
            count = items.size,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = Dimens.HomeRowPaddingH),
            modifier = Modifier.focusGroup(),
            userScrollEnabled = false,
        ) {
            itemsIndexed(items, key = { _, item -> item.stableKey }) { index, item ->
                ContinueWatchingCard(
                    item = item,
                    dimmed = focusedIndex != -1 && focusedIndex != index,
                    modifier = when {
                        firstItemFocusRequester != null && index == 0 -> Modifier.focusRequester(firstItemFocusRequester)
                        else -> Modifier
                    },
                    onFocus = {
                        focusedIndex = index
                        onFocus()
                    },
                    onBlur = { if (focusedIndex == index) focusedIndex = -1 },
                    onClick = { onItemClick(item) },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: LauncherContinuationItem,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onBlur: () -> Unit = {},
    onClick: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val progressFraction = if (item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else null
    val progressPercent = progressFraction?.let { (it * 100).toInt() }

    val cardAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.72f else 1f,
        animationSpec = tween(220),
        label = "cardAlpha",
    )

    FocusableSurface(
        onClick = onClick,
        modifier = modifier
            .width(276.dp)
            .alpha(cardAlpha)
            .onFocusChanged { if (it.hasFocus) onFocus() else onBlur() },
        shape = RoundedCornerShape(20.dp),
        focusedScale = 1.045f,
        glowElevation = 14,
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerLow,
        selectedContainerColor = colors.surfaceContainerHigh,
        contentAlignment = Alignment.TopStart,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            // Poster / placeholder
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(colors.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    OwnTVIcon(OwnTVIcon.MOVIES, tint = colors.onSurfaceVariant, modifier = Modifier.size(44.dp))
                }
            }

            // Subtle top scrim so Resume chip stays readable
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.40f),
                            0.28f to Color.Transparent,
                        )
                    ),
            )

            // Heavy bottom scrim for text legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.30f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.94f),
                        )
                    ),
            )

            // "Resume" pill — top-right, only when focused
            if (focused) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.primary)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OwnTVIcon(OwnTVIcon.PLAY, tint = Color.White, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Resume",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Bottom info block
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 11.dp),
            ) {
                val subline = buildContinueWatchingSubtitle(item)
                if (subline != null) {
                    Text(
                        text = subline,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focused) colors.primary else Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progressFraction != null && progressFraction > 0f) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.22f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.primary),
                            )
                        }
                        if (progressPercent != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildContinueWatchingSubtitle(item: LauncherContinuationItem): String? {
    val episodePart = if (item.seasonNumber != null && item.episodeNumber != null) {
        "S${item.seasonNumber} E${item.episodeNumber}"
    } else null
    val containerPart = item.containerTitle
    return when {
        episodePart != null && containerPart != null -> "$episodePart • $containerPart"
        episodePart != null -> episodePart
        containerPart != null -> containerPart
        item.subtitle != null -> item.subtitle
        else -> null
    }
}

@Composable
private fun ChannelRailRow(
    title: String,
    channels: List<ChannelEntity>,
    onChannelClick: (Long) -> Unit,
    onFocus: () -> Unit,
    firstItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        HomeSectionHeader(
            title = title,
            subtitle = if (title.startsWith("Recently")) "Jump back into live TV" else "Your saved live channels",
            count = channels.size,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = Dimens.HomeRowPaddingH),
            modifier = Modifier.focusGroup(),
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .height(112.dp),
                ) {
                    val colors = OwnTVTheme.colors
                    FocusableSurface(
                        onClick = { onChannelClick(channel.id) },
                        modifier = when {
                            firstItemFocusRequester != null && index == 0 -> Modifier.focusRequester(firstItemFocusRequester)
                            else -> Modifier
                        }.onFocusChanged { if (it.hasFocus) onFocus() },
                        shape = RoundedCornerShape(18.dp),
                        focusedScale = 1.045f,
                        glowElevation = 14,
                        focusedContainerColor = colors.surfaceContainerHighest,
                        unfocusedContainerColor = colors.surfaceContainerLow,
                        selectedContainerColor = colors.surfaceContainerHigh,
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(colors.surfaceContainerLowest, colors.surfaceContainer),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!channel.logoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = channel.logoUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit,
                                    )
                                } else {
                                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(26.dp))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF4D5E)))
                                    Text(
                                        text = "LIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    text = channel.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (focused) colors.primary else colors.onSurface,
                                    fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroFallbackPane(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    onChildFocused: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .clip(RoundedCornerShape(20.dp))
            .background(colors.panel)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 72, textSize = 42)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Nothing to preview yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your continue-watching items will appear here once playback history is available.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyHomeState(
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .focusProperties { canFocus = false }
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 84, textSize = 48)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Start watching to see your activity here.",
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Continue watching will show up on Home once you have playback history.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
