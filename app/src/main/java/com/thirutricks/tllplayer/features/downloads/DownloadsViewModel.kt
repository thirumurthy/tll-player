@file:OptIn(ExperimentalCoroutinesApi::class)

package com.thirutricks.tllplayer.features.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import com.thirutricks.tllplayer.core.database.dao.DownloadDao
import com.thirutricks.tllplayer.core.database.entity.DownloadEntity
import com.thirutricks.tllplayer.core.download.DownloadManager
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository
import com.thirutricks.tllplayer.player.OwnTVPlayer

/** Phase 12 — lists the active profile's downloads and plays completed ones from local storage. */
class DownloadsViewModel(
    private val downloadDao: DownloadDao,
    private val settings: SettingsRepository,
    private val downloadManager: DownloadManager,
    val player: OwnTVPlayer,
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else downloadDao.observeForProfile(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _lastPlayedId = MutableStateFlow<Long?>(null)
    val lastPlayedId: StateFlow<Long?> = _lastPlayedId.asStateFlow()

    /** Play a completed download from its local file. */
    fun play(download: DownloadEntity) {
        val path = download.filePath ?: return
        _lastPlayedId.value = download.id
        player.play(path, title = download.title, isLive = false)
    }

    fun retry(download: DownloadEntity) = downloadManager.retry(download)
    fun pause(download: DownloadEntity) = downloadManager.pause(download)
    fun resume(download: DownloadEntity) = downloadManager.resume(download)
    fun delete(download: DownloadEntity) = downloadManager.delete(download)
}
