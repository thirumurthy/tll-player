package com.thirutricks.tllplayer.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.ChannelWithWatchedAt
import com.thirutricks.tllplayer.core.database.dao.MovieDao
import com.thirutricks.tllplayer.core.database.dao.ProfileDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.dao.resolveExistingProfileId
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpisodeEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.core.launcher.LauncherContinuationItem
import com.thirutricks.tllplayer.core.launcher.LauncherContinuationKind
import com.thirutricks.tllplayer.core.launcher.LauncherRecommendationPlanner
import com.thirutricks.tllplayer.core.launcher.LauncherWatchNextType
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.player.HeroPreviewEngine

sealed interface HeroItem {
    val streamUrl: String
    val seekToMs: Long
    val positionMs: Long
    val durationMs: Long
    val watchNextType: LauncherWatchNextType
    val lastEngagementAt: Long

    data class MovieHero(
        val movie: MovieEntity,
        val item: LauncherContinuationItem,
    ) : HeroItem {
        override val streamUrl: String = movie.streamUrl
        override val seekToMs: Long = (item.positionMs - 10_000L).coerceAtLeast(0L)
        override val positionMs: Long = item.positionMs
        override val durationMs: Long = item.durationMs
        override val watchNextType: LauncherWatchNextType = item.watchNextType
        override val lastEngagementAt: Long = item.lastEngagementAt
    }

    data class SeriesHero(
        val series: SeriesEntity,
        val episode: EpisodeEntity,
        val item: LauncherContinuationItem,
    ) : HeroItem {
        override val streamUrl: String = episode.streamUrl
        override val seekToMs: Long = if (item.watchNextType == LauncherWatchNextType.NEXT) {
            0L
        } else {
            (item.positionMs - 10_000L).coerceAtLeast(0L)
        }
        override val positionMs: Long = item.positionMs
        override val durationMs: Long = item.durationMs
        override val watchNextType: LauncherWatchNextType = item.watchNextType
        override val lastEngagementAt: Long = item.lastEngagementAt
    }

    data class LiveHero(
        val channel: ChannelEntity,
        val watchedAt: Long,
    ) : HeroItem {
        override val streamUrl: String = channel.streamUrl
        override val seekToMs: Long = 0L
        override val positionMs: Long = 0L
        override val durationMs: Long = 0L
        override val watchNextType: LauncherWatchNextType = LauncherWatchNextType.CONTINUE
        override val lastEngagementAt: Long = watchedAt
    }
}

data class HomeUiState(
    val heroItems: List<HeroItem> = emptyList(),
    val activeHeroIndex: Int = 0,
    val continueMovies: List<LauncherContinuationItem> = emptyList(),
    val continueSeries: List<LauncherContinuationItem> = emptyList(),
    val recentLive: List<ChannelEntity> = emptyList(),
    val favoriteLive: List<ChannelEntity> = emptyList(),
    val isEmpty: Boolean = true,
)

class HomeViewModel(
    private val planner: LauncherRecommendationPlanner,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val channelDao: ChannelDao,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val heroPreviewEngine: HeroPreviewEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _heroFocused = MutableStateFlow(false)
    private val _previewEnabled = MutableStateFlow(true)
    private val _lastHeroInteractionMs = MutableStateFlow(0L)

    val lastHeroInteractionMs: StateFlow<Long> = _lastHeroInteractionMs.asStateFlow()

    val isPreviewActive: StateFlow<Boolean> = combine(_heroFocused, _previewEnabled, _uiState) { focused, enabled, state ->
        focused && enabled && state.heroItems.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setPreviewEnabled(enabled: Boolean) {
        _previewEnabled.value = enabled
    }

    fun setHeroFocused(focused: Boolean) {
        _heroFocused.value = focused
    }

    fun navigateHero(index: Int) {
        val items = _uiState.value.heroItems
        if (index !in items.indices) return
        _uiState.value = _uiState.value.copy(activeHeroIndex = index)
    }

    fun onHeroUserNavigate(index: Int) {
        _lastHeroInteractionMs.value = System.currentTimeMillis()
        navigateHero(index)
    }

    fun stopPreview() {
        heroPreviewEngine.stop()
    }

    fun refresh() {
        viewModelScope.launch {
            val pid = currentProfileId() ?: return@launch
            loadHomeData(pid)
        }
    }

    private suspend fun loadHomeData(profileId: Long) {
        val state = withContext(Dispatchers.IO) {
            val items = planner.buildContinuationItems(profileId)
            val movies = items.filter { it.kind == LauncherContinuationKind.MOVIE }
            val series = items.filter { it.kind == LauncherContinuationKind.EPISODE }
            val liveWithTs = channelDao.recentlyWatchedWithTimestamp(profileId, 10).first()
            val live = liveWithTs.map { it.channel }
            val favLive = channelDao.favoritesListAlpha(profileId, 50).first()
            val heroItems = buildHeroItems(items, liveWithTs)

            HomeUiState(
                heroItems = heroItems,
                activeHeroIndex = 0,
                continueMovies = movies,
                continueSeries = series,
                recentLive = live,
                favoriteLive = favLive,
                isEmpty = movies.isEmpty() && series.isEmpty() && live.isEmpty() && favLive.isEmpty(),
            )
        }
        _uiState.value = state
    }

    private suspend fun buildHeroItems(
        continuationItems: List<LauncherContinuationItem>,
        liveChannels: List<ChannelWithWatchedAt>,
    ): List<HeroItem> {
        data class Candidate(val engagedAt: Long, val resolve: suspend () -> HeroItem?)

        val candidates = mutableListOf<Candidate>()

        continuationItems.forEach { item ->
            candidates += Candidate(item.lastEngagementAt) {
                when (item.kind) {
                    LauncherContinuationKind.MOVIE -> {
                        val movie = movieDao.getById(item.sourceItemId) ?: return@Candidate null
                        HeroItem.MovieHero(movie, item)
                    }
                    LauncherContinuationKind.EPISODE -> {
                        val episode = seriesDao.getEpisodeById(item.targetItemId) ?: return@Candidate null
                        val series = seriesDao.getSeriesById(episode.seriesId) ?: return@Candidate null
                        HeroItem.SeriesHero(series, episode, item)
                    }
                    LauncherContinuationKind.LIVE -> null
                }
            }
        }

        liveChannels.forEach { watched ->
            candidates += Candidate(watched.watchedAt) {
                HeroItem.LiveHero(watched.channel, watched.watchedAt)
            }
        }

        val result = mutableListOf<HeroItem>()
        for (candidate in candidates.sortedByDescending { it.engagedAt }) {
            if (result.size >= 10) break
            candidate.resolve()?.let { result += it }
        }
        return result
    }

    private suspend fun currentProfileId(): Long? {
        val preferred = settings.activeProfileId.first()
        return if (preferred >= 0) profileDao.resolveExistingProfileId(preferred) else null
    }

}
