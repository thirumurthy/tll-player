package com.thirutricks.tllplayer.core.launcher

import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.thirutricks.tllplayer.core.database.dao.MovieDao
import com.thirutricks.tllplayer.core.database.dao.ProgressDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpisodeEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.PlaybackProgressEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.core.model.MediaType
import java.nio.ByteBuffer
import java.security.MessageDigest

class LauncherRecommendationPlanner(
    private val sourceDao: SourceDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val progressDao: ProgressDao,
) {
    companion object {
        private const val WATCH_NEXT_MIN_POSITION_MS = 10_000L
        private const val WATCH_NEXT_COMPLETE_FRACTION = 0.95f
        private const val CONTINUATION_MAX_ITEMS = 10
    }

    suspend fun buildSnapshot(profileId: Long): LauncherSnapshot = withContext(Dispatchers.IO) {
        LauncherSnapshot(profileId = profileId, continuationItems = buildContinuationItems(profileId))
    }

    suspend fun buildContinuationItems(profileId: Long): List<LauncherContinuationItem> = withContext(Dispatchers.IO) {
        val sourceIds = sourceIdsForProfile(profileId)
        if (sourceIds.isEmpty()) return@withContext emptyList()

        val allProgress = progressDao.getAllOnce().filter { it.profileId == profileId }
        val out = ArrayList<LauncherContinuationItem>()

        for (progress in allProgress) {
            when (progress.mediaType) {
                MediaType.MOVIE -> {
                    val movie = movieDao.getById(progress.itemId) ?: continue
                    if (movie.sourceId !in sourceIds) continue
                    if (!eligibleForWatchNext(progress.positionMs, progress.durationMs)) continue
                    out += movieItem(movie, progress)
                }

                MediaType.EPISODE -> Unit
                MediaType.LIVE, MediaType.SERIES -> Unit
            }
        }

        val latestBySeries = LinkedHashMap<Long, PlaybackProgressEntity>()
        for (progress in allProgress) {
            if (progress.mediaType != MediaType.EPISODE) continue
            val episode = seriesDao.getEpisodeById(progress.itemId) ?: continue
            val show = seriesDao.getSeriesById(episode.seriesId) ?: continue
            if (show.sourceId !in sourceIds) continue
            val current = latestBySeries[episode.seriesId]
            if (current == null || progress.updatedAt >= current.updatedAt) {
                latestBySeries[episode.seriesId] = progress
            }
        }
        for (progress in latestBySeries.values) {
            val episode = seriesDao.getEpisodeById(progress.itemId) ?: continue
            val show = seriesDao.getSeriesById(episode.seriesId) ?: continue
            val episodes = orderedEpisodes(show.id)
            val currentIndex = episodes.indexOfFirst { it.id == episode.id }
            val complete = isCompleted(progress)
            if (!complete && !eligibleForWatchNext(progress.positionMs, progress.durationMs)) continue
            if (complete && currentIndex == episodes.lastIndex) continue
            out += episodeItem(show, episode, progress, episodes, currentIndex, complete)
        }

        out.sortedByDescending { it.lastEngagementAt }.take(CONTINUATION_MAX_ITEMS)
    }

    suspend fun sourceIdsForProfile(profileId: Long): Set<Long> = sourceDao.sourceIdsForProfile(profileId).toSet()

    suspend fun isVisibleToProfile(profileId: Long, sourceId: Long): Boolean =
        sourceIdsForProfile(profileId).contains(sourceId)

    suspend fun orderedEpisodes(seriesId: Long): List<EpisodeEntity> =
        seriesDao.episodesBySeries(seriesId).first().sortedWith(compareBy<EpisodeEntity> { it.seasonNumber }.thenBy { it.episodeNumber })

    fun movieStableKey(movie: MovieEntity): String =
        "movie:${movie.sourceId}:${movie.remoteId ?: movie.name}"

    fun episodeStableKey(show: SeriesEntity, episode: EpisodeEntity): String =
        "episode:${show.sourceId}:${show.remoteId ?: show.name}:${episode.remoteId ?: "${episode.seasonNumber}-${episode.episodeNumber}"}"

    fun liveStableKeyString(channel: ChannelEntity): String =
        "live:${channel.sourceId}:${channel.remoteId ?: channel.name}"

    fun movieStableKeyHash(movie: MovieEntity): Long = stableHash64(movieStableKey(movie))

    fun episodeStableKeyHash(show: SeriesEntity, episode: EpisodeEntity): Long =
        stableHash64(episodeStableKey(show, episode))

    fun liveStableKey(channel: ChannelEntity): Long = stableHash64(liveStableKeyString(channel))

    fun eligibleForWatchNext(positionMs: Long, durationMs: Long): Boolean =
        positionMs >= WATCH_NEXT_MIN_POSITION_MS && durationMs > 0 && !isCompleted(positionMs, durationMs)

    fun isCompleted(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= (durationMs * WATCH_NEXT_COMPLETE_FRACTION).toLong()

    fun isCompleted(progress: PlaybackProgressEntity): Boolean =
        isCompleted(progress.positionMs, progress.durationMs)

    private fun movieItem(movie: MovieEntity, progress: PlaybackProgressEntity): LauncherContinuationItem {
        val durationMs = progress.durationMs.takeIf { it > 0 } ?: movie.durationSecs?.times(1000L) ?: 0L
        return LauncherContinuationItem(
            kind = LauncherContinuationKind.MOVIE,
            sourceItemId = movie.id,
            targetItemId = movie.id,
            stableKey = movieStableKey(movie),
            title = movie.name,
            subtitle = movie.year?.toString() ?: "Movie",
            posterUrl = movie.posterUrl,
            playbackUri = LauncherDeepLink.Movie(
                sourceId = movie.sourceId,
                remoteId = movie.remoteId,
                name = movie.name,
                itemId = movie.id,
            ).toUri(),
            lastEngagementAt = progress.updatedAt,
            durationMs = durationMs,
            positionMs = progress.positionMs,
            watchNextType = LauncherWatchNextType.CONTINUE,
            year = movie.year,
        )
    }

    private fun episodeItem(
        show: SeriesEntity,
        episode: EpisodeEntity,
        progress: PlaybackProgressEntity,
        episodes: List<EpisodeEntity>,
        currentIndex: Int,
        complete: Boolean,
    ): LauncherContinuationItem {
        val target = if (complete && currentIndex >= 0) episodes.getOrNull(currentIndex + 1) else episode
        val watchNextType = if (complete && target != null && target.id != episode.id) LauncherWatchNextType.NEXT else LauncherWatchNextType.CONTINUE
        val sourceEpisode = episode
        val targetEpisode = target ?: episode
        val durationMs = if (complete && target != null && target.id != episode.id) {
            target.durationSecs?.times(1000L) ?: progress.durationMs
        } else {
            progress.durationMs.takeIf { it > 0 } ?: episode.durationSecs?.times(1000L) ?: 0L
        }
        val positionMs = if (watchNextType == LauncherWatchNextType.NEXT) 0L else progress.positionMs
        return LauncherContinuationItem(
            kind = LauncherContinuationKind.EPISODE,
            sourceItemId = sourceEpisode.id,
            targetItemId = targetEpisode.id,
            stableKey = episodeStableKey(show, sourceEpisode),
            title = targetEpisode.name.ifBlank { show.name },
            subtitle = buildList {
                add(show.name)
                add("Season ${targetEpisode.seasonNumber}")
                if (targetEpisode.episodeNumber > 0) add("Episode ${targetEpisode.episodeNumber}")
            }.joinToString(" · "),
            posterUrl = show.posterUrl,
            playbackUri = LauncherDeepLink.Episode(
                seriesSourceId = show.sourceId,
                seriesRemoteId = show.remoteId,
                seriesName = show.name,
                episodeRemoteId = targetEpisode.remoteId,
                season = targetEpisode.seasonNumber,
                episode = targetEpisode.episodeNumber,
                seriesItemId = show.id,
                episodeItemId = targetEpisode.id,
            ).toUri(),
            lastEngagementAt = progress.updatedAt,
            durationMs = durationMs,
            positionMs = positionMs,
            watchNextType = watchNextType,
            containerTitle = show.name,
            seasonNumber = targetEpisode.seasonNumber,
            episodeNumber = targetEpisode.episodeNumber,
        )
    }

    private fun stableHash64(value: String): Long = ByteBuffer.wrap(sha256Bytes(value)).long

    private fun sha256Bytes(value: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
}
