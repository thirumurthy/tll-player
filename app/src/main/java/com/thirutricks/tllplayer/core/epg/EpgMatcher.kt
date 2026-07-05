package com.thirutricks.tllplayer.core.epg

/**
 * Smart EPG matching (#13): pairs a channel with a guide channel by *name* when the provider's
 * tvg-id is missing or doesn't line up with the EPG feed's ids.
 *
 * Two pure pieces, both easy to unit-test:
 *  - [normalizeForEpg] strips the cosmetic noise IPTV names carry (quality tags, country brackets,
 *    separators) so "DE| FUSS-TV 3 ᴴᴰ" and "fusstv3.de" reduce to comparable tokens.
 *  - [jaroWinkler] scores two normalized strings 0..1; [bestEpgMatch] picks the top candidate.
 *
 * Nothing here touches the DB or DataStore — callers feed in candidates and persist the winner into
 * the existing `CustomizationStore.epgMatches`, so no new table/migration is needed.
 */
object EpgMatcher {

    /** Score at/above which a match is trustworthy enough to apply automatically. */
    const val AUTO_THRESHOLD = 0.92

    /** Score at/above which a match is worth showing for human review (below = ignored). */
    const val REVIEW_THRESHOLD = 0.74

    // Cosmetic tokens that say nothing about *which* channel this is.
    private val NOISE = setOf(
        "hd", "fhd", "uhd", "sd", "4k", "8k", "hq", "lq",
        "hevc", "h265", "h264", "fps", "raw", "vip", "backup", "feed", "alt",
    )

    // Country/region codes IPTV names tag on as a leading/trailing group prefix ("DE| …", "… UK").
    // Only stripped at the ends (never mid-name), so a real word in the middle is never lost.
    private val COUNTRY = setOf(
        "us", "uk", "ca", "au", "nz", "ie", "za", "in", "pk",
        "de", "at", "ch", "fr", "es", "pt", "it", "nl", "be", "lu",
        "pl", "cz", "sk", "hu", "ro", "bg", "gr", "tr", "ru", "ua",
        "se", "no", "dk", "fi", "is", "ee", "lv", "lt", "hr", "rs", "si",
        "br", "mx", "ar", "cl", "co", "pe", "ve", "ae", "sa", "qa", "eg",
    )

    private val BRACKETS = Regex("[\\[(\\{][^\\])}]*[\\])}]")
    private val SEPARATORS = Regex("[._\\-:|/+]")
    private val NON_ALNUM = Regex("[^a-z0-9 ]")
    private val SPACES = Regex("\\s+")

    /**
     * Reduce a raw channel/EPG name to a comparable token string: lowercase, bracketed tags removed,
     * separators flattened to spaces, cosmetic/quality words dropped, non-alphanumerics stripped.
     * e.g. "DE| FUSS-TV 3 [HD]" -> "fuss 3", "FussTV3.de" -> "fusstv3 de" ... then noise filtered.
     */
    fun normalizeForEpg(raw: String): String {
        var s = raw.lowercase()
        s = s.replace(BRACKETS, " ")
        s = s.replace(SEPARATORS, " ")
        s = s.replace(NON_ALNUM, " ")
        val tokens = s.split(SPACES).filter { it.isNotBlank() && it !in NOISE }.toMutableList()
        // Drop a country/region tag at either end (keep at least one real token).
        if (tokens.size > 1 && tokens.first() in COUNTRY) tokens.removeAt(0)
        if (tokens.size > 1 && tokens.last() in COUNTRY) tokens.removeAt(tokens.size - 1)
        return tokens.joinToString(" ").trim()
    }

    /**
     * Jaro–Winkler similarity (0..1) — tolerant of typos/transpositions and biased toward common
     * prefixes, which suits channel names well. Operates on already-normalized strings.
     */
    fun jaroWinkler(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val jaro = jaro(a, b)
        // Winkler boost: up to 4 leading chars shared, factor 0.1.
        var prefix = 0
        val max = minOf(4, minOf(a.length, b.length))
        while (prefix < max && a[prefix] == b[prefix]) prefix++
        return jaro + prefix * 0.1 * (1 - jaro)
    }

    private fun jaro(a: String, b: String): Double {
        val matchDistance = maxOf(a.length, b.length) / 2 - 1
        val aMatches = BooleanArray(a.length)
        val bMatches = BooleanArray(b.length)
        var matches = 0
        for (i in a.indices) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, b.length)
            for (j in start until end) {
                if (bMatches[j] || a[i] != b[j]) continue
                aMatches[i] = true
                bMatches[j] = true
                matches++
                break
            }
        }
        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in a.indices) {
            if (!aMatches[i]) continue
            while (!bMatches[k]) k++
            if (a[i] != b[k]) transpositions++
            k++
        }
        val m = matches.toDouble()
        return (m / a.length + m / b.length + (m - transpositions / 2.0) / m) / 3.0
    }

    /** A guide channel to match against — its stable id plus optional human display name. */
    data class Candidate(val epgChannelId: String, val displayName: String?)

    /** A [Candidate] with its names pre-normalized, so a bulk scan normalizes each candidate once. */
    data class Prepared(val epgChannelId: String, val displayName: String?, val normName: String, val normId: String)

    /** The chosen guide channel for a source channel, with the confidence that picked it. */
    data class Result(val epgChannelId: String, val displayName: String?, val score: Double)

    /** Pre-normalize a candidate list once before scanning many channels against it. */
    fun prepare(candidates: List<Candidate>): List<Prepared> = candidates.map {
        Prepared(it.epgChannelId, it.displayName, it.displayName?.let(::normalizeForEpg) ?: "", normalizeForEpg(it.epgChannelId))
    }

    /**
     * Best guide candidate for [channelName], or null if nothing clears [minScore]. Each candidate is
     * scored on its display name *and* its id (some feeds only have meaningful ids), best of the two.
     */
    fun bestEpgMatch(
        channelName: String,
        candidates: List<Candidate>,
        minScore: Double = REVIEW_THRESHOLD,
    ): Result? = bestEpgMatchPrepared(channelName, prepare(candidates), minScore)

    /** [bestEpgMatch] over a pre-[prepare]d candidate list — the hot path for bulk auto-matching. */
    fun bestEpgMatchPrepared(
        channelName: String,
        candidates: List<Prepared>,
        minScore: Double = REVIEW_THRESHOLD,
    ): Result? {
        val target = normalizeForEpg(channelName)
        if (target.isEmpty()) return null
        var best: Result? = null
        for (c in candidates) {
            val byName = if (c.normName.isNotEmpty()) jaroWinkler(target, c.normName) else 0.0
            val byId = jaroWinkler(target, c.normId)
            val score = maxOf(byName, byId)
            if (score >= minScore && (best == null || score > best.score)) {
                best = Result(c.epgChannelId, c.displayName, score)
                if (score == 1.0) break // perfect match — can't beat it
            }
        }
        return best
    }
}
