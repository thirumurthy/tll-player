package com.thirutricks.tllplayer.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.thirutricks.tllplayer.core.model.MediaType
import com.thirutricks.tllplayer.core.tv.TvProviderSurface

/**
 * Tracks rows mirrored into Android TV provider tables so we can refresh or delete them later.
 *
 * `groupId` stores a stable hash of the best-effort content identity:
 * - movies and live channels use their source/remote/name identity
 * - episodes use the watched episode identity so a continue row can morph into a next-episode
 *   suggestion without changing its local row identity
 */
@Entity(
    tableName = "tv_provider_programs",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "surface", "mediaType", "groupId"], unique = true),
    ],
)
data class TvProviderProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val surface: TvProviderSurface,
    val mediaType: MediaType,
    val groupId: Long,
    val targetItemId: Long,
    val providerProgramId: Long? = null,
    val lastPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastEngagementAt: Long = System.currentTimeMillis(),
    val lastPublishedAt: Long = System.currentTimeMillis(),
)
