@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.thirutricks.tllplayer.features.series

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.thirutricks.tllplayer.core.customize.CustomizationStore
import com.thirutricks.tllplayer.core.customize.applyCustomizations
import com.thirutricks.tllplayer.core.database.dao.CategoryDao
import com.thirutricks.tllplayer.core.database.dao.FavoriteDao
import com.thirutricks.tllplayer.core.database.dao.HistoryDao
import com.thirutricks.tllplayer.core.database.dao.ProgressDao
import com.thirutricks.tllplayer.core.database.dao.ProfileDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.dao.resolveExistingProfileId
import com.thirutricks.tllplayer.core.database.entity.DownloadEntity
import com.thirutricks.tllplayer.core.database.entity.EpisodeEntity
import com.thirutricks.tllplayer.core.database.entity.FavoriteEntity
import com.thirutricks.tllplayer.core.database.entity.PlaybackProgressEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.core.database.entity.WatchHistoryEntity
import com.thirutricks.tllplayer.core.launcher.LauncherIntegrationRepository
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.core.download.DownloadManager
import com.thirutricks.tllplayer.core.repository.SeriesRepository
import com.thirutricks.tllplayer.core.storage.StorageAccess
import com.thirutricks.tllplayer.features.live.LiveKey
import com.thirutricks.tllplayer.features.live.LiveRailItem
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.player.MediaMeta
import com.thirutricks.tllplayer.player.OwnTVPlayer
import com.thirutricks.tllplayer.player.PlaylistItem
import com.thirutricks.tllplayer.ui.components.OwnTVIcon

class SeriesViewModel(
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val favoriteDao: FavoriteDao,
    private val historyDao: HistoryDao,
    private val progressDao: ProgressDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val seriesRepository: SeriesRepository,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val player: OwnTVPlayer,
    private val downloadManager: DownloadManager,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
) : ViewModel() {

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)
    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Series
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    private val ctx: StateFlow<Ctx> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(Ctx(pid, emptyList()))
            else sourceDao.observeForProfile(pid).map { srcs -> Ctx(pid, srcs.map { it.id }) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    /** List ordering for this section (Provider order vs A–Z), persisted in DataStore. */
    val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortSeries
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SortMode.ALPHA)

    fun toggleSort() {
        viewModelScope.launch {
            settings.setSortSeries(
                if (sortMode.value == SettingsRepository.SortMode.PLAYLIST) SettingsRepository.SortMode.ALPHA
                else SettingsRepository.SortMode.PLAYLIST,
            )
        }
    }

    val viewMode: StateFlow<SettingsRepository.VodViewMode> = settings.vodViewMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.VodViewMode.GRID)

    fun toggleViewMode() {
        viewModelScope.launch {
            settings.setVodViewMode(
                if (viewMode.value == SettingsRepository.VodViewMode.GRID) SettingsRepository.VodViewMode.LIST
                else SettingsRepository.VodViewMode.GRID,
            )
        }
    }

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selectedKey: StateFlow<LiveKey> = _selected.asStateFlow()

    private val _search = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _search.asStateFlow()

    private val _selectedSeries = MutableStateFlow<SeriesEntity?>(null)
    val selectedSeries: StateFlow<SeriesEntity?> = _selectedSeries.asStateFlow()

    private val _openedSeries = MutableStateFlow<SeriesEntity?>(null)
    val openedSeries: StateFlow<SeriesEntity?> = _openedSeries.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _lastPlayedEpisodeId = MutableStateFlow<Long?>(null)
    val lastPlayedEpisodeId: StateFlow<Long?> = _lastPlayedEpisodeId.asStateFlow()

    private val _episodesLoading = MutableStateFlow(false)
    val episodesLoading: StateFlow<Boolean> = _episodesLoading.asStateFlow()

    init {
        // Periodically persist the playing episode's resume position (same cadence as movies).
        // Episodes were previously *read* on play but never saved — resume never actually worked.
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                saveEpisodeProgressNow()
            }
        }
        // Auto-play continuation across seasons: the player advances within a season itself, then signals
        // here when a season's last episode finishes so we can start the next season's first episode.
        viewModelScope.launch {
            player.queueEnded.collect { continueToNextSeason() }
        }
    }

    /** A season's last episode finished with auto-play on — start the next season's first episode, if any.
     *  Matches the just-finished episode by its stream URL (robust to in-season auto-advance). */
    private fun continueToNextSeason() {
        val url = player.currentMediaUrl ?: return
        val all = episodes.value
        val finished = all.firstOrNull { it.streamUrl == url } ?: return // not one of this series' episodes
        val nextEpisode = all
            .filter { it.seasonNumber == finished.seasonNumber + 1 }
            .minByOrNull { it.episodeNumber } ?: return // no next season — series finished
        playEpisode(nextEpisode)
    }

    /** Saves the currently playing episode's position (matched by stream URL, so prev/next in the
     *  player queue is tracked correctly even though the VM didn't initiate the switch). */
    fun saveEpisodeProgressNow() {
        if (player.isLiveContent || !player.isPlaying.value) return
        val url = player.currentMediaUrl ?: return
        val ep = episodes.value.firstOrNull { it.streamUrl == url } ?: return
        val pos = player.position.value
        val dur = player.duration.value
        if (pos > 0 && dur > 0) {
            viewModelScope.launch {
                val pid = currentProfileId() ?: return@launch
                Log.d(TAG, "saveEpisodeProgressNow episodeId=${ep.id} profile=$pid positionMs=$pos durationMs=$dur")
                runCatching {
                    progressDao.save(
                        PlaybackProgressEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = ep.id, positionMs = pos, durationMs = dur),
                    )
                }.onFailure { t ->
                    Log.w(TAG, "saveEpisodeProgressNow progress save failed episodeId=${ep.id} profile=$pid", t)
                }
                launcherIntegrationRepository.publishEpisodeProgress(pid, ep.id, pos, dur)
            }
        }
    }

    val railItems: StateFlow<List<LiveRailItem>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(defaultRail)
            else combine(
                categoryDao.observe(c.sourceIds, MediaType.SERIES),
                customize.observe(c.profileId, MediaType.SERIES),
            ) { cats, cust ->
                defaultRail + cats.applyCustomizations(cust).map { (cat, name) ->
                    LiveRailItem(LiveKey.Folder(cat.id), name.take(3).uppercase(), name)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultRail)

    val series: Flow<PagingData<SeriesEntity>> = combine(
        _selected, ctx, _search.map { it.trim() }.debounce(300).distinctUntilChanged(), sortMode,
    ) { key, c, query, sort -> Args(key, c, query, sort) }
        .flatMapLatest { (key, c, query, sort) ->
            Pager(PagingConfig(pageSize = 60, prefetchDistance = 30, initialLoadSize = 90, maxSize = 300)) {
                pagingSource(key, c, query, sort)
            }.flow
        }
        .cachedIn(viewModelScope)

    private data class Args(val key: LiveKey, val ctx: Ctx, val query: String, val sort: SettingsRepository.SortMode)

    val count: StateFlow<Int> = combine(_selected, ctx) { key, c -> key to c }
        .flatMapLatest { (key, c) -> countFlow(key, c) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.SERIES) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val episodes: StateFlow<List<EpisodeEntity>> = _openedSeries
        .flatMapLatest { s -> if (s == null) flowOf(emptyList()) else seriesDao.episodesBySeries(s.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun select(key: LiveKey) { _selected.value = key }
    fun setSearchQuery(query: String) { _search.value = query }
    fun onSeriesFocused(s: SeriesEntity) { _selectedSeries.value = s }
    fun selectSeason(season: Int) { _selectedSeason.value = season }

    fun openSeries(s: SeriesEntity) {
        _openedSeries.value = s
        _selectedSeason.value = 1 // reset season when opening a different show
        _lastPlayedEpisodeId.value = null
        Log.d(TAG, "openSeries seriesId=${s.id} profile=${ctx.value.profileId}")
        viewModelScope.launch {
            _episodesLoading.value = true
            seriesRepository.loadEpisodes(s)
            // Jump to where you left off: seed the last-watched episode (and its season) from saved progress
            // BEFORE clearing loading, so the screen's focus effect lands on it instead of episode 1 (#22).
            val eps = seriesDao.episodesBySeries(s.id).first()
            val lastEp = progressDao.lastWatchedEpisodeId(ctx.value.profileId, s.id)
                ?.let { id -> eps.firstOrNull { it.id == id } }
            if (lastEp != null) {
                _selectedSeason.value = lastEp.seasonNumber
                _lastPlayedEpisodeId.value = lastEp.id
            }
            _episodesLoading.value = false
        }
    }

    fun openSeriesById(seriesId: Long) {
        viewModelScope.launch {
            val show = seriesDao.getSeriesById(seriesId) ?: return@launch
            openSeries(show)
        }
    }

    fun playFromHome(seriesId: Long, episodeId: Long, startPositionMs: Long = 0) {
        viewModelScope.launch {
            playFromHomeAsync(seriesId, episodeId, startPositionMs)
        }
    }

    suspend fun playFromHomeAsync(seriesId: Long, episodeId: Long, startPositionMs: Long = 0): Boolean {
        val episode = seriesDao.getEpisodeById(episodeId) ?: return false
        val showId = if (seriesId > 0) seriesId else episode.seriesId
        val show = seriesDao.getSeriesById(showId) ?: return false
        if (episode.seriesId != show.id) return false
        seriesRepository.loadEpisodes(show)
        val queue = seriesDao.episodesBySeries(show.id).first()
        if (queue.isEmpty()) return false
        playEpisodeQueue(show, queue, episode, startPositionMs)
        return true
    }

    fun closeSeries() {
        _openedSeries.value = null
    }

    /** The user's resume preference (Always / Ask / Never) — the screen drives the prompt. */
    val resumeMode: StateFlow<SettingsRepository.ResumeMode> = settings.resumeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.ResumeMode.ASK)

    /** Saved resume position for [episode] (0 when none) — used by the screen to decide the prompt. */
    suspend fun savedPositionMs(episode: EpisodeEntity): Long =
        currentProfileId()?.let { progressDao.get(it, MediaType.EPISODE, episode.id)?.positionMs ?: 0 } ?: 0

    fun playEpisode(episode: EpisodeEntity, startPositionMs: Long = 0) {
        val show = _openedSeries.value ?: return
        val seasonEpisodes = episodes.value
            .filter { it.seasonNumber == episode.seasonNumber }
            .sortedBy { it.episodeNumber }
        playEpisodeQueue(show, seasonEpisodes, episode, startPositionMs)
    }

    fun playEpisodeQueue(show: SeriesEntity, queue: List<EpisodeEntity>, episode: EpisodeEntity, startPositionMs: Long = 0) {
        _openedSeries.value = show
        _lastPlayedEpisodeId.value = episode.id
        viewModelScope.launch {
            val pid = currentProfileId()
            val startIndex = queue.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
            Log.d(TAG, "playEpisodeQueue seriesId=${show.id} episodeId=${episode.id} profile=$pid queue=${queue.size} startIndex=$startIndex startPositionMs=$startPositionMs")
            player.playEpisodes(
                items = queue.map { ep ->
                    PlaylistItem(
                        url = ep.streamUrl,
                        meta = MediaMeta(
                            title = ep.name,
                            subtitle = listOfNotNull(show.name, "Season ${ep.seasonNumber}").joinToString(" · "),
                        ),
                    )
                },
                startIndex = startIndex,
                startPositionMs = startPositionMs,
            )
            if (pid != null) {
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.EPISODE, itemId = episode.id))
                }.onFailure { t ->
                    Log.w(TAG, "playEpisodeQueue episode history record failed episodeId=${episode.id} profile=$pid", t)
                }
                runCatching {
                    historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = episode.seriesId))
                }.onFailure { t ->
                    Log.w(TAG, "playEpisodeQueue series history record failed seriesId=${episode.seriesId} profile=$pid", t)
                }
            }
        }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    /** Download states for the open show's episodes, keyed by episode id. */
    val episodeDownloads: StateFlow<Map<Long, DownloadEntity>> = ctx
        .flatMapLatest { c -> if (c.profileId < 0) flowOf(emptyList()) else downloadManager.observe(c.profileId) }
        .map { list -> list.filter { it.mediaType == MediaType.EPISODE }.associateBy { it.itemId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun downloadEpisode(episode: EpisodeEntity) {
        val show = _openedSeries.value
        val showDir = StorageAccess.sanitize(show?.name ?: "Series")
        val ext = episode.containerExt ?: StorageAccess.extOf(episode.streamUrl)
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            downloadManager.enqueue(
                profileId = pid,
                mediaType = MediaType.EPISODE,
                itemId = episode.id,
                title = episode.name,
                posterUrl = show?.posterUrl,
                streamUrl = episode.streamUrl,
                relativeDir = "Series/$showDir/Season ${episode.seasonNumber}",
                fileName = "${StorageAccess.sanitize(episode.name)}.$ext",
            )
        }
    }

    fun toggleFavorite(s: SeriesEntity) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            if (favoriteIds.value.contains(s.id)) favoriteDao.remove(pid, MediaType.SERIES, s.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.SERIES, itemId = s.id))
        }
    }

    private fun pagingSource(key: LiveKey, c: Ctx, query: String, sort: SettingsRepository.SortMode): PagingSource<Int, SeriesEntity> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        val playlist = sort == SettingsRepository.SortMode.PLAYLIST
        return if (query.isBlank()) when (key) {
            LiveKey.All -> if (playlist) seriesDao.pagingAllOriginal(ids) else seriesDao.pagingAll(ids)
            LiveKey.Favorites -> seriesDao.pagingFavorites(c.profileId)
            LiveKey.History -> seriesDao.pagingHistory(c.profileId)
            is LiveKey.Folder -> if (playlist) seriesDao.pagingByCategory(key.id) else seriesDao.pagingByCategoryAlpha(key.id)
        } else when (key) {
            LiveKey.All -> seriesDao.searchAll(query, ids)
            LiveKey.Favorites -> seriesDao.searchFavorites(query, c.profileId)
            LiveKey.History -> seriesDao.searchHistory(query, c.profileId)
            is LiveKey.Folder -> seriesDao.searchInCategory(query, key.id)
        }
    }

    private fun countFlow(key: LiveKey, c: Ctx): Flow<Int> {
        val ids = c.sourceIds.ifEmpty { listOf(-1L) }
        return when (key) {
            LiveKey.All -> seriesDao.countAll(ids)
            LiveKey.Favorites -> seriesDao.countFavorites(c.profileId)
            LiveKey.History -> historyDao.count(c.profileId, MediaType.SERIES)
            is LiveKey.Folder -> seriesDao.countByCategory(key.id)
        }
    }

    private companion object {
        const val TAG = "OwnTVHome"
        val defaultRail = listOf(
            LiveRailItem(LiveKey.Favorites, "FAV", "Favorites", OwnTVIcon.STAR),
            LiveRailItem(LiveKey.History, "HIS", "History", OwnTVIcon.HISTORY),
            LiveRailItem(LiveKey.All, "ALL", "All Series"),
        )
    }
}
