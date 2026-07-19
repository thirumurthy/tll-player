package com.thirutricks.tllplayer.core.stalker

import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.model.SourceType

/**
 * Turns a stored item URL into a playable URL at play time (plan §5.4). M3U/Xtream already store a
 * final URL, so they pass through unchanged. Stalker stores the portal `cmd` (not playable) — this
 * resolves it via `create_link` every call (Stalker links are short-lived, so never cache), with a
 * one-shot token re-handshake on expiry. Keep the stored `cmd` as the item's identity everywhere;
 * only the value handed to the player engine comes from here.
 *
 * **C-3 (§5.4.1) — live URL expiry:** a Stalker resolved URL dies after ~2-4h, so the playback
 * engines' auto-reconnect must NOT replay the dead URL for Stalker. Instead they call back into a
 * [ReconnectUrlProvider] (installed by `LiveViewModel` only for a Stalker source) to mint a fresh
 * URL before retrying. M3U/Xtream have no provider → the engines replay their stored URL as before.
 */
class StreamUrlResolver(
    private val stalkerAuth: StalkerAuthManager,
    private val stalkerClient: StalkerClient,
) {
    /** True when this source needs play-time resolution (so callers can skip the work entirely). */
    fun needsResolve(source: SourceEntity?): Boolean = source?.type == SourceType.STALKER

    /**
     * Resolve [storedUrl] to a playable URL. For Stalker this is a fresh `create_link`; for anything
     * else it returns [storedUrl] untouched. Throws on a portal/auth failure so the caller can show
     * an error instead of handing a dead URL to the engine.
     *
     * Every call is a FRESH resolve (never cached) — this is what the engines' reconnect paths call
     * (plan §5.4.1 "resolveFresh"). For direct-play portals the fresh URL equals the stored one
     * (their embedded token is static); for placeholder-cmd portals it's a brand-new `create_link`.
     */
    suspend fun resolve(source: SourceEntity, storedUrl: String, vod: Boolean = false, episode: Int? = null): String {
        if (source.type != SourceType.STALKER) return storedUrl
        // Many portals embed the full playable URL right in the channel cmd — play it as-is. Only a
        // placeholder cmd (localhost / no query) needs create_link (which on the former kind blanks
        // the stream id → HTTP 405). Series episodes are the exception: the season cmd is shared by
        // every episode, so it always goes through create_link to mint the episode's URL (series=<ep>).
        val direct = StalkerClient.stripCmdPrefix(storedUrl)
        if (episode == null && StalkerClient.isDirectPlayUrl(direct)) return direct
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) } ?: return direct
        val creds = StalkerCredentials(source.id, source.url, mac, source.userAgent)
        return stalkerAuth.withAuthRetry(creds) { session ->
            stalkerClient.createLink(
                session.apiBase, mac, session.token, creds.userAgent, storedUrl,
                type = if (vod) "vod" else "itv", episode = episode,
            )
        }
    }

    /**
     * Phase E (§5.6) — mint a playable archive URL for a past programme on a catch-up channel.
     * The archive is requested with `type=tv_archive&action=create_link` and the classic STB media
     * cmd `auto /media/<channel portal id>_<start unix>_<duration s>.mpg` (the "create_link variant
     * carrying the programme's start time", §1.5). Like [resolve], every call is fresh (archive URLs
     * are as short-lived as live ones). Throws on portal/auth failure — callers show an error/no-op.
     */
    suspend fun resolveCatchup(source: SourceEntity, channelRemoteId: String, startMs: Long, endMs: Long): String {
        require(source.type == SourceType.STALKER) { "resolveCatchup is Stalker-only" }
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) }
            ?: throw java.io.IOException("Stalker source ${source.id} has no valid MAC")
        val startS = startMs / 1000
        val durationS = ((endMs - startMs) / 1000).coerceAtLeast(60)
        val cmd = "auto /media/${channelRemoteId}_${startS}_${durationS}.mpg"
        val creds = StalkerCredentials(source.id, source.url, mac, source.userAgent)
        return stalkerAuth.withAuthRetry(creds) { session ->
            stalkerClient.createLink(session.apiBase, mac, session.token, creds.userAgent, cmd, type = "tv_archive")
        }
    }

    /**
     * Phase E (§5.5) — the channel's now/next programmes via the portal's per-channel `get_short_epg`
     * (the Stalker counterpart of Xtream's short-EPG fallback in `LiveViewModel.loadEpg`). Throws on
     * portal/auth failure — the caller treats that as "no guide data".
     */
    suspend fun shortEpg(source: SourceEntity, channelRemoteId: String, size: Int = 8): List<StalkerClient.ShortEpgEntry> {
        require(source.type == SourceType.STALKER) { "shortEpg is Stalker-only" }
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) }
            ?: throw java.io.IOException("Stalker source ${source.id} has no valid MAC")
        val creds = StalkerCredentials(source.id, source.url, mac, source.userAgent)
        return stalkerAuth.withAuthRetry(creds) { session ->
            stalkerClient.getShortEpg(session.apiBase, mac, session.token, creds.userAgent, channelRemoteId, size)
        }
    }
}

/**
 * Installed on a playback engine when the current item's URL can't simply be replayed on reconnect
 * (Stalker live, plan §5.4.1). Before retrying, the engine awaits [freshUrl]; a null return means
 * "replay the stored URL as-is" (also the default when no provider is set — i.e. M3U/Xtream, whose
 * URLs are stable forever).
 *
 * Engines call this on their MAIN scope (ExoPlayer is single-threaded; mpv dispatches to main) and
 * treat the returned value as the URL to (re)load. A throw means resolution failed — the engine
 * surfaces its normal reconnect-exhausted / error state.
 */
fun interface ReconnectUrlProvider {
    /** Return a fresh playable URL, or null to replay the current (non-expiring) URL as-is. */
    suspend fun freshUrl(): String?
}
