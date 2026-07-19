package com.thirutricks.tllplayer.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TMDB enrichment cache (plan §7). Two additive tables, both pure caches — safe to wipe (they re-fetch)
 * and deliberately decoupled from the huge content tables (movies/series/episodes are never mutated).
 *
 * IMPORTANT: content DB ids change on every re-sync (M3U is clear-then-insert), so [MetadataMatchEntity]
 * keys on a *stable* content identity (`type:sourceId:remoteId|name`), the same survives-resync approach
 * as CustomizeKeys — never the raw autoincrement id.
 */

/** One resolved TMDB object. [key] is e.g. "movie:872585" (or later "tv:1399", "tv:1399:s1e1"). */
@Entity(
    tableName = "metadata_cache",
    indices = [Index("tmdbId"), Index("updatedAt")],
)
data class MetadataCacheEntity(
    @PrimaryKey val key: String,
    val tmdbId: Int,
    val imdbId: String?,      // the id Trakt sync will need
    val type: String,         // movie | tv | episode
    val title: String,
    val year: Int?,
    val overview: String?,
    val posterPath: String?,  // TMDB relative path; build the image.tmdb.org URL at render time
    val backdropPath: String?,
    val rating: Double?,
    val genresJson: String?,  // JSON array of genre names
    val castJson: String?,    // JSON array of top cast names
    val trailerKey: String?,  // YouTube video key for the in-app trailer player (plan §7.3); null = no trailer
    val logoPath: String?,    // TMDB title/logo path for cinematic Home hero treatment; null = text title fallback
    val updatedAt: Long,      // for TTL / manual refresh
)

/**
 * Local content item → resolved TMDB id (or a negative "searched, no match" marker). Keeps the 170k-row
 * content tables from being re-searched on every scroll and lets a wrong match be undone by deleting the
 * mapping (which restores the pure provider view). [tmdbId] null = negative cache.
 */
@Entity(
    tableName = "metadata_match",
    indices = [Index("updatedAt")],
)
data class MetadataMatchEntity(
    /** Stable, re-sync-proof key: e.g. "movie:12:tt-remote" or "movie:12:Some Title" (see plan §7). */
    @PrimaryKey val localKey: String,
    val type: String,         // movie | tv | episode
    val tmdbId: Int?,         // null = negative cache (no confident match)
    val confidence: Double,   // 0..1 match confidence (0 for negative)
    val updatedAt: Long,
)
