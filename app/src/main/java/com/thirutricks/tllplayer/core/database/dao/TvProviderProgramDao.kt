package com.thirutricks.tllplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thirutricks.tllplayer.core.database.entity.TvProviderProgramEntity
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.core.tv.TvProviderSurface

@Dao
interface TvProviderProgramDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TvProviderProgramEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TvProviderProgramEntity>)

    @Query(
        "SELECT * FROM tv_provider_programs WHERE profileId = :profileId AND surface = :surface " +
            "AND mediaType = :mediaType AND groupId = :groupId LIMIT 1",
    )
    suspend fun find(profileId: Long, surface: TvProviderSurface, mediaType: MediaType, groupId: Long): TvProviderProgramEntity?

    @Query("SELECT * FROM tv_provider_programs WHERE profileId = :profileId ORDER BY surface ASC, lastPublishedAt DESC")
    suspend fun getAllForProfile(profileId: Long): List<TvProviderProgramEntity>

    @Query("SELECT * FROM tv_provider_programs WHERE profileId = :profileId AND surface = :surface")
    suspend fun getForSurface(profileId: Long, surface: TvProviderSurface): List<TvProviderProgramEntity>

    @Query("DELETE FROM tv_provider_programs WHERE profileId = :profileId AND surface = :surface AND mediaType = :mediaType AND groupId = :groupId")
    suspend fun delete(profileId: Long, surface: TvProviderSurface, mediaType: MediaType, groupId: Long)

    @Query("DELETE FROM tv_provider_programs WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: Long)
}
