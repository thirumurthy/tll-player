package com.thirutricks.tllplayer.core.metadata

import android.util.Log
import org.json.JSONArray
import com.thirutricks.tllplayer.core.database.dao.MetadataDao
import com.thirutricks.tllplayer.core.database.entity.MetadataCacheEntity
import com.thirutricks.tllplayer.core.database.entity.MetadataMatchEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository

/**
 * On-demand TMDB enrichment orchestrator (plan §3, §7). Resolves a local content item → TMDB metadata,
 * caching both the resolution (match table) and the metadata (cache table) so a second view is instant
 * and offline. NEVER bulk — callers invoke this lazily when a detail screen opens.
 *
 * Merge rule (§7.1) is applied by the UI at render time (`providerField ?: tmdbField`); this layer only
 * fetches and caches TMDB fields, never mutating the provider content tables.
 */
class MetadataRepository(
    private val provider: MetadataProvider,
    private val dao: MetadataDao,
    private val settings: SettingsRepository,
    private val overrideStore: MetadataOverrideStore,
) {

    /**
     * Resolve TMDB metadata for a movie. Returns the cached row (fresh or freshly fetched), or null when
     * enrichment is off, no confident match exists, or the network failed. Cheap on repeat calls.
     */
    suspend fun resolveMovie(movie: MovieEntity): MetadataCacheEntity? {
        if (!settings.metadataConfig().enabled) return null

        val localKey = movieLocalKey(movie)
        val now = System.currentTimeMillis()

        // 1. Consult the local→tmdb mapping (incl. negative cache) before hitting the network.
        dao.getMatch(localKey)?.let { match ->
            val ttl = if (match.tmdbId == null) NEGATIVE_TTL_MS else POSITIVE_TTL_MS
            if (now - match.updatedAt < ttl) {
                val tmdbId = match.tmdbId ?: return null // fresh negative cache
                dao.getCache(cacheKey(tmdbId))?.let { return it } // fresh positive cache
                // Match known but cache row missing/evicted → re-fetch details below.
                return fetchAndCache(tmdbId, localKey, match.confidence)
            }
        }

        // 2. Build the search query: a user override (plan §11.2 U5b) wins over the auto-normalizer.
        val q = resolveQuery(localKey, movie.name, movie.year)
        if (q.query.isBlank()) return null

        // null = transport failure (offline / rate-limited / proxy down): bail WITHOUT negative-caching,
        // so the title retries next time instead of showing no metadata for 7 days.
        val hits = runCatching { provider.searchMovie(q.query, q.year) }
            .onFailure { Log.w(TAG, "resolveMovie search failed: ${it.message}") }
            .getOrNull() ?: return null

        // An override is the user telling us the exact name → trust TMDB's top relevance hit directly
        // (no fuzzy threshold) so a hand-typed title isn't rejected over punctuation/formatting differences.
        val best: Scored? = if (q.isOverride) hits.firstOrNull()?.let { Scored(it, 1.0) } else pickBest(q.query, q.year, hits)
        if (best == null) {
            // Negative cache: remember "searched, no confident match" so we don't re-hammer on scroll.
            dao.upsertMatch(MetadataMatchEntity(localKey, TYPE_MOVIE, tmdbId = null, confidence = 0.0, updatedAt = now))
            return null
        }

        dao.upsertMatch(MetadataMatchEntity(localKey, TYPE_MOVIE, tmdbId = best.result.tmdbId, confidence = best.score, updatedAt = now))
        return fetchAndCache(best.result.tmdbId, localKey, best.score, fallback = best.result)
    }

    /**
     * Resolve TMDB metadata for a series (show-level). Same lazy resolve + cache + negative-cache as
     * [resolveMovie], but against TMDB's TV endpoints. Cache/match keyed with the "tv" type.
     */
    suspend fun resolveSeries(series: com.thirutricks.tllplayer.core.database.entity.SeriesEntity): MetadataCacheEntity? {
        if (!settings.metadataConfig().enabled) return null

        val localKey = seriesLocalKey(series)
        val now = System.currentTimeMillis()

        dao.getMatch(localKey)?.let { match ->
            val ttl = if (match.tmdbId == null) NEGATIVE_TTL_MS else POSITIVE_TTL_MS
            if (now - match.updatedAt < ttl) {
                val tmdbId = match.tmdbId ?: return null
                dao.getCache(tvCacheKey(tmdbId))?.let { return it }
                return fetchAndCacheTv(tmdbId, null)
            }
        }

        val q = resolveQuery(localKey, series.name, series.year)
        if (q.query.isBlank()) return null

        // Same as resolveMovie: null = transport failure → no negative-cache, retry next open.
        val hits = runCatching { provider.searchTv(q.query, q.year) }
            .onFailure { Log.w(TAG, "resolveSeries search failed: ${it.message}") }
            .getOrNull() ?: return null

        // An override is the user telling us the exact name → trust TMDB's top relevance hit directly.
        val best: Scored? = if (q.isOverride) hits.firstOrNull()?.let { Scored(it, 1.0) } else pickBest(q.query, q.year, hits)
        if (best == null) {
            dao.upsertMatch(MetadataMatchEntity(localKey, TYPE_TV, tmdbId = null, confidence = 0.0, updatedAt = now))
            return null
        }
        dao.upsertMatch(MetadataMatchEntity(localKey, TYPE_TV, tmdbId = best.result.tmdbId, confidence = best.score, updatedAt = now))
        return fetchAndCacheTv(best.result.tmdbId, best.result)
    }

    private suspend fun fetchAndCacheTv(tmdbId: Int, fallback: MetadataSearchResult?): MetadataCacheEntity? {
        val now = System.currentTimeMillis()
        val details = provider.tvDetails(tmdbId)
        val entity = when {
            details != null -> MetadataCacheEntity(
                key = tvCacheKey(tmdbId), tmdbId = tmdbId, imdbId = details.imdbId, type = TYPE_TV,
                title = details.title, year = details.year ?: fallback?.year,
                overview = details.overview ?: fallback?.overview,
                posterPath = details.posterPath ?: fallback?.posterPath,
                backdropPath = details.backdropPath, rating = details.rating,
                genresJson = details.genres.takeIf { it.isNotEmpty() }?.let { JSONArray(it).toString() },
                castJson = details.cast.takeIf { it.isNotEmpty() }?.let { JSONArray(it).toString() },
                trailerKey = details.trailerKey,
                logoPath = details.logoPath,
                updatedAt = now,
            )
            fallback != null -> MetadataCacheEntity(
                key = tvCacheKey(tmdbId), tmdbId = tmdbId, imdbId = null, type = TYPE_TV,
                title = fallback.title, year = fallback.year, overview = fallback.overview,
                posterPath = fallback.posterPath, backdropPath = null, rating = null,
                genresJson = null, castJson = null, trailerKey = null, logoPath = null, updatedAt = now,
            )
            else -> return dao.getCache(tvCacheKey(tmdbId))
        }
        dao.upsertCache(entity)
        return entity
    }

    /**
     * Resolve per-episode TMDB metadata (still, plot, air date, rating). First resolves the show (cached)
     * to get its TMDB id, then fetches the episode lazily and caches it under `tv:<id>:s<n>e<m>`. Returns
     * null when enrichment is off, the show has no match, or that episode isn't on TMDB.
     */
    suspend fun resolveEpisode(
        series: com.thirutricks.tllplayer.core.database.entity.SeriesEntity,
        episode: com.thirutricks.tllplayer.core.database.entity.EpisodeEntity,
    ): MetadataCacheEntity? {
        if (!settings.metadataConfig().enabled) return null
        val show = resolveSeries(series) ?: return null // no confident show match → no episode lookup
        val tvId = show.tmdbId
        val season = episode.seasonNumber
        val ep = episode.episodeNumber
        val key = episodeCacheKey(tvId, season, ep)
        val now = System.currentTimeMillis()

        dao.getCache(key)?.let { if (now - it.updatedAt < POSITIVE_TTL_MS) return it }

        val d = provider.tvEpisodeDetails(tvId, season, ep) ?: return dao.getCache(key)
        val entity = MetadataCacheEntity(
            key = key, tmdbId = tvId, imdbId = null, type = TYPE_EPISODE,
            title = d.name?.takeIf { it.isNotBlank() } ?: episode.name,
            year = d.airDate?.take(4)?.toIntOrNull(),
            overview = d.overview,
            posterPath = d.stillPath, // 16:9 still, rendered via MetadataImages.backdrop sizing
            backdropPath = d.stillPath,
            rating = d.rating,
            genresJson = null, castJson = null, trailerKey = null, updatedAt = now,
            logoPath = null,
        )
        dao.upsertCache(entity)
        return entity
    }

    /**
     * Clear a movie's TMDB match (negative OR positive) and its cached details so the next [resolveMovie]
     * re-searches from scratch (plan §11.2 U5a — manual "Refetch TMDB details"). Does NOT resolve; the caller
     * re-triggers [resolveMovie] afterwards.
     */
    suspend fun clearMovie(movie: MovieEntity) {
        val localKey = movieLocalKey(movie)
        dao.getMatch(localKey)?.tmdbId?.let { dao.deleteCache(cacheKey(it)) }
        dao.deleteMatch(localKey)
    }

    /**
     * Clear a series' match + cached show details (plan §11.2 U5a). Per-episode cache rows for the old tmdbId
     * are left in place — they're orphaned but harmless (episode resolve looks them up by tmdbId, so stale
     * rows under an old id are simply never read). Caller re-triggers [resolveSeries].
     */
    suspend fun clearSeries(series: com.thirutricks.tllplayer.core.database.entity.SeriesEntity) {
        val localKey = seriesLocalKey(series)
        dao.getMatch(localKey)?.tmdbId?.let { dao.deleteCache(tvCacheKey(it)) }
        dao.deleteMatch(localKey)
    }

    /**
     * Clear an episode's cache AND its show's match + show cache (plan §11.2 U5a). Episodes inherit the show's
     * match, so an episode whose show was negative-cached can only recover by clearing the show match too.
     * Caller re-triggers [resolveEpisode].
     */
    suspend fun clearEpisode(
        series: com.thirutricks.tllplayer.core.database.entity.SeriesEntity,
        episode: com.thirutricks.tllplayer.core.database.entity.EpisodeEntity,
    ) {
        val localKey = seriesLocalKey(series)
        dao.getMatch(localKey)?.tmdbId?.let { tid ->
            dao.deleteCache(tvCacheKey(tid)) // show details
            dao.deleteCache(episodeCacheKey(tid, episode.seasonNumber, episode.episodeNumber)) // this episode
        }
        dao.deleteMatch(localKey) // show match (negative OR positive)
    }

    // --- TMDB name overrides (plan §11.2 U5b) ---
    // Stored in DataStore (no Room schema change) and keyed by the same stable local key as matching, so
    // they survive re-sync. Setting/clearing also drops the cached match+details so the next resolve
    // re-searches under the new query (caller bumps the meta-refresh tick to trigger it).

    /** The saved override for this movie, if any (used to prefill the dialog). */
    suspend fun movieOverride(movie: MovieEntity): TmdbOverride? = overrideStore.get(movieLocalKey(movie))

    /** The saved override for this series, if any. */
    suspend fun seriesOverride(series: com.thirutricks.tllplayer.core.database.entity.SeriesEntity): TmdbOverride? =
        overrideStore.get(seriesLocalKey(series))

    /** Save a movie's override and drop its cached match so the next resolve uses the new query. */
    suspend fun setMovieOverride(movie: MovieEntity, title: String, year: Int?) {
        overrideStore.set(movieLocalKey(movie), title, year)
        clearMovie(movie)
    }

    /** Save a series' override and drop its cached match so the next resolve uses the new query. */
    suspend fun setSeriesOverride(series: com.thirutricks.tllplayer.core.database.entity.SeriesEntity, title: String, year: Int?) {
        overrideStore.set(seriesLocalKey(series), title, year)
        clearSeries(series)
    }

    /** Remove a movie's override and drop its cached match so the next resolve re-normalizes the provider title. */
    suspend fun clearMovieOverride(movie: MovieEntity) {
        overrideStore.clear(movieLocalKey(movie))
        clearMovie(movie)
    }

    /** Remove a series' override and drop its cached match so the next resolve re-normalizes the provider title. */
    suspend fun clearSeriesOverride(series: com.thirutricks.tllplayer.core.database.entity.SeriesEntity) {
        overrideStore.clear(seriesLocalKey(series))
        clearSeries(series)
    }

    /**
     * Build the TMDB search query + year for [localKey]: a user override (§11.2 U5b) wins over the
     * auto-normalized provider title. [ResolvedQuery.isOverride] lets the caller bypass the fuzzy
     * threshold and trust TMDB's top relevance hit when the user hand-typed the name.
     */
    private suspend fun resolveQuery(localKey: String, rawName: String, providerYear: Int?): ResolvedQuery {
        overrideStore.get(localKey)?.let { return ResolvedQuery(it.title, it.year ?: providerYear, isOverride = true) }
        val norm = TitleNormalizer.normalize(rawName)
        return ResolvedQuery(norm.query, providerYear ?: norm.year, isOverride = false)
    }

    private data class ResolvedQuery(val query: String, val year: Int?, val isOverride: Boolean)

    /** Fetch full details for [tmdbId] and cache them; falls back to the search hit if details fail. */
    private suspend fun fetchAndCache(
        tmdbId: Int,
        localKey: String,
        confidence: Double,
        fallback: MetadataSearchResult? = null,
    ): MetadataCacheEntity? {
        val now = System.currentTimeMillis()
        val details = provider.movieDetails(tmdbId)
        val entity = when {
            details != null -> MetadataCacheEntity(
                key = cacheKey(tmdbId),
                tmdbId = tmdbId,
                imdbId = details.imdbId,
                type = TYPE_MOVIE,
                title = details.title,
                year = details.year ?: fallback?.year,
                overview = details.overview ?: fallback?.overview,
                posterPath = details.posterPath ?: fallback?.posterPath,
                backdropPath = details.backdropPath,
                rating = details.rating,
                genresJson = details.genres.takeIf { it.isNotEmpty() }?.let { JSONArray(it).toString() },
                castJson = details.cast.takeIf { it.isNotEmpty() }?.let { JSONArray(it).toString() },
                trailerKey = details.trailerKey,
                logoPath = details.logoPath,
                updatedAt = now,
            )
            fallback != null -> MetadataCacheEntity(
                key = cacheKey(tmdbId), tmdbId = tmdbId, imdbId = null, type = TYPE_MOVIE,
                title = fallback.title, year = fallback.year, overview = fallback.overview,
                posterPath = fallback.posterPath, backdropPath = null, rating = null,
                genresJson = null, castJson = null, trailerKey = null, logoPath = null, updatedAt = now,
            )
            else -> return dao.getCache(cacheKey(tmdbId)) // nothing to write; return existing if any
        }
        dao.upsertCache(entity)
        return entity
    }

    /** Best confident match, or null (plan §12: "no art beats wrong art"). */
    private fun pickBest(query: String, year: Int?, hits: List<MetadataSearchResult>): Scored? {
        if (hits.isEmpty()) return null
        return hits.asSequence()
            .map { Scored(it, score(query, year, it)) }
            .filter { it.score >= ACCEPT_THRESHOLD }
            .maxByOrNull { it.score }
    }

    private data class Scored(val result: MetadataSearchResult, val score: Double)

    /** 0..1 confidence from title similarity + year agreement. */
    private fun score(query: String, year: Int?, r: MetadataSearchResult): Double {
        val q = query.lowercase().trim()
        val t = r.title.lowercase().trim()
        var s = when {
            q == t -> 1.0
            t.contains(q) || q.contains(t) -> 0.75
            else -> tokenOverlap(q, t)
        }
        if (year != null && r.year != null) {
            val diff = kotlin.math.abs(year - r.year)
            s += when {
                diff == 0 -> 0.15
                diff == 1 -> 0.0
                else -> -0.35
            }
        }
        return s.coerceIn(0.0, 1.0)
    }

    /** Jaccard overlap of word tokens — a cheap similarity for near-miss titles. */
    private fun tokenOverlap(a: String, b: String): Double {
        val sa = a.split(WORD_SPLIT).filter { it.isNotBlank() }.toSet()
        val sb = b.split(WORD_SPLIT).filter { it.isNotBlank() }.toSet()
        if (sa.isEmpty() || sb.isEmpty()) return 0.0
        val inter = sa.intersect(sb).size.toDouble()
        return inter / (sa.size + sb.size - inter)
    }

    companion object {
        private const val TAG = "MetadataRepository"
        private const val TYPE_MOVIE = "movie"
        private const val TYPE_TV = "tv"
        private const val TYPE_EPISODE = "episode"

        /** Accept a match at/above this confidence; below it, prefer no metadata over a wrong one. */
        private const val ACCEPT_THRESHOLD = 0.6

        private const val POSITIVE_TTL_MS = 60L * 24 * 3600 * 1000  // 60 days
        private const val NEGATIVE_TTL_MS = 7L * 24 * 3600 * 1000   // 7 days

        private val WORD_SPLIT = Regex("""\s+""")

        /** Stable, re-sync-proof local key (mirrors CustomizeKeys): sourceId + remoteId, or name fallback. */
        fun movieLocalKey(movie: MovieEntity): String = "$TYPE_MOVIE:${movie.sourceId}:${movie.remoteId ?: movie.name}"

        fun cacheKey(tmdbId: Int): String = "$TYPE_MOVIE:$tmdbId"

        fun seriesLocalKey(series: com.thirutricks.tllplayer.core.database.entity.SeriesEntity): String =
            "$TYPE_TV:${series.sourceId}:${series.remoteId ?: series.name}"

        fun tvCacheKey(tmdbId: Int): String = "$TYPE_TV:$tmdbId"

        fun episodeCacheKey(tvId: Int, season: Int, episode: Int): String = "$TYPE_TV:$tvId:s${season}e$episode"
    }
}
