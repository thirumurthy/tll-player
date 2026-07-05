package com.thirutricks.tllplayer.core.repository

import kotlinx.coroutines.flow.Flow
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.ProfileSourceCrossRef
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.model.SourceType
import com.thirutricks.tllplayer.core.sync.ImportStage
import com.thirutricks.tllplayer.core.sync.SyncManager
import com.thirutricks.tllplayer.core.sync.SyncResult

/**
 * Adds/links sources to a profile and runs imports. The setup wizard (Phase 6) and playlist screen
 * (Phase 13) drive this; the actual parsing/inserting lives in [SyncManager].
 */
class SourceRepository(
    private val sourceDao: SourceDao,
    private val syncManager: SyncManager,
    private val userData: com.thirutricks.tllplayer.core.backup.UserDataResolver,
) {
    fun observeSources(profileId: Long): Flow<List<SourceEntity>> = sourceDao.observeForProfile(profileId)

    suspend fun getById(id: Long): SourceEntity? = sourceDao.getById(id)

    suspend fun addXtreamSource(
        profileId: Long, name: String, serverUrl: String, username: String, password: String,
        userAgent: String? = null, epgUrl: String? = null,
    ): SourceEntity = addAndLink(
        profileId,
        SourceEntity(name = name, type = SourceType.XTREAM, url = serverUrl, username = username, password = password, userAgent = userAgent, epgUrl = epgUrl),
    )

    suspend fun addM3uSource(
        profileId: Long, name: String, url: String, userAgent: String? = null, epgUrl: String? = null,
    ): SourceEntity = addAndLink(
        profileId,
        SourceEntity(name = name, type = SourceType.M3U, url = url, userAgent = userAgent, epgUrl = epgUrl),
    )

    suspend fun addTllSource(
        profileId: Long, name: String, url: String, userAgent: String? = null,
    ): SourceEntity = addAndLink(
        profileId,
        SourceEntity(name = name, type = SourceType.TLL, url = url, userAgent = userAgent),
    )

    private suspend fun addAndLink(profileId: Long, source: SourceEntity): SourceEntity {
        val id = sourceDao.insert(source)
        sourceDao.link(ProfileSourceCrossRef(profileId = profileId, sourceId = id))
        return source.copy(id = id)
    }

    suspend fun deleteSource(source: SourceEntity) = sourceDao.delete(source)

    suspend fun updateSource(source: SourceEntity) = sourceDao.update(source)

    suspend fun sync(source: SourceEntity, onProgress: (ImportStage) -> Unit): SyncResult {
        // Snapshot favorites/history/resume with stable keys BEFORE the sync clears content (their ids
        // change on every refresh, so they'd otherwise orphan — count badge set, list empty).
        val snapshot = runCatching { userData.exportAll() }.getOrNull()
        val result = syncManager.sync(source, onProgress)
        if (result == SyncResult.Success) {
            // Content rows just regenerated — re-attach the snapshot (and any restored backup data) to
            // the new ids, and drop rows the provider removed.
            runCatching { userData.relinkAfterSync(snapshot ?: org.json.JSONArray()) }
        }
        return result
    }
}
