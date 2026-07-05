@file:OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class) // debounce, flatMapLatest

package com.thirutricks.tllplayer.features.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thirutricks.tllplayer.core.customize.SectionCustomizations
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.EpgDao
import com.thirutricks.tllplayer.core.database.dao.HistoryDao
import com.thirutricks.tllplayer.core.database.dao.ProfileDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.dao.resolveExistingProfileId
import com.thirutricks.tllplayer.core.epg.CatchupUrl
import com.thirutricks.tllplayer.core.model.SourceType
import com.thirutricks.tllplayer.core.parser.XtreamClient
import com.thirutricks.tllplayer.core.customize.CustomizationStore
import com.thirutricks.tllplayer.core.customize.CustomizeKeys
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity
import com.thirutricks.tllplayer.core.database.entity.WatchHistoryEntity
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.core.network.ConnectivityObserver
import com.thirutricks.tllplayer.core.repository.EpgRepository
import com.thirutricks.tllplayer.core.repository.SourceRepository
import com.thirutricks.tllplayer.core.util.friendlySyncError
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.player.OwnTVPlayer

data class EpgUiState(
    /** All channels with guide data in the window; each row loads its own programmes lazily. */
    val channels: List<ChannelEntity> = emptyList(),
    val windowStart: Long = 0,
    val windowEnd: Long = 0,
    val now: Long = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    /** False when the user hasn't added any EPG source yet → the screen shows an "Add EPG" prompt. */
    val hasEpgSources: Boolean = true,
    /** "N channels · M programmes" once a guide is stored — the visible proof the EPG feed works. */
    val stats: String? = null,
    /** How many of the profile's channels advertise catch-up — 0 hides the Catch-up sort option. */
    val catchupCount: Int = 0,
    /** How many of the profile's channels are favourited — 0 hides the Favorites sort option. */
    val favoriteCount: Int = 0,
)

/**
 * Drives the EPG guide grid. Loads the active profile's EPG-capable channels and the programmes in a
 * rolling [GRID_HOURS] window from the DB, and can re-download the bulk XMLTV guide via [EpgRepository].
 */
class EpgViewModel(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val profileDao: ProfileDao,
    private val epgRepository: EpgRepository,
    private val epgSourceStore: com.thirutricks.tllplayer.core.epg.EpgSourceStore,
    private val connectivity: ConnectivityObserver,
    private val customize: CustomizationStore,
    private val historyDao: HistoryDao,
    private val sourceDao: SourceDao,
    private val xtream: XtreamClient,
    val player: OwnTVPlayer,
    private val favoriteDao: com.thirutricks.tllplayer.core.database.dao.FavoriteDao,
    private val categoryDao: com.thirutricks.tllplayer.core.database.dao.CategoryDao,
) : ViewModel() {

    /** Guide category filter: null = all channels, otherwise only that category's channels (#8). */
    private val _categoryFilter = MutableStateFlow<Long?>(null)
    val categoryFilter: StateFlow<Long?> = _categoryFilter.asStateFlow()

    /** Live categories for the active profile — drives the guide's "Category" picker. */
    val guideCategories: StateFlow<List<com.thirutricks.tllplayer.core.database.entity.CategoryEntity>> =
        settings.activeProfileId
            .flatMapLatest { pid ->
                if (pid < 0) flowOf(emptyList())
                else sourceDao.observeForProfile(pid).flatMapLatest { srcs -> categoryDao.observe(srcs.map { it.id }, MediaType.LIVE) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCategoryFilter(categoryId: Long?) { _categoryFilter.value = categoryId }

    private val _state = MutableStateFlow(EpgUiState())
    val state: StateFlow<EpgUiState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // Per-row programme cache (epg key → programmes in the current window). Rows re-read it instantly
    // when scrolled back into view; cleared whenever the window/data reloads.
    private val rowCache = java.util.concurrent.ConcurrentHashMap<String, List<EpgProgrammeEntity>>()
    @Volatile private var loadedSourceIds: List<Long> = emptyList()
    // The window the rowCache was filled for — re-batch only when it actually changes (a sort/filter change
    // keeps the same window, so the cache stays valid and we skip the reload entirely).
    @Volatile private var cachedWindow: Pair<Long, Long>? = null
    @Volatile private var lastStored = -1 // stored programme count the cache was built from (data-change guard)
    // Bumped when the background catch-up lookback (pass 2) merges into rowCache, so visible rows re-read it.
    private val _cacheRevision = MutableStateFlow(0)
    val cacheRevision: StateFlow<Int> = _cacheRevision.asStateFlow()

    /** Synchronous cache peek — lets a re-composed row render instantly without a loading flash. */
    fun cachedProgrammes(channel: ChannelEntity): List<EpgProgrammeEntity>? =
        channel.epgChannelId?.trim()?.lowercase()?.let { rowCache[it] }

    /** Lazily loads one row's programmes (indexed query + cache) as the row scrolls into view. */
    suspend fun programmesFor(channel: ChannelEntity): List<EpgProgrammeEntity> {
        val key = channel.epgChannelId?.trim()?.lowercase() ?: return emptyList()
        rowCache[key]?.let { return it }
        val s = _state.value
        val list = epgDao.programmesForChannel(loadedSourceIds, key, s.windowStart, s.windowEnd)
        rowCache[key] = list
        return list
    }

    init {
        // Re-filter the grid as the user types (DB-level, so it searches ALL guide channels, not
        // just the visible rows). drop(1): the screen triggers the initial load itself.
        _query
            .drop(1)
            .debounce(300)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        // Reload the guide when its own sort, or (for the LIVE_TV mode) the Live sort, changes.
        // drop(1): the screen triggers the initial load itself.
        settings.sortGuide
            .drop(1)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        // Reload when the category filter changes (#8).
        _categoryFilter
            .drop(1)
            .distinctUntilChanged()
            .onEach { load() }
            .launchIn(viewModelScope)
        settings.sortLive
            .drop(1)
            .distinctUntilChanged()
            .onEach { if (settings.sortGuide.first() == SettingsRepository.GuideSort.LIVE_TV) load() }
            .launchIn(viewModelScope)
    }

    /** The Guide's current sort, for the header button. */
    val sortGuide: StateFlow<SettingsRepository.GuideSort> = settings.sortGuide
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.GuideSort.LIVE_TV)

    /** Cycle the Guide sort: A–Z → Provider → Live TV → Catch-up → … (Catch-up only when one exists). */
    fun cycleGuideSort() {
        viewModelScope.launch {
            val modes = SettingsRepository.GuideSort.entries
                .filter { it != SettingsRepository.GuideSort.CATCHUP || _state.value.catchupCount > 0 }
                .filter { it != SettingsRepository.GuideSort.FAVORITES || _state.value.favoriteCount > 0 }
            val cur = modes.indexOf(sortGuide.value).let { if (it < 0) 0 else it }
            settings.setSortGuide(modes[(cur + 1) % modes.size])
        }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** The channel last tuned from the guide — the screen refocuses its row after fullscreen exits. */
    var lastTunedChannelId: Long? = null
        private set

    /** True once a guide channel is playing fullscreen and there's a list to step through, so the
     *  player HUD's CH+/CH- keys can zap up/down the guide's channels (like the Live list). */
    private val _canZap = MutableStateFlow(false)
    val canZap: StateFlow<Boolean> = _canZap.asStateFlow()

    /** Tune to a channel from the guide (fullscreen playback + history, like the Live list). */
    fun play(channel: ChannelEntity) {
        lastTunedChannelId = channel.id
        _canZap.value = _state.value.channels.size > 1
        player.play(channel.streamUrl, title = channel.name, logoUrl = channel.logoUrl, isLive = true)
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            runCatching {
                historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }.onFailure { t ->
                android.util.Log.w("OwnTVHome", "play history record failed channelId=${channel.id} profile=$pid", t)
            }
        }
    }

    /**
     * Play a past programme from the channel's catch-up archive (seekable, like VOD). Used by the
     * Guide's "Watch from start / Play recording" action on catch-up channels.
     */
    fun playCatchup(channel: ChannelEntity, programme: EpgProgrammeEntity) {
        viewModelScope.launch {
            val url = withContext(kotlinx.coroutines.Dispatchers.IO) { catchupUrlFor(channel, programme) }
            if (url == null) {
                _matchSummary.value = "Catch-up isn't available for this channel."
                return@launch
            }
            lastTunedChannelId = channel.id
            _canZap.value = false // archive playback isn't part of the live zap list
            // isLive = false → the archive plays back seekable, with a normal progress bar.
            // preferSoftware → tolerate mid-GOP archive segments the hardware decoder can't (blank/crash).
            player.play(url, title = channel.name, subtitle = programme.title, logoUrl = channel.logoUrl, isLive = false, preferSoftware = true)
        }
    }

    /** Build the catch-up URL for a [programme] on [channel], or null if the provider can't serve it. */
    private suspend fun catchupUrlFor(channel: ChannelEntity, programme: EpgProgrammeEntity): String? {
        val source = sourceDao.getById(channel.sourceId) ?: return null
        return CatchupUrl.forSource(channel, programme, source, settings.resolveCatchupTimeZone(), xtream)
    }

    /** True when a programme can be played from the archive: a catch-up channel, already started, and
     *  still inside the channel's archive window. The Guide gates its "Watch from start" button on this. */
    fun canCatchup(channel: ChannelEntity, programme: EpgProgrammeEntity, now: Long): Boolean {
        if (!channel.catchup || programme.startMs > now) return false
        val windowMs = channel.catchupDays.coerceAtLeast(1) * 24L * 60 * 60 * 1000
        return now - programme.startMs <= windowMs
    }

    // This profile's customizations — for changing a channel's manual EPG match from the Guide (#10).
    private val custom: StateFlow<SectionCustomizations> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(SectionCustomizations()) else customize.observe(pid, MediaType.LIVE) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SectionCustomizations())

    /** The channel's current manual EPG match (or null if auto-matched). */
    fun currentEpgMatch(channel: ChannelEntity): String? = custom.value.epgMatches[CustomizeKeys.channel(channel)]

    /** Set/clear a channel's manual EPG match (null clears → auto-match), then reload the guide. */
    fun setEpgMatch(channel: ChannelEntity, epgChannelId: String?) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(channel), epgChannelId)
            if (epgChannelId != null) fillMatchedInBackground(listOf(epgChannelId)) else load()
        }
    }

    /** Fill in matched channels' programmes from the cached XMLTV (no network) and refresh the guide — in the
     *  BACKGROUND so the match action/spinner returns instantly and doesn't wait on a full cache re-parse. */
    private fun fillMatchedInBackground(epgIds: Collection<String>) {
        viewModelScope.launch {
            val ids = epgIds.map { it.trim().lowercase() }.filterTo(HashSet()) { it.isNotBlank() }
            if (ids.isEmpty()) return@launch
            // One cache pass for the whole set; only re-sync over the network if the cache is gone/stale.
            val handled = runCatching { epgRepository.storeProgrammesForIdsFromCache(ids) }.getOrDefault(false)
            if (!handled) runCatching { refreshAllEpgFromNetwork() }
            load()
        }
    }

    private suspend fun refreshAllEpgFromNetwork() {
        val pid = settings.activeProfileId.first()
        if (pid >= 0) sourceRepository.observeSources(pid).first().forEach { runCatching { epgRepository.refresh(it) } }
        epgSourceStore.getAll().forEach { runCatching { epgRepository.refreshUrl(it.id, it.url, it.userAgent) } }
    }

    // ---- Smart EPG matching (#13): scan channels with no working guide and match them by name ----

    /** A proposed EPG match for a channel that didn't auto-resolve confidently enough to apply. */
    data class EpgMatchSuggestion(
        val channel: ChannelEntity,
        val epgChannelId: String,
        val epgName: String?,
        val score: Double,
    )

    private val _matching = MutableStateFlow(false)
    val matching: StateFlow<Boolean> = _matching.asStateFlow()

    /** Low-confidence suggestions awaiting the user's accept/skip (high-confidence ones auto-apply). */
    private val _review = MutableStateFlow<List<EpgMatchSuggestion>>(emptyList())
    val review: StateFlow<List<EpgMatchSuggestion>> = _review.asStateFlow()

    /** One-line outcome of the last auto-match run, shown as a transient banner. */
    private val _matchSummary = MutableStateFlow<String?>(null)
    val matchSummary: StateFlow<String?> = _matchSummary.asStateFlow()

    /**
     * Scan every channel that has no working guide (no manual match and its tvg-id isn't in the EPG
     * feed), match it by name, auto-apply high-confidence hits and queue the rest for review.
     */
    fun autoMatchEpg() {
        if (_matching.value) return
        viewModelScope.launch {
            _matching.value = true
            try {
                val pid = settings.activeProfileId.first()
                val playlistIds = if (pid < 0) emptyList() else sourceRepository.observeSources(pid).first().map { it.id }
                if (playlistIds.isEmpty()) { _matchSummary.value = "Add a playlist first."; return@launch }
                val ids = playlistIds + epgSourceStore.getAll().map { it.id }
                val cust = customize.observe(pid, MediaType.LIVE).first()

                val candidates = epgDao.listEpgChannels(ids, "", MAX_EPG_CANDIDATES)
                if (candidates.isEmpty()) { _matchSummary.value = "No EPG data to match against yet."; return@launch }
                val knownIds = candidates.mapTo(HashSet()) { it.epgChannelId.trim().lowercase() }

                val (applied, review, appliedIds) = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val prepared = com.thirutricks.tllplayer.core.epg.EpgMatcher.prepare(
                        candidates.map { com.thirutricks.tllplayer.core.epg.EpgMatcher.Candidate(it.epgChannelId, it.displayName) },
                    )
                    val channels = channelDao.allForSources(playlistIds, MAX_CHANNELS)
                    var applied = 0
                    val toApply = mutableListOf<Pair<String, String>>() // key -> epgId
                    val review = mutableListOf<EpgMatchSuggestion>()
                    for (ch in channels) {
                        val key = CustomizeKeys.channel(ch)
                        if (key in cust.epgMatches || key in cust.hiddenItems) continue // already matched/hidden
                        val tvg = ch.epgChannelId?.trim()?.lowercase()
                        if (!tvg.isNullOrEmpty() && tvg in knownIds) continue // already has a working guide
                        val best = com.thirutricks.tllplayer.core.epg.EpgMatcher.bestEpgMatchPrepared(ch.name, prepared) ?: continue
                        if (best.score >= com.thirutricks.tllplayer.core.epg.EpgMatcher.AUTO_THRESHOLD) {
                            toApply.add(key to best.epgChannelId)
                            applied++
                        } else {
                            review.add(EpgMatchSuggestion(ch, best.epgChannelId, best.displayName, best.score))
                        }
                    }
                    // Persist the confident matches (DataStore writes are cheap but do them off the scan).
                    for ((key, epgId) in toApply) customize.setEpgMatch(pid, MediaType.LIVE, key, epgId)
                    Triple(applied, review.sortedByDescending { it.score }, toApply.map { it.second })
                }

                _review.value = review
                _matchSummary.value = buildString {
                    append("Auto-matched $applied channel${if (applied == 1) "" else "s"}.")
                    if (review.isNotEmpty()) append(" ${review.size} need review.")
                    if (applied == 0 && review.isEmpty()) { setLength(0); append("Everything's already matched.") }
                }
                if (applied > 0) fillMatchedInBackground(appliedIds) // off the spinner — fills in shortly after
            } finally {
                _matching.value = false
            }
        }
    }

    /**
     * Auto-match a SINGLE channel by name (Guide long-press → "Auto-match"). Applies the best candidate
     * found (the user asked for this one specifically, so we apply even a middling match) or reports none.
     */
    fun autoMatchOne(channel: ChannelEntity) {
        if (_matching.value) return
        viewModelScope.launch {
            _matching.value = true
            try {
                val pid = settings.activeProfileId.first()
                val playlistIds = if (pid < 0) emptyList() else sourceRepository.observeSources(pid).first().map { it.id }
                val ids = playlistIds + epgSourceStore.getAll().map { it.id }
                val candidates = epgDao.listEpgChannels(ids, "", MAX_EPG_CANDIDATES)
                if (candidates.isEmpty()) { _matchSummary.value = "No EPG data to match against yet."; return@launch }
                val best = withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val prepared = com.thirutricks.tllplayer.core.epg.EpgMatcher.prepare(
                        candidates.map { com.thirutricks.tllplayer.core.epg.EpgMatcher.Candidate(it.epgChannelId, it.displayName) },
                    )
                    com.thirutricks.tllplayer.core.epg.EpgMatcher.bestEpgMatchPrepared(channel.name, prepared)
                }
                if (best == null) {
                    _matchSummary.value = "No EPG match found for “${channel.name}” — try picking manually."
                } else {
                    customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(channel), best.epgChannelId)
                    _matchSummary.value = "Matched “${channel.name}” → ${best.displayName ?: best.epgChannelId} (${(best.score * 100).toInt()}%)."
                    fillMatchedInBackground(listOf(best.epgChannelId)) // off the spinner
                }
            } finally {
                _matching.value = false
            }
        }
    }

    /** Accept a reviewed suggestion → persist the match and drop it from the review list. */
    fun acceptSuggestion(s: EpgMatchSuggestion) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(s.channel), s.epgChannelId)
            _review.value = _review.value.filterNot { it.channel.id == s.channel.id }
            fillMatchedInBackground(listOf(s.epgChannelId))
        }
    }

    /** Skip a suggestion without matching it (just remove it from the review list). */
    fun dismissSuggestion(s: EpgMatchSuggestion) {
        _review.value = _review.value.filterNot { it.channel.id == s.channel.id }
    }

    /** Accept every remaining suggestion at once, then clear the review list and reload the guide. */
    fun acceptAllSuggestions() {
        val all = _review.value
        if (all.isEmpty()) return
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            for (s in all) customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(s.channel), s.epgChannelId)
            _review.value = emptyList()
            fillMatchedInBackground(all.map { it.epgChannelId })
        }
    }

    /** Close the review list / clear the summary banner. */
    fun clearReview() {
        _review.value = emptyList()
        _matchSummary.value = null
    }

    /** Zap to the neighbouring guide channel ([delta] = +1 down / -1 up), wrapping at the ends so it
     *  never dead-ends (last → first, first → last). */
    fun zap(delta: Int) {
        val list = _state.value.channels
        if (list.size < 2) return
        val i = list.indexOfFirst { it.id == lastTunedChannelId }
        if (i < 0) return
        val next = ((i + delta) % list.size + list.size) % list.size // modulo wrap (handles negatives)
        if (next == i) return
        play(list[next])
    }

    fun load() {
        viewModelScope.launch {
            // Show the spinner only on the FIRST load. The EpgViewModel is shared, so on re-entry the guide
            // is already populated — keep the existing list on screen and refresh it silently, so opening the
            // Guide menu is instant instead of flashing a spinner and re-rendering from scratch every time.
            if (_state.value.channels.isEmpty()) _state.value = _state.value.copy(loading = true, message = null)
            val pid = currentProfileId()
            if (pid == null) {
                _state.value = EpgUiState(
                    loading = false,
                    message = "Create a profile to see the guide.",
                    hasEpgSources = epgSourceStore.getAll().isNotEmpty(),
                )
                return@launch
            }
            val playlistIds = sourceRepository.observeSources(pid).first().map { it.id }
            val epgIds = epgSourceStore.getAll().map { it.id }
            // Channels come from the playlists; guide data is matched from BOTH the playlists' own EPG
            // (kept for compatibility) and the standalone EPG sources — by epgChannelId across all ids.
            val ids = playlistIds + epgIds

            if (playlistIds.isEmpty()) {
                _state.value = EpgUiState(loading = false, message = "Add a playlist to see the guide.")
                return@launch
            }

            val now = System.currentTimeMillis()
            val nowAligned = now - (now % HALF_HOUR_MS) // align to the half hour
            // When the profile has catch-up channels, extend the guide BACKWARD so archived programmes
            // are visible (and playable), bounded by both the archive depth and what EPG we retain.
            val maxCatchupDays = channelDao.maxCatchupDays(playlistIds)
            val lookbackMs = if (maxCatchupDays > 0)
                minOf(maxCatchupDays * DAY_MS, CATCHUP_LOOKBACK_CAP_MS) else 0L
            val windowStart = nowAligned - lookbackMs
            val windowEnd = nowAligned + GRID_HOURS * 60L * 60 * 1000

            // Respect customizations: hidden channels stay out of the guide, renames show.
            val cust = customize.observe(pid, MediaType.LIVE).first()
            val q = _query.value.trim()
            val rawChannels = channelDao.channelsWithGuide(ids, q, MAX_CHANNELS)
            // Catch-up count comes from the playlist channels (the tv_archive flag), so it shows even
            // before any XMLTV guide is downloaded — it tells the user their provider supports catch-up.
            val catchupCount = channelDao.countCatchup(playlistIds)
            val favoriteIds = favoriteDao.observeFavoriteIds(pid, MediaType.LIVE).first().toSet()
            val sortLiveMode = settings.sortLive.first()
            val sortGuideMode = settings.sortGuide.first()
            val categoryFilter = _categoryFilter.value
            // Heavy work — filter hidden, apply renames + manual EPG matches, sort, category-filter — runs off
            // the main thread (#3/#5) so a 50k-channel playlist never freezes the UI building the guide list.
            val channels = withContext(Dispatchers.Default) {
                val auto = rawChannels
                    .filter { CustomizeKeys.channel(it) !in cust.hiddenItems }
                    .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
                val matched = applyEpgMatches(auto, cust, playlistIds, q)
                // Order the guide by its own sort. LIVE_TV mirrors the Live sort; CATCHUP floats archive
                // channels to the top; ALPHA/PROVIDER are explicit. CATCHUP with none available falls to LIVE_TV.
                val byAlpha = compareBy<ChannelEntity> { it.name.lowercase() }
                val byProvider = compareBy<ChannelEntity>({ it.sourceId }, { it.sortOrder }, { it.name.lowercase() })
                val liveOrdered = when (sortLiveMode) {
                    SettingsRepository.SortMode.ALPHA -> matched.sortedWith(byAlpha)
                    SettingsRepository.SortMode.PLAYLIST -> matched.sortedWith(byProvider)
                }
                when (sortGuideMode) {
                    SettingsRepository.GuideSort.ALPHA -> matched.sortedWith(byAlpha)
                    SettingsRepository.GuideSort.PROVIDER -> matched.sortedWith(byProvider)
                    SettingsRepository.GuideSort.CATCHUP ->
                        if (catchupCount > 0) matched.sortedWith(compareByDescending<ChannelEntity> { it.catchup }.then(byAlpha)) else liveOrdered
                    // Favorites: show ONLY favourited channels (in the Live order); none favourited → fall back.
                    SettingsRepository.GuideSort.FAVORITES ->
                        if (favoriteIds.isNotEmpty()) liveOrdered.filter { it.id in favoriteIds } else liveOrdered
                    SettingsRepository.GuideSort.LIVE_TV -> liveOrdered
                }.let { sorted ->
                    // Category filter (#8): when a group is chosen, show only its channels.
                    categoryFilter?.let { catId -> sorted.filter { it.categoryId == catId } } ?: sorted
                }
            }
            val stored = epgDao.countForSources(ids)

            // Per-row programmes are loaded in ONE batched query and grouped into rowCache here, instead of
            // each row firing its own programmesForChannel (an N+1 query storm on cold open). Only re-batch
            // when the window or sources actually change — a sort / filter / category change keeps the same
            // window, so the cache stays valid and we reuse it. Grouping runs off the main thread.
            // Also re-batch when the stored programme count changed (a sync, or a post-match top-up, added
            // data) even if the window is unchanged — so freshly-matched channels' guides appear.
            val windowChanged = cachedWindow != (windowStart to windowEnd) || loadedSourceIds != ids || stored != lastStored
            loadedSourceIds = ids
            lastStored = stored
            if (windowChanged) {
                cachedWindow = windowStart to windowEnd
                rowCache.clear()
                // Pass 1 (blocking): the forward window only, so the Guide opens instantly and fully from cache.
                val forward = withContext(Dispatchers.Default) {
                    epgDao.programmesInWindow(ids, nowAligned, windowEnd).groupBy { it.epgChannelId }
                }
                forward.forEach { (k, list) -> rowCache[k] = list }
            }

            val hasEpg = epgIds.isNotEmpty()
            val message = when {
                stored == 0 -> null // handled by the "No EPG added" prompt (hasEpgSources=false)
                channels.isEmpty() && q.isNotBlank() -> "No guide channels found for “$q”."
                channels.isEmpty() ->
                    "Guide data is stored, but its channel ids don't match your channels' EPG ids — " +
                        "this EPG feed may belong to a different provider lineup."
                else -> null
            }
            val catchupNote = if (catchupCount > 0) "$catchupCount with catch-up" else "no catch-up channels"
            val stats = if (stored > 0) {
                val guideChannels = epgDao.countGuideChannels(ids)
                "Guide loaded: $guideChannels channels · $stored programmes · $catchupNote"
            } else if (catchupCount > 0) {
                "Catch-up available on $catchupCount channels"
            } else {
                "No catch-up channels available"
            }

            _state.value = EpgUiState(
                channels = channels, windowStart = windowStart, windowEnd = windowEnd, now = now,
                loading = false, message = message, hasEpgSources = hasEpg, stats = stats, catchupCount = catchupCount,
                favoriteCount = favoriteIds.size,
            )

            // Pass 2 (background): merge the catch-up lookback into rowCache gradually, so a multi-day × many-
            // channel window never blocks the open or spikes memory on low-RAM (1.8 GB) boxes. Only runs when
            // there's a real lookback (catch-up channels). distinctBy(id): a programme straddling `now` is in
            // both window queries. The revision bump makes already-visible rows re-read the completed cache.
            if (windowChanged && windowStart < nowAligned) {
                viewModelScope.launch {
                    val back = withContext(Dispatchers.Default) {
                        epgDao.programmesInWindow(ids, windowStart, nowAligned).groupBy { it.epgChannelId }
                    }
                    if (back.isNotEmpty()) {
                        back.forEach { (k, extra) ->
                            rowCache[k] = (extra + (rowCache[k] ?: emptyList())).distinctBy { it.id }.sortedBy { it.startMs }
                        }
                        _cacheRevision.value++
                    }
                }
            }
        }
    }

    /** Apply per-channel manual EPG overrides to the auto-matched guide list. */
    private suspend fun applyEpgMatches(
        auto: List<ChannelEntity>,
        cust: com.thirutricks.tllplayer.core.customize.SectionCustomizations,
        playlistIds: List<Long>,
        query: String,
    ): List<ChannelEntity> {
        val matches = cust.epgMatches
        if (matches.isEmpty()) return auto
        val byKey = auto.associateBy { CustomizeKeys.channel(it) }
        // Override the epg id of channels already in the list.
        val overridden = auto.map { ch -> matches[CustomizeKeys.channel(ch)]?.let { ch.copy(epgChannelId = it) } ?: ch }.toMutableList()
        // Add matched channels that didn't auto-appear. Resolve them with ONE bulk query (all channels keyed
        // by their customize key) instead of two DB lookups per match — the old per-match resolveChannel was a
        // query-storm that dominated guide load time once a lot of channels had been smart-matched.
        val missingKeys = matches.keys.filter { it !in byKey && it !in cust.hiddenItems }
        if (missingKeys.isNotEmpty()) {
            // Resolve only the matched channels (their key's tail is the remoteId/Xtream stream id) in one
            // query, instead of loading the entire channel table — that table-load was seconds on big lists.
            val remoteIds = missingKeys.mapNotNull { it.substringAfter(':', "").takeIf { r -> r.isNotEmpty() } }.distinct()
            val resolved = channelDao.findByRemoteIds(playlistIds, remoteIds).associateBy { CustomizeKeys.channel(it) }
            for (key in missingKeys) {
                val epgId = matches[key] ?: continue
                val ch = resolved[key] ?: continue
                val name = cust.itemNames[key] ?: ch.name
                if (query.isNotBlank() && !name.contains(query, ignoreCase = true)) continue
                overridden.add(ch.copy(epgChannelId = epgId, name = name))
            }
        }
        return overridden
    }

    /** Distinct EPG channels for the manual "Match EPG" picker (across the profile's feeds). */
    suspend fun availableEpgChannels(query: String): List<com.thirutricks.tllplayer.core.database.entity.EpgChannelEntity> {
        val pid = currentProfileId() ?: return emptyList()
        val playlistIds = sourceRepository.observeSources(pid).first().map { it.id }
        val ids = playlistIds + epgSourceStore.getAll().map { it.id }
        if (ids.isEmpty()) return emptyList()
        return epgDao.listEpgChannels(ids, query.trim().lowercase(), 300)
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    companion object {
        const val GRID_HOURS = 24
        private const val HALF_HOUR_MS = 30L * 60 * 1000
        private const val DAY_MS = 24L * 60 * 60 * 1000
        // How far back the Guide may extend for catch-up (must stay within EpgRepository's retention).
        private const val CATCHUP_LOOKBACK_CAP_MS = 7L * 24 * 60 * 60 * 1000
        // Generous safety bound only (rows load lazily, so this is about the channel list itself).
        private const val MAX_CHANNELS = 20_000
        // Cap the candidate set the bulk matcher scans against (keeps the O(channels×candidates) scan bounded).
        private const val MAX_EPG_CANDIDATES = 20_000
    }
}
