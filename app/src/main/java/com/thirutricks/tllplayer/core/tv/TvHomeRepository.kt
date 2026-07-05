package com.thirutricks.tllplayer.core.tv

import android.content.ContentUris
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.thirutricks.tllplayer.R
import com.thirutricks.tllplayer.core.customize.CustomizationStore
import com.thirutricks.tllplayer.core.customize.CustomizeKeys
import com.thirutricks.tllplayer.core.customize.SectionCustomizations
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.MovieDao
import com.thirutricks.tllplayer.core.database.dao.ProgressDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.dao.TvProviderProgramDao
import com.thirutricks.tllplayer.core.database.entity.EpisodeEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.core.database.entity.TvProviderProgramEntity
import com.thirutricks.tllplayer.core.launcher.LauncherContinuationItem
import com.thirutricks.tllplayer.core.launcher.LauncherContinuationKind
import com.thirutricks.tllplayer.core.launcher.LauncherDeepLink
import com.thirutricks.tllplayer.core.launcher.LauncherRecommendationPlanner
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import java.security.MessageDigest

/** Mirrors the app's continue-watching state into Android TV provider rows. */
class TvHomeRepository(
    private val context: Context,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val progressDao: ProgressDao,
    private val tvProviderProgramDao: TvProviderProgramDao,
    private val customize: CustomizationStore,
    private val settings: SettingsRepository,
    private val launcherPlanner: LauncherRecommendationPlanner,
) {
    private val resolver: ContentResolver get() = context.contentResolver
    private val channelHelper = PreviewChannelHelper(context)
    private val mutex = Mutex()

    companion object {
        private const val TAG = "OwnTVHome"
        private const val WATCH_NEXT_PUBLISH_INTERVAL_MS = 60_000L
        private const val RECENT_LIVE_MAX_ITEMS = 10
        private const val RECENT_LIVE_REFRESH_INTERVAL_MS = 5_000L
        private const val RECENT_LIVE_CHANNEL_GROUP_ID = 0L
        private const val RECENT_LIVE_CHANNEL_NAME = "Recent Live"
        private const val RECENT_LIVE_CHANNEL_STABLE_KEY = "recent-live"
    }

    private fun logD(message: String) {
        Log.d(TAG, message)
    }

    private fun logW(message: String, tr: Throwable? = null) {
        if (tr == null) Log.w(TAG, message) else Log.w(TAG, message, tr)
    }

    private fun TvProviderProgramEntity.describe(): String =
        "providerId=${providerProgramId ?: -1} surface=$surface mediaType=$mediaType groupId=$groupId targetItemId=$targetItemId lastPositionMs=$lastPositionMs durationMs=$durationMs lastPublishedAt=$lastPublishedAt"

    suspend fun refreshProfile(profileId: Long, allowBrowsableRequest: Boolean = false) = withContext(Dispatchers.IO) {
        if (profileId < 0) return@withContext
        val enabled = settings.androidTvHomeEnabled.first()
        logD("refreshProfile profile=$profileId enabled=$enabled allowBrowsable=$allowBrowsableRequest")
        if (!enabled) {
            logD("refreshProfile profile=$profileId clearing platform rows because Android TV home is disabled")
            clearProfile(profileId)
            return@withContext
        }
        mutex.withLock {
            logD("refreshProfile profile=$profileId publishing Watch Next and Recent Live rows")
            refreshWatchNextLocked(profileId)
            refreshRecentLiveLocked(profileId, allowBrowsableRequest)
        }
        logD("refreshProfile profile=$profileId done")
    }

    suspend fun clearProfile(profileId: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val rows = tvProviderProgramDao.getAllForProfile(profileId)
            logD("clearProfile profile=$profileId rows=${rows.size}")
            rows.forEach { deletePlatformRow(it) }
            tvProviderProgramDao.deleteForProfile(profileId)
        }
        logD("clearProfile profile=$profileId done")
    }

    suspend fun publishMovieProgress(profileId: Long, movieId: Long, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        val enabled = settings.androidTvHomeEnabled.first()
        if (profileId < 0 || !enabled) {
            logD("publishMovieProgress skip profile=$profileId enabled=$enabled movieId=$movieId positionMs=$positionMs durationMs=$durationMs")
            return@withContext
        }
        logD("publishMovieProgress profile=$profileId movieId=$movieId positionMs=$positionMs durationMs=$durationMs")
        mutex.withLock { syncMovie(profileId, movieId, positionMs, durationMs) }
    }

    suspend fun publishEpisodeProgress(profileId: Long, episodeId: Long, positionMs: Long, durationMs: Long) = withContext(Dispatchers.IO) {
        val enabled = settings.androidTvHomeEnabled.first()
        if (profileId < 0 || !enabled) {
            logD("publishEpisodeProgress skip profile=$profileId enabled=$enabled episodeId=$episodeId positionMs=$positionMs durationMs=$durationMs")
            return@withContext
        }
        logD("publishEpisodeProgress profile=$profileId episodeId=$episodeId positionMs=$positionMs durationMs=$durationMs")
        mutex.withLock { syncEpisode(profileId, episodeId, positionMs, durationMs) }
    }

    suspend fun refreshRecentLive(profileId: Long, allowBrowsableRequest: Boolean = false) = withContext(Dispatchers.IO) {
        val enabled = settings.androidTvHomeEnabled.first()
        if (profileId < 0 || !enabled) {
            logD("refreshRecentLive skip profile=$profileId enabled=$enabled allowBrowsable=$allowBrowsableRequest")
            return@withContext
        }
        logD("refreshRecentLive profile=$profileId allowBrowsable=$allowBrowsableRequest")
        mutex.withLock { refreshRecentLiveLocked(profileId, allowBrowsableRequest) }
    }

    private suspend fun refreshWatchNextLocked(profileId: Long) {
        val desired = launcherPlanner.buildContinuationItems(profileId)
        val desiredKeys = desired.map { it.stableKey }.toSet()
        val existing = tvProviderProgramDao.getAllForProfile(profileId)
        logD("refreshWatchNext profile=$profileId desired=${desired.size} existing=${existing.size}")
        for (row in existing) {
            if (row.surface != TvProviderSurface.WATCH_NEXT) continue
            if (row.key() !in desiredKeys) {
                logD("refreshWatchNext deleting stale ${row.describe()}")
                deletePlatformRow(row)
                tvProviderProgramDao.delete(profileId, row.surface, row.mediaType, row.groupId)
            }
        }
        for (item in desired) {
            syncContinuationItem(profileId, item)
        }
    }

    private suspend fun syncContinuationItem(profileId: Long, item: LauncherContinuationItem, force: Boolean = true) {
        when (item.kind) {
            LauncherContinuationKind.MOVIE -> syncMovie(profileId, item.sourceItemId, item.positionMs, item.durationMs, force)
            LauncherContinuationKind.EPISODE -> {
                val progress = progressDao.get(profileId, MediaType.EPISODE, item.sourceItemId) ?: return
                syncEpisode(profileId, item.sourceItemId, progress.positionMs, progress.durationMs, force)
            }
            LauncherContinuationKind.LIVE -> Unit
        }
    }

    private suspend fun refreshRecentLiveLocked(profileId: Long, allowBrowsableRequest: Boolean) {
        val customizations = customize.observe(profileId, MediaType.LIVE).first()
        val channelRow = ensureRecentLiveChannel(profileId, allowBrowsableRequest)
        val channelId = channelRow.providerProgramId ?: return
        val now = System.currentTimeMillis()
        if (now - channelRow.lastPublishedAt < RECENT_LIVE_REFRESH_INTERVAL_MS) return

        val sourceIds = sourceDao.sourceIdsForProfile(profileId).toSet()
        val recentChannels = channelDao.recentlyWatched(profileId, RECENT_LIVE_MAX_ITEMS * 2).first()
            .filter { it.sourceId in sourceIds }
            .filter { !isHidden(customizations, it) }
            .distinctBy { it.id }
            .take(RECENT_LIVE_MAX_ITEMS)
        val existingRows = tvProviderProgramDao.getForSurface(profileId, TvProviderSurface.RECENT_LIVE)
            .filter { it.groupId != RECENT_LIVE_CHANNEL_GROUP_ID }
        logD(
            "refreshRecentLive profile=$profileId channelId=$channelId recent=${recentChannels.size} " +
                "sourceCount=${sourceIds.size} existing=${existingRows.size}",
        )

        val desiredKeys = recentChannels.map { launcherPlanner.liveStableKey(it) }.toSet()

        for (row in existingRows) {
            if (row.groupId !in desiredKeys) {
                deletePlatformRow(row)
                tvProviderProgramDao.delete(profileId, row.surface, row.mediaType, row.groupId)
            }
        }

        recentChannels.forEachIndexed { index, channel ->
            val stableKey = launcherPlanner.liveStableKey(channel)
            val row = tvProviderProgramDao.find(profileId, TvProviderSurface.RECENT_LIVE, MediaType.LIVE, stableKey)
                ?: TvProviderProgramEntity(
                    profileId = profileId,
                    surface = TvProviderSurface.RECENT_LIVE,
                    mediaType = MediaType.LIVE,
                    groupId = stableKey,
                    targetItemId = channel.id,
                )
            publishRecentLiveProgram(profileId, channelId, channel, row, index, customizations)
        }

        tvProviderProgramDao.upsert(channelRow.copy(lastPublishedAt = now, lastEngagementAt = now))
        logD("refreshRecentLive profile=$profileId updated channel bookkeeping")
    }

    private suspend fun syncMovie(profileId: Long, movieId: Long, positionMs: Long, durationMs: Long, force: Boolean = false) {
        val movie = movieDao.getById(movieId) ?: return
        if (!launcherPlanner.isVisibleToProfile(profileId, movie.sourceId)) {
            logD("syncMovie skip hidden profile=$profileId movieId=$movieId sourceId=${movie.sourceId}")
            return
        }

        val groupId = launcherPlanner.movieStableKeyHash(movie)
        val existing = tvProviderProgramDao.find(profileId, TvProviderSurface.WATCH_NEXT, MediaType.MOVIE, groupId)
        val eligible = launcherPlanner.eligibleForWatchNext(positionMs, durationMs)
        logD(
            "syncMovie profile=$profileId movieId=$movieId groupId=$groupId existing=${existing?.providerProgramId ?: -1} " +
                "eligible=$eligible force=$force",
        )

        if (!eligible) {
            logD("syncMovie delete profile=$profileId movieId=$movieId groupId=$groupId reason=ineligible")
            deleteExisting(profileId, existing, MediaType.MOVIE, groupId)
            return
        }

        val row = persistableRow(
            existing = existing,
            profileId = profileId,
            mediaType = MediaType.MOVIE,
            groupId = groupId,
            targetItemId = movie.id,
            positionMs = positionMs,
            durationMs = durationMs,
        )
        if (!force && !shouldPublish(existing, row.targetItemId, positionMs, durationMs)) return
        upsertWatchNext(movie, row)
    }

    private suspend fun syncEpisode(profileId: Long, episodeId: Long, positionMs: Long, durationMs: Long, force: Boolean = false) {
        val episode = seriesDao.getEpisodeById(episodeId) ?: return
        val show = seriesDao.getSeriesById(episode.seriesId) ?: return
        if (!launcherPlanner.isVisibleToProfile(profileId, show.sourceId)) {
            logD("syncEpisode skip hidden profile=$profileId episodeId=$episodeId showId=${show.id} sourceId=${show.sourceId}")
            return
        }

        val episodes = launcherPlanner.orderedEpisodes(show.id)
        val currentIndex = episodes.indexOfFirst { it.id == episode.id }
        val isComplete = launcherPlanner.isCompleted(positionMs, durationMs)
        val currentGroupId = launcherPlanner.episodeStableKeyHash(show, episode)
        val existingCurrent = tvProviderProgramDao.find(profileId, TvProviderSurface.WATCH_NEXT, MediaType.EPISODE, currentGroupId)
        val eligible = launcherPlanner.eligibleForWatchNext(positionMs, durationMs)
        logD(
            "syncEpisode profile=$profileId episodeId=$episodeId showId=${show.id} groupId=$currentGroupId existing=${existingCurrent?.providerProgramId ?: -1} " +
                "eligible=$eligible complete=$isComplete currentIndex=$currentIndex force=$force",
        )
        if (!isComplete && !eligible) {
            logD("syncEpisode delete profile=$profileId episodeId=$episodeId groupId=$currentGroupId reason=ineligible")
            deleteExisting(profileId, existingCurrent, MediaType.EPISODE, currentGroupId)
            return
        }
        if (isComplete && currentIndex == episodes.lastIndex) {
            logD("syncEpisode delete profile=$profileId episodeId=$episodeId groupId=$currentGroupId reason=show-complete-last-episode")
            deleteExisting(profileId, existingCurrent, MediaType.EPISODE, currentGroupId)
            return
        }
        val targetCandidate: EpisodeEntity? = if (isComplete && currentIndex >= 0) {
            episodes.getOrNull(currentIndex + 1)
        } else {
            episode
        }
        val target = targetCandidate ?: run {
            deleteExisting(profileId, existingCurrent, MediaType.EPISODE, currentGroupId)
            return
        }

        val row = persistableRow(
            existing = existingCurrent,
            profileId = profileId,
            mediaType = MediaType.EPISODE,
            groupId = currentGroupId,
            targetItemId = target.id,
            positionMs = if (isComplete) 0L else positionMs,
            durationMs = if (isComplete) (target.durationSecs?.times(1000L) ?: durationMs) else durationMs,
        )
        if (!force && !shouldPublish(existingCurrent, row.targetItemId, positionMs, durationMs)) return
        val watchNextType = if (isComplete && target.id != episode.id) {
            TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
        } else {
            TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
        }
        logD(
            "syncEpisode publish profile=$profileId episodeId=$episodeId targetId=${target.id} watchNextType=$watchNextType " +
                "row=${row.describe()}",
        )
        upsertWatchNext(show, target, row, watchNextType, launcherPlanner.episodeStableKey(show, episode))
    }

    private suspend fun upsertWatchNext(movie: MovieEntity, row: TvProviderProgramEntity) {
        val stableKey = launcherPlanner.movieStableKey(movie)
        val program = WatchNextProgram.Builder()
            .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
            .setType(TvContractCompat.PreviewProgramColumns.TYPE_MOVIE)
            .setTitle(movie.name)
            .setDescription(movie.year?.toString() ?: "Movie")
            .setPosterArtUri(safeMediaArtUri(movie.posterUrl))
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_MOVIE_POSTER)
            .setLastPlaybackPositionMillis(safeMillisToInt(row.lastPositionMs))
            .setDurationMillis(safeMillisToInt(row.durationMs))
            .setInternalProviderId(platformInternalId(TvProviderSurface.WATCH_NEXT, row.profileId, MediaType.MOVIE, stableKey))
            .setIntent(Intent(Intent.ACTION_VIEW, LauncherDeepLink.Movie(movie.sourceId, movie.remoteId, movie.name).toUri()))
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
            .build()
        logD("persistWatchNext movie profile=${row.profileId} stableKey=$stableKey row=${row.describe()}")
        persistProgram(row, program)
    }

    private suspend fun upsertWatchNext(
        show: SeriesEntity,
        episode: EpisodeEntity,
        row: TvProviderProgramEntity,
        watchNextType: Int,
        rowStableKey: String,
    ) {
        val program = WatchNextProgram.Builder()
            .setWatchNextType(watchNextType)
            .setType(TvContractCompat.PreviewProgramColumns.TYPE_TV_EPISODE)
            .setTitle(episode.name.ifBlank { show.name })
            .setDescription(buildList {
                add(show.name)
                add("Season ${episode.seasonNumber}")
                if (episode.episodeNumber > 0) add("Episode ${episode.episodeNumber}")
            }.joinToString(" · "))
            .setPosterArtUri(safeMediaArtUri(show.posterUrl))
            .setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_MOVIE_POSTER)
            .setLastPlaybackPositionMillis(safeMillisToInt(row.lastPositionMs))
            .setDurationMillis(safeMillisToInt(row.durationMs))
            .setInternalProviderId(platformInternalId(TvProviderSurface.WATCH_NEXT, row.profileId, MediaType.EPISODE, rowStableKey))
            .setIntent(
                Intent(
                    Intent.ACTION_VIEW,
                    LauncherDeepLink.Episode(
                        seriesSourceId = show.sourceId,
                        seriesRemoteId = show.remoteId,
                        seriesName = show.name,
                        episodeRemoteId = episode.remoteId,
                        season = episode.seasonNumber,
                        episode = episode.episodeNumber,
                    ).toUri(),
                ),
            )
            .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
            .build()
        logD("persistWatchNext episode profile=${row.profileId} row=${row.describe()} watchNextType=$watchNextType")
        persistProgram(row, program)
    }

    private suspend fun persistProgram(row: TvProviderProgramEntity, program: WatchNextProgram) {
        val programId = row.providerProgramId
        if (programId != null) {
            val updated = runCatching {
                resolver.update(TvContractCompat.buildWatchNextProgramUri(programId), program.toContentValues(), null, null)
            }.onFailure { t ->
                logW("persistWatchNext update failed profile=${row.profileId} programId=$programId row=${row.describe()}", t)
            }.getOrNull() ?: return
            logD("persistWatchNext update profile=${row.profileId} programId=$programId updated=$updated row=${row.describe()}")
            if (updated == 0) {
                insertProgram(row, program)
            } else {
                tvProviderProgramDao.upsert(row.copy(lastPublishedAt = System.currentTimeMillis(), lastEngagementAt = System.currentTimeMillis()))
            }
        } else {
            insertProgram(row, program)
        }
    }

    private suspend fun insertProgram(row: TvProviderProgramEntity, program: WatchNextProgram) {
        val uri = runCatching { resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues()) }
            .onFailure { t ->
                logW("persistWatchNext insert failed profile=${row.profileId} row=${row.describe()}", t)
            }
            .getOrNull()
        if (uri == null) {
            logW("persistWatchNext insert returned null profile=${row.profileId} row=${row.describe()}")
            return
        }
        val providerId = ContentUris.parseId(uri)
        logD("persistWatchNext insert profile=${row.profileId} programId=$providerId row=${row.describe()}")
        tvProviderProgramDao.upsert(
            row.copy(
                providerProgramId = providerId,
                lastPublishedAt = System.currentTimeMillis(),
                lastEngagementAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun deleteExisting(profileId: Long, existing: TvProviderProgramEntity?, mediaType: MediaType, groupId: Long) {
        existing?.let { deletePlatformRow(it) }
        tvProviderProgramDao.delete(profileId, TvProviderSurface.WATCH_NEXT, mediaType, groupId)
    }

    private suspend fun deletePlatformRow(row: TvProviderProgramEntity) {
        row.providerProgramId?.let {
            runCatching {
                when (row.surface) {
                    TvProviderSurface.WATCH_NEXT -> resolver.delete(TvContractCompat.buildWatchNextProgramUri(it), null, null)
                    TvProviderSurface.RECENT_LIVE -> {
                        if (row.groupId == RECENT_LIVE_CHANNEL_GROUP_ID) channelHelper.deletePreviewChannel(it)
                        else channelHelper.deletePreviewProgram(it)
                    }
                }
            }
        }
    }

    private suspend fun ensureRecentLiveChannel(profileId: Long, allowBrowsableRequest: Boolean): TvProviderProgramEntity {
        val existing = tvProviderProgramDao.find(profileId, TvProviderSurface.RECENT_LIVE, MediaType.LIVE, RECENT_LIVE_CHANNEL_GROUP_ID)
        val now = System.currentTimeMillis()
        if (existing?.providerProgramId != null) {
            val current = runCatching { channelHelper.getPreviewChannel(existing.providerProgramId) }
                .getOrElse { t ->
                    logW("ensureRecentLiveChannel read failed profile=$profileId channelId=${existing.providerProgramId}", t)
                    null
                }
            if (current != null) {
                val desired = buildRecentLiveChannel(profileId)
                if (current.hasAnyUpdatedValues(desired)) {
                    logD("ensureRecentLiveChannel update profile=$profileId channelId=${existing.providerProgramId} allowBrowsable=$allowBrowsableRequest")
                    runCatching { channelHelper.updatePreviewChannel(existing.providerProgramId, desired) }
                }
                if (allowBrowsableRequest && !current.isBrowsable()) {
                    logD("ensureRecentLiveChannel requestBrowsable profile=$profileId channelId=${existing.providerProgramId}")
                    runCatching { TvContractCompat.requestChannelBrowsable(context, existing.providerProgramId) }
                }
                logD("ensureRecentLiveChannel reuse profile=$profileId channelId=${existing.providerProgramId}")
                return existing
            }
            logW("ensureRecentLiveChannel existing row missing from platform profile=$profileId providerId=${existing.providerProgramId}")
        }

        val channel = buildRecentLiveChannel(profileId)
        val ownChannelCount = runCatching { channelHelper.getAllChannels().count { it.packageName == context.packageName } }
            .getOrDefault(0)
        val channelId = runCatching {
            if (ownChannelCount == 0) channelHelper.publishDefaultChannel(channel)
            else {
                val published = channelHelper.publishChannel(channel)
                if (allowBrowsableRequest && published > 0) {
                    runCatching { TvContractCompat.requestChannelBrowsable(context, published) }
                }
                published
            }
        }.getOrElse { -1L }
        logD("ensureRecentLiveChannel publish profile=$profileId ownChannelCount=$ownChannelCount allowBrowsable=$allowBrowsableRequest result=$channelId")
        if (channelId <= 0L) {
            logW("ensureRecentLiveChannel publish failed profile=$profileId result=$channelId")
            return existing ?: TvProviderProgramEntity(
                profileId = profileId,
                surface = TvProviderSurface.RECENT_LIVE,
                mediaType = MediaType.LIVE,
                groupId = RECENT_LIVE_CHANNEL_GROUP_ID,
                targetItemId = 0L,
                providerProgramId = null,
                lastPublishedAt = 0L,
            )
        }

        val row = (existing ?: TvProviderProgramEntity(
            profileId = profileId,
            surface = TvProviderSurface.RECENT_LIVE,
            mediaType = MediaType.LIVE,
            groupId = RECENT_LIVE_CHANNEL_GROUP_ID,
            targetItemId = channelId,
            providerProgramId = channelId,
        )).copy(
            profileId = profileId,
            surface = TvProviderSurface.RECENT_LIVE,
            mediaType = MediaType.LIVE,
            groupId = RECENT_LIVE_CHANNEL_GROUP_ID,
            targetItemId = channelId,
            providerProgramId = channelId,
            lastEngagementAt = now,
            lastPublishedAt = 0L,
        )
        logD("ensureRecentLiveChannel stored profile=$profileId channelId=$channelId row=${row.describe()}")
        tvProviderProgramDao.upsert(row)
        return row
    }

    private suspend fun publishRecentLiveProgram(
        profileId: Long,
        channelId: Long,
        channel: com.thirutricks.tllplayer.core.database.entity.ChannelEntity,
        row: TvProviderProgramEntity,
        index: Int,
        customizations: SectionCustomizations,
    ) {
        val label = customizations.itemNames[CustomizeKeys.channel(channel)] ?: channel.name
        val art = safeLiveArtUri(channel.logoUrl)
        val stableKey = launcherPlanner.liveStableKey(channel)
        val stableKeyString = launcherPlanner.liveStableKeyString(channel)
        val program = PreviewProgram.Builder()
            .setChannelId(channelId)
            .setType(TvContractCompat.PreviewProgramColumns.TYPE_CHANNEL)
            .setTitle(label)
            .setDescription(channelDaoName(channel))
            .setInternalProviderId(platformInternalId(TvProviderSurface.RECENT_LIVE, profileId, MediaType.LIVE, stableKeyString))
            .setIntent(Intent(Intent.ACTION_VIEW, LauncherDeepLink.Live(channel.sourceId, channel.remoteId, channel.name).toUri()))
            .setWeight(RECENT_LIVE_MAX_ITEMS - index)
            .apply { if (art != null) setPosterArtUri(art) }
            .apply { if (art != null) setPosterArtAspectRatio(TvContractCompat.PreviewProgramColumns.ASPECT_RATIO_16_9) }
            .build()
        logD("persistRecentLive profile=$profileId channelId=$channelId index=$index label=$label row=${row.describe()}")
        persistRecentLiveProgram(row, program, stableKey)
    }

    private suspend fun persistRecentLiveProgram(row: TvProviderProgramEntity, program: PreviewProgram, stableKey: Long) {
        val existing = row.providerProgramId
        if (existing != null) {
            val updated = runCatching {
                resolver.update(TvContractCompat.buildPreviewProgramUri(existing), program.toContentValues(), null, null)
            }.onFailure { t ->
                logW("persistRecentLive update failed profile=${row.profileId} programId=$existing row=${row.describe()}", t)
            }.getOrNull() ?: return
            logD("persistRecentLive update profile=${row.profileId} programId=$existing updated=$updated row=${row.describe()}")
            if (updated == 0) {
                insertRecentLiveProgram(row, program, stableKey)
            } else {
                tvProviderProgramDao.upsert(row.copy(lastPublishedAt = System.currentTimeMillis(), lastEngagementAt = System.currentTimeMillis(), groupId = stableKey))
            }
        } else {
            insertRecentLiveProgram(row, program, stableKey)
        }
    }

    private suspend fun insertRecentLiveProgram(row: TvProviderProgramEntity, program: PreviewProgram, stableKey: Long) {
        val uri = runCatching { resolver.insert(TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues()) }
            .onFailure { t ->
                logW("persistRecentLive insert failed profile=${row.profileId} row=${row.describe()}", t)
            }
            .getOrNull()
        if (uri == null) {
            logW("persistRecentLive insert returned null profile=${row.profileId} row=${row.describe()}")
            return
        }
        val providerId = ContentUris.parseId(uri)
        logD("persistRecentLive insert profile=${row.profileId} programId=$providerId row=${row.describe()}")
        tvProviderProgramDao.upsert(
            row.copy(
                providerProgramId = providerId,
                groupId = stableKey,
                lastPublishedAt = System.currentTimeMillis(),
                lastEngagementAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun buildRecentLiveChannel(profileId: Long): PreviewChannel {
        return PreviewChannel.Builder()
            .setDisplayName(RECENT_LIVE_CHANNEL_NAME)
            .setDescription("Recently watched live channels")
            .setAppLinkIntentUri(LauncherDeepLink.OpenLiveSection.toUri())
            .setInternalProviderId(platformInternalId(TvProviderSurface.RECENT_LIVE, profileId, MediaType.LIVE, RECENT_LIVE_CHANNEL_STABLE_KEY))
            .setLogo(resourceUri(R.drawable.tv_banner))
            .build()
    }

    private fun safeMillisToInt(value: Long): Int = value.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    private fun safeLiveArtUri(raw: String?): Uri? {
        val value = raw?.trim().orEmpty()
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value.toUri()
            else -> null
        }
    }

    private fun safeMediaArtUri(raw: String?): Uri? = safeLiveArtUri(raw)

    private fun resourceUri(resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")

    private fun isHidden(customizations: SectionCustomizations, channel: com.thirutricks.tllplayer.core.database.entity.ChannelEntity): Boolean =
        CustomizeKeys.channel(channel) in customizations.hiddenItems

    private fun channelDaoName(channel: com.thirutricks.tllplayer.core.database.entity.ChannelEntity): String = channel.name
    private fun platformInternalId(surface: TvProviderSurface, profileId: Long, mediaType: MediaType, stableKey: String): String =
        "owntv:${surface.name.lowercase()}:${sha256Hex("$profileId|${mediaType.name}|$stableKey")}"

    private fun sha256Hex(value: String): String = sha256Bytes(value).joinToString("") { "%02x".format(it) }

    private fun sha256Bytes(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))

    private fun shouldPublish(
        existing: TvProviderProgramEntity?,
        targetItemId: Long,
        positionMs: Long,
        durationMs: Long,
    ): Boolean {
        val now = System.currentTimeMillis()
        if (existing == null || existing.providerProgramId == null) return true
        if (existing.targetItemId != targetItemId) return true
        if (launcherPlanner.isCompleted(positionMs, durationMs)) return true
        return now - existing.lastPublishedAt >= WATCH_NEXT_PUBLISH_INTERVAL_MS
    }

    private fun persistableRow(
        existing: TvProviderProgramEntity?,
        profileId: Long,
        mediaType: MediaType,
        groupId: Long,
        targetItemId: Long,
        positionMs: Long,
        durationMs: Long,
    ): TvProviderProgramEntity = (existing ?: TvProviderProgramEntity(
        profileId = profileId,
        surface = TvProviderSurface.WATCH_NEXT,
        mediaType = mediaType,
        groupId = groupId,
        targetItemId = targetItemId,
    )).copy(
        profileId = profileId,
        surface = TvProviderSurface.WATCH_NEXT,
        mediaType = mediaType,
        groupId = groupId,
        targetItemId = targetItemId,
        lastPositionMs = positionMs,
        durationMs = durationMs,
        lastEngagementAt = System.currentTimeMillis(),
        lastPublishedAt = System.currentTimeMillis(),
    )

    private fun TvProviderProgramEntity.key(): String = "${surface.name}:${mediaType.name}:$groupId"
}
