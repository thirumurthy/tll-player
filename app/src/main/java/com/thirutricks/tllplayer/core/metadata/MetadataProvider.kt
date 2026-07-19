package com.thirutricks.tllplayer.core.metadata

/**
 * TMDB metadata enrichment (see extras/future-plan/tmdb-metadata-plan.md).
 *
 * OwnTV enriches VOD movies / series / episodes on demand from TMDB — never in bulk (libraries run to
 * ~170k movies / ~50k series). A single [MetadataProvider] serves all three access tiers; only its base
 * URL / auth differ (see [MetadataConfig] and [TmdbProvider]). Live TV is out of scope (no canonical id).
 */

/** Kind of TMDB object a lookup targets. */
enum class MetadataType { MOVIE, TV, EPISODE }

/**
 * A slim TMDB search hit — enough to pick a match and show a poster. Full details (cast, rating, genres,
 * imdb_id, backdrop) are fetched lazily on the detail screen in a later phase.
 */
data class MetadataSearchResult(
    val tmdbId: Int,
    val type: MetadataType,
    val title: String,
    val year: Int?,
    val overview: String?,
    /** TMDB relative poster path (e.g. "/abc.jpg"); build the image.tmdb.org URL at render time. */
    val posterPath: String?,
    /** TMDB popularity — used as a tiebreak when several titles match. */
    val popularity: Double,
)

/**
 * Metadata source mode (plan §4.1). Replaces the old on/off master toggle and also selects the render-time
 * field precedence for the merge (§7.1).
 */
enum class MetadataMode(val label: String) {
    /** Only provider data; TMDB fully off (no lookups). */
    PROVIDER("Provider only"),
    /** Provider wins; TMDB fills gaps & adds extras. `providerField ?: tmdbField`. */
    PROVIDER_PLUS_TMDB("Provider + TMDB"),
    /** TMDB wins; provider only fills what TMDB lacks. `tmdbField ?: providerField`. */
    TMDB_ONLY("TMDB only");

    /** True when TMDB enrichment should run (both non-Provider modes). */
    val enrich: Boolean get() = this != PROVIDER

    /** True when TMDB fields take precedence over provider fields. */
    val tmdbWins: Boolean get() = this == TMDB_ONLY
}

/**
 * Resolved access configuration for the metadata provider. Precedence (highest first), per plan §4:
 *  1. [customServerUrl] set  → Tier 3 self-host (TMDB-shaped proxy/mirror; no key sent).
 *  2. [tmdbApiKey] set       → Tier 2 advanced (calls api.themoviedb.org directly with the user's key).
 *  3. neither                → Tier 0 default caching Cloudflare Worker (key injected server-side).
 *
 * [mode] is the source mode; [enabled] is derived from it (enrichment runs unless mode is Provider).
 */
data class MetadataConfig(
    val mode: MetadataMode = MetadataMode.PROVIDER_PLUS_TMDB,
    val tmdbApiKey: String = "",
    val customServerUrl: String = "",
) {
    /** Whether TMDB lookups should run at all. */
    val enabled: Boolean get() = mode.enrich

    /** Which tier this config resolves to (for the Settings label). */
    val tier: Tier
        get() = when {
            customServerUrl.isNotBlank() -> Tier.SELF_HOST
            tmdbApiKey.isNotBlank() -> Tier.OWN_KEY
            else -> Tier.DEFAULT_WORKER
        }

    enum class Tier(val label: String) {
        DEFAULT_WORKER("Default (shared)"),
        OWN_KEY("Your TMDB key"),
        SELF_HOST("Self-hosted"),
    }
}

/**
 * Full movie details (TMDB `/movie/{id}?append_to_response=credits,external_ids`). Everything IPTV rarely
 * carries — imdb_id (Trakt), backdrop, genres, cast, rating — plus the fields already in the search hit.
 */
data class MovieDetails(
    val tmdbId: Int,
    val imdbId: String?,
    val title: String,
    val year: Int?,
    val overview: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val rating: Double?,
    val genres: List<String>,
    val cast: List<String>,
    /** Best YouTube trailer video key from `videos` (official Trailer > Trailer > Teaser); null if none. */
    val trailerKey: String?,
    /** Best title/logo image path from TMDB images; null when no usable logo exists. */
    val logoPath: String?,
)

/** Per-episode TMDB details (`/tv/{id}/season/{n}/episode/{m}`). Its own still, plot, air date, rating. */
data class EpisodeDetails(
    val name: String?,
    val overview: String?,
    val stillPath: String?,   // 16:9 episode thumbnail (relative path)
    val airDate: String?,     // "2019-04-14"
    val rating: Double?,
)

/** Enrichment source abstraction. Only [TmdbProvider] exists today; fanart.tv could be added later. */
interface MetadataProvider {

    /**
     * Search movies by cleaned [title] (+ optional [year]). Best matches first.
     * **Empty list = TMDB answered "no results"** (callers may negative-cache);
     * **null = transport failure** (network down, rate-limited, proxy error) — callers must NOT
     * negative-cache, so the lookup retries on the next open instead of being wrong for 7 days.
     */
    suspend fun searchMovie(title: String, year: Int? = null): List<MetadataSearchResult>?

    /** Search TV shows by cleaned [title] (+ optional first-air [year]). Same null-vs-empty contract as [searchMovie]. */
    suspend fun searchTv(title: String, year: Int? = null): List<MetadataSearchResult>?

    /** Full details for a resolved movie id; null on network/parse failure. */
    suspend fun movieDetails(tmdbId: Int): MovieDetails?

    /** Full details for a resolved TV show id (reuses [MovieDetails]: title=name, year=first air); null on failure. */
    suspend fun tvDetails(tmdbId: Int): MovieDetails?

    /** Per-episode details for a resolved show; null on failure or if that episode isn't on TMDB. */
    suspend fun tvEpisodeDetails(tvId: Int, season: Int, episode: Int): EpisodeDetails?
}
