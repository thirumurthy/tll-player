package com.thirutricks.tllplayer.core.companion

import com.thirutricks.tllplayer.core.model.SourceType

/**
 * One add-source submission from the Remote companion web form. A single shape covers all three
 * source kinds; irrelevant fields stay blank for a given [type] (e.g. Xtream leaves [portalUrl]/[mac]
 * blank, Stalker leaves [server]/[user]/[pass] blank).
 *
 * The phone only *fills* this — it never starts the import. The TV pre-fills its Add Source form from
 * the payload and the user presses Start Import.
 */
data class CompanionPayload(
    val type: SourceType,
    val name: String = "",
    /** Xtream server URL, or the M3U playlist URL. */
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val portalUrl: String = "",
    val mac: String = "",
    val userAgent: String = "",
    val epgUrl: String = "",
    /** Name of a [tv.own.owntv.features.settings.data.PlaylistAutoRefresh] entry; defaults to OFF. */
    val autoRefresh: String = "OFF",
    val syncLive: Boolean = true,
    val syncMovies: Boolean = true,
    val syncSeries: Boolean = true,
    val isDefault: Boolean = false,
)
