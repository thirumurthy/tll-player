package com.thirutricks.tllplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.thirutricks.tllplayer.core.database.entity.PlaybackProgressEntity
import com.thirutricks.tllplayer.core.model.MediaType

/** Resume positions for VOD and episodes (per profile). */
@Dao
interface ProgressDao {
    /** One row per (profile, type, item); REPLACE updates position via the unique index. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND mediaType = :type AND itemId = :itemId")
    fun observe(profileId: Long, type: MediaType, itemId: Long): Flow<PlaybackProgressEntity?>

    @Query("SELECT * FROM playback_progress WHERE profileId = :profileId AND mediaType = :type AND itemId = :itemId")
    suspend fun get(profileId: Long, type: MediaType, itemId: Long): PlaybackProgressEntity?

    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND mediaType = :type AND itemId = :itemId")
    suspend fun clear(profileId: Long, type: MediaType, itemId: Long)

    /** Wipe all resume positions for a profile — drives "Clear watch history" (so Home's continue-watching empties). */
    @Query("DELETE FROM playback_progress WHERE profileId = :profileId")
    suspend fun clearProfile(profileId: Long)

    /** Wipe resume positions for one media type (MOVIE / EPISODE) for a profile. */
    @Query("DELETE FROM playback_progress WHERE profileId = :profileId AND mediaType = :type")
    suspend fun clearProfileType(profileId: Long, type: MediaType)

    /** Everything, for Backup & Restore. */
    @Query("SELECT * FROM playback_progress")
    suspend fun getAllOnce(): List<PlaybackProgressEntity>

    /** The episode most recently watched in [seriesId] (by this profile), or null — so opening a show can
     *  jump straight to where you left off instead of episode 1. */
    @Query(
        "SELECT itemId FROM playback_progress " +
            "WHERE profileId = :profileId AND mediaType = 'EPISODE' " +
            "AND itemId IN (SELECT id FROM episodes WHERE seriesId = :seriesId) " +
            "ORDER BY updatedAt DESC LIMIT 1",
    )
    suspend fun lastWatchedEpisodeId(profileId: Long, seriesId: Long): Long?

    /**
     * Drops resume positions orphaned by a re-sync (see FavoriteDao.purgeOrphans). Episodes are
     * excluded — they load lazily, so episode progress is kept and re-attached when the show opens.
     */
    @Query(
        "DELETE FROM playback_progress WHERE " +
            "(mediaType = 'LIVE'   AND itemId NOT IN (SELECT id FROM channels)) OR " +
            "(mediaType = 'MOVIE'  AND itemId NOT IN (SELECT id FROM movies))   OR " +
            "(mediaType = 'SERIES' AND itemId NOT IN (SELECT id FROM series))",
    )
    suspend fun purgeOrphans()
}
