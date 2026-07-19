package com.thirutricks.tllplayer.core.metadata

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.metadataOverrideStore: DataStore<Preferences> by
    preferencesDataStore(name = "owntv_tmdb_overrides")

/** A user-set TMDB search override: a custom title (and optional year) used instead of the auto-normalized one. */
data class TmdbOverride(val title: String, val year: Int?)

/**
 * Per-content TMDB name overrides (plan §11.2 U5b), stored as JSON in DataStore — deliberately NOT a Room
 * table, so no schema change (the DB uses destructive migrations) and the override survives every re-sync
 * via the same stable local key used for matching ([MetadataRepository.movieLocalKey] / [seriesLocalKey]).
 *
 * The map is small (one entry per item the user has hand-corrected), so a single JSON blob under one
 * preference key is simpler than a key-per-entry scheme and matches how [CustomizationStore] serializes.
 */
class MetadataOverrideStore(private val context: Context) {

    private val mapKey = stringPreferencesKey("overrides")

    suspend fun get(localKey: String): TmdbOverride? {
        val raw = context.metadataOverrideStore.data.first()[mapKey] ?: return null
        return parseMap(raw)[localKey]
    }

    /** True if an override is stored for [localKey] — used to decide whether the dialog shows a "Clear" button. */
    suspend fun has(localKey: String): Boolean {
        val raw = context.metadataOverrideStore.data.first()[mapKey] ?: return false
        return parseMap(raw).containsKey(localKey)
    }

    suspend fun set(localKey: String, title: String, year: Int?) {
        context.metadataOverrideStore.edit { prefs ->
            val map = parseMap(prefs[mapKey])
            map[localKey] = TmdbOverride(title.trim(), year)
            prefs[mapKey] = serializeMap(map)
        }
    }

    suspend fun clear(localKey: String) {
        context.metadataOverrideStore.edit { prefs ->
            val map = parseMap(prefs[mapKey])
            if (!map.containsKey(localKey)) return@edit
            map.remove(localKey)
            if (map.isEmpty()) prefs.remove(mapKey) else prefs[mapKey] = serializeMap(map)
        }
    }

    // --- backup & restore (keys embed the source ids, which BackupManager preserves on restore) ---

    /** The whole override map as its JSON blob ("" when empty), for the backup file. */
    suspend fun exportJson(): String = context.metadataOverrideStore.data.first()[mapKey] ?: ""

    /** Merges [raw] into the current map (backup entries win per key); returns the imported keys so
     *  the caller can invalidate any cached TMDB match/details stored under them. */
    suspend fun importJson(raw: String?): Set<String> {
        val incoming = parseMap(raw)
        if (incoming.isEmpty()) return emptySet()
        context.metadataOverrideStore.edit { prefs ->
            val map = parseMap(prefs[mapKey])
            map.putAll(incoming)
            prefs[mapKey] = serializeMap(map)
        }
        return incoming.keys
    }

    private fun parseMap(raw: String?): HashMap<String, TmdbOverride> {
        if (raw.isNullOrBlank()) return HashMap()
        return runCatching {
            val o = JSONObject(raw)
            val out = HashMap<String, TmdbOverride>()
            o.keys().forEach { k ->
                val entry = o.optJSONObject(k) ?: return@forEach
                val title = entry.optString("t").takeIf { it.isNotBlank() } ?: return@forEach
                val year = entry.optInt("y", 0).takeIf { it > 0 }
                out[k] = TmdbOverride(title, year)
            }
            out
        }.getOrDefault(HashMap())
    }

    private fun serializeMap(map: Map<String, TmdbOverride>): String = JSONObject().apply {
        map.forEach { (k, v) ->
            val entry = JSONObject().put("t", v.title)
            if (v.year != null) entry.put("y", v.year)
            put(k, entry)
        }
    }.toString()
}
