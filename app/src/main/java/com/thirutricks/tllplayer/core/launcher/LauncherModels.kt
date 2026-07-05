package com.thirutricks.tllplayer.core.launcher

import android.net.Uri

enum class LauncherContinuationKind {
    MOVIE,
    EPISODE,
    LIVE,
}

enum class LauncherWatchNextType {
    CONTINUE,
    NEXT,
}

data class LauncherContinuationItem(
    val kind: LauncherContinuationKind,
    val sourceItemId: Long,
    val targetItemId: Long,
    val stableKey: String,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val playbackUri: Uri,
    val lastEngagementAt: Long,
    val durationMs: Long,
    val positionMs: Long,
    val watchNextType: LauncherWatchNextType,
    val containerTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val year: Int? = null,
)

data class LauncherRecommendationCluster(
    val stableKey: String,
    val title: String,
    val description: String? = null,
)

data class LauncherSnapshot(
    val profileId: Long,
    val continuationItems: List<LauncherContinuationItem>,
    val recommendationClusters: List<LauncherRecommendationCluster> = emptyList(),
)
