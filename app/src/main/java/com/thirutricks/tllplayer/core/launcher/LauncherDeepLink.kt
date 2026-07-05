package com.thirutricks.tllplayer.core.launcher

import android.net.Uri
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpisodeEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity

/** Internal deep links used by launcher rows and external launch intents. */
sealed interface LauncherDeepLink {
    fun toUri(): Uri

    data object OpenLiveSection : LauncherDeepLink {
        override fun toUri(): Uri = Uri.parse("owntv://open/live")
    }

    data class Movie(
        val sourceId: Long? = null,
        val remoteId: String? = null,
        val name: String? = null,
        val itemId: Long? = null,
    ) : LauncherDeepLink {
        override fun toUri(): Uri = Uri.parse("owntv://play/movie").buildUpon()
            .appendOptionalQueryParameter("sourceId", sourceId?.toString())
            .appendOptionalQueryParameter("remoteId", remoteId)
            .appendOptionalQueryParameter("name", name)
            .appendOptionalQueryParameter("itemId", itemId?.toString())
            .build()
    }

    data class Episode(
        val seriesSourceId: Long? = null,
        val seriesRemoteId: String? = null,
        val seriesName: String? = null,
        val episodeRemoteId: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val seriesItemId: Long? = null,
        val episodeItemId: Long? = null,
    ) : LauncherDeepLink {
        override fun toUri(): Uri = Uri.parse("owntv://play/episode").buildUpon()
            .appendOptionalQueryParameter("seriesSourceId", seriesSourceId?.toString())
            .appendOptionalQueryParameter("seriesRemoteId", seriesRemoteId)
            .appendOptionalQueryParameter("seriesName", seriesName)
            .appendOptionalQueryParameter("episodeRemoteId", episodeRemoteId)
            .appendOptionalQueryParameter("season", season?.toString())
            .appendOptionalQueryParameter("episode", episode?.toString())
            .appendOptionalQueryParameter("seriesId", seriesItemId?.toString())
            .appendOptionalQueryParameter("itemId", episodeItemId?.toString())
            .build()
    }

    data class Live(
        val sourceId: Long? = null,
        val remoteId: String? = null,
        val name: String? = null,
        val itemId: Long? = null,
    ) : LauncherDeepLink {
        override fun toUri(): Uri = Uri.parse("owntv://play/live").buildUpon()
            .appendOptionalQueryParameter("sourceId", sourceId?.toString())
            .appendOptionalQueryParameter("remoteId", remoteId)
            .appendOptionalQueryParameter("name", name)
            .appendOptionalQueryParameter("itemId", itemId?.toString())
            .build()
    }

    companion object {
        fun parse(uri: Uri?): LauncherDeepLink? {
            if (uri == null || uri.scheme != "owntv") return null
            return when (uri.host) {
                "open" -> when (uri.pathSegments.firstOrNull()) {
                    "live" -> OpenLiveSection
                    else -> null
                }
                "play" -> when (uri.pathSegments.firstOrNull()) {
                    "movie" -> Movie(
                        sourceId = uri.queryLong("sourceId"),
                        remoteId = uri.queryString("remoteId"),
                        name = uri.queryString("name"),
                        itemId = uri.queryLong("itemId"),
                    )
                    "episode" -> Episode(
                        seriesSourceId = uri.queryLong("seriesSourceId"),
                        seriesRemoteId = uri.queryString("seriesRemoteId"),
                        seriesName = uri.queryString("seriesName"),
                        episodeRemoteId = uri.queryString("episodeRemoteId"),
                        season = uri.queryInt("season"),
                        episode = uri.queryInt("episode"),
                        seriesItemId = uri.queryLong("seriesId"),
                        episodeItemId = uri.queryLong("itemId"),
                    )
                    "live" -> Live(
                        sourceId = uri.queryLong("sourceId"),
                        remoteId = uri.queryString("remoteId"),
                        name = uri.queryString("name"),
                        itemId = uri.queryLong("itemId"),
                    )
                    else -> null
                }
                else -> null
            }
        }

        private fun Uri.queryString(name: String): String? =
            getQueryParameter(name)?.trim()?.takeIf { it.isNotEmpty() }

        private fun Uri.queryLong(name: String): Long? =
            getQueryParameter(name)?.toLongOrNull()

        private fun Uri.queryInt(name: String): Int? =
            getQueryParameter(name)?.toIntOrNull()
    }
}

private fun Uri.Builder.appendOptionalQueryParameter(name: String, value: String?): Uri.Builder = apply {
    if (!value.isNullOrBlank()) appendQueryParameter(name, value)
}

sealed interface LauncherLaunch {
    data class Movie(val movie: MovieEntity, val startPositionMs: Long) : LauncherLaunch
    data class Episode(val show: SeriesEntity, val episode: EpisodeEntity, val queue: List<EpisodeEntity>, val startPositionMs: Long) : LauncherLaunch
    data class Series(val show: SeriesEntity) : LauncherLaunch
    data class Live(val channel: ChannelEntity) : LauncherLaunch
}
