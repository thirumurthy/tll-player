package com.thirutricks.tllplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.thirutricks.tllplayer.core.database.entity.DownloadEntity
import com.thirutricks.tllplayer.core.model.DownloadStatus

/** Downloads (movies & series episodes only), per profile. */
@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity): Long

    @Delete
    suspend fun delete(download: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE profileId = :profileId ORDER BY createdAt DESC")
    fun observeForProfile(profileId: Long): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt ASC")
    suspend fun byStatus(status: DownloadStatus): List<DownloadEntity>

    @Query("SELECT COUNT(*) FROM downloads WHERE profileId = :profileId")
    fun count(profileId: Long): Flow<Int>

    @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloaded, totalBytes = :total, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateProgress(id: Long, status: DownloadStatus, downloaded: Long, total: Long, timestamp: Long)
}
