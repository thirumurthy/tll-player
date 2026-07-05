package com.thirutricks.tllplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.thirutricks.tllplayer.core.database.entity.ProfileSourceCrossRef
import com.thirutricks.tllplayer.core.database.entity.SourceEntity

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Delete
    suspend fun delete(source: SourceEntity)

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getById(id: Long): SourceEntity?

    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("UPDATE sources SET lastSyncAt = :timestamp WHERE id = :id")
    suspend fun markSynced(id: Long, timestamp: Long)

    // --- profile <-> source links (hybrid model) ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(ref: ProfileSourceCrossRef)

    @Query("DELETE FROM profile_source WHERE profileId = :profileId AND sourceId = :sourceId")
    suspend fun unlink(profileId: Long, sourceId: Long)

    @Query(
        "SELECT s.* FROM sources s " +
            "INNER JOIN profile_source ps ON ps.sourceId = s.id " +
            "WHERE ps.profileId = :profileId ORDER BY s.createdAt ASC",
    )
    fun observeForProfile(profileId: Long): Flow<List<SourceEntity>>

    @Query("SELECT sourceId FROM profile_source WHERE profileId = :profileId")
    suspend fun sourceIdsForProfile(profileId: Long): List<Long>

    @Query("SELECT id FROM sources")
    suspend fun allSourceIds(): List<Long>

    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<SourceEntity>

    @Query("SELECT * FROM profile_source")
    suspend fun allLinks(): List<ProfileSourceCrossRef>

    @Query("DELETE FROM sources")
    suspend fun deleteAllSources()
}
