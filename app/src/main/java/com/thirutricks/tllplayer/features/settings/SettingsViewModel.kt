@file:OptIn(ExperimentalCoroutinesApi::class)

package com.thirutricks.tllplayer.features.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.thirutricks.tllplayer.core.database.dao.ProfileDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.network.ConnectivityObserver
import com.thirutricks.tllplayer.core.repository.SourceRepository
import com.thirutricks.tllplayer.core.sync.ImportStage
import com.thirutricks.tllplayer.core.sync.SyncResult
import com.thirutricks.tllplayer.core.util.friendlySyncError
import com.thirutricks.tllplayer.core.database.dao.resolveExistingProfileId
import com.thirutricks.tllplayer.core.launcher.LauncherIntegrationRepository
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.ui.theme.AccentColor
import com.thirutricks.tllplayer.ui.theme.ThemeMode
import com.thirutricks.tllplayer.ui.theme.UiZoom

/** Phase 13 — manage IPTV sources (list / add / re-sync / delete) for the active profile. */
class SettingsViewModel(
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val sourceRepository: SourceRepository,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val epgDao: com.thirutricks.tllplayer.core.database.dao.EpgDao,
    private val importFinalizer: com.thirutricks.tllplayer.core.sync.ImportFinalizer,
    private val channelDao: com.thirutricks.tllplayer.core.database.dao.ChannelDao,
    private val historyDao: com.thirutricks.tllplayer.core.database.dao.HistoryDao,
    private val progressDao: com.thirutricks.tllplayer.core.database.dao.ProgressDao,
    private val epgRepository: com.thirutricks.tllplayer.core.repository.EpgRepository,
    private val epgSourceStore: com.thirutricks.tllplayer.core.epg.EpgSourceStore,
    private val launcherIntegrationRepository: LauncherIntegrationRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "OwnTVHome"
    }

    // Semi-auto EPG: after a playlist import, if the playlist has a guide URL we offer to sync the EPG now
    // (instead of the old slow auto-sync). "Sync now" shows a live programme count, just like the import.
    private var pendingEpgSource: SourceEntity? = null
    private val _epgSync = MutableStateFlow<EpgSyncUi>(EpgSyncUi.Hidden)
    val epgSync: StateFlow<EpgSyncUi> = _epgSync.asStateFlow()

    fun syncPendingEpg() {
        val src = pendingEpgSource ?: return
        viewModelScope.launch { runSemiAutoEpgSync(src, epgRepository, epgSourceStore) { _epgSync.value = it } }
    }

    /** Skip (from the prompt) or acknowledge (after Done) — either way, close the EPG flow. */
    fun dismissPendingEpg() { pendingEpgSource = null; _epgSync.value = EpgSyncUi.Hidden }

    /** Clear the active profile's watch history (the "recently watched" / continue rows). #26
     *  [type] null = everything; otherwise just LIVE / MOVIE / SERIES. */
    fun clearWatchHistory(type: com.thirutricks.tllplayer.core.model.MediaType? = null) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first()
            if (pid < 0) return@launch
            if (type == null) {
                historyDao.clear(pid)
                progressDao.clearProfile(pid) // also wipe resume positions → empties Home's continue-watching
            } else {
                historyDao.clearType(pid, type)
                // Home's Movies/Series continue-watching comes from the resume (progress) table, not history;
                // series progress is stored under EPISODE. Live has no resume progress to clear.
                when (type) {
                    com.thirutricks.tllplayer.core.model.MediaType.MOVIE ->
                        progressDao.clearProfileType(pid, com.thirutricks.tllplayer.core.model.MediaType.MOVIE)
                    com.thirutricks.tllplayer.core.model.MediaType.SERIES ->
                        progressDao.clearProfileType(pid, com.thirutricks.tllplayer.core.model.MediaType.EPISODE)
                    else -> Unit
                }
            }
            // Rebuild the Android TV home cards so the cleared items also leave the system Continue Watching row.
            runCatching { launcherIntegrationRepository.refreshProfile(pid) }
        }
    }

    /** Stored EPG programme count for a source — the row shows it as the EPG status. */
    fun epgCount(sourceId: Long): kotlinx.coroutines.flow.Flow<Int> = epgDao.countForSource(sourceId)

    /** Content counts (channels/movies/series) for a source — shown on each Playlists row. */
    fun contentCounts(sourceId: Long): kotlinx.coroutines.flow.Flow<com.thirutricks.tllplayer.core.sync.SyncCounts> =
        kotlinx.coroutines.flow.flow { emit(importFinalizer.contentCounts(sourceId)) }

    sealed interface ImportState {
        data object Idle : ImportState
        data object Running : ImportState
        /** [summary] is the per-type breakdown, e.g. "40K channels · 100K movies · 30K series synced". */
        data class Success(val summary: String) : ImportState
        data class Failed(val message: String) : ImportState
    }

    val sources: StateFlow<List<SourceEntity>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else sourceRepository.observeSources(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many of the active profile's channels advertise catch-up — for the Catch-up settings note. */
    val catchupChannelCount: StateFlow<Int> = sources
        .flatMapLatest { srcs ->
            val ids = srcs.map { it.id }
            kotlinx.coroutines.flow.flow { emit(if (ids.isEmpty()) 0 else channelDao.countCatchup(ids)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Configured download folder ("" = app-specific storage). */
    val downloadRoot: StateFlow<String> = settings.downloadRoot
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setDownloadRoot(path: String) {
        viewModelScope.launch { settings.setDownloadRoot(path) }
    }

    /** The source marked as default/active (shown in the sidebar). */
    val defaultSourceId: StateFlow<Long> = settings.defaultSourceId
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    fun setDefaultSource(id: Long) {
        viewModelScope.launch { settings.setDefaultSource(id) }
    }

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setLivePreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLivePreviewEnabled(enabled) }
    }

    val livePreviewAudio: StateFlow<Boolean> = settings.livePreviewAudio
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setLivePreviewAudio(enabled: Boolean) {
        viewModelScope.launch { settings.setLivePreviewAudio(enabled) }
    }

    val hdrEnabled: StateFlow<Boolean> = settings.hdrEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setHdrEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setHdrEnabled(enabled) }
    }

    val surroundSound: StateFlow<Boolean> = settings.surroundSound
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setSurroundSound(enabled: Boolean) {
        viewModelScope.launch { settings.setSurroundSound(enabled) }
    }

    val autoPlayNext: StateFlow<Boolean> = settings.autoPlayNext
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAutoPlayNext(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoPlayNext(enabled) }
    }

    val catchupTimezone: StateFlow<SettingsRepository.CatchupTimezone> = settings.catchupTimezone
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.CatchupTimezone.MANUAL)

    val catchupOffsetMinutes: StateFlow<Int> = settings.catchupOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val catchupOffsetRangeMinutes: IntRange = settings.catchupOffsetRangeMinutes

    fun setCatchupTimezone(mode: SettingsRepository.CatchupTimezone) {
        viewModelScope.launch { settings.setCatchupTimezone(mode) }
    }

    /** Nudge the manual UTC offset by [deltaMinutes] (the picker's − / + steps), clamped to range. */
    fun adjustCatchupOffset(deltaMinutes: Int) {
        viewModelScope.launch { settings.setCatchupOffsetMinutes(catchupOffsetMinutes.value + deltaMinutes) }
    }

    val androidTvHomeEnabled: StateFlow<Boolean> = settings.androidTvHomeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setAndroidTvHomeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "setAndroidTvHomeEnabled enabled=$enabled")
            settings.setAndroidTvHomeEnabled(enabled)
            if (enabled) {
                refreshActiveTvHome(allowBrowsableRequest = true)
            } else {
                profileDao.getAllOnce().forEach { profile -> launcherIntegrationRepository.clearProfile(profile.id) }
            }
        }
    }

    /** Status of the manual "Refresh now" so the UI can show Rebuilding… → Done. */
    enum class TvHomeRefresh { IDLE, REFRESHING, DONE }
    private val _tvHomeRefresh = MutableStateFlow(TvHomeRefresh.IDLE)
    val tvHomeRefresh: StateFlow<TvHomeRefresh> = _tvHomeRefresh.asStateFlow()

    fun refreshAndroidTvHome() {
        if (_tvHomeRefresh.value == TvHomeRefresh.REFRESHING) return
        viewModelScope.launch {
            _tvHomeRefresh.value = TvHomeRefresh.REFRESHING
            runCatching { refreshActiveTvHome(allowBrowsableRequest = true) }
            _tvHomeRefresh.value = TvHomeRefresh.DONE
            kotlinx.coroutines.delay(1_800)
            if (_tvHomeRefresh.value == TvHomeRefresh.DONE) _tvHomeRefresh.value = TvHomeRefresh.IDLE
        }
    }

    // --- Video Player Settings ---
    val hwDecoding: StateFlow<Boolean> = settings.hwDecoding.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setHwDecoding(enabled: Boolean) { viewModelScope.launch { settings.setHwDecoding(enabled) } }

    val updateCheckOnStart: StateFlow<Boolean> =
        settings.updateCheckOnStart.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setUpdateCheckOnStart(enabled: Boolean) { viewModelScope.launch { settings.setUpdateCheckOnStart(enabled) } }

    val resumeLastChannel: StateFlow<Boolean> =
        settings.resumeLastChannel.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun setResumeLastChannel(enabled: Boolean) { viewModelScope.launch { settings.setResumeLastChannel(enabled) } }

    // Per-profile startup landing (v4.0.0): Home / Last channel / Live·Favorites.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val startupMode: StateFlow<com.thirutricks.tllplayer.features.settings.data.StartupMode> =
        settings.activeProfileId
            .flatMapLatest { settings.startupMode(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, com.thirutricks.tllplayer.features.settings.data.StartupMode.HOME)
    fun setStartupMode(mode: com.thirutricks.tllplayer.features.settings.data.StartupMode) {
        viewModelScope.launch { settings.setStartupMode(settings.activeProfileId.first(), mode) }
    }

    val resumeMode: StateFlow<SettingsRepository.ResumeMode> =
        settings.resumeMode.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.ResumeMode.ASK)
    fun setResumeMode(name: String) {
        viewModelScope.launch {
            settings.setResumeMode(runCatching { SettingsRepository.ResumeMode.valueOf(name) }.getOrDefault(SettingsRepository.ResumeMode.ASK))
        }
    }

    val defaultZoom: StateFlow<String> = settings.defaultZoom.stateIn(viewModelScope, SharingStarted.Eagerly, "FIT")
    fun setDefaultZoom(name: String) { viewModelScope.launch { settings.setDefaultZoom(name) } }

    val subtitleScale: StateFlow<Float> = settings.subtitleScale.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    fun setSubtitleScale(scale: Float) { viewModelScope.launch { settings.setSubtitleScale(scale) } }

    val audioDelayMs: StateFlow<Int> = settings.audioDelayMs.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    fun setAudioDelayMs(ms: Int) { viewModelScope.launch { settings.setAudioDelayMs(ms) } }

    val preferredAudioLang: StateFlow<String> = settings.preferredAudioLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setPreferredAudioLang(lang: String) { viewModelScope.launch { settings.setPreferredAudioLang(lang) } }

    val preferredSubLang: StateFlow<String> = settings.preferredSubLang.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setPreferredSubLang(lang: String) { viewModelScope.launch { settings.setPreferredSubLang(lang) } }

    // --- Personalization (theme / accent / UI zoom) ---
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.AMOLED_DARK)
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    val accent: StateFlow<AccentColor> = settings.accent.stateIn(viewModelScope, SharingStarted.Eagerly, AccentColor.TEAL)
    fun setAccent(accent: AccentColor) { viewModelScope.launch { settings.setAccent(accent) } }

    /** Custom accent hex ("#52DBC8"); blank = the preset is in effect. */
    val customAccent: StateFlow<String> = settings.customAccent.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setCustomAccent(hex: String) { viewModelScope.launch { settings.setCustomAccent(hex) } }

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent.stateIn(viewModelScope, SharingStarted.Eagerly, UiZoom.DEFAULT)
    fun setUiZoom(percent: Int) { viewModelScope.launch { settings.setUiZoomPercent(UiZoom.clamp(percent)) } }

    val animationLevel: StateFlow<com.thirutricks.tllplayer.ui.theme.AnimationLevel> =
        settings.animationLevel.stateIn(viewModelScope, SharingStarted.Eagerly, com.thirutricks.tllplayer.ui.theme.AnimationLevel.FULL)
    fun setAnimationLevel(level: com.thirutricks.tllplayer.ui.theme.AnimationLevel) { viewModelScope.launch { settings.setAnimationLevel(level) } }

    /** Source ids flagged "refresh on startup". */
    val refreshSourceIds: StateFlow<Set<Long>> = settings.refreshSourceIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun setSourceRefresh(sourceId: Long, enabled: Boolean) {
        viewModelScope.launch { settings.setSourceRefresh(sourceId, enabled) }
    }

    /** Edit an existing source's settings (no re-import unless the user re-syncs). */
    fun updateSource(id: Long, name: String, urlOrServer: String, user: String, pass: String, userAgent: String, epgUrl: String, refreshOnStart: Boolean) {
        viewModelScope.launch {
            val existing = sourceDao.getById(id) ?: return@launch
            sourceRepository.updateSource(
                existing.copy(
                    name = name.ifBlank { existing.name },
                    url = urlOrServer.trim().ifBlank { existing.url },
                    username = user.trim().takeIf { it.isNotBlank() } ?: existing.username,
                    password = pass.takeIf { it.isNotBlank() } ?: existing.password,
                    userAgent = userAgent.trim().takeIf { it.isNotBlank() },
                    epgUrl = epgUrl.trim().takeIf { it.isNotBlank() },
                ),
            )
            settings.setSourceRefresh(id, refreshOnStart)
        }
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _progress = MutableStateFlow<ImportStage?>(null)
    val progress: StateFlow<ImportStage?> = _progress.asStateFlow()

    fun addXtream(name: String, server: String, user: String, pass: String, userAgent: String = "", epgUrl: String = "", refreshOnStart: Boolean = false) = runImport(refreshOnStart) { pid ->
        sourceRepository.addXtreamSource(
            pid, name.ifBlank { "My IPTV" }, server.trim(), user.trim(), pass,
            userAgent.trim().takeIf { it.isNotBlank() },
            epgUrl.trim().takeIf { it.isNotBlank() },
        )
    }

    fun addM3u(name: String, url: String, userAgent: String = "", epgUrl: String = "", refreshOnStart: Boolean = false) = runImport(refreshOnStart) { pid ->
        sourceRepository.addM3uSource(
            pid, name.ifBlank { "My Playlist" }, url.trim(),
            userAgent.trim().takeIf { it.isNotBlank() },
            epgUrl.trim().takeIf { it.isNotBlank() },
        )
    }

    fun addTll(name: String, url: String, userAgent: String = "", refreshOnStart: Boolean = false) = runImport(refreshOnStart) { pid ->
        sourceRepository.addTllSource(
            pid, name.ifBlank { "TLL Source" }, url.trim(),
            userAgent.trim().takeIf { it.isNotBlank() },
        )
    }

    private fun runImport(refreshOnStart: Boolean = false, addSource: suspend (Long) -> SourceEntity) {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            _progress.value = null
            try {
                val pid = profileDao.resolveExistingProfileId(settings.activeProfileId.first()) ?: return@launch
                Log.d(TAG, "runImport profile=$pid refreshOnStart=$refreshOnStart")
                val source = addSource(pid)
                settings.setSourceRefresh(source.id, refreshOnStart)
                when (val r = sourceRepository.sync(source) { _progress.value = it }) {
                    SyncResult.Success -> {
                        // Settings playlist add: content breakdown only (EPG syncs silently and is
                        // shown on the EPG Sources screen, per the separated-EPG design).
                        val counts = importFinalizer.finalize(source)
                        Log.d(TAG, "runImport sync success sourceId=${source.id} profile=$pid")
                        refreshActiveTvHome(allowBrowsableRequest = true)
                        _importState.value = ImportState.Success(counts.summary(includeEpg = false))
                        // Offer a one-tap EPG sync if this playlist actually has a guide feed.
                        if (epgRepository.guideUrl(source) != null) {
                            pendingEpgSource = source
                            _epgSync.value = EpgSyncUi.Ask(source.name)
                        }
                    }
                    is SyncResult.Failed -> _importState.value = ImportState.Failed(friendlySyncError(r.message, connectivity.isOnlineNow()))
                    SyncResult.Cancelled -> _importState.value = ImportState.Idle
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: Exception) {
                _importState.value = ImportState.Failed(friendlySyncError(e.message, connectivity.isOnlineNow()))
            }
        }
    }

    /** Re-sync an existing source, driving the same import-progress UI as adding one. */
    fun resync(source: SourceEntity) {
        viewModelScope.launch {
            _importState.value = ImportState.Running
            _progress.value = null
            Log.d(TAG, "resync sourceId=${source.id}")
            when (val r = sourceRepository.sync(source) { _progress.value = it }) {
                SyncResult.Success -> {
                    val counts = importFinalizer.finalize(source)
                    Log.d(TAG, "resync sync success sourceId=${source.id}")
                    refreshActiveTvHome(allowBrowsableRequest = true)
                    _importState.value = ImportState.Success(counts.summary(includeEpg = false))
                }
                is SyncResult.Failed -> _importState.value = ImportState.Failed(friendlySyncError(r.message, connectivity.isOnlineNow()))
                SyncResult.Cancelled -> _importState.value = ImportState.Idle
            }
        }
    }

    fun delete(source: SourceEntity) {
        viewModelScope.launch {
            Log.d(TAG, "delete sourceId=${source.id}")
            sourceRepository.deleteSource(source)
            if (defaultSourceId.value == source.id) settings.setDefaultSource(-1L)
            refreshActiveTvHome(allowBrowsableRequest = true)
        }
    }

    fun resetImport() {
        _importState.value = ImportState.Idle
        _progress.value = null
    }

    private suspend fun refreshActiveTvHome(allowBrowsableRequest: Boolean = true) {
        val pid = profileDao.resolveExistingProfileId(settings.activeProfileId.first()) ?: return
        Log.d(TAG, "refreshActiveTvHome profile=$pid allowBrowsable=$allowBrowsableRequest")
        launcherIntegrationRepository.refreshProfile(pid, allowBrowsableRequest)
    }
}
