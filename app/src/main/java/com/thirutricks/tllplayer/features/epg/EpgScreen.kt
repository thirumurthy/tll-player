package com.thirutricks.tllplayer.features.epg

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.ui.components.longPressMenuGuard
import com.thirutricks.tllplayer.ui.components.ErrorState
import com.thirutricks.tllplayer.ui.components.FocusableSurface
import com.thirutricks.tllplayer.ui.components.OwnTVButton
import com.thirutricks.tllplayer.ui.components.OwnTVButtonStyle
import com.thirutricks.tllplayer.ui.components.OwnTVIcon
import com.thirutricks.tllplayer.ui.components.OwnTVSpinner
import com.thirutricks.tllplayer.ui.components.SearchBar
import com.thirutricks.tllplayer.ui.theme.Dimens
import com.thirutricks.tllplayer.ui.theme.OwnTVTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CHANNEL_COL = 176.dp
private val ROW_HEIGHT = 64.dp
private val PX_PER_MIN = 4.dp
private const val SLOT_MIN = 30

private fun clock(ms: Long) = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

/**
 * The full EPG guide: a time × channel grid. Channel labels are pinned on the left; every channel row
 * and the time axis share one horizontal scroll state, so moving the D-pad across programmes scrolls
 * the whole guide in lock-step. Picking a programme opens its details.
 */
@Composable
fun EpgScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onFullscreen: () -> Unit = {},
    onAddEpg: () -> Unit = {},
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
) {
    val vm: EpgViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val matching by vm.matching.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val matchSummary by vm.matchSummary.collectAsStateWithLifecycle()
    val sortGuide by vm.sortGuide.collectAsStateWithLifecycle()
    val categoryFilter by vm.categoryFilter.collectAsStateWithLifecycle()
    val guideCategories by vm.guideCategories.collectAsStateWithLifecycle()
    var showCategoryPicker by remember { mutableStateOf(false) }
    val colors = OwnTVTheme.colors
    val hScroll = rememberScrollState()
    val rowListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val firstCell = remember { FocusRequester() }
    val tunedCell = remember { FocusRequester() }
    // The channel a dialog was opened from — focus returns to its row when the dialog closes.
    val restoreCell = remember { FocusRequester() }
    var restoreChannelId by remember { mutableStateOf<Long?>(null) }
    var detail by remember { mutableStateOf<Pair<ChannelEntity, EpgProgrammeEntity>?>(null) }
    var matchingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var matchChooser by remember { mutableStateOf<ChannelEntity?>(null) }
    // Two-stage timeline navigation (#4): Right from a channel focuses its whole programme row (ROW
    // stage); OK steps into per-programme browsing (CELL stage) where Left/Right move a cursor and
    // Up/Down jump to the adjacent channel at the same time. cursorTime is the highlighted time.
    var inCellMode by remember { mutableStateOf(false) }
    var cursorTime by remember { mutableStateOf(0L) }
    // The pending focus target onEnter routes to: our own restore requests cross into this group
    // from outside, so onEnter must cooperate or it would hijack them to the first channel.
    var pendingEnter by remember { mutableStateOf<FocusRequester?>(null) }

    // No BackHandler here: the Guide is a top-level section, so Back is the shell's job (content →
    // sidebar → exit dialog). A screen-level handler would swallow Back forever and block app exit.
    LaunchedEffect(Unit) { vm.load() } // reload from DB each time the guide is opened
    LaunchedEffect(state.loading, state.channels.isNotEmpty()) {
        // Auto-focus the grid once it's actually composed (while state.loading the spinner branch
        // shows instead, so the cells aren't attached yet) — but never while typing a search, and
        // never when returning from playback (the tuned-channel restore below handles that).
        if (!state.loading && state.channels.isNotEmpty() && query.isBlank() && !restoreFocus) {
            kotlinx.coroutines.delay(80)
            // tunedCell fallback: when the last-tuned channel IS row 0, firstCell isn't attached.
            if (runCatching { firstCell.requestFocus() }.isFailure) runCatching { tunedCell.requestFocus() }
        }
    }

    // Back from a channel tuned in the guide: scroll to and refocus that channel's row. Must wait
    // for the reload (vm.load() runs on every mount) — while state.loading the grid isn't composed
    // at all (spinner branch), so a requestFocus would silently fail and burn the restore flag.
    LaunchedEffect(restoreFocus, state.loading, state.channels.size) {
        if (!restoreFocus || state.loading || state.channels.isEmpty()) return@LaunchedEffect
        val idx = vm.lastTunedChannelId?.let { id -> state.channels.indexOfFirst { it.id == id } } ?: -1
        val target = if (idx >= 0) tunedCell else firstCell
        if (idx >= 0) runCatching { rowListState.scrollToItem(idx) }
        pendingEnter = target
        kotlinx.coroutines.delay(80)
        runCatching { target.requestFocus() }
        onRestored()
    }

    // With a catch-up backward window the guide spans past→future; open it scrolled to "now" so the
    // current programmes are what you see first (past sits to the left, reachable with D-pad Left).
    val density = LocalDensity.current
    LaunchedEffect(state.windowStart, state.channels.isNotEmpty()) {
        if (state.channels.isEmpty()) return@LaunchedEffect
        val minutesBack = ((state.now - state.windowStart) / 60_000L).toInt()
        if (minutesBack <= SLOT_MIN) return@LaunchedEffect // no real lookback → leave at the start
        val px = with(density) { (minutesBack * PX_PER_MIN.value).dp.toPx() }.toInt()
        runCatching { hScroll.scrollTo(px) }
    }

    // In CELL mode, Back steps out to whole-row (ROW) selection instead of leaving the guide.
    BackHandler(enabled = inCellMode) { inCellMode = false }

    // Keep the highlighted (cursor) programme in view while browsing a row in CELL mode.
    LaunchedEffect(cursorTime, inCellMode) {
        if (!inCellMode || state.channels.isEmpty()) return@LaunchedEffect
        val minutes = ((cursorTime - state.windowStart) / 60_000L).toInt() - SLOT_MIN // one slot of left margin
        val px = with(density) { (minutes.coerceAtLeast(0) * PX_PER_MIN.value).dp.toPx() }.toInt()
        runCatching { hScroll.scrollTo(px) }
    }

    // Closing ANY of the guide's overlays (programme detail, the match chooser, the manual EPG picker)
    // would otherwise drop focus to the sidebar. Restore focus to the row the dialog was opened from.
    val anyDialogOpen = detail != null || matchChooser != null || matchingChannel != null
    var hadDialog by remember { mutableStateOf(false) }
    LaunchedEffect(anyDialogOpen) {
        if (anyDialogOpen) {
            hadDialog = true
            return@LaunchedEffect
        }
        if (!hadDialog) return@LaunchedEffect
        hadDialog = false
        val idx = restoreChannelId?.let { id -> state.channels.indexOfFirst { it.id == id } } ?: -1
        val target = if (idx >= 0) restoreCell else firstCell
        if (idx >= 0) runCatching { rowListState.scrollToItem(idx) }
        pendingEnter = target
        kotlinx.coroutines.delay(80)
        if (runCatching { target.requestFocus() }.isFailure) runCatching { firstCell.requestFocus() }
        restoreChannelId = null // focus is set; release the row so playback-restore can reuse it
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surface)
            // Entry from the sidebar lands on the first channel — unless a restore is pending
            // (back from playback / dialog close), which onEnter routes to instead of hijacking.
            // onEnter only fires for entries from OUTSIDE the group — search bar / refresh / back
            // are inside it, so moving up from the grid to them never re-triggers this.
            .focusProperties {
                onEnter = {
                    val target = pendingEnter ?: firstCell
                    pendingEnter = null
                    // tunedCell fallback: when the last-tuned channel IS row 0, firstCell isn't attached.
                    if (runCatching { target.requestFocus() }.isFailure) runCatching { tunedCell.requestFocus() }
                }
            }
            .focusGroup()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        // Header: back + title + date + refresh
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusableSurface(onClick = onBack, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), contentAlignment = Alignment.Center) { _ ->
                OwnTVIcon(OwnTVIcon.BACK, tint = colors.onSurface, modifier = Modifier.size(20.dp))
            }
            Text("TV Guide", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
            if (state.windowStart > 0) {
                Text(
                    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(state.windowStart)),
                    style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            // Guide sort: A–Z / Provider / Live TV (mirrors Live) / Catch-up (archive first; hidden when none).
            val sortLabel = when {
                sortGuide == SettingsRepository.GuideSort.CATCHUP && state.catchupCount == 0 -> SettingsRepository.GuideSort.LIVE_TV.label
                sortGuide == SettingsRepository.GuideSort.FAVORITES && state.favoriteCount == 0 -> SettingsRepository.GuideSort.LIVE_TV.label
                else -> sortGuide.label
            }
            // Category filter (#8): narrow the guide to one group instead of all channels at once.
            if (guideCategories.isNotEmpty()) {
                val catLabel = categoryFilter?.let { id -> guideCategories.firstOrNull { it.id == id }?.name } ?: "All"
                OwnTVButton("Category: $catLabel", onClick = { showCategoryPicker = true }, icon = OwnTVIcon.MENU, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.width(12.dp))
            }
            OwnTVButton("Sort: $sortLabel", onClick = vm::cycleGuideSort, icon = OwnTVIcon.SORT, style = OwnTVButtonStyle.SECONDARY)
            Spacer(Modifier.width(12.dp))
            // Smart-match: auto-link channels whose tvg-id doesn't match the EPG feed, by name (#13).
            if (matching) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OwnTVSpinner(sizeDp = 20)
                    Text("Matching…", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            } else {
                OwnTVButton("Auto-match EPG", onClick = vm::autoMatchEpg, icon = OwnTVIcon.EPG, style = OwnTVButtonStyle.SECONDARY)
            }
        }
        if (state.stats != null) {
            Spacer(Modifier.height(4.dp))
            Text(state.stats!!, style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
        // Outcome of the last auto-match run (auto-applied count / how many need review). Dismissible.
        matchSummary?.let { summary ->
            if (review.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                FocusableSurface(onClick = vm::clearReview, shape = RoundedCornerShape(10.dp), unfocusedContainerColor = colors.surfaceContainerHigh, contentAlignment = Alignment.CenterStart) { _ ->
                    Text(summary, style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        SearchBar(
            query = query,
            onQueryChange = vm::setQuery,
            placeholder = "Search guide channels…",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        when {
            state.loading -> CenterBox { OwnTVSpinner(sizeDp = 56) }
            // No EPG feed added yet → guide it can't fill. Point the user to EPG Sources.
            !state.hasEpgSources && state.channels.isEmpty() -> CenterBox {
                Text("No EPG added.", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add an EPG (XMLTV) source to fill the guide with programmes.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                OwnTVButton("Add EPG", onClick = onAddEpg, icon = OwnTVIcon.ADD)
            }
            state.channels.isEmpty() -> CenterBox {
                Text(state.message ?: "No guide.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
            else -> {
                // Time axis (shares hScroll with the rows below).
                val slots = ((state.windowEnd - state.windowStart) / (SLOT_MIN * 60_000L)).toInt()
                Row {
                    Spacer(Modifier.width(CHANNEL_COL))
                    Row(Modifier.horizontalScroll(hScroll)) {
                        for (i in 0 until slots) {
                            val slotMs = state.windowStart + i * SLOT_MIN * 60_000L
                            Text(
                                clock(slotMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width((SLOT_MIN * PX_PER_MIN.value).dp).padding(start = 6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                LazyColumn(state = rowListState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(state.channels, key = { _, ch -> ch.id }) { index, channel ->
                        GuideChannelRow(
                            vm = vm,
                            channel = channel,
                            windowStart = state.windowStart,
                            windowEnd = state.windowEnd,
                            now = state.now,
                            hScroll = hScroll,
                            labelFocus = when {
                                channel.id == restoreChannelId -> restoreCell
                                channel.id == vm.lastTunedChannelId -> tunedCell
                                index == 0 -> firstCell
                                else -> null
                            },
                            onTune = { vm.play(channel); onFullscreen() },
                            onOpen = { restoreChannelId = channel.id; detail = channel to it },
                            onMatchEpg = { restoreChannelId = channel.id; matchChooser = channel },
                            inCellMode = inCellMode,
                            cursorTime = cursorTime,
                            onEnterCell = { cursorTime = state.now; inCellMode = true },
                            onExitToChannels = { inCellMode = false },
                            onMoveCursor = { cursorTime = it },
                        )
                    }
                }
            }
        }
    }

    detail?.let { (channel, p) ->
        ProgrammeDetailDialog(
            channelName = channel.name,
            programme = p,
            canCatchup = vm.canCatchup(channel, p, state.now),
            onWatch = { detail = null; vm.play(channel); onFullscreen() },
            onPlayCatchup = { detail = null; vm.playCatchup(channel, p); onFullscreen() },
            onDismiss = { detail = null },
        )
    }

    // Long-press a channel → choose how to match its EPG: auto (match this one by name) or manual pick.
    matchChooser?.let { channel ->
        EpgMatchChooserDialog(
            channelName = channel.name,
            onAuto = { vm.autoMatchOne(channel); matchChooser = null },
            onManual = { matchChooser = null; matchingChannel = channel },
            onDismiss = { matchChooser = null },
        )
    }

    matchingChannel?.let { channel ->
        com.thirutricks.tllplayer.features.live.EpgMatchDialog(
            channelName = channel.name,
            currentMatch = vm.currentEpgMatch(channel),
            loadChannels = { vm.availableEpgChannels(it) },
            onPick = { vm.setEpgMatch(channel, it); matchingChannel = null },
            onClear = { vm.setEpgMatch(channel, null); matchingChannel = null },
            onDismiss = { matchingChannel = null },
        )
    }

    if (review.isNotEmpty()) {
        EpgMatchReviewDialog(
            suggestions = review,
            onAccept = vm::acceptSuggestion,
            onSkip = vm::dismissSuggestion,
            onAcceptAll = vm::acceptAllSuggestions,
            onSkipAll = vm::clearReview,
            onDone = vm::clearReview,
        )
    }

    if (showCategoryPicker) {
        com.thirutricks.tllplayer.features.settings.PickerDialog(
            title = "Guide category",
            options = listOf("ALL" to "All categories") + guideCategories.map { it.id.toString() to it.name },
            selected = categoryFilter?.toString() ?: "ALL",
            onSelect = { vm.setCategoryFilter(it.toLongOrNull()); showCategoryPicker = false },
            onDismiss = { showCategoryPicker = false },
            searchable = true,
        )
    }
}

/**
 * Review screen for the smart EPG matcher (#13): lists the lower-confidence suggestions the auto-match
 * couldn't apply on its own, so the user can accept the good ones and skip the rest. High-confidence
 * matches are applied automatically and never reach here.
 */
@Composable
private fun EpgMatchReviewDialog(
    suggestions: List<EpgViewModel.EpgMatchSuggestion>,
    onAccept: (EpgViewModel.EpgMatchSuggestion) -> Unit,
    onSkip: (EpgViewModel.EpgMatchSuggestion) -> Unit,
    onAcceptAll: () -> Unit,
    onSkipAll: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    BackHandler { onDone() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusGroup(),
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(640.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp)) {
            Text("Review EPG matches", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                "These channels were matched by name but weren't a confident fit. Accept the right ones; skip the rest.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(suggestions, key = { _, s -> s.channel.id }) { index, s ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.channel.name, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "→ ${s.epgName ?: s.epgChannelId}  ·  ${(s.score * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        FocusableSurface(
                            onClick = { onAccept(s) },
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            shape = RoundedCornerShape(10.dp),
                            unfocusedContainerColor = colors.primaryContainer,
                            contentAlignment = Alignment.Center,
                        ) { _ -> Text("Accept", style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                        FocusableSurface(
                            onClick = { onSkip(s) },
                            shape = RoundedCornerShape(10.dp),
                            unfocusedContainerColor = colors.surfaceContainerHigh,
                            contentAlignment = Alignment.Center,
                        ) { _ -> Text("Skip", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Accept all", onClick = onAcceptAll, icon = OwnTVIcon.PLAY)
                OwnTVButton("Skip all", onClick = onSkipAll, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                OwnTVButton("Done", onClick = onDone, style = OwnTVButtonStyle.SECONDARY)
            }
        }
    }
}

/**
 * Long-press chooser for a Guide channel's EPG: either auto-match just this channel by name, or open
 * the manual picker to choose its guide channel from the full list (#10/#13).
 */
@Composable
private fun EpgMatchChooserDialog(
    channelName: String,
    onAuto: () -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    BackHandler { onDismiss() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).focusGroup()
            .longPressMenuGuard(), // long-press OK is still held — don't auto-click Auto-match
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.width(440.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(24.dp)) {
            Text("Match EPG", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                "Set the guide data for “$channelName”.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OwnTVButton("Auto-match", onClick = onAuto, icon = OwnTVIcon.EPG, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus))
            Spacer(Modifier.height(10.dp))
            OwnTVButton("Pick manually", onClick = onManual, style = OwnTVButtonStyle.SECONDARY, icon = OwnTVIcon.SEARCH, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            OwnTVButton("Cancel", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
        }
    }
}

/**
 * One guide row: pinned tunable channel label + lazily loaded programme strip. Programmes are fetched
 * from the DB only when the row scrolls into view (indexed query + VM cache), so the guide can list
 * every channel without holding the whole day's data in memory.
 */
@Composable
private fun GuideChannelRow(
    vm: EpgViewModel,
    channel: ChannelEntity,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    hScroll: androidx.compose.foundation.ScrollState,
    labelFocus: FocusRequester?,
    onTune: () -> Unit,
    onOpen: (EpgProgrammeEntity) -> Unit,
    onMatchEpg: () -> Unit,
    inCellMode: Boolean,
    cursorTime: Long,
    onEnterCell: () -> Unit,
    onExitToChannels: () -> Unit,
    onMoveCursor: (Long) -> Unit,
) {
    val colors = OwnTVTheme.colors
    // Cache peek as the initial value → rows render instantly from the batch-loaded cache, no flash, no
    // per-row query. Re-key on cacheRevision so a row re-reads the cache when the background catch-up
    // lookback (pass 2) merges in.
    val cacheRevision by vm.cacheRevision.collectAsStateWithLifecycle()
    val programmes by produceState(initialValue = vm.cachedProgrammes(channel), channel.id, windowStart, cacheRevision) {
        value = vm.cachedProgrammes(channel) ?: vm.programmesFor(channel)
    }
    val labelFR = remember { FocusRequester() }
    val stripFR = remember { FocusRequester() }
    var stripFocused by remember { mutableStateOf(false) }

    Row {
        // Pinned channel label — OK tunes the channel; long-press opens the EPG-match chooser; Right
        // steps into the timeline (the strip becomes the focus target).
        FocusableSurface(
            onClick = onTune,
            onLongClick = onMatchEpg,
            modifier = Modifier.width(CHANNEL_COL).height(ROW_HEIGHT).padding(end = 6.dp)
                .focusRequester(labelFR)
                .then(if (labelFocus != null) Modifier.focusRequester(labelFocus) else Modifier)
                .focusProperties { right = stripFR }
                .onFocusChanged { if (it.isFocused) onExitToChannels() }, // back on a label ⇒ leave CELL stage
            shape = RoundedCornerShape(10.dp),
            unfocusedContainerColor = colors.surfaceContainerHigh,
            contentAlignment = Alignment.CenterStart,
        ) { focused ->
            Text(
                channel.number?.let { "$it  ${channel.name}" } ?: channel.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (focused) colors.primary else colors.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        // Programme strip = ONE focus target (cells aren't individually focusable). ROW stage outlines
        // the whole strip; OK enters CELL stage where Left/Right move the cursor and Up/Down jump rows.
        val rowSelected = stripFocused && !inCellMode
        Box(
            modifier = Modifier.weight(1f).height(ROW_HEIGHT)
                .focusRequester(stripFR)
                .onFocusChanged { stripFocused = it.isFocused }
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val progs = programmes
                    if (inCellMode) when (e.key) {
                        Key.DirectionLeft -> { moveGuideCursor(progs, cursorTime, -1, windowStart, onMoveCursor); true }
                        Key.DirectionRight -> { moveGuideCursor(progs, cursorTime, +1, windowStart, onMoveCursor); true }
                        Key.DirectionCenter, Key.Enter -> { progs?.let { openAtCursor(it, cursorTime, onOpen) }; true }
                        else -> false // Up/Down fall through to spatial nav (jump to the next channel's row)
                    } else when (e.key) {
                        Key.DirectionCenter, Key.Enter -> { if (!progs.isNullOrEmpty()) onEnterCell(); true }
                        Key.DirectionLeft -> { runCatching { labelFR.requestFocus() }; true } // back to the channel
                        Key.DirectionRight -> true // nothing further right — stay on the row
                        else -> false
                    }
                }
                .focusable()
                .clip(RoundedCornerShape(10.dp))
                .then(if (rowSelected) Modifier.border(Dimens.FocusBorderWidth, colors.focusBorder, RoundedCornerShape(10.dp)) else Modifier),
        ) {
            programmes?.let { progs ->
                ProgrammeStripCanvas(
                    programmes = progs,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    now = now,
                    highlightTime = if (stripFocused && inCellMode) cursorTime else null,
                    hScroll = hScroll,
                )
            }
        }
    }
}

/** Move the CELL-stage cursor [delta] programmes within [progs], reporting the new highlighted time. */
private fun moveGuideCursor(
    progs: List<EpgProgrammeEntity>?,
    cursorTime: Long,
    delta: Int,
    windowStart: Long,
    onMove: (Long) -> Unit,
) {
    if (progs.isNullOrEmpty()) return
    val curIdx = progs.indexOfLast { it.startMs <= cursorTime }.let { if (it < 0) 0 else it }
    val newIdx = (curIdx + delta).coerceIn(0, progs.size - 1)
    onMove(progs[newIdx].startMs.coerceAtLeast(windowStart))
}

/** Open the programme the cursor is on (the one airing at [cursorTime], else the nearest before it). */
private fun openAtCursor(progs: List<EpgProgrammeEntity>, cursorTime: Long, onOpen: (EpgProgrammeEntity) -> Unit) {
    val p = progs.firstOrNull { cursorTime in it.startMs until it.stopMs }
        ?: progs.lastOrNull { it.startMs <= cursorTime }
        ?: progs.firstOrNull()
    p?.let(onOpen)
}

/**
 * One channel's programme strip, drawn in a SINGLE Canvas (not ~48–336 composables per row). It reads the
 * shared [hScroll] offset to translate + cull to just the visible programmes, so a 7-day window stays cheap.
 * Per the perf review: a cached [rememberTextMeasurer] and pre-computed styles/labels keep allocations out of
 * the draw loop (no GC thrash → no scroll micro-stutter).
 */
@Composable
private fun ProgrammeStripCanvas(
    programmes: List<EpgProgrammeEntity>,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    highlightTime: Long?,
    hScroll: androidx.compose.foundation.ScrollState,
) {
    val colors = OwnTVTheme.colors
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 64)

    // Pre-computed once — nothing here is allocated inside the draw loop.
    val pxPerMin = with(density) { PX_PER_MIN.toPx() }
    val gapPx = with(density) { 4.dp.toPx() }
    val padPx = with(density) { 10.dp.toPx() }
    val borderPx = with(density) { Dimens.FocusBorderWidth.toPx() }
    val corner = with(density) { CornerRadius(10.dp.toPx(), 10.dp.toPx()) }
    val titleStyle = MaterialTheme.typography.titleSmall.copy(color = colors.onSurface)
    val titleNowStyle = MaterialTheme.typography.titleSmall.copy(color = colors.onPrimaryContainer)
    val timeStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val timeNowStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onPrimaryContainer)
    // Time labels built once (string formatting kept out of the per-frame draw loop).
    val labels = remember(programmes, now) {
        programmes.map { p ->
            val t = "${clock(p.startMs)} – ${clock(p.stopMs)}"
            if (now in p.startMs until p.stopMs) "NOW · $t" else t
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val scrollPx = hScroll.value.toFloat()
        val viewW = size.width
        val h = size.height
        programmes.forEachIndexed { i, p ->
            val s = p.startMs.coerceIn(windowStart, windowEnd)
            val e = p.stopMs.coerceIn(windowStart, windowEnd)
            if (e <= s) return@forEachIndexed
            val x = ((s - windowStart) / 60_000f) * pxPerMin - scrollPx
            val w = (((e - s) / 60_000f) * pxPerMin - gapPx).coerceAtLeast(0f)
            if (x + w <= 0f || x >= viewW) return@forEachIndexed // cull off-screen programmes
            val isNow = now in p.startMs until p.stopMs
            val hi = highlightTime != null && highlightTime in p.startMs until p.stopMs
            val bg = when { hi -> colors.card; isNow -> colors.primaryContainer; else -> colors.surfaceContainerHigh }
            drawRoundRect(color = bg, topLeft = Offset(x, 0f), size = Size(w, h), cornerRadius = corner)
            if (hi) drawRoundRect(color = colors.focusBorder, topLeft = Offset(x, 0f), size = Size(w, h), cornerRadius = corner, style = Stroke(borderPx))
            val textW = (w - padPx * 2f).toInt()
            if (textW > 8) {
                val tStyle = if (isNow && !hi) titleNowStyle else titleStyle
                val mStyle = if (isNow && !hi) timeNowStyle else timeStyle
                val title = measurer.measure(p.title, tStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = textW))
                val time = measurer.measure(labels[i], mStyle, overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = textW))
                val top = (h - (title.size.height + time.size.height + 2)) / 2f
                drawText(title, topLeft = Offset(x + padPx, top))
                drawText(time, topLeft = Offset(x + padPx, top + title.size.height + 2))
            }
        }
    }
}

@Composable
private fun ProgrammeDetailDialog(
    channelName: String,
    programme: EpgProgrammeEntity,
    canCatchup: Boolean,
    onWatch: () -> Unit,
    onPlayCatchup: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OwnTVTheme.colors
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BackHandler { onDismiss() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(20.dp)).background(colors.surfaceContainerHigh).padding(28.dp)) {
            Text(channelName.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.primary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(programme.title, style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
            Spacer(Modifier.height(8.dp))
            Text("${clock(programme.startMs)} – ${clock(programme.stopMs)}", style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant)
            if (!programme.description.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(programme.description!!, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OwnTVButton("Close", onClick = onDismiss, style = OwnTVButtonStyle.SECONDARY)
                Spacer(Modifier.weight(1f))
                // Catch-up channels: replay this programme from its start (seekable archive playback).
                if (canCatchup) {
                    OwnTVButton("Watch from start", onClick = onPlayCatchup, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
                    OwnTVButton("Watch channel", onClick = onWatch, style = OwnTVButtonStyle.SECONDARY)
                } else {
                    OwnTVButton("Watch channel", onClick = onWatch, icon = OwnTVIcon.PLAY, modifier = Modifier.focusRequester(fr))
                }
            }
        }
    }
}

@Composable
private fun CenterBox(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
    }
}
