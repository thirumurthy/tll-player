package com.thirutricks.tllplayer.core.sync

import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.MovieDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.entity.SourceEntity

/** Per-type counts after a playlist import, for the success breakdown. */
data class SyncCounts(val channels: Int, val movies: Int, val series: Int, val epg: Int = 0) {
    /** e.g. "40K channels · 100K movies · 30K series · 30K EPG synced". */
    fun summary(includeEpg: Boolean): String {
        val parts = buildList {
            if (channels > 0) add("${human(channels)} channels")
            if (movies > 0) add("${human(movies)} movies")
            if (series > 0) add("${human(series)} series")
            if (includeEpg && epg > 0) add("${human(epg)} EPG")
        }
        return if (parts.isEmpty()) "Synced successfully" else parts.joinToString(" · ") + " synced"
    }

    /** Content breakdown without a trailing verb, for list rows: "40K channels · 100K movies · 30K series". */
    val breakdown: String
        get() = buildList {
            if (channels > 0) add("${human(channels)} channels")
            if (movies > 0) add("${human(movies)} movies")
            if (series > 0) add("${human(series)} series")
        }.joinToString(" · ")

    private fun human(n: Int): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).removeSuffix(".0M").let { if (it.endsWith("M")) it else "${it}M" }
        n >= 1_000 -> "%.1fK".format(n / 1_000.0).removeSuffix(".0K").let { if (it.endsWith("K")) it else "${it}K" }
        else -> n.toString()
    }
}

/**
 * Runs after a playlist syncs: returns the per-type content counts for the success message. EPG is NOT
 * auto-created/synced here — that's slow and not everyone wants it. The user adds a guide explicitly via
 * Settings → EPG sources, where the form pre-fills the playlist's own guide URL (Xtream `xmltv.php` /
 * M3U `url-tvg`) so it's still one tap.
 */
class ImportFinalizer(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
) {
    suspend fun finalize(source: SourceEntity): SyncCounts = contentCounts(source.id)

    /** Current content counts for a source (no EPG) — for the success message and the Playlists list rows. */
    suspend fun contentCounts(sourceId: Long): SyncCounts = SyncCounts(
        channels = channelDao.countForSourceOnce(sourceId),
        movies = movieDao.countForSourceOnce(sourceId),
        series = seriesDao.countForSourceOnce(sourceId),
    )
}
