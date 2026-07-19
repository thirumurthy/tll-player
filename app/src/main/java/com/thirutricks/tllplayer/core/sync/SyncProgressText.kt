package com.thirutricks.tllplayer.core.sync

import com.thirutricks.tllplayer.core.model.SourceType

data class SyncProgressCounts(
    val live: Int,
    val movies: Int,
    val series: Int,
    val liveActive: Boolean,
    val moviesActive: Boolean,
    val seriesActive: Boolean,
) {
    val hasItems: Boolean
        get() = live > 0 || movies > 0 || series > 0

    fun label(): String = buildList {
        if (liveActive && live > 0) add("${syncCount(live)} channels")
        if (moviesActive && movies > 0) add("${syncCount(movies)} movies")
        if (seriesActive && series > 0) add("${syncCount(series)} series")
    }.joinToString(" · ")
}

data class SyncProgressDisplay(
    val title: String,
    val primaryText: String,
    val detail: String,
)

fun ImportStage.progressCounts(): SyncProgressCounts = SyncProgressCounts(
    live = liveProcessed,
    movies = moviesProcessed,
    series = seriesProcessed,
    liveActive = liveActive,
    moviesActive = moviesActive,
    seriesActive = seriesActive,
)

fun ImportStage.importProgressDisplay(): SyncProgressDisplay =
    importProgressDisplay(progressCounts())

fun syncProgressCountsForSource(
    sourceType: SourceType,
    liveProcessed: Int,
    moviesProcessed: Int,
    seriesProcessed: Int,
    liveActive: Boolean,
    moviesActive: Boolean,
    seriesActive: Boolean,
): SyncProgressCounts = when (sourceType) {
    SourceType.M3U -> SyncProgressCounts(
        live = liveProcessed,
        movies = 0,
        series = 0,
        liveActive = true,
        moviesActive = false,
        seriesActive = false,
    )
    SourceType.XTREAM -> {
        val hasActivePhase = liveActive || moviesActive || seriesActive
        SyncProgressCounts(
            live = liveProcessed,
            movies = moviesProcessed,
            series = seriesProcessed,
            liveActive = if (hasActivePhase) liveActive else true,
            moviesActive = if (hasActivePhase) moviesActive else true,
            seriesActive = if (hasActivePhase) seriesActive else true,
        )
    }
    SourceType.LOCAL_BACKUP -> SyncProgressCounts(
        live = 0,
        movies = 0,
        series = 0,
        liveActive = false,
        moviesActive = false,
        seriesActive = false,
    )
    // Stalker: LIVE ships (Phase C-1); VOD/series arrive in Phase D. Progress is shaped like Xtream
    // so a later Phase D needs no change here.
    SourceType.STALKER -> {
        val hasActivePhase = liveActive || moviesActive || seriesActive
        SyncProgressCounts(
            live = liveProcessed,
            movies = moviesProcessed,
            series = seriesProcessed,
            liveActive = if (hasActivePhase) liveActive else true,
            moviesActive = if (hasActivePhase) moviesActive else true,
            seriesActive = if (hasActivePhase) seriesActive else true,
        )
    }
}

fun importProgressDisplay(counts: SyncProgressCounts?): SyncProgressDisplay {
    val label = counts?.label().orEmpty()
    val primaryText = label.ifBlank { "Preparing catalog" }
    return SyncProgressDisplay(
        title = "Importing catalog…",
        primaryText = primaryText,
        detail = if (counts?.hasItems == true) "Syncing catalog" else "Connecting to source...",
    )
}

fun resyncBadgeText(baseItemCount: Int, totalProcessed: Int): String =
    if (baseItemCount > 0 && totalProcessed > 0) {
        "Syncing ${((totalProcessed * 100L) / baseItemCount).coerceIn(1, 99)}%"
    } else {
        "Syncing"
    }

fun syncProgressDisplay(counts: SyncProgressCounts?): SyncProgressDisplay =
    SyncProgressDisplay(
        title = "Importing catalog…",
        primaryText = counts?.label()?.ifBlank { null } ?: "Preparing catalog",
        detail = if (counts?.hasItems == true) "Syncing catalog" else "Connecting to source...",
    )

fun syncProgressCountsLabel(counts: SyncProgressCounts): String? =
    counts.takeIf { it.hasItems }?.label()?.ifBlank { null }

private fun syncCount(count: Int): String = when {
    count >= 1_000_000 -> scaledCount(count / 1_000_000.0, "M")
    count >= 1_000 -> scaledCount(count / 1_000.0, "K")
    else -> count.toString()
}

private fun scaledCount(value: Double, suffix: String): String =
    "%.1f".format(value).removeSuffix(".0") + suffix
