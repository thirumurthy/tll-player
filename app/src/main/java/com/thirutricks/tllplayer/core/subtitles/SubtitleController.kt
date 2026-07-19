package com.thirutricks.tllplayer.core.subtitles

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.thirutricks.tllplayer.core.database.entity.EpisodeEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.player.OwnTVPlayer

/**
 * The bridge between "what movie/episode is playing right now" and the OpenSubtitles feature
 * (subtitle plan §6). The movie/series play paths set a [Context] here; live/other playback clears
 * it — so the player's ADD SUBTITLES entry only ever appears for movies and episodes (§3.4).
 *
 * The search screen (in the player HUD overlay) reads [current] and calls [search] / [apply]; on
 * apply the downloaded subtitle is attached to the running mpv player and remembered for resume.
 */
class SubtitleController(
    private val repository: SubtitleRepository,
    private val accounts: OpenSubtitlesAccountManager,
    private val settings: SettingsRepository,
    private val player: OwnTVPlayer,
) {
    /** Identity of the item currently playing, enough to pre-fill an OpenSubtitles search (§6.2). */
    data class Context(
        val profileId: Long,
        val contentKey: String,
        val isEpisode: Boolean,
        val title: String,
        val year: Int?,
        val season: Int?,
        val episode: Int?,
        /** Movie TMDB id, or the series' parent TMDB id for an episode (review R7). Null → query search. */
        val tmdbId: Long?,
        /** Local media file when playing an OwnTV download (§3.3) — enables the moviehash enhancer. */
        val localFilePath: String? = null,
    ) {
        val mediaType: String get() = if (isEpisode) "SERIES" else "MOVIE"

        /** Title for the delete lists: "Oppenheimer" or "Breaking Bad · S1E1". */
        val displayTitle: String
            get() = if (isEpisode && season != null && episode != null) "$title · S${season}E${episode}" else title
    }

    private val _current = MutableStateFlow<Context?>(null)
    val current: StateFlow<Context?> = _current.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Timing memory (§8.4): the player reports the active subtitle as "path:<file>" (external) or
    // "emb:<ordinal>:<lang>" (embedded); external paths map to their stable subtitleKey via this map,
    // filled on apply/restore. [activeSubKey] is what a user timing change is persisted under.
    private val pathToKey = HashMap<String, String>()
    private var activeSubKey: String? = null

    init {
        // Re-list a title's previously downloaded subtitles whenever its file finishes loading (§9).
        // They're attached but not selected — the user re-picks (per owner decision).
        player.onVodFileLoaded = { restoreForCurrentItem() }
        // When the active subtitle changes, apply ITS remembered offset (never another release's, §8.4);
        // when the user adjusts timing, persist it under the exact subtitle identity.
        player.onActiveSubtitleChanged = { identity -> applyRememberedTiming(identity) }
        player.onSubtitleDelayUserChange = { offsetMs -> persistTiming(offsetMs) }
    }

    private fun applyRememberedTiming(identity: String?) {
        scope.launch {
            val ctx = _current.value ?: return@launch
            val key = identity?.let { if (it.startsWith("path:")) pathToKey[it.removePrefix("path:")] else it }
            activeSubKey = key
            val offset = key?.let {
                runCatching { repository.timingOffset(ctx.profileId, ctx.contentKey, it) }.getOrNull()
            } ?: 0
            player.applySubtitleDelay(offset)
        }
    }

    private fun persistTiming(offsetMs: Int) {
        val ctx = _current.value ?: return
        val key = activeSubKey ?: return
        scope.launch {
            runCatching { repository.setTimingOffset(ctx.profileId, ctx.contentKey, key, offsetMs) }
        }
    }

    fun setMovie(profileId: Long, movie: MovieEntity, tmdbId: Long?, localFilePath: String? = null) {
        if (profileId < 0) return clear()
        _current.value = Context(
            profileId = profileId,
            contentKey = movieKey(movie),
            isEpisode = false,
            title = movie.name,
            year = movie.year,
            season = null,
            episode = null,
            tmdbId = tmdbId,
            localFilePath = localFilePath,
        )
    }

    fun setEpisode(profileId: Long, show: SeriesEntity, episode: EpisodeEntity, parentTmdbId: Long?, localFilePath: String? = null) {
        if (profileId < 0) return clear()
        _current.value = Context(
            profileId = profileId,
            contentKey = episodeKey(show, episode),
            isEpisode = true,
            title = show.name,
            year = show.year,
            season = episode.seasonNumber,
            episode = episode.episodeNumber,
            tmdbId = parentTmdbId,
            localFilePath = localFilePath,
        )
    }

    fun clear() { _current.value = null }

    /** True when the active profile can search/download (signed in to OpenSubtitles). */
    fun isSignedIn(): Boolean {
        val pid = _current.value?.profileId ?: return false
        return accounts.session(pid) != null
    }

    /** In-player sign-in for the R1 "account needed" flow — same path as the Settings screen, so the
     *  user lands back in the search that started it (§5.2). Throws for the sign-in-failed dialog. */
    suspend fun signIn(username: String, password: String, staySignedIn: Boolean) {
        val pid = _current.value?.profileId ?: activeProfile()
        check(pid >= 0) { "No active profile" }
        accounts.signIn(pid, username, password, staySignedIn)
    }

    /** The profile's preferred subtitle language(s) as OpenSubtitles codes (§6.4); empty = all. */
    suspend fun preferredLanguages(): List<String> =
        settings.preferredSubLang.first()
            .split(',')
            .mapNotNull { it.trim().takeIf(String::isNotBlank)?.let(::toTwoLetter) }
            .distinct()

    /**
     * Run an OpenSubtitles search for the current item (§6.2, review R7). [languages] filters by
     * language code(s); [editedQuery] (non-null) overrides the metadata match with a free-text query
     * ("Edit search", §6.2). Returns the raw OpenSubtitles response for the results screen to parse.
     */
    suspend fun search(languages: List<String>, editedQuery: String? = null): JSONObject {
        val ctx = _current.value ?: return JSONObject()
        val q = HashMap<String, String>()
        if (languages.isNotEmpty()) q["languages"] = languages.joinToString(",")

        val useQuery = editedQuery?.takeIf { it.isNotBlank() }
        if (ctx.isEpisode) {
            q["type"] = "episode"
            ctx.season?.let { q["season_number"] = it.toString() }
            ctx.episode?.let { q["episode_number"] = it.toString() }
            if (useQuery != null) {
                q["query"] = useQuery
            } else if (ctx.tmdbId != null) {
                q["parent_tmdb_id"] = ctx.tmdbId.toString() // R7: strongest episode match
            } else {
                q["query"] = ctx.title
            }
        } else {
            q["type"] = "movie"
            if (useQuery != null) {
                q["query"] = useQuery
            } else if (ctx.tmdbId != null) {
                q["tmdb_id"] = ctx.tmdbId.toString()
            } else {
                q["query"] = ctx.title
                ctx.year?.let { q["year"] = it.toString() }
            }
        }
        // Moviehash enhancer (§3.3, downloads only): the complete file is local, so the OpenSubtitles
        // hash can sharpen the match. Strictly best-effort — capped at 2s and never blocks the search.
        if (ctx.localFilePath != null && editedQuery == null) {
            kotlinx.coroutines.withTimeoutOrNull(2_000) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    MovieHash.compute(java.io.File(ctx.localFilePath))
                }
            }?.let { q["moviehash"] = it }
        }
        return repository.search(q)
    }

    /**
     * Download the chosen result (cache-first, review R3), attach it to the running player, and
     * remember it for this profile + item (§6.5). [onQuota] delivers the provider's post-download
     * remaining/reset for the allowance display. Throws upward for the §14 error dialogs.
     */
    suspend fun apply(
        fileId: Long,
        language: String?,
        languageName: String?,
        releaseName: String?,
        hearingImpaired: Boolean,
        onQuota: (remaining: Int?, resetTime: String?) -> Unit = { _, _ -> },
    ) {
        val ctx = _current.value ?: throw IllegalStateException("No item is playing")
        val resolved = repository.downloadAndCache(
            profileId = ctx.profileId,
            fileId = fileId,
            language = language,
            languageName = languageName,
            releaseName = releaseName,
            hearingImpaired = hearingImpaired,
            onQuota = onQuota,
        )
        pathToKey[resolved.path] = resolved.subtitleKey // timing identity (§8.4), before the attach fires
        player.addExternalSubtitle(resolved.path, labelFor(resolved), resolved.language)
        repository.rememberSelection(ctx.profileId, ctx.contentKey, resolved.cacheId)
        // Link it to this item so it re-lists on replay and appears in the delete surfaces (§11).
        repository.linkDownload(ctx.profileId, ctx.contentKey, resolved.cacheId, ctx.mediaType, ctx.displayTitle)
    }

    /**
     * Attach a user-picked local subtitle file to the running player (plan §7, Phase 3): managed
     * UTF-8 copy via the repository, then the same remember/link flow as an OpenSubtitles download
     * so it re-lists on replay and shows in the delete surfaces. No account or network needed.
     */
    suspend fun applyLocal(file: java.io.File) {
        val ctx = _current.value ?: throw IllegalStateException("No item is playing")
        val resolved = repository.importLocalFile(file)
        pathToKey[resolved.path] = resolved.subtitleKey // timing identity (§8.4), before the attach fires
        player.addExternalSubtitle(resolved.path, labelFor(resolved), resolved.language)
        repository.rememberSelection(ctx.profileId, ctx.contentKey, resolved.cacheId)
        repository.linkDownload(ctx.profileId, ctx.contentKey, resolved.cacheId, ctx.mediaType, ctx.displayTitle)
    }

    /** Track-row label: "Bengali — OpenSubtitles" / "movie.en.srt — Local file" (plan §4/§7.3). */
    private fun labelFor(s: SubtitleRepository.ResolvedSubtitle): String =
        if (s.source == SubtitleRepository.SOURCE_LOCAL) {
            listOfNotNull(s.languageName ?: s.language ?: s.releaseName, "Local file").joinToString(" — ")
        } else {
            listOfNotNull(s.languageName ?: s.language, "OpenSubtitles").joinToString(" — ")
        }

    /** Re-attach (unselected) every subtitle previously downloaded for the current item, on file load. */
    private fun restoreForCurrentItem() {
        val ctx = _current.value ?: return
        scope.launch {
            val subs = runCatching { repository.restoreForContent(ctx.profileId, ctx.contentKey) }.getOrNull().orEmpty()
            if (subs.isEmpty()) return@launch
            subs.forEach { pathToKey[it.path] = it.subtitleKey } // timing identities (§8.4)
            player.restoreExternalSubtitles(
                subs.map { s -> OwnTVPlayer.ExternalSub(s.path, labelFor(s), s.language) },
            )
        }
    }

    // --- delete surfaces (settings screen + per-item long-press, §11) ---

    /** Downloaded subtitles for a media type ("MOVIE"/"SERIES"), active profile — the delete screen. */
    suspend fun downloadsForType(mediaType: String): List<tv.own.owntv.core.database.dao.LinkedSubtitle> {
        val pid = activeProfile(); if (pid < 0) return emptyList()
        return repository.linkedForType(pid, mediaType)
    }

    /** Downloaded subtitles for a specific movie (per-item long-press popup). */
    suspend fun downloadsForMovie(movie: MovieEntity): List<tv.own.owntv.core.database.dao.LinkedSubtitle> {
        val pid = activeProfile(); if (pid < 0) return emptyList()
        return repository.linkedForContent(pid, movieKey(movie))
    }

    /** Downloaded subtitles for a specific episode (per-item long-press popup). */
    suspend fun downloadsForEpisode(show: SeriesEntity, ep: EpisodeEntity): List<tv.own.owntv.core.database.dao.LinkedSubtitle> {
        val pid = activeProfile(); if (pid < 0) return emptyList()
        return repository.linkedForContent(pid, episodeKey(show, ep))
    }

    suspend fun hasDownloadsForMovie(movie: MovieEntity): Boolean {
        val pid = activeProfile(); if (pid < 0) return false
        return repository.hasDownloadsForContent(pid, movieKey(movie))
    }

    suspend fun hasDownloadsForEpisode(show: SeriesEntity, ep: EpisodeEntity): Boolean {
        val pid = activeProfile(); if (pid < 0) return false
        return repository.hasDownloadsForContent(pid, episodeKey(show, ep))
    }

    /** Profile-scoped (owner decision): drops the ACTIVE profile's copy; the shared file survives
     *  while any other profile still references it. */
    suspend fun deleteCached(cacheId: Long) {
        val pid = activeProfile(); if (pid >= 0) repository.deleteCached(pid, cacheId)
    }

    suspend fun deleteAllForType(mediaType: String) {
        val pid = activeProfile(); if (pid >= 0) repository.deleteAllForType(pid, mediaType)
    }

    suspend fun deleteAllDownloads() {
        deleteAllForType("MOVIE"); deleteAllForType("SERIES")
    }

    private suspend fun activeProfile(): Long = settings.activeProfileId.first()

    private fun movieKey(movie: MovieEntity) = "movie:${movie.sourceId}:${movie.remoteId ?: movie.name}"

    private fun episodeKey(show: SeriesEntity, ep: EpisodeEntity) =
        "episode:${show.sourceId}:${show.remoteId ?: show.name}:S${ep.seasonNumber}E${ep.episodeNumber}"

    /** OwnTV stores ISO-639-2 (e.g. "eng"); OpenSubtitles wants 2-letter codes (e.g. "en"). */
    private fun toTwoLetter(code: String): String? = when (code.lowercase()) {
        "eng" -> "en"; "spa" -> "es"; "fra", "fre" -> "fr"; "deu", "ger" -> "de"; "ita" -> "it"
        "por" -> "pt"; "nld", "dut" -> "nl"; "rus" -> "ru"; "ara" -> "ar"; "hin" -> "hi"
        "zho", "chi" -> "zh"; "jpn" -> "ja"; "kor" -> "ko"; "tur" -> "tr"
        else -> code.takeIf { it.length == 2 }?.lowercase()
    }
}
