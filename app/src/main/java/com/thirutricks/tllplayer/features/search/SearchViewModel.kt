@file:OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)

package com.thirutricks.tllplayer.features.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import com.thirutricks.tllplayer.core.customize.CustomizeKeys
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.FavoriteDao
import com.thirutricks.tllplayer.core.database.dao.HistoryDao
import com.thirutricks.tllplayer.core.database.dao.MovieDao
import com.thirutricks.tllplayer.core.database.dao.ProfileDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.dao.resolveExistingProfileId
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.FavoriteEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.core.database.entity.WatchHistoryEntity
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.player.OwnTVPlayer

/** Combined results of a global query (each list bounded). */
data class SearchResults(
    val channels: List<com.thirutricks.tllplayer.core.database.dao.ChannelSearchResult> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty()
}

/** Phase 11 — cross-section search over a profile's channels, movies and series. */
class SearchViewModel(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val historyDao: HistoryDao,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val favoriteDao: FavoriteDao,
    val player: OwnTVPlayer,
) : ViewModel() {

    private data class Ctx(val profileId: Long, val sourceIds: List<Long>)
    // Observe the active profile's sources reactively so adding/removing a playlist refreshes Search
    // immediately (was read once at startup, so a new playlist showed nothing until app restart).
    private val ctx: StateFlow<Ctx> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(Ctx(pid, emptyList()))
            else sourceDao.observeForProfile(pid).map { srcs -> Ctx(pid, srcs.map { it.id }) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Ctx(-1L, emptyList()))

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    val results: StateFlow<SearchResults> = _query
        .map { it.trim() }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.length < 2) {
                flowOf(SearchResults())
            } else {
                flowOf(search(q))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    fun setQuery(q: String) { _query.value = q }

    private suspend fun search(q: String): SearchResults {
        val pid = currentProfileId() ?: return SearchResults()
        val ids = ctx.value.sourceIds.ifEmpty { return SearchResults() }
        // Respect this profile's customizations: hidden channels never surface, renames are shown.
        val cust = customize.observe(pid, MediaType.LIVE).first()
        return SearchResults(
            channels = channelDao.searchListDetailed(q, ids, LIMIT)
                .filter { CustomizeKeys.channel(it.channel) !in cust.hiddenItems }
                .map { row -> cust.itemNames[CustomizeKeys.channel(row.channel)]?.let { row.copy(channel = row.channel.copy(name = it)) } ?: row },
            movies = movieDao.searchList(q, ids, LIMIT),
            series = seriesDao.searchList(q, ids, LIMIT),
        )
    }

    /** Live channels this profile has favourited — so a search result can show a star and toggle it. */
    val favoriteChannelIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Long-press a channel result to add/remove it from Favorites (no need to open Live TV first). */
    fun toggleFavoriteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId
            if (pid < 0) return@launch
            if (favoriteChannelIds.value.contains(channel.id)) {
                favoriteDao.remove(pid, MediaType.LIVE, channel.id)
            } else {
                favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
            }
        }
    }

    fun playChannel(channel: ChannelEntity) {
        player.play(channel.streamUrl, title = channel.name, logoUrl = channel.logoUrl, isLive = true)
        record(MediaType.LIVE, channel.id)
    }

    fun playMovie(movie: MovieEntity) {
        player.play(movie.streamUrl, title = movie.name, year = movie.year?.toString(), isLive = false)
        record(MediaType.MOVIE, movie.id)
    }

    private fun record(type: MediaType, itemId: Long) {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            runCatching {
                historyDao.record(WatchHistoryEntity(profileId = pid, mediaType = type, itemId = itemId))
            }.onFailure { t ->
                Log.w(TAG, "record history failed profile=$pid type=$type itemId=$itemId", t)
            }
        }
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

    private companion object {
        const val TAG = "OwnTVHome"
        const val LIMIT = 40
    }
}
