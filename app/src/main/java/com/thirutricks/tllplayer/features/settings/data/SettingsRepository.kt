package com.thirutricks.tllplayer.features.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.thirutricks.tllplayer.ui.theme.AccentColor
import com.thirutricks.tllplayer.ui.theme.ThemeMode
import com.thirutricks.tllplayer.ui.theme.UiZoom

/** Per-profile startup landing (Phase 3 / v4.0.0). LAST_CHANNEL also covers "auto-play my channel" since
 *  it's always the one you last watched. */
enum class StartupMode(val label: String) {
    HOME("Home"), LAST_CHANNEL("Last channel"), FAVORITES("Live · Favorites")
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_settings")

/**
 * Persists app-level preferences. Phase 1 only needs the theme selection; this will grow to hold
 * UI zoom, custom user-agent, refresh-on-start, etc. in later phases.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val UI_ZOOM_PCT = intPreferencesKey("ui_zoom_percent")
        val ACCENT = stringPreferencesKey("accent_color")
        val ACCENT_CUSTOM = stringPreferencesKey("accent_custom")
        val AVATAR_ID = intPreferencesKey("avatar_id")
        val ACTIVE_PROFILE = longPreferencesKey("active_profile_id")
        val DEFAULT_SOURCE = longPreferencesKey("default_source_id")
        val DOWNLOAD_ROOT = stringPreferencesKey("download_root")
        val REFRESH_SOURCE_IDS = stringSetPreferencesKey("refresh_source_ids")
        val LIVE_PREVIEW = booleanPreferencesKey("live_preview")
        val LIVE_PREVIEW_AUDIO = booleanPreferencesKey("live_preview_audio")
        val HDR_ENABLED = booleanPreferencesKey("hdr_enabled")
        val ANDROID_TV_HOME = booleanPreferencesKey("android_tv_home")
        // Video Player Settings
        val HW_DECODING = booleanPreferencesKey("hw_decoding")
        val SURROUND_SOUND = booleanPreferencesKey("surround_sound")
        val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val DEFAULT_ZOOM = stringPreferencesKey("default_zoom")
        val SUB_SCALE = floatPreferencesKey("sub_scale")
        val AUDIO_DELAY_MS = intPreferencesKey("audio_delay_ms")
        val PREF_AUDIO_LANG = stringPreferencesKey("pref_audio_lang")
        val PREF_SUB_LANG = stringPreferencesKey("pref_sub_lang")
        // Per-section list sorting ("PLAYLIST" or "ALPHA")
        val SORT_LIVE = stringPreferencesKey("sort_live")
        val SORT_GUIDE = stringPreferencesKey("sort_guide")
        val SORT_MOVIES = stringPreferencesKey("sort_movies")
        val SORT_SERIES = stringPreferencesKey("sort_series")
        val RESUME_MODE = stringPreferencesKey("resume_mode")
        val UPDATE_CHECK_ON_START = booleanPreferencesKey("update_check_on_start")
        val CATCHUP_TZ = stringPreferencesKey("catchup_timezone")
        val CATCHUP_OFFSET_MIN = intPreferencesKey("catchup_offset_minutes")
        val ANIMATION_LEVEL = stringPreferencesKey("animation_level")
        val RESUME_LAST_CHANNEL = booleanPreferencesKey("resume_last_channel")
        val LAST_LIVE_CATEGORY = stringPreferencesKey("last_live_category")
        val LAST_LIVE_CHANNEL = androidx.datastore.preferences.core.longPreferencesKey("last_live_channel")
        val VOD_VIEW_MODE = stringPreferencesKey("vod_view_mode")
    }

    // --- Live TV: remember the last focused channel so reopening lands focus back on it ---
    val lastLiveChannelId: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_LIVE_CHANNEL] ?: -1L }
    suspend fun setLastLiveChannelId(id: Long) {
        context.dataStore.edit { it[Keys.LAST_LIVE_CHANNEL] = id }
    }

    // --- Startup: per-profile landing (v4.0.0). Falls back to the legacy global resume toggle for existing
    //     users (so "Resume last channel = On" keeps working until they pick a per-profile mode). ---
    fun startupMode(profileId: Long): Flow<StartupMode> = context.dataStore.data.map { prefs ->
        prefs[stringPreferencesKey("startup_mode_$profileId")]?.let { runCatching { StartupMode.valueOf(it) }.getOrNull() }
            ?: if (prefs[Keys.RESUME_LAST_CHANNEL] == true) StartupMode.LAST_CHANNEL else StartupMode.HOME
    }
    suspend fun setStartupMode(profileId: Long, mode: StartupMode) {
        context.dataStore.edit { it[stringPreferencesKey("startup_mode_$profileId")] = mode.name }
    }

    // --- Startup: auto-open the last-watched live channel (default OFF) — legacy, now migrated to startupMode ---
    val resumeLastChannel: Flow<Boolean> = context.dataStore.data.map { it[Keys.RESUME_LAST_CHANNEL] ?: false }
    suspend fun setResumeLastChannel(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RESUME_LAST_CHANNEL] = enabled }
    }

    // --- Live TV: remember the last selected category so reopening lands where you left off ---
    val lastLiveCategory: Flow<String> = context.dataStore.data.map { it[Keys.LAST_LIVE_CATEGORY] ?: "" }
    suspend fun setLastLiveCategory(key: String) {
        context.dataStore.edit { it[Keys.LAST_LIVE_CATEGORY] = key }
    }

    // --- Appearance: animation level (perf control for low-end boxes) ---
    val animationLevel: Flow<com.thirutricks.tllplayer.ui.theme.AnimationLevel> = context.dataStore.data.map { prefs ->
        prefs[Keys.ANIMATION_LEVEL]?.let { runCatching { com.thirutricks.tllplayer.ui.theme.AnimationLevel.valueOf(it) }.getOrNull() }
            ?: com.thirutricks.tllplayer.ui.theme.AnimationLevel.FULL
    }

    suspend fun setAnimationLevel(level: com.thirutricks.tllplayer.ui.theme.AnimationLevel) {
        context.dataStore.edit { it[Keys.ANIMATION_LEVEL] = level.name }
    }

    // --- Catch-up (archive) playback ---

    /** Which timezone to format Xtream timeshift URLs in. Most panels run on the server's local time, which
     *  usually matches the user's region — so **Device** is the default; a manual UTC offset is the fallback. */
    enum class CatchupTimezone { DEVICE, MANUAL }

    /** Manual UTC offset bounds (whole hours), in minutes. */
    val catchupOffsetRangeMinutes: IntRange = -12 * 60..14 * 60

    val catchupTimezone: Flow<CatchupTimezone> = context.dataStore.data.map { prefs ->
        prefs[Keys.CATCHUP_TZ]?.let { runCatching { CatchupTimezone.valueOf(it) }.getOrNull() } ?: CatchupTimezone.DEVICE
    }

    /** Manual mode's offset from UTC, in minutes (0 = UTC, the previous default). */
    val catchupOffsetMinutes: Flow<Int> = context.dataStore.data.map { it[Keys.CATCHUP_OFFSET_MIN] ?: 0 }

    suspend fun setCatchupTimezone(mode: CatchupTimezone) {
        context.dataStore.edit { it[Keys.CATCHUP_TZ] = mode.name }
    }

    suspend fun setCatchupOffsetMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.CATCHUP_OFFSET_MIN] = minutes.coerceIn(catchupOffsetRangeMinutes) }
    }

    /** The timezone catch-up/timeshift URLs are formatted in — device tz, or a manual UTC offset. */
    suspend fun resolveCatchupTimeZone(): java.util.TimeZone = when (catchupTimezone.first()) {
        CatchupTimezone.DEVICE -> java.util.TimeZone.getDefault()
        CatchupTimezone.MANUAL -> java.util.SimpleTimeZone(catchupOffsetMinutes.first() * 60_000, "catchup")
    }

    /** Automatically check GitHub Releases for a newer version shortly after launch. */
    val updateCheckOnStart: Flow<Boolean> = context.dataStore.data.map { it[Keys.UPDATE_CHECK_ON_START] ?: true }

    suspend fun setUpdateCheckOnStart(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UPDATE_CHECK_ON_START] = enabled }
    }

    // --- Resume behavior for movies/episodes with a saved position ---

    enum class ResumeMode(val label: String) {
        AUTO("Always resume"), ASK("Ask to resume"), NEVER("Never resume")
    }

    val resumeMode: Flow<ResumeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.RESUME_MODE]?.let { runCatching { ResumeMode.valueOf(it) }.getOrNull() } ?: ResumeMode.ASK
    }

    suspend fun setResumeMode(mode: ResumeMode) {
        context.dataStore.edit { it[Keys.RESUME_MODE] = mode.name }
    }

    // --- List sorting (per browse section) ---

    /** How a browse section's lists are ordered. */
    enum class SortMode { PLAYLIST, ALPHA }

    /** Live TV defaults to the playlist's own order; Movies/Series default to A–Z. */
    val sortLive: Flow<SortMode> = context.dataStore.data.map { parseSort(it[Keys.SORT_LIVE], SortMode.PLAYLIST) }
    val sortMovies: Flow<SortMode> = context.dataStore.data.map { parseSort(it[Keys.SORT_MOVIES], SortMode.ALPHA) }
    val sortSeries: Flow<SortMode> = context.dataStore.data.map { parseSort(it[Keys.SORT_SERIES], SortMode.ALPHA) }

    suspend fun setSortLive(mode: SortMode) {
        context.dataStore.edit { it[Keys.SORT_LIVE] = mode.name }
    }

    suspend fun setSortMovies(mode: SortMode) {
        context.dataStore.edit { it[Keys.SORT_MOVIES] = mode.name }
    }

    suspend fun setSortSeries(mode: SortMode) {
        context.dataStore.edit { it[Keys.SORT_SERIES] = mode.name }
    }

    private fun parseSort(raw: String?, default: SortMode): SortMode =
        raw?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: default

    /** The TV Guide's own ordering. LIVE_TV mirrors the Live TV sort; CATCHUP floats archive channels up. */
    enum class GuideSort(val label: String) { ALPHA("A–Z"), PROVIDER("Provider"), LIVE_TV("Live TV"), CATCHUP("Catch-up"), FAVORITES("Favorites") }

    /** How Movies & Series browse: the poster wall, or a compact list (more titles at once). */
    enum class VodViewMode(val label: String) { GRID("Grid"), LIST("List") }
    val vodViewMode: Flow<VodViewMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.VOD_VIEW_MODE]?.let { runCatching { VodViewMode.valueOf(it) }.getOrNull() } ?: VodViewMode.GRID
    }
    suspend fun setVodViewMode(mode: VodViewMode) {
        context.dataStore.edit { it[Keys.VOD_VIEW_MODE] = mode.name }
    }

    val sortGuide: Flow<GuideSort> = context.dataStore.data.map { prefs ->
        prefs[Keys.SORT_GUIDE]?.let { runCatching { GuideSort.valueOf(it) }.getOrNull() } ?: GuideSort.LIVE_TV
    }

    suspend fun setSortGuide(mode: GuideSort) {
        context.dataStore.edit { it[Keys.SORT_GUIDE] = mode.name }
    }

    // --- Video Player Settings ---

    /** Hardware decoding (mpv hwdec auto-safe). Off = force software decoding for tricky streams. */
    val hwDecoding: Flow<Boolean> = context.dataStore.data.map { it[Keys.HW_DECODING] ?: true }

    suspend fun setHwDecoding(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HW_DECODING] = enabled }
    }

    /** Surround sound (**off by default — opt-in**). Most users are on TV speakers / 2.0 soundbars, and
     *  forcing a multichannel-LPCM path exposes flaky vendor audio HALs / lying HDMI-ARC chips that claim
     *  5.1 then mis-play it (drained 2× → "fast video, no sound", #25). So default stereo for stability;
     *  users with a real 5.1/7.1 receiver turn this on. On: mpv decodes Dolby/DTS to multichannel LPCM (the
     *  sink picks the layout), with a runaway-detector that auto-falls-back to stereo on a broken output. We
     *  never bitstream/passthrough (its AudioTrack reports no clock and stutters video to a slideshow).
     *  Second, subtler failure mode (confirmed in the field): even when multichannel LPCM plays correctly,
     *  the wider HDMI/ARC buffer adds latency the TV/soundbar doesn't report back, so audio lags video
     *  (lip-sync drift) on VODs. Stereo's small, well-reported buffer stays locked. Hence: default OFF. */
    val surroundSound: Flow<Boolean> = context.dataStore.data.map { it[Keys.SURROUND_SOUND] ?: false }

    suspend fun setSurroundSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SURROUND_SOUND] = enabled }
    }

    /** Auto-play the next episode (and roll into the next season) when one finishes. On by default. */
    val autoPlayNext: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_PLAY_NEXT] ?: true }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_PLAY_NEXT] = enabled }
    }

    /** Default zoom/aspect mode applied when playback starts (a [com.thirutricks.tllplayer.player.ZoomMode] name). */
    val defaultZoom: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_ZOOM] ?: "FIT" }

    suspend fun setDefaultZoom(name: String) {
        context.dataStore.edit { it[Keys.DEFAULT_ZOOM] = name }
    }

    /** Subtitle scale multiplier (mpv sub-scale); 1.0 = normal. */
    val subtitleScale: Flow<Float> = context.dataStore.data.map { it[Keys.SUB_SCALE] ?: 1.0f }

    suspend fun setSubtitleScale(scale: Float) {
        context.dataStore.edit { it[Keys.SUB_SCALE] = scale }
    }

    /** Audio sync offset in milliseconds (mpv audio-delay); +ve delays audio. */
    val audioDelayMs: Flow<Int> = context.dataStore.data.map { it[Keys.AUDIO_DELAY_MS] ?: 0 }

    suspend fun setAudioDelayMs(ms: Int) {
        context.dataStore.edit { it[Keys.AUDIO_DELAY_MS] = ms }
    }

    /** Preferred audio language (ISO code, mpv alang); blank = no preference. */
    val preferredAudioLang: Flow<String> = context.dataStore.data.map { it[Keys.PREF_AUDIO_LANG] ?: "" }

    suspend fun setPreferredAudioLang(lang: String) {
        context.dataStore.edit { it[Keys.PREF_AUDIO_LANG] = lang }
    }

    /** Preferred subtitle language (ISO code, mpv slang); blank = no preference. */
    val preferredSubLang: Flow<String> = context.dataStore.data.map { it[Keys.PREF_SUB_LANG] ?: "" }

    suspend fun setPreferredSubLang(lang: String) {
        context.dataStore.edit { it[Keys.PREF_SUB_LANG] = lang }
    }

    /** Per-source "refresh on startup" — the set of source ids to re-sync when the app launches. */
    val refreshSourceIds: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.REFRESH_SOURCE_IDS]
        if (raw == null) {
            // Default first-run behavior: enable startup refresh for pre-populated TLL source (id = 1)
            setOf(1L)
        } else {
            raw.mapNotNull { it.toLongOrNull() }.toSet()
        }
    }

    suspend fun setSourceRefresh(sourceId: Long, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.REFRESH_SOURCE_IDS].orEmpty().toMutableSet()
            if (enabled) current.add(sourceId.toString()) else current.remove(sourceId.toString())
            prefs[Keys.REFRESH_SOURCE_IDS] = current
        }
    }

    /** Whether focusing a channel auto-plays it in the Live preview pane. */
    val livePreviewEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIVE_PREVIEW] ?: true }

    suspend fun setLivePreviewEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_PREVIEW] = enabled }
    }

    /** Whether the Live preview plays audio (off by default so browsing stays quiet). */
    val livePreviewAudio: Flow<Boolean> = context.dataStore.data.map { it[Keys.LIVE_PREVIEW_AUDIO] ?: false }

    suspend fun setLivePreviewAudio(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LIVE_PREVIEW_AUDIO] = enabled }
    }

    /** Use HDR output when the video and display support it. */
    val hdrEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.HDR_ENABLED] ?: true }

    suspend fun setHdrEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HDR_ENABLED] = enabled }
    }

    /** Mirror continue-watching rows into Android TV home surfaces. */
    val androidTvHomeEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.ANDROID_TV_HOME] ?: true }

    suspend fun setAndroidTvHomeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ANDROID_TV_HOME] = enabled }
    }

    /** The source shown as "active" in the sidebar; -1 = none chosen (fall back to the first source). */
    val defaultSourceId: Flow<Long> = context.dataStore.data.map { it[Keys.DEFAULT_SOURCE] ?: -1L }

    suspend fun setDefaultSource(id: Long) {
        context.dataStore.edit { it[Keys.DEFAULT_SOURCE] = id }
    }

    /** User-chosen download base folder; blank = app-specific storage. */
    val downloadRoot: Flow<String> = context.dataStore.data.map { it[Keys.DOWNLOAD_ROOT] ?: "" }

    suspend fun setDownloadRoot(path: String) {
        context.dataStore.edit { it[Keys.DOWNLOAD_ROOT] = path }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.AMOLED_DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val uiZoomPercent: Flow<Int> = context.dataStore.data.map { prefs ->
        UiZoom.clamp(prefs[Keys.UI_ZOOM_PCT] ?: UiZoom.DEFAULT)
    }

    suspend fun setUiZoomPercent(percent: Int) {
        context.dataStore.edit { it[Keys.UI_ZOOM_PCT] = UiZoom.clamp(percent) }
    }

    val accent: Flow<AccentColor> = context.dataStore.data.map { prefs ->
        prefs[Keys.ACCENT]?.let { runCatching { AccentColor.valueOf(it) }.getOrNull() }
            ?: AccentColor.TEAL
    }

    /** Picking a preset clears any custom accent so the preset takes effect. */
    suspend fun setAccent(accent: AccentColor) {
        context.dataStore.edit {
            it[Keys.ACCENT] = accent.name
            it[Keys.ACCENT_CUSTOM] = ""
        }
    }

    /** Custom accent as a hex string ("#52DBC8"); blank = use the [accent] preset. */
    val customAccent: Flow<String> = context.dataStore.data.map { it[Keys.ACCENT_CUSTOM] ?: "" }

    suspend fun setCustomAccent(hex: String) {
        context.dataStore.edit { it[Keys.ACCENT_CUSTOM] = hex.trim() }
    }

    /** Avatar for the current (placeholder) profile until real profiles arrive in the wizard. */
    val avatarId: Flow<Int> = context.dataStore.data.map { it[Keys.AVATAR_ID] ?: 0 }

    suspend fun setAvatarId(id: Int) {
        context.dataStore.edit { it[Keys.AVATAR_ID] = id }
    }

    /** Active profile id; -1 means first-run / setup not yet completed. */
    val activeProfileId: Flow<Long> = context.dataStore.data.map { it[Keys.ACTIVE_PROFILE] ?: 1L }

    suspend fun setActiveProfile(id: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE] = id }
    }

    // --- Backup / restore of pure UI/player preferences (device-agnostic) ---
    // Deliberately EXCLUDES the download folder (a device-specific path) and the profile/source-coupled
    // keys (active profile, default source, refresh-on-startup) — those ride with the sources backup.

    private val backupStringKeys = listOf(
        Keys.THEME_MODE, Keys.ACCENT, Keys.ACCENT_CUSTOM, Keys.DEFAULT_ZOOM,
        Keys.PREF_AUDIO_LANG, Keys.PREF_SUB_LANG, Keys.SORT_LIVE, Keys.SORT_MOVIES,
        Keys.SORT_SERIES, Keys.RESUME_MODE,
    )
    private val backupIntKeys = listOf(Keys.UI_ZOOM_PCT, Keys.AUDIO_DELAY_MS)
    private val backupBoolKeys = listOf(Keys.LIVE_PREVIEW, Keys.LIVE_PREVIEW_AUDIO, Keys.HDR_ENABLED, Keys.ANDROID_TV_HOME, Keys.HW_DECODING, Keys.UPDATE_CHECK_ON_START)
    private val backupFloatKeys = listOf(Keys.SUB_SCALE)

    suspend fun exportSettings(): org.json.JSONObject {
        val p = context.dataStore.data.first()
        return org.json.JSONObject().apply {
            backupStringKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupIntKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupBoolKeys.forEach { k -> p[k]?.let { put(k.name, it) } }
            backupFloatKeys.forEach { k -> p[k]?.let { put(k.name, it.toDouble()) } }
        }
    }

    suspend fun importSettings(o: org.json.JSONObject) {
        context.dataStore.edit { prefs ->
            backupStringKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getString(k.name) }
            backupIntKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getInt(k.name) }
            backupBoolKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getBoolean(k.name) }
            backupFloatKeys.forEach { k -> if (o.has(k.name)) prefs[k] = o.getDouble(k.name).toFloat() }
        }
    }

    // ---- TMDB metadata config (used by TmdbProvider / MetadataRepository) ----
    data class MetadataConfig(
        val enabled: Boolean = true,
        val tier: Tier = Tier.DEFAULT_WORKER,
        val customServerUrl: String = "",
        val tmdbApiKey: String = "",
    ) {
        enum class Tier { SELF_HOST, OWN_KEY, DEFAULT_WORKER }
    }

    fun metadataConfig(): MetadataConfig = MetadataConfig()
}
