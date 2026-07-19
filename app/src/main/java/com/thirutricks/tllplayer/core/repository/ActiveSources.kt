package com.thirutricks.tllplayer.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.features.settings.data.SettingsRepository

/**
 * The set of sources the Browse screens should show right now: the active profile's linked sources,
 * narrowed to the user's chosen "active playlist" ([SettingsRepository.defaultSourceId]) when one is set.
 *
 * A default of `-1` (or a default that no longer belongs to the profile) means **All playlists** — the
 * full merged view. This is purely a *display* filter: every playlist still imports and stores all of its
 * content and EPG; this only decides which source ids the grids/guide read from.
 */
data class ActiveProfileSources(val profileId: Long, val sources: List<SourceEntity>) {
    val sourceIds: List<Long> get() = sources.map { it.id }
}

/**
 * Reactive [ActiveProfileSources] for the current profile + active-playlist filter. Emits again whenever
 * the profile, its linked sources, or the chosen default changes, so Browse refreshes live.
 */
@Suppress("OPT_IN_USAGE")
fun activeProfileSources(
    settings: SettingsRepository,
    sourceDao: SourceDao,
): Flow<ActiveProfileSources> =
    settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) {
                flowOf(ActiveProfileSources(pid, emptyList()))
            } else {
                combine(sourceDao.observeForProfile(pid), settings.defaultSourceId) { srcs, defaultId ->
                    val filtered = if (defaultId > 0 && srcs.any { it.id == defaultId }) {
                        srcs.filter { it.id == defaultId }
                    } else {
                        srcs
                    }
                    ActiveProfileSources(pid, filtered)
                }
            }
        }
        .distinctUntilChanged()

/**
 * One-shot version of [activeProfileSources] for imperative code paths: the [profileId]'s linked source
 * ids, narrowed to the active-playlist filter when one is set (else all). Empty when the profile has none.
 */
suspend fun activeSourceIds(
    settings: SettingsRepository,
    sourceDao: SourceDao,
    profileId: Long,
): List<Long> {
    val all = sourceDao.sourceIdsForProfile(profileId)
    val defaultId = settings.defaultSourceId.first()
    return if (defaultId > 0 && defaultId in all) listOf(defaultId) else all
}
