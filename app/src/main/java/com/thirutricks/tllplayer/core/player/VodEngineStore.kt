package com.thirutricks.tllplayer.core.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vodEngineStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_vod_engine")

/** A movie/episode the user manually pinned to an engine via the player's gear toggle. */
enum class VodEnginePin { MPV, EXO }

/**
 * Movies/episodes the user manually switched to a specific engine with the player's gear toggle —
 * the VOD counterpart of [ForceMpvStore] (Live's "compatibility mode"). Self-learning: flip an item
 * once and it opens on that engine forever after, regardless of the global "Movies & Series player"
 * setting. Items never toggled keep following the setting.
 *
 * Keyed by stream URL — the only stable identity across playlist re-syncs (rows are REPLACE-upserted
 * on refresh, so Room ids don't survive; the URL does).
 */
class VodEngineStore(private val context: Context) {
    private val mpvKey = stringSetPreferencesKey("mpv_urls")
    private val exoKey = stringSetPreferencesKey("exo_urls")

    val mpvUrls: Flow<Set<String>> = context.vodEngineStore.data.map { it[mpvKey] ?: emptySet() }
    val exoUrls: Flow<Set<String>> = context.vodEngineStore.data.map { it[exoKey] ?: emptySet() }

    /** Pin [url] to [engine] (the gear toggle's target), replacing any previous pin for it. */
    suspend fun pin(url: String, engine: VodEnginePin) {
        context.vodEngineStore.edit { prefs ->
            val mpv = prefs[mpvKey] ?: emptySet()
            val exo = prefs[exoKey] ?: emptySet()
            prefs[mpvKey] = if (engine == VodEnginePin.MPV) mpv + url else mpv - url
            prefs[exoKey] = if (engine == VodEnginePin.EXO) exo + url else exo - url
        }
    }

    // --- Backup / restore (optional section; keyed by stream URL, no id remapping needed) ---

    /** Current MPV-pinned URLs, for backup export. */
    suspend fun exportMpvUrls(): Set<String> = context.vodEngineStore.data.first()[mpvKey] ?: emptySet()

    /** Current EXO-pinned URLs, for backup export. */
    suspend fun exportExoUrls(): Set<String> = context.vodEngineStore.data.first()[exoKey] ?: emptySet()

    /**
     * Merge restored pins in. A URL present in both lists (corrupt backup) resolves to MPV then EXO
     * removes it — so we drop any URL that appears in both to avoid an inconsistent double-pin.
     */
    suspend fun importUrls(mpvUrls: Collection<String>, exoUrls: Collection<String>) {
        val mpv = mpvUrls.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val exo = exoUrls.filterNotNull().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val conflicting = mpv intersect exo
        val mpvClean = mpv - conflicting
        val exoClean = exo - conflicting
        if (mpvClean.isEmpty() && exoClean.isEmpty()) return
        context.vodEngineStore.edit { prefs ->
            prefs[mpvKey] = (prefs[mpvKey] ?: emptySet()) + mpvClean
            prefs[exoKey] = (prefs[exoKey] ?: emptySet()) + exoClean
        }
    }
}
