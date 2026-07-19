package com.thirutricks.tllplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * External-subtitle tables (subtitle plan §11 / Part B Phase 2). Three tables, added together in
 * DB v15 so Phases 3 (local files) and 4 (timing) need no further migration:
 *
 *  - [SubtitleCacheEntity]     — the actual downloaded/imported subtitle files, shared device-wide.
 *  - [SubtitleSelectionEntity] — per profile + movie/episode, the remembered active subtitle.
 *  - [SubtitleTimingEntity]    — per profile + content + exact subtitle, the saved timing offset
 *                                (§8.4: a WEB-DL and a Blu-ray sub for the same title keep separate
 *                                offsets, and switching subtitles never inherits another's offset).
 */

/**
 * One cached external subtitle file. Device-level and profile-agnostic (plan §5.1: "Downloaded
 * subtitle file cache: shared safely at device level") — the same download is reused across
 * profiles. OpenSubtitles rows dedupe on [openSubFileId] (review R3: a cache hit skips /download and
 * spends no quota); local-file rows carry a null [openSubFileId] and are keyed by [cachedPath].
 */
@Entity(
    tableName = "subtitle_cache",
    indices = [
        // NULLs compare distinct in SQLite, so many local rows (null file_id) coexist while every
        // OpenSubtitles file_id stays unique — that uniqueness is what makes the R3 cache-hit safe.
        Index(value = ["openSubFileId"], unique = true),
        Index("lastUsedAt"),
    ],
)
data class SubtitleCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "OPENSUB" or "LOCAL". */
    val source: String,
    /** OpenSubtitles file_id (the /download key); null for local files. */
    val openSubFileId: Long? = null,
    /** ISO language code (e.g. "en"); may be null for a local file of unknown language. */
    val language: String? = null,
    /** Human display language (e.g. "English"); falls back to [language] when unknown. */
    val languageName: String? = null,
    /** OpenSubtitles release name / local file name, shown in results and the active-track label. */
    val releaseName: String? = null,
    /** Subtitle format extension without the dot: "srt", "ass", "ssa", "vtt". */
    val format: String? = null,
    val hearingImpaired: Boolean = false,
    /** File name of the stored subtitle (for the "English — Local file / movie.en.srt" label). */
    val fileName: String,
    /** Absolute path of the managed copy in OwnTV's subtitle cache dir. */
    val cachedPath: String,
    val lastUsedAt: Long,
)

/**
 * The remembered active subtitle for one profile + content item (plan §9). [contentKey] is a stable
 * per-item key (movie or exact episode) that survives re-sync, mirroring [SubtitleCacheEntity] usage
 * downstream. A row with a null [cacheId] and [off] = true means the user explicitly chose Off; no
 * row at all means "no external choice yet" (fall back to normal auto-select).
 */
@Entity(
    tableName = "subtitle_selection",
    primaryKeys = ["profileId", "contentKey"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        // Deleting a cached file nulls the reference; the selection then falls back gracefully (§9).
        ForeignKey(entity = SubtitleCacheEntity::class, parentColumns = ["id"], childColumns = ["cacheId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("profileId"), Index("cacheId")],
)
data class SubtitleSelectionEntity(
    val profileId: Long,
    val contentKey: String,
    /** The chosen cached subtitle, or null when [off] or when the reference was cleared. */
    val cacheId: Long? = null,
    /** True when the user explicitly selected Off (distinct from "never chose"). */
    @ColumnInfo(defaultValue = "0") val off: Boolean = false,
    val updatedAt: Long,
)

/**
 * Saved timing offset for a profile + content item + exact subtitle (plan §8.4). [subtitleKey]
 * identifies the exact subtitle release ("opensub:<file_id>" or "local:<cacheId>"), so two releases
 * for the same title keep independent offsets and switching between them never carries an offset over.
 */
/**
 * Links a cached subtitle to the movie/episode it was downloaded for (subtitle plan §11), added in
 * DB v16. This is what lets OwnTV re-list a title's downloaded subtitles on replay and drive the
 * "Delete subtitles" surfaces (per-item long-press and the Movies/Series delete screen). Many rows
 * per content are allowed (English + Bengali for the same movie); deleting the cached file cascades
 * the link away.
 */
@Entity(
    tableName = "subtitle_link",
    primaryKeys = ["profileId", "contentKey", "cacheId"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = SubtitleCacheEntity::class, parentColumns = ["id"], childColumns = ["cacheId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profileId"), Index("cacheId"), Index(value = ["profileId", "mediaType"])],
)
data class SubtitleLinkEntity(
    val profileId: Long,
    val contentKey: String,
    val cacheId: Long,
    /** "MOVIE" or "SERIES" — the two sections of the delete screen. */
    val mediaType: String,
    /** Display title for the delete list ("Oppenheimer" or "Breaking Bad · S1E1"). */
    val contentTitle: String,
    val addedAt: Long,
)

@Entity(
    tableName = "subtitle_timing",
    primaryKeys = ["profileId", "contentKey", "subtitleKey"],
    foreignKeys = [
        ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profileId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("profileId")],
)
data class SubtitleTimingEntity(
    val profileId: Long,
    val contentKey: String,
    val subtitleKey: String,
    /** Positive = subtitles shown later, negative = earlier (plan §8.2). */
    val offsetMs: Int,
    val updatedAt: Long,
)
