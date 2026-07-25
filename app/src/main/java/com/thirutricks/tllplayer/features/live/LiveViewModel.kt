@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.thirutricks.tllplayer.features.live

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.filter
import androidx.paging.map
import com.thirutricks.tllplayer.core.customize.CustomizationStore
import com.thirutricks.tllplayer.core.customize.CustomizeKeys
import com.thirutricks.tllplayer.core.epg.CatchupUrl
import com.thirutricks.tllplayer.core.customize.SectionCustomizations
import com.thirutricks.tllplayer.core.customize.applyCustomizations
import com.thirutricks.tllplayer.core.database.dao.CategoryDao
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.FavoriteDao
import com.thirutricks.tllplayer.core.database.dao.HistoryDao
import com.thirutricks.tllplayer.core.database.dao.ProfileDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.dao.resolveExistingProfileId
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.FavoriteEntity
import com.thirutricks.tllplayer.core.database.entity.WatchHistoryEntity
import com.thirutricks.tllplayer.core.launcher.LauncherIntegrationRepository
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.core.model.SourceType
import com.thirutricks.tllplayer.core.parser.XtEpgEntry
import com.thirutricks.tllplayer.core.parser.XtreamClient
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.player.OwnTVPlayer
import com.thirutricks.tllplayer.ui.components.OwnTVIcon

/** Layer-2 rail selection for Live TV. */
sealed interface LiveKey {
    data object Favorites : LiveKey
    data object History : LiveKey
    data object All : LiveKey
    data class Folder(val id: Long) : LiveKey
}

/** A rail entry. Favorites/History carry an [icon] (rendered instead of the abbreviation). */
data class LiveRailItem(val key: LiveKey, val abbr: String, val title: String, val icon: OwnTVIcon? = null)

/** Now-playing + up-next EPG for the focused channel (null entries when the guide is unavailable). */
data class EpgNowNext(val now: XtEpgEntry?, val next: XtEpgEntry?, val upcoming: List<XtEpgEntry> = emptyList())

class LiveViewModel(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val xtreamClient: XtreamClient,
    private val customize: CustomizationStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
    private val epgDao: com.thirutricks.tllplayer.core.database.dao.EpgDao,
    private val epgSourceStore: com.thirutricks.tllplayer.core.epg.EpgSourceStore,
    val player: OwnTVPlayer,
    val previewEngine: com.thirutricks.tllplayer.player.LivePreviewEngine,
    private val forceMpvStore: com.thirutricks.tllplayer.core.player.ForceMpvStore,
) : ViewModel() {

    /** Channels pinned to mpv ("compatibility mode") — opened straight on mpv, bypassing ExoPlayer. Eagerly
     *  collected so the routing decision in [ensurePlaying] always sees the current set. Keyed by stream URL. */
    val forceMpvUrls: StateFlow<Set<String>> = forceMpvStore.urls
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** List ordering for this section (Playlist order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortLive
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.PLAYLIST)

    fun toggleSort() {
        viewModelScope.launch {
            settings.setSortLive(
                if (sortMode.value == SettingsRepository.SortMode.PLAYLIST) SettingsRepository.SortMode.ALPHA
                else SettingsRepository.SortMode.PLAYLIST,
            )
        }
    }

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)

    /** sourceId → type, warmed reactively from the profile's sources so channel routing knows whether a
     *  channel is TLL. Declared BEFORE ctx, which reads it during initialization. */
    private val sourceTypeCache = HashMap<Long, SourceType>()

    // Observe the active profile's sources REACTIVELY so adding/removing a playlist refreshes Live TV
    // immediately (it used to be read once at startup, so a new playlist showed nothing until restart).
    // Also warms the sourceType cache (sourceId → type) so channel routing knows whether a channel is TLL.
    private val ctx: StateFlow<Ctx> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(Ctx(pid, emptyList()))
            else sourceDao.observeForProfile(pid).map { srcs ->
                srcs.forEach { sourceTypeCache[it.id] = it.type }
                Ctx(pid, srcs.map { it.id })
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _previewChannel = MutableStateFlow<ChannelEntity?>(null)
    val previewChannel: StateFlow<ChannelEntity?> = _previewChannel.asStateFlow()

    private data class CachedEpg(val at: Long, val data: EpgNowNext)
    private val epgCache = HashMap<Long, CachedEpg>()

    /** Now/next for the focused channel — fetched (debounced) from the Xtream `get_short_epg` API. */
    val nowNext: StateFlow<EpgNowNext?> = _previewChannel
        .debounce(350)
        .distinctUntilChanged { a, b -> a?.id == b?.id }
        .mapLatest { ch -> ch?.let { loadEpg(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    /** This profile's hide/rename/reorder customizations for Live TV. */
    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.LIVE)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /**
     * Category DB ids of the profile's hidden categories. Hiding a category used to only drop its rail
     * folder — its channels still showed in "All Channels", search and recently-watched (so hiding the
     * adult groups left them all visible under ALL). Resolving the hidden category keys to ids here lets
     * those lists filter the channels out, so hiding a group hides its channels everywhere.
     */
    private val hiddenCategoryIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(emptySet())
            } else {
                combine(categoryDao.observe(c.sourceIds, MediaType.LIVE), custom) { cats, cust ->
                    if (cust.hiddenCategories.isEmpty()) emptySet()
                    else cats.filter { CustomizeKeys.category(it) in cust.hiddenCategories }.map { it.id }.toSet()
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Customizations + resolved hidden-category ids, bundled so the list pipeline takes one flow. */
    private data class CustState(val cust: SectionCustomizations, val hiddenCats: Set<Long>)
    private val custResolved: StateFlow<CustState> = combine(custom, hiddenCategoryIds) { c, h -> CustState(c, h) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CustState(SectionCustomizations(), emptySet()))

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(categoryDao.observe(c.sourceIds, MediaType.LIVE), custom) { cats, cust ->
                defaultRail + cats.applyCustomizations(cust).map { (cat, name) ->
                    LiveRailItem(LiveKey.Folder(cat.id), abbreviate(name), name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultRail)

    val channels: Flow<PagingData<ChannelEntity>> = combine(
        _selected,
        ctx,
        _search.map { it.trim() }.debounce(300).distinctUntilChanged(),
        sortMode,
        custResolved,
    ) { key, c, query, sort, cs -> Args(key, c, query, sort, cs) }
        .flatMapLatest { (key, c, query, sort, cs) ->
            // Customizations are applied to each fresh PagingData inside the pager chain — a PagingData
            // that the UI already collected must never be re-transformed (Paging forbids re-collection,
            // which is why hiding a channel used to crash). A customization change re-creates the pager.
            Pager(PagingConfig(pageSize = 80, prefetchDistance = 40, initialLoadSize = 120, maxSize = 400)) {
                pagingSource(key, c, query, sort)
            }.flow.map { paging ->
                val cust = cs.cust
                val hiddenCats = cs.hiddenCats
                if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty() && hiddenCats.isEmpty()) paging
                else paging
                    .filter { ch ->
                        CustomizeKeys.channel(ch) !in cust.hiddenItems &&
                            (ch.categoryId == null || ch.categoryId !in hiddenCats)
                    }
                    .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
            }
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode, val cs: CustState)

    /** Hide the focused channel from all lists (undo via Settings → Customize → Hidden channels). */
    fun hideChannel(channel: ChannelEntity) {
        if (_previewChannel.value?.id == channel.id) stopPreview()
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setItemHidden(pid, MediaType.LIVE, CustomizeKeys.channel(channel), channel.name, true)
        }
    }

    /** Rename the channel for this profile (blank restores the provider's name). */
    fun renameChannel(channel: ChannelEntity, newName: String?) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.renameItem(pid, MediaType.LIVE, CustomizeKeys.channel(channel), newName)
        }
    }

    /** Manually map a channel to an EPG channel id (null clears the override → auto-match). */
    fun setEpgMatch(channel: ChannelEntity, epgChannelId: String?) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(channel), epgChannelId)
        }
    }

    /** The current manual EPG id for a channel, or null if auto-matched. */
    fun currentEpgMatch(channel: ChannelEntity): String? = custom.value.epgMatches[CustomizeKeys.channel(channel)]

    /** Distinct EPG channels for the "Match EPG" picker (across the profile's playlists + EPG feeds). */
    suspend fun availableEpgChannels(query: String): List<com.thirutricks.tllplayer.core.database.entity.EpgChannelEntity> {
        if (currentProfileId() == null) return emptyList()
        val ids = ctx.value.sourceIds + epgSourceStore.getAll().map { it.id }
        if (ids.isEmpty()) return emptyList()
        return epgDao.listEpgChannels(ids, query.trim().lowercase(), 300)
    }

    val count: StateFlow<Int> = combine(_selected, ctx, hiddenCategoryIds) { key, c, hidden -> Triple(key, c, hidden) }
        .flatMapLatest { (key, c, hidden) -> countFlow(key, c, hidden) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val recentlyWatched: StateFlow<List<ChannelEntity>> = ctx
        .flatMapLatest { channelDao.recentlyWatched(it.profileId, 20) }
        .combine(custResolved) { list, cs ->
            list.filter {
                CustomizeKeys.channel(it) !in cs.cust.hiddenItems &&
                    (it.categoryId == null || it.categoryId !in cs.hiddenCats)
            }
                .map { ch -> cs.cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun select(key: LiveKey) {
        _selected.value = key
    }

    // --- Remember the last selected category (so reopening Live TV lands where you left off, #6) ---
    private fun LiveKey.serialize(): String = when (this) {
        LiveKey.Favorites -> "FAV"
        LiveKey.History -> "HIST"
        LiveKey.All -> "ALL"
        is LiveKey.Folder -> "FOLDER:$id"
    }

    private fun parseLiveKey(s: String): LiveKey? = when {
        s == "FAV" -> LiveKey.Favorites
        s == "HIST" -> LiveKey.History
        s == "ALL" -> LiveKey.All
        s.startsWith("FOLDER:") -> s.removePrefix("FOLDER:").toLongOrNull()?.let { LiveKey.Folder(it) }
        else -> null
    }

    init {
        // Persist the selected category (debounced — the rail fires select() on focus as you scroll).
        viewModelScope.launch {
            _selected.drop(1).debounce(800).distinctUntilChanged().collect { settings.setLastLiveCategory(it.serialize()) }
        }
        // Restore it once at startup — but only while still on the default (don't yank a user who already
        // navigated). A saved folder is honoured only once it actually exists in this profile's rail.
        viewModelScope.launch {
            val saved = parseLiveKey(settings.lastLiveCategory.first()) ?: return@launch
            if (saved is LiveKey.Folder) {
                val ok = kotlinx.coroutines.withTimeoutOrNull(5_000) {
                    railItems.first { list -> list.any { it.key == saved } }
                } != null
                if (ok && _selected.value == LiveKey.All) _selected.value = saved
            } else if (_selected.value == LiveKey.All) {
                _selected.value = saved
            }
        }
        // Persist the last focused/interacted channel (debounced), and restore it once at startup so opening
        // Live TV lands focus back on it. Restore leaves the preview disarmed (no auto-preview on launch).
        viewModelScope.launch {
            _previewChannel.drop(1).filterNotNull().map { it.id }.debounce(800).distinctUntilChanged()
                .collect { settings.setLastLiveChannelId(it) }
        }
        viewModelScope.launch {
            val savedId = settings.lastLiveChannelId.first()
            if (savedId > 0 && _previewChannel.value == null) {
                ctx.first { it.profileId >= 0 }
                channelDao.getById(savedId)?.let { if (_previewChannel.value == null) _previewChannel.value = it }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _search.value = query
    }

    fun onChannelFocused(channel: ChannelEntity) {
        _previewChannel.value = channel
    }

    // The ordered channel list of the row the user opened fullscreen from, so the player HUD can
    // zap up/down with the remote without going back to the list. Snapshot of the loaded paging
    // window (enough neighbours either side of the opened channel).
    private var zapList: List<ChannelEntity> = emptyList()
    private val _canZap = MutableStateFlow(false)
    val canZap: StateFlow<Boolean> = _canZap.asStateFlow()
    // The opened channel list, exposed so the in-player channel-list overlay can show & jump within it.
    private val _zapChannels = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val zapChannels: StateFlow<List<ChannelEntity>> = _zapChannels.asStateFlow()

    /** True when full-screen is running on the **ExoPlayer** engine (a promoted preview) rather than mpv.
     *  The shell renders the ExoPlayer surface instead of mpv's when this is set. */
    private val _liveOnExo = MutableStateFlow(false)
    val liveOnExo: StateFlow<Boolean> = _liveOnExo.asStateFlow()

    /** Called when anything OTHER than a promoted live channel takes over full-screen (a movie/episode,
     *  catch-up, an EPG/search channel — all play on mpv). Clears the ExoPlayer flag so the shell renders
     *  mpv's surface (not the leftover live channel) and stops the preview so it doesn't hold a connection. */
    /** Back out of full-screen to the Live screen: we're no longer full-screen on ExoPlayer, so the preview
     *  pane may re-take the engine (and re-apply the preview mute) on the next focus. Keeps the stream
     *  playing (no stop) — just clears the flag so [playPreview] works again. */
    fun onFullscreenExited() {
        _liveOnExo.value = false
    }

    fun clearLiveOnExo() {
        exoOutcomeJob?.cancel()
        _liveOnExo.value = false
        previewEngine.stop()
    }

    /** The most-recently-watched live channel for the active profile (for "resume last channel"). Waits
     *  for the profile to be known, then reads the newest watch-history row. Null if there is none. */
    suspend fun lastWatchedLiveChannel(): ChannelEntity? {
        val pid = ctx.first { it.profileId >= 0 }.profileId
        return channelDao.recentlyWatched(pid, 1).first().firstOrNull()
    }

    /** Open a channel fullscreen, remembering [list] so the remote can zap up/down from here.
     *  The Paging 3 snapshot passed from LiveScreen is a partial page — it contains only the
     *  channels visible at that moment, NOT the full source list. For a 800+ channel TLL source
     *  the opened channel is very often NOT in that snapshot, so [zap] finds index=-1 and
     *  silently does nothing. We always backfill from the DB in the background. */
    fun watchFullscreen(channel: ChannelEntity, list: List<ChannelEntity>) {
        zapList = list
        _zapChannels.value = list
        _canZap.value = list.size > 1
        ensurePlaying(channel)
        // Always load the full channel list for this source in the background.
        // Trigger condition: the channel is not found in the snapshot (paging partial-page case),
        // OR the list is too small (single-item startup "resume last channel" path).
        // Using a generous limit (5000) to cover the largest TLL/M3U sources.
        val channelInList = list.any { it.id == channel.id }
        if (!channelInList || list.size <= 1) {
            viewModelScope.launch {
                val srcs = ctx.first { it.profileId >= 0 }.sourceIds
                if (srcs.isEmpty()) return@launch
                val sourceId = channel.sourceId.takeIf { it in srcs } ?: srcs.first()
                val fullList = withContext(Dispatchers.IO) {
                    channelDao.allForSources(listOf(sourceId), 5000)
                }
                if (fullList.size > 1) {
                    zapList = fullList
                    _zapChannels.value = fullList
                    _canZap.value = true
                }
            }
        }
    }

    /** Zap to the neighbouring channel ([delta] = +1 down / -1 up) within the opened list, wrapping
     *  around at the ends so you never dead-end (last → first, first → last). */
    fun zap(delta: Int) {
        val list = zapList
        if (list.size < 2) return
        val i = list.indexOfFirst { it.id == _previewChannel.value?.id }
        if (i < 0) return
        val next = ((i + delta) % list.size + list.size) % list.size // modulo wrap (handles negatives)
        if (next == i) return
        ensurePlaying(list[next])
    }

    /** Go full-screen on [channel]. ExoPlayer is the **primary** live engine (instant for HLS, and it plays
     *  the channels mpv struggles to open): promote the running preview if it's already this channel, else
     *  (re)start ExoPlayer on it. We fall back to the full **mpv** player ONLY if ExoPlayer **errors** (a
     *  stream it can't open) — never just because it's still loading (clicking OK before the preview is ready
     *  used to drop to mpv and stick on a black screen for HLS). */
    fun ensurePlaying(channel: ChannelEntity) {
        _previewChannel.value = channel
        timeshiftJob?.cancel(); tickJob?.cancel(); _timeshiftOffsetSec.value = null // normal live = not timeshifted
        // Self-learning routing: a channel the user pinned to mpv skips ExoPlayer entirely (no artifacts/silent
        // first), straight to the engine that plays it. Everyone else gets the fast ExoPlayer-first path.
        val pinned = channel.streamUrl in forceMpvUrls.value
        android.util.Log.i(ENGINE_TAG, "tune '${channel.name}' -> ${if (pinned) "mpv (pinned)" else "exoplayer"}")
        if (pinned) startOnMpv(channel) else startOnExo(channel)
        recordLiveHistory(channel)
    }

    /** Resolve this channel's source type from its sourceId. Returns null while the DB read is pending or
     *  if the source was removed; callers treat null as "not TLL" (normal ExoPlayer/mpv path). */
    private fun sourceTypeOf(channel: ChannelEntity): SourceType? = sourceTypeCache[channel.sourceId]

    private fun startOnExo(channel: ChannelEntity) {
        _liveOnExo.value = true
        player.stop() // free mpv (decoder/connection) if a previous full-screen used it
        if (previewEngine.currentUrl == channel.streamUrl) {
            previewEngine.setMuted(false) // promote — instant if already PLAYING, otherwise keeps loading
        } else {
            previewEngine.play(
                channel.streamUrl, muted = false,
                meta = com.thirutricks.tllplayer.player.MediaMeta(title = channel.name, logoUrl = channel.logoUrl),
            )
        }
        watchExoOutcome(channel)
    }

    /** Start [channel] on the full mpv engine (pinned "compatibility" channel, or an ExoPlayer fallback). */
    private fun startOnMpv(channel: ChannelEntity) { viewModelScope.launch { fallbackToMpv(channel) } }

    /** HUD "compatibility mode" toggle: pin/unpin the current channel to mpv and swap engines live. */
    fun toggleForceMpv() {
        val channel = _previewChannel.value ?: return
        val turnOn = channel.streamUrl !in forceMpvUrls.value
        android.util.Log.i(ENGINE_TAG, "compat toggle '${channel.name}' -> ${if (turnOn) "mpv" else "exoplayer"} (currentlyOnExo=${_liveOnExo.value})")
        viewModelScope.launch {
            forceMpvStore.set(channel.streamUrl, turnOn)
            when {
                turnOn && _liveOnExo.value -> fallbackToMpv(channel) // ExoPlayer → mpv now
                !turnOn && !_liveOnExo.value -> {                    // mpv → ExoPlayer now
                    player.stop()
                    delay(500) // let mpv's decoder/surface release before ExoPlayer takes over
                    if (_previewChannel.value?.streamUrl == channel.streamUrl) startOnExo(channel)
                }
            }
        }
    }

    fun ensurePlayingById(channelId: Long) {
        viewModelScope.launch {
            val channel = channelDao.getById(channelId) ?: return@launch
            ensurePlaying(channel)
        }
    }

    suspend fun ensurePlayingByIdAsync(channelId: Long, zapChannels: List<ChannelEntity> = emptyList()): Boolean {
        val channel = channelDao.getById(channelId) ?: return false
        // Backfill the zap list if the caller didn't provide one (e.g. opening a channel from Home / a
        // deep link). Without this, canZap stays false and D-pad up/down silently does nothing — the zap
        // function itself is fine; the missing list was the gap. Load this channel's source's channels.
        if (zapChannels.isEmpty()) {
            val srcs = ctx.first { it.profileId >= 0 }.sourceIds
            if (srcs.isNotEmpty()) {
                val sourceId = channel.sourceId.takeIf { it in srcs } ?: srcs.first()
                val list = channelDao.allForSources(listOf(sourceId), 500)
                if (list.size > 1) { zapList = list; _zapChannels.value = list; _canZap.value = true; ensurePlaying(channel); return true }
            }
        }
        zapList = zapChannels
        _zapChannels.value = zapChannels
        _canZap.value = zapChannels.size > 1
        ensurePlaying(channel)
        return true
    }

    /** One-shot: hand [channel] to mpv if ExoPlayer can't play it fully — either it **errors** opening, or it
     *  plays but ExoPlayer can decode **none of its audio** (e.g. an AC3/E-AC3/DTS movie file added via M3U,
     *  on a device without that decoder — it'd play silently). mpv (FFmpeg) decodes everything. */
    private var exoOutcomeJob: Job? = null
    private fun watchExoOutcome(channel: ChannelEntity) {
        exoOutcomeJob?.cancel()
        exoOutcomeJob = viewModelScope.launch {
            val terminal = previewEngine.state.first {
                it == com.thirutricks.tllplayer.player.LivePreviewEngine.State.PLAYING ||
                    it == com.thirutricks.tllplayer.player.LivePreviewEngine.State.ERROR
            }
            if (!isStill(channel)) return@launch
            if (terminal == com.thirutricks.tllplayer.player.LivePreviewEngine.State.ERROR) { fallbackToMpv(channel); return@launch }
            // PLAYING: give the track list a moment to settle, then route silent (undecodable-audio) streams to mpv.
            delay(300)
            if (isStill(channel) && previewEngine.audioUnsupported.value) fallbackToMpv(channel)
        }
    }

    private fun isStill(channel: ChannelEntity) =
        _liveOnExo.value && _previewChannel.value?.streamUrl == channel.streamUrl

    private suspend fun fallbackToMpv(channel: ChannelEntity) {
        android.util.Log.i(ENGINE_TAG, "starting mpv for '${channel.name}'")
        _liveOnExo.value = false            // shell flips to mpv's surface
        previewEngine.stop()
        delay(500)                          // let ExoPlayer's decoder release before mpv inits
        if (_previewChannel.value?.streamUrl == channel.streamUrl) {
            player.play(channel.streamUrl, title = channel.name, logoUrl = channel.logoUrl, isLive = true, muted = false, sourceType = sourceTypeOf(channel))
        }
    }

    private var historyJob: Job? = null

    private fun recordLiveHistory(channel: ChannelEntity) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            delay(5_000L)
            val pid = currentProfileId() ?: return@launch
            Log.d(TAG, "ensurePlaying history profile=$pid channelId=${channel.id}")
            runCatching {
                historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }.onFailure { t ->
                Log.w(TAG, "ensurePlaying history record failed channelId=${channel.id} profile=$pid", t)
            }
            runCatching { launcherIntegrationRepository.refreshRecentLive(pid) }
        }
    }

    // ---- Catch-up from Live TV: pick a recent programme to replay from the archive (#proposal) ----

    /** Recent (already-aired) programmes for a catch-up channel, newest first — drives the Live TV
     *  catch-up picker. Bounded to the EPG we retain (≈ 2 days) and the channel's archive window. */
    suspend fun catchupProgrammes(ch: ChannelEntity): List<com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity> = withContext(Dispatchers.IO) {
        if (!ch.catchup) return@withContext emptyList()
        val epgKey = (custom.value.epgMatches[CustomizeKeys.channel(ch)] ?: ch.epgChannelId)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@withContext emptyList()
        val now = System.currentTimeMillis()
        val windowMs = (ch.catchupDays.coerceAtLeast(1) * 24L * 60 * 60 * 1000).coerceAtMost(CATCHUP_LOOKBACK_CAP_MS)
        val ids = ctx.value.sourceIds + epgSourceStore.getAll().map { it.id }
        epgDao.programmesForChannel(ids, epgKey, now - windowMs, now + 60 * 60 * 1000)
            .filter { it.startMs <= now }          // already started → catch-up applies
            .sortedByDescending { it.startMs }      // most recent first
            .take(80)
    }

    /** Replay a past programme from the channel's archive (seekable, like the Guide's "Watch from start"). */
    fun playCatchupProgramme(ch: ChannelEntity, programme: com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity) {
        viewModelScope.launch {
            val sourceType = sourceTypeOf(ch)
            val url = withContext(Dispatchers.IO) {
                val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
                CatchupUrl.forSource(ch, programme, source, settings.resolveCatchupTimeZone(), xtreamClient)
            } ?: return@launch
            _previewChannel.value = ch
            _timeshiftOffsetSec.value = null // guide archive isn't the live-rewind timeshift
            clearLiveOnExo() // catch-up is a VOD-style archive on mpv, not the live ExoPlayer channel
            // isLive=false → seekable archive; preferSoftware → tolerate mid-GOP archive segments.
            player.play(url, title = ch.name, subtitle = programme.title, logoUrl = ch.logoUrl, isLive = false, preferSoftware = true, sourceType = sourceType)
        }
    }

    // ---- Live rewind / timeshift -------------------------------------------------------------------
    // Watch a catch-up-capable live channel a few minutes behind the live edge (a missed goal, etc.) using
    // the provider's rolling archive (Xtream timeshift / M3U catchup), then jump back to live. The archive
    // is a VOD-style stream on mpv (preferSoftware, mid-GOP tolerant); "Go to live" returns to ExoPlayer.
    private val _timeshiftOffsetSec = MutableStateFlow<Int?>(null) // null = at the live edge; >0 = N s behind
    val timeshiftOffsetSec: StateFlow<Int?> = _timeshiftOffsetSec.asStateFlow()

    /** True when the channel on screen records an archive — the HUD then offers "Rewind" on live. */
    val canRewindLive: StateFlow<Boolean> =
        _previewChannel.map { it?.catchup == true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var timeshiftJob: Job? = null
    private var tickJob: Job? = null
    private var timeshiftStartWall = 0L // wall-clock time of the loaded archive's start (for the live counter)
    private val rewindStepSec = 30

    /** 30 s buttons. */
    fun rewindLive() = scrubLive(rewindStepSec)
    fun forwardLive() = scrubLive(-rewindStepSec)

    /** Move [deltaSec] further back (+) or toward live (−) into the archive (also drives the timeline
     *  scrubber). Coalesced so holding a key scrubs freely and loads the archive once at the final point;
     *  reaching the live edge returns to the real-time stream. */
    fun scrubLive(deltaSec: Int) {
        val ch = _previewChannel.value ?: return
        if (!ch.catchup) return
        val maxBack = (ch.catchupDays.takeIf { it > 0 } ?: 7) * 24 * 3600
        val next = ((_timeshiftOffsetSec.value ?: 0) + deltaSec).coerceIn(0, maxBack)
        if (next == 0) { goToLive(); return }
        _timeshiftOffsetSec.value = next
        scheduleTimeshiftLoad(ch, next)
    }

    /** Jump back to the real-time live edge (back on the fast ExoPlayer engine). */
    fun goToLive() {
        timeshiftJob?.cancel(); tickJob?.cancel()
        _timeshiftOffsetSec.value = null
        _previewChannel.value?.let { ensurePlaying(it) }
    }

    private fun scheduleTimeshiftLoad(ch: ChannelEntity, offsetSec: Int) {
        timeshiftJob?.cancel(); tickJob?.cancel()
        timeshiftJob = viewModelScope.launch {
            delay(350) // coalesce rapid rewind/forward presses into one archive load
            val nowMs = System.currentTimeMillis()
            val startMs = nowMs - offsetSec * 1000L
            val tz = withContext(Dispatchers.IO) { settings.resolveCatchupTimeZone() }
            val url = withContext(Dispatchers.IO) {
                val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
                buildLiveTimeshiftUrl(ch, source, startMs, offsetSec, tz)
            } ?: return@launch
            if (_timeshiftOffsetSec.value == null) return@launch // user jumped back to live meanwhile
            // Show the clock time being watched (handy for the user; no credentials in logs).
            val localLabel = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .apply { timeZone = java.util.TimeZone.getDefault() }.format(java.util.Date(startMs))
            _previewChannel.value = ch
            clearLiveOnExo() // archive plays as a VOD-style mpv stream, not the live ExoPlayer channel
            player.play(url, title = ch.name, subtitle = "Rewind · $localLabel", logoUrl = ch.logoUrl, isLive = false, preferSoftware = true, sourceType = sourceTypeOf(ch))
            timeshiftStartWall = startMs
            startOffsetTick()
        }
    }

    /** Tick the "behind live" counter down as the archive plays forward (offset = realNow − watched time =
     *  realNow − (archive start + playback position)). Pausing makes it grow (you fall further behind). */
    private fun startOffsetTick() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                if (_timeshiftOffsetSec.value == null) break
                val behindSec = ((System.currentTimeMillis() - (timeshiftStartWall + player.position.value)) / 1000)
                _timeshiftOffsetSec.value = behindSec.toInt().coerceAtLeast(0)
            }
        }
    }

    private fun buildLiveTimeshiftUrl(ch: ChannelEntity, source: com.thirutricks.tllplayer.core.database.entity.SourceEntity, startMs: Long, offsetSec: Int, tz: java.util.TimeZone): String? {
        val durationMin = (offsetSec / 60 + 5).coerceAtLeast(1) // rewound window + buffer to play up to live
        return when (source.type) {
            SourceType.XTREAM -> ch.remoteId?.let { xtreamClient.timeshiftUrl(source, it, startMs, durationMin, tz) }
            SourceType.M3U, SourceType.TLL -> CatchupUrl.forM3u(ch.streamUrl, null, ch.catchupSource, startMs, startMs + durationMin * 60_000L)
            else -> null
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(channel.id)) {
                favoriteDao.remove(pid, MediaType.LIVE, channel.id)
            } else {
                favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }
        }
    }

    fun stopPreview() {
        previewEngine.stop()
        player.stop()
        _previewChannel.value = null
    }

    /** Now/next for [ch], cached ~5 min. Prefers a manual EPG match / stored bulk guide, then falls
     *  back to Xtream's short EPG API. */
    private suspend fun loadEpg(ch: ChannelEntity): EpgNowNext? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        epgCache[ch.id]?.takeIf { now - it.at < 5 * 60_000 }?.let { return@withContext it.data }

        // 1) Bulk guide via the effective EPG id (manual match overrides the channel's own id).
        val epgKey = (custom.value.epgMatches[CustomizeKeys.channel(ch)] ?: ch.epgChannelId)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (epgKey != null) {
            val nowProg = epgDao.nowPlaying(epgKey, now)
            val future = epgDao.upcoming(epgKey, now, 6).first().filter { it.startMs > (nowProg?.startMs ?: 0) }
            val nextProg = future.firstOrNull()
            if (nowProg != null || nextProg != null) {
                val result = EpgNowNext(nowProg?.toXt(), nextProg?.toXt(), future.drop(1).take(4).map { it.toXt() })
                epgCache[ch.id] = CachedEpg(now, result)
                return@withContext result
            }
        }

        // 2) Xtream short-EPG API fallback.
        val streamId = ch.remoteId ?: return@withContext null
        val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
        if (source.type != SourceType.XTREAM) return@withContext null
        val entries = runCatching { xtreamClient.getShortEpg(source, streamId, limit = 8) }
            .getOrNull().orEmpty()
        val current = entries.firstOrNull { it.startMs <= now && it.stopMs > now }
            ?: entries.firstOrNull { it.stopMs > now }
        val future = entries.filter { it.startMs > (current?.startMs ?: now) }.sortedBy { it.startMs }
        val result = EpgNowNext(current, future.firstOrNull(), future.drop(1).take(4))
        epgCache[ch.id] = CachedEpg(now, result)
        result
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    private fun com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity.toXt() =
        XtEpgEntry(title = title, description = description, startMs = startMs, stopMs = stopMs)

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, ChannelEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        return if (query.isBlank()) {
            when (key) {
                LiveKey.All -> if (playlist) channelDao.pagingAllOriginal(ids) else channelDao.pagingAll(ids)
                LiveKey.Favorites -> channelDao.pagingFavorites(c.profileId)
                LiveKey.History -> channelDao.pagingHistory(c.profileId)
                is LiveKey.Folder -> if (playlist) channelDao.pagingByCategory(key.id) else channelDao.pagingByCategoryAlpha(key.id)
            }
        } else {
            when (key) {
                LiveKey.All -> channelDao.searchAll(query, ids)
                LiveKey.Favorites -> channelDao.searchFavorites(query, c.profileId)
                LiveKey.History -> channelDao.searchHistory(query, c.profileId)
                is LiveKey.Folder -> channelDao.searchInCategory(query, key.id)
            }
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx, hiddenCats: Set<Long>): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All -> if (hiddenCats.isEmpty()) channelDao.countAll(ids) else channelDao.countAllExcluding(ids, hiddenCats.toList())
            LiveKey.Favorites -> channelDao.countFavorites(c.profileId)
            LiveKey.History -> historyDao.count(c.profileId, MediaType.LIVE)
            is LiveKey.Folder -> channelDao.countByCategory(key.id)
        }
    }

    private companion object {
        const val ENGINE_TAG = "LiveEngine"
        const val TAG = "OwnTVHome"
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "FAV", "Favorites", OwnTVIcon.STAR),
            LiveRailItem(LiveKey.History, "HIS", "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "ALL", "All Channels"),
        )
        const val CATCHUP_LOOKBACK_CAP_MS = 48L * 60 * 60 * 1000 // bounded by the EPG we retain (~2 days)
    }
}

/** Short 2–3 char rail label from a category name. */
private fun abbreviate(name: String): String {
    val cleaned = name.filter { it.isLetterOrDigit() || it == ' ' }.trim()
    val words = cleaned.split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "•"
        words.size == 1 -> words[0].take(3).uppercase()
        else -> words.take(3).joinToString("") { it.first().uppercase() }
    }
}
