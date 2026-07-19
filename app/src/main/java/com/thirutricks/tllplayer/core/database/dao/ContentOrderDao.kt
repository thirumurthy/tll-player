package com.thirutricks.tllplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import com.thirutricks.tllplayer.core.database.entity.ContentOrderEntity
import com.thirutricks.tllplayer.core.model.MediaType

/**
 * Write side of the per-profile manual item order ("Move up/down"). The ordered content lists are
 * produced by the content DAOs (LEFT JOIN content_order). See [ContentOrderEntity].
 */
@Dao
interface ContentOrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ContentOrderEntity>)

    @Query("DELETE FROM content_order WHERE profileId = :profileId AND mediaType = :type AND contextKey = :contextKey")
    suspend fun clearContext(profileId: Long, type: MediaType, contextKey: String)

    /** Context keys that actually have manual-order rows for this profile/section — the C3 fast
     *  path: folders with no overrides use the plain indexed paging query instead of the
     *  unindexable content_order join-sort (which materializes the whole folder per page). */
    @Query("SELECT DISTINCT contextKey FROM content_order WHERE profileId = :profileId AND mediaType = :type")
    fun observeContextKeys(profileId: Long, type: MediaType): Flow<List<String>>

    /** Replaces a context's entire order in one transaction (commit point of a Move). */
    @Transaction
    suspend fun replaceContext(profileId: Long, type: MediaType, contextKey: String, rows: List<ContentOrderEntity>) {
        clearContext(profileId, type, contextKey)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    /** Everything, for Backup & Restore / re-sync snapshotting. */
    @Query("SELECT * FROM content_order")
    suspend fun getAllOnce(): List<ContentOrderEntity>

    /**
     * Drops order rows whose content row no longer exists — content is clear-then-insert on every sync,
     * so an item's itemId goes stale. Called after a re-sync's relink (UserDataResolver), mirroring
     * FavoriteDao.purgeOrphans. Episodes never appear here (items only — LIVE/MOVIE/SERIES).
     */
    @Query(
        "DELETE FROM content_order WHERE " +
            "(mediaType = 'LIVE'   AND itemId NOT IN (SELECT id FROM channels)) OR " +
            "(mediaType = 'MOVIE'  AND itemId NOT IN (SELECT id FROM movies))   OR " +
            "(mediaType = 'SERIES' AND itemId NOT IN (SELECT id FROM series))",
    )
    suspend fun purgeOrphans()
}
