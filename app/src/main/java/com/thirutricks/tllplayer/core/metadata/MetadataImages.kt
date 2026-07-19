package com.thirutricks.tllplayer.core.metadata

/**
 * Builds image.tmdb.org URLs from TMDB relative paths (plan §4). Images load directly from TMDB's free
 * CDN — no API key, never through the Worker/proxy — so quota only ever sees small JSON.
 */
object MetadataImages {

    /** Poster URL (default w500 — crisp on a TV detail pane). Null-safe: blank/null path → null. */
    fun poster(path: String?, size: String = "w500"): String? =
        path?.takeIf { it.isNotBlank() }?.let { "${TmdbProvider.IMAGE_BASE}/$size${it.ensureLeadingSlash()}" }

    /** Backdrop URL (default w780). */
    fun backdrop(path: String?, size: String = "w780"): String? =
        path?.takeIf { it.isNotBlank() }?.let { "${TmdbProvider.IMAGE_BASE}/$size${it.ensureLeadingSlash()}" }

    /** Title/logo URL (default w500). */
    fun logo(path: String?, size: String = "w500"): String? =
        path?.takeIf { it.isNotBlank() }?.let { "${TmdbProvider.IMAGE_BASE}/$size${it.ensureLeadingSlash()}" }

    private fun String.ensureLeadingSlash(): String = if (startsWith("/")) this else "/$this"
}
