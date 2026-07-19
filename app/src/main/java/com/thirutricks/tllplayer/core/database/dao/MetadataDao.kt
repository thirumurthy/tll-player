package com.thirutricks.tllplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.thirutricks.tllplayer.core.database.entity.MetadataCacheEntity
import com.thirutricks.tllplayer.core.database.entity.MetadataMatchEntity

/** DAO for the TMDB enrichment cache (plan §7). Both tables are pure caches. */
@Dao
interface MetadataDao {

    // --- local item → tmdb resolution (incl. negative cache) ---

    @Query("SELECT * FROM metadata_match WHERE localKey = :localKey LIMIT 1")
    suspend fun getMatch(localKey: String): MetadataMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMatch(match: MetadataMatchEntity)

    @Query("DELETE FROM metadata_match WHERE localKey = :localKey")
    suspend fun deleteMatch(localKey: String)

    // --- resolved TMDB metadata ---

    @Query("SELECT * FROM metadata_cache WHERE key = :key LIMIT 1")
    suspend fun getCache(key: String): MetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(entity: MetadataCacheEntity)

    @Query("DELETE FROM metadata_cache WHERE key = :key")
    suspend fun deleteCache(key: String)

    // --- maintenance (manual "clear metadata" / refresh) ---

    @Query("DELETE FROM metadata_cache")
    suspend fun clearCache()

    @Query("DELETE FROM metadata_match")
    suspend fun clearMatches()

    // --- bounded eviction (C4): both tables grow unbounded as the user browses a ~220k-item
    //     catalog. TTL delete rides the existing Index("updatedAt"); rows re-fetch on next focus.
    //     Run off the cold-start path (after a sync completes — ImportFinalizer.finalize). ---

    @Query("DELETE FROM metadata_cache WHERE updatedAt < :cutoff")
    suspend fun evictCacheOlderThan(cutoff: Long): Int

    @Query("DELETE FROM metadata_match WHERE updatedAt < :cutoff")
    suspend fun evictMatchesOlderThan(cutoff: Long): Int
}
