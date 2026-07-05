package com.thirutricks.tllplayer.core.launcher

/**
 * App-facing launcher repository. It keeps the public methods in one place while the old TV Provider
 * publisher remains the implementation detail for now.
 */
class LauncherIntegrationRepository(
    private val planner: LauncherRecommendationPlanner,
    private val resolver: LauncherLaunchResolver,
    private val tvHomeRepository: com.thirutricks.tllplayer.core.tv.TvHomeRepository,
) {
    suspend fun buildSnapshot(profileId: Long): LauncherSnapshot = planner.buildSnapshot(profileId)

    suspend fun refreshProfile(profileId: Long, allowBrowsableRequest: Boolean = false) =
        tvHomeRepository.refreshProfile(profileId, allowBrowsableRequest)

    suspend fun clearProfile(profileId: Long) = tvHomeRepository.clearProfile(profileId)

    suspend fun publishMovieProgress(profileId: Long, movieId: Long, positionMs: Long, durationMs: Long) =
        tvHomeRepository.publishMovieProgress(profileId, movieId, positionMs, durationMs)

    suspend fun publishEpisodeProgress(profileId: Long, episodeId: Long, positionMs: Long, durationMs: Long) =
        tvHomeRepository.publishEpisodeProgress(profileId, episodeId, positionMs, durationMs)

    suspend fun refreshRecentLive(profileId: Long, allowBrowsableRequest: Boolean = false) =
        tvHomeRepository.refreshRecentLive(profileId, allowBrowsableRequest)

    suspend fun resolveLaunch(profileId: Long, deepLink: LauncherDeepLink): LauncherLaunch? =
        resolver.resolveLaunch(profileId, deepLink)
}
