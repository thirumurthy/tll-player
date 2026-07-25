package com.thirutricks.tllplayer.features.live

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.features.shell.components.CategoryRail
import com.thirutricks.tllplayer.features.shell.components.RailCategory
import com.thirutricks.tllplayer.ui.components.longPressMenuGuard
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.OwnTVSpinner
import com.thirutricks.tllplayer.ui.components.SearchBar
import com.thirutricks.tllplayer.ui.components.SortChip
import com.thirutricks.tllplayer.ui.components.TextInputDialog
import com.thirutricks.tllplayer.ui.components.formatCount
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme

/** Live TV browser: compact category rail and a responsive, paged channel grid. */
@Composable
fun LiveScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    val vm: LiveViewModel = koinViewModel()
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val previewChannel by vm.previewChannel.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()

    val listState = rememberLazyGridState()
    val selFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    var renaming by remember { mutableStateOf<ChannelEntity?>(null) }
    var matchingEpg by remember { mutableStateOf<ChannelEntity?>(null) }
    var catchupChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) } // long-press quick menu
    // When the long-press menu closes (Cancel, Favourite, Hide) WITHOUT opening another dialog, return focus
    // to the channel it was opened from — otherwise focus falls back to the nav panel.
    var contextMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(contextChannel) {
        if (contextChannel != null) { contextMenuOpen = true; return@LaunchedEffect }
        if (contextMenuOpen) {
            contextMenuOpen = false
            if (renaming == null && matchingEpg == null && catchupChannel == null) {
                delay(60)
                runCatching { selFocus.requestFocus() }
            }
        }
    }
    // Returning from fullscreen: scroll to and focus the channel you were watching (waits for the list to load).
    // Also used by "Startup → Live · Favorites": there's no remembered channel yet, so land on the first row
    // (not the nav panel).
    LaunchedEffect(restoreFocus, channels.itemCount) {
        if (!restoreFocus || channels.itemCount == 0) return@LaunchedEffect
        val ch = previewChannel
        val idx = if (ch != null) channels.itemSnapshotList.items.indexOfFirst { it.id == ch.id } else -1
        if (idx >= 0) {
            runCatching { listState.scrollToItem(idx) }
            delay(60)
            runCatching { selFocus.requestFocus() }
        } else {
            delay(60)
            runCatching { firstItemFocus.requestFocus() }
        }
        onRestored()
    }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        colors.background,
                        colors.primary.copy(alpha = 0.035f),
                        colors.background,
                    ),
                ),
            )
            .onFocusChanged { if (it.hasFocus) onChildFocused() },
    ) {
        CategoryRail(
            categories = railItems.map { RailCategory(it.abbr, it.title, it.icon) },
            selectedIndex = selectedIndex,
            onSelect = { idx -> railItems.getOrNull(idx)?.let { vm.select(it.key) } },
        )

        // The channel browser fills all remaining space; no preview stream or detail card is composed.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                // Entering this pane from the rail must land on a channel row, never
                // the search bar: prefer the last-focused channel, else the first row. onEnter fires
                // only for directional entry from outside (internal moves don't re-trigger it).
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
            LiveHeader(
                title = selectedItem?.title ?: "All Channels",
                abbreviation = selectedItem?.abbr ?: "ALL",
                channelCount = count,
            )
            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    placeholder = "Search ${selectedItem?.title ?: "channels"}…",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(10.dp))
                SortChip(mode = sortMode, onToggle = vm::toggleSort)
            }
            Spacer(Modifier.height(14.dp))

            if (channels.itemCount == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (searchQuery.isNotBlank()) "No channels found for “${searchQuery.trim()}”" else "No channels in this playlist yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.focusGroup(),
                ) {
                    items(channels.itemCount) { index ->
                        val channel = channels[index]
                        if (channel != null) {
                            ChannelCard(
                                channel = channel,
                                isFavorite = favoriteIds.contains(channel.id),
                                isActive = channel.id == previewChannel?.id,
                                modifier = when {
                                    channel.id == previewChannel?.id -> Modifier.focusRequester(selFocus)
                                    index == 0 -> Modifier.focusRequester(firstItemFocus)
                                    else -> Modifier
                                },
                                onFocus = { vm.onChannelFocused(channel) },
                                onClick = {
                                    vm.watchFullscreen(channel, channels.itemSnapshotList.items.filterNotNull())
                                    onFullscreen()
                                },
                                onLongClick = { contextChannel = channel },
                            )
                        }
                    }
                }
            }
        }
    }

    catchupChannel?.let { ch ->
        CatchupDialog(
            channelName = ch.name,
            loadProgrammes = { vm.catchupProgrammes(ch) },
            onPick = { prog -> catchupChannel = null; vm.playCatchupProgramme(ch, prog); onFullscreen() },
            onDismiss = { catchupChannel = null },
        )
    }

    renaming?.let { ch ->
        TextInputDialog(
            title = "Rename channel",
            initial = ch.name,
            hint = "Only for this profile. Leave blank to restore the original name.",
            onConfirm = { vm.renameChannel(ch, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    matchingEpg?.let { ch ->
        EpgMatchDialog(
            channelName = ch.name,
            currentMatch = vm.currentEpgMatch(ch),
            loadChannels = { q -> vm.availableEpgChannels(q) },
            onPick = { epgId -> vm.setEpgMatch(ch, epgId); matchingEpg = null },
            onClear = { vm.setEpgMatch(ch, null); matchingEpg = null },
            onDismiss = { matchingEpg = null },
        )
    }

    // Long-press a channel → quick actions (favourite, rename, hide, match EPG, catch-up).
    contextChannel?.let { ch ->
        ChannelContextMenu(
            channelName = ch.name,
            isFavorite = favoriteIds.contains(ch.id),
            hasCatchup = ch.catchup,
            onToggleFavorite = { vm.toggleFavorite(ch); contextChannel = null },
            onRename = { renaming = ch; contextChannel = null },
            onHide = { vm.hideChannel(ch); contextChannel = null },
            onMatchEpg = { matchingEpg = ch; contextChannel = null },
            onCatchup = { catchupChannel = ch; contextChannel = null },
            onDismiss = { contextChannel = null },
        )
    }
}

@Composable
private fun LiveHeader(
    title: String,
    abbreviation: String,
    channelCount: Int,
) {
    val colors = OwnTVTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiveBadge()
            Text(
                text = "$abbreviation PLAYLIST",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = formatCount(channelCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onSurface,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Browse ${formatCount(channelCount)} live channels",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChannelCard(
    channel: ChannelEntity,
    isFavorite: Boolean,
    isActive: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        selected = isActive,
        modifier = modifier.onFocusChanged { if (it.hasFocus) onFocus() },
        shape = RoundedCornerShape(18.dp),
        focusedScale = 1.045f,
        glowElevation = 14,
        focusedContainerColor = colors.surfaceContainerHighest,
        unfocusedContainerColor = colors.surfaceContainerLow,
        selectedContainerColor = colors.primaryContainer.copy(alpha = 0.36f),
        contentAlignment = Alignment.TopStart,
    ) { focused ->
        Column(modifier = Modifier.fillMaxWidth().padding(7.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(13.dp))
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
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(13.dp),
                    )
                } else {
                    OwnTVIcon(OwnTVIcon.LIVE_TV, tint = colors.onSurfaceVariant, modifier = Modifier.size(30.dp))
                }
                if (isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.52f))
                            .padding(5.dp),
                    ) {
                        OwnTVIcon(
                            OwnTVIcon.STAR,
                            tint = colors.favorite,
                            filled = true,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                channel.number?.let { num ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.58f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "$num",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.52f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFFFF4D5E)))
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (focused || isActive) colors.primary else colors.onSurface,
                fontWeight = if (focused || isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp),
            )
        }
    }
}

/** Long-press quick actions for a Live channel (favourite / rename / hide / match EPG / catch-up). */
@Composable
private fun ChannelContextMenu(
    channelName: String,
    isFavorite: Boolean,
    hasCatchup: Boolean,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onMatchEpg: () -> Unit,
    onCatchup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    Box(
        modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f))
            .longPressMenuGuard(), // the long-press OK is still held — don't let it auto-click a menu item
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.width(440.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(channelName, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            OwnTVButton(
                if (isFavorite) "Remove from Favourites" else "Add to Favourites",
                onClick = onToggleFavorite, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.STAR,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            OwnTVButton("Rename", onClick = onRename, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            OwnTVButton("Hide channel", onClick = onHide, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            OwnTVButton("Match EPG", onClick = onMatchEpg, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.EPG, modifier = Modifier.fillMaxWidth())
            if (hasCatchup) OwnTVButton("Catch-up", onClick = onCatchup, style = OwnTVButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            OwnTVButton("Close", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Pulsing ● LIVE badge. */
@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveDot",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFCC0000).copy(alpha = 0.92f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = dotAlpha)),
        )
        Text("LIVE", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

private val clockFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
private val catchupDayTimeFormat = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault())
private fun formatCatchupTime(startMs: Long, stopMs: Long): String =
    "${catchupDayTimeFormat.format(java.util.Date(startMs))} – ${clockFormat.format(java.util.Date(stopMs))}"

/** Live TV catch-up: pick a recent (already-aired) programme on a catch-up channel to replay from start. */
@Composable
private fun CatchupDialog(
    channelName: String,
    loadProgrammes: suspend () -> List<com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity>,
    onPick: (com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val list by androidx.compose.runtime.produceState<List<com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity>?>(initialValue = null) {
        value = runCatching { loadProgrammes() }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(list) {
        if (list.isNullOrEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() }
    }
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(620.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp)) {
            Text("Catch-up · $channelName", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text("Pick a recent programme to replay from the start.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            when (val progs = list) {
                null -> Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                else -> if (progs.isEmpty()) {
                    Text(
                        "No recent guide data for this channel yet — make sure its EPG is matched (long-press it, or use Match EPG).",
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(progs, key = { it.id }) { p ->
                            FocusableSurface(
                                onClick = { onPick(p) },
                                modifier = if (p == progs.first()) Modifier.fillMaxWidth().focusRequester(firstFocus) else Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) { _ ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    Text(p.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatCatchupTime(p.startMs, p.stopMs), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

/** Manual EPG matching: pick which guide channel this channel uses (search across all EPG feeds).
 *  Shared with the Guide screen (long-press a channel → Match EPG). */
@Composable
internal fun EpgMatchDialog(
    channelName: String,
    currentMatch: String?,
    loadChannels: suspend (String) -> List<com.thirutricks.tllplayer.core.database.entity.EpgChannelEntity>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    var query by remember { mutableStateOf("") }
    val results by androidx.compose.runtime.produceState<List<com.thirutricks.tllplayer.core.database.entity.EpgChannelEntity>?>(initialValue = null, query) {
        kotlinx.coroutines.delay(250)
        value = runCatching { loadChannels(query) }.getOrDefault(emptyList())
    }
    androidx.activity.compose.BackHandler { onDismiss() }

    // Pull focus into the dialog once the list first arrives (first result, else the search bar).
    // One-shot, so later search-driven reloads don't steal focus from the field while typing.
    val firstItemFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    var didInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(results) {
        if (didInitialFocus || results == null) return@LaunchedEffect
        didInitialFocus = true
        kotlinx.coroutines.delay(60)
        if (results!!.isNotEmpty()) runCatching { firstItemFocus.requestFocus() }
        else runCatching { searchFocus.requestFocus() }
    }

    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(580.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp)) {
            Text("Match EPG", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                "Pick the guide channel for “$channelName”." + (currentMatch?.let { "  Current: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            SearchBar(query = query, onQueryChange = { query = it }, placeholder = "Search guide channels…", modifier = Modifier.fillMaxWidth().focusRequester(searchFocus))
            Spacer(Modifier.height(12.dp))
            val list = results
            when {
                list == null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { OwnTVSpinner(sizeDp = 28) }
                list.isEmpty() -> Text(
                    if (query.isBlank()) "No EPG data yet — add an EPG source in Settings." else "No guide channels match “$query”.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
                else -> LazyColumn(Modifier.fillMaxWidth().height(300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list, key = { it.id }) { epg ->
                        FocusableSurface(
                            onClick = { onPick(epg.epgChannelId) },
                            modifier = if (epg == list.first()) Modifier.fillMaxWidth().focusRequester(firstItemFocus) else Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { _ ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text(epg.displayName ?: epg.epgChannelId, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(epg.epgChannelId, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                if (currentMatch != null) OwnTVButton("Clear match", onClick = onClear, style = OwnTVButtonStyle.SECONDARY)
            }
        }
    }
}
