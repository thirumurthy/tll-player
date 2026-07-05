package com.thirutricks.tllplayer.features.settings

import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.epg.EpgSourceStore
import com.thirutricks.tllplayer.core.repository.EpgRepository

/**
 * Shared semi-auto EPG sync used by both onboarding and Settings → Playlists. Registers the playlist's own
 * guide feed as an [EpgSource][com.thirutricks.tllplayer.core.epg.EpgSource] in the [store] (re-using an existing entry
 * with the same URL) so it appears in Settings → EPG, then downloads it keyed by that source id while
 * reporting a live programme count through [setState].
 */
suspend fun runSemiAutoEpgSync(
    source: SourceEntity,
    epgRepository: EpgRepository,
    store: EpgSourceStore,
    setState: (EpgSyncUi) -> Unit,
) {
    val url = epgRepository.guideUrl(source) ?: run { setState(EpgSyncUi.Done); return }
    setState(EpgSyncUi.Syncing(0))
    // Show up in Settings → EPG like any other feed (so the user can re-sync / delete it there).
    val epgSource = store.getAll().firstOrNull { it.url == url } ?: store.add(source.name, url, source.userAgent)
    val now = System.currentTimeMillis()
    runCatching {
        epgRepository.refreshUrl(epgSource.id, epgSource.url, epgSource.userAgent) { count -> setState(EpgSyncUi.Syncing(count)) }
    }
        .onSuccess { store.setSynced(epgSource.id, now, null) }
        .onFailure { store.setSynced(epgSource.id, now, it.message) }
    setState(EpgSyncUi.Done)
}
