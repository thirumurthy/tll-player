package com.thirutricks.tllplayer.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.thirutricks.tllplayer.core.model.MediaType

/**
 * A category/folder within a source for a given media type (LIVE / MOVIE / SERIES). The Layer-2 rail
 * is built from these. `remoteId` is the provider's category id (used to dedupe on re-sync).
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "mediaType"]),
        Index(value = ["sourceId", "mediaType", "remoteId"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val mediaType: MediaType,
    val name: String,
    val remoteId: String? = null,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("sourceId"),
        Index("categoryId"),
        Index("name"),
        Index("epgChannelId"),
        Index(value = ["sourceId", "remoteId"], unique = true),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val categoryId: Long? = null,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    /** tvg-id (M3U) / epg_channel_id (Xtream) — links to EPG. */
    val epgChannelId: String? = null,
    val number: Int? = null,
    val remoteId: String? = null,
    val sortOrder: Int = 0,
    /** Catch-up/archive available for this channel (Xtream `tv_archive` / M3U `catchup`). */
    val catchup: Boolean = false,
    /** How many days back the archive goes (Xtream `tv_archive_duration` / M3U `catchup-days`). */
    val catchupDays: Int = 0,
    /** M3U `catchup-source` URL template (with `${start}`/`${timestamp}`/… placeholders). Null for
     *  Xtream, whose timeshift URL is built from the source credentials instead. */
    val catchupSource: String? = null,
)

@Entity(
    tableName = "movies",
    foreignKeys = [
        ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("sourceId"),
        Index("categoryId"),
        Index("name"),
        Index(value = ["sourceId", "remoteId"], unique = true),
    ],
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val categoryId: Long? = null,
    val name: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val durationSecs: Int? = null,
    val plot: String? = null,
    val streamUrl: String,
    val containerExt: String? = null,
    val remoteId: String? = null,
    val addedAt: Long? = null,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "series",
    foreignKeys = [
        ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("sourceId"),
        Index("categoryId"),
        Index("name"),
        Index(value = ["sourceId", "remoteId"], unique = true),
    ],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val categoryId: Long? = null,
    val name: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val remoteId: String? = null,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(entity = SeriesEntity::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("seriesId")],
)
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val seasonNumber: Int,
    val name: String? = null,
    val remoteId: String? = null,
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(entity = SeriesEntity::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SeasonEntity::class, parentColumns = ["id"], childColumns = ["seasonId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index("seriesId"),
        Index("seasonId"),
        Index("name"),
        Index(value = ["seriesId", "remoteId"], unique = true),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val seasonId: Long? = null,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val plot: String? = null,
    val streamUrl: String,
    val durationSecs: Int? = null,
    val containerExt: String? = null,
    val remoteId: String? = null,
)
