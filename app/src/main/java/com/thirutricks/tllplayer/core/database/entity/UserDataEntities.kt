package com.thirutricks.tllplayer.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.thirutricks.tllplayer.core.model.DownloadStatus
import com.thirutricks.tllplayer.core.model.MediaType

/*
 * User data is scoped per profile. `itemId` is the local id of a channel/movie/series/episode and is
 * disambiguated by `mediaType` (it can't be a single foreign key since it points at several tables),
 * so referential cleanup of these rows is handled in the repository layer.
 */

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "mediaType", "itemId"], unique = true),
    ],
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val mediaType: MediaType,
    val itemId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "watch_history",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "watchedAt"]),
        Index(value = ["profileId", "mediaType", "itemId"], unique = true),
    ],
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val mediaType: MediaType,
    val itemId: Long,
    val watchedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playback_progress",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("profileId"),
        Index(value = ["profileId", "mediaType", "itemId"], unique = true),
    ],
)
data class PlaybackProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val mediaType: MediaType,
    val itemId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("profileId"),
        Index("status"),
        Index(value = ["profileId", "mediaType", "itemId"], unique = true),
    ],
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    /** Movies & series episodes only — never LIVE. */
    val mediaType: MediaType,
    val itemId: Long,
    val title: String,
    val posterUrl: String? = null,
    val streamUrl: String,
    val filePath: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
