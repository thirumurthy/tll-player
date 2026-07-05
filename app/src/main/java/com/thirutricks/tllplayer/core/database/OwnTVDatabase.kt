package com.thirutricks.tllplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.thirutricks.tllplayer.core.database.dao.CategoryDao
import com.thirutricks.tllplayer.core.database.dao.ChannelDao
import com.thirutricks.tllplayer.core.database.dao.DownloadDao
import com.thirutricks.tllplayer.core.database.dao.EpgDao
import com.thirutricks.tllplayer.core.database.dao.FavoriteDao
import com.thirutricks.tllplayer.core.database.dao.HistoryDao
import com.thirutricks.tllplayer.core.database.dao.MovieDao
import com.thirutricks.tllplayer.core.database.dao.ProfileDao
import com.thirutricks.tllplayer.core.database.dao.ProgressDao
import com.thirutricks.tllplayer.core.database.dao.SeriesDao
import com.thirutricks.tllplayer.core.database.dao.TvProviderProgramDao
import com.thirutricks.tllplayer.core.database.dao.SourceDao
import com.thirutricks.tllplayer.core.database.entity.CategoryEntity
import com.thirutricks.tllplayer.core.database.entity.ChannelEntity
import com.thirutricks.tllplayer.core.database.entity.ChannelFtsEntity
import com.thirutricks.tllplayer.core.database.entity.DownloadEntity
import com.thirutricks.tllplayer.core.database.entity.EpgChannelEntity
import com.thirutricks.tllplayer.core.database.entity.EpgProgrammeEntity
import com.thirutricks.tllplayer.core.database.entity.EpisodeEntity
import com.thirutricks.tllplayer.core.database.entity.EpisodeFtsEntity
import com.thirutricks.tllplayer.core.database.entity.FavoriteEntity
import com.thirutricks.tllplayer.core.database.entity.MovieEntity
import com.thirutricks.tllplayer.core.database.entity.MovieFtsEntity
import com.thirutricks.tllplayer.core.database.entity.PlaybackProgressEntity
import com.thirutricks.tllplayer.core.database.entity.ProfileEntity
import com.thirutricks.tllplayer.core.database.entity.ProfileSourceCrossRef
import com.thirutricks.tllplayer.core.database.entity.SeasonEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesEntity
import com.thirutricks.tllplayer.core.database.entity.SeriesFtsEntity
import com.thirutricks.tllplayer.core.database.entity.SourceEntity
import com.thirutricks.tllplayer.core.database.entity.WatchHistoryEntity
import com.thirutricks.tllplayer.core.database.entity.TvProviderProgramEntity

@Database(
    entities = [
        // Profiles & sources
        ProfileEntity::class,
        SourceEntity::class,
        ProfileSourceCrossRef::class,
        // Content
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        // User data (profile-scoped)
        FavoriteEntity::class,
        WatchHistoryEntity::class,
        PlaybackProgressEntity::class,
        DownloadEntity::class,
        // Android TV home-screen bookkeeping
        TvProviderProgramEntity::class,
        // EPG
        EpgChannelEntity::class,
        EpgProgrammeEntity::class,
        // FTS (search)
        ChannelFtsEntity::class,
        MovieFtsEntity::class,
        SeriesFtsEntity::class,
        EpisodeFtsEntity::class,
    ],
    version = 4, // v4: unify split v3 schemas (catch-up + Android TV home bookkeeping)
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OwnTVDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sourceDao(): SourceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun progressDao(): ProgressDao
    abstract fun tvProviderProgramDao(): TvProviderProgramDao
    abstract fun downloadDao(): DownloadDao
    abstract fun epgDao(): EpgDao

    companion object {
        const val NAME = "owntv.db"

        /**
         * v1 → v2: drop the foreign key on the EPG tables (standalone EPG sources use ids that
         * aren't in `sources`). EPG data is transient and re-synced, so the tables are recreated
         * empty — everything else (profiles, sources, content, favorites, history) is preserved.
         */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `epg_programmes`")
                db.execSQL("DROP TABLE IF EXISTS `epg_channels`")
                db.execSQL("CREATE TABLE IF NOT EXISTS `epg_channels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `epgChannelId` TEXT NOT NULL, `displayName` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_channels_sourceId` ON `epg_channels` (`sourceId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epg_channels_sourceId_epgChannelId` ON `epg_channels` (`sourceId`, `epgChannelId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `epg_programmes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `epgChannelId` TEXT NOT NULL, `startMs` INTEGER NOT NULL, `stopMs` INTEGER NOT NULL, `title` TEXT NOT NULL, `description` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programmes_epgChannelId_startMs` ON `epg_programmes` (`epgChannelId`, `startMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programmes_sourceId` ON `epg_programmes` (`sourceId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programmes_stopMs` ON `epg_programmes` (`stopMs`)")
            }
        }

        /**
         * v2 → v3:
         * - add catch-up/archive columns to `channels` (pure additive ALTERs with defaults)
         * - add Android TV provider bookkeeping for Watch Next / Continue Watching rows
         */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchup` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupDays` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupSource` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tv_provider_programs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, " +
                        "`surface` TEXT NOT NULL, " +
                        "`mediaType` TEXT NOT NULL, " +
                        "`groupId` INTEGER NOT NULL, " +
                        "`targetItemId` INTEGER NOT NULL, " +
                        "`providerProgramId` INTEGER, " +
                        "`lastPositionMs` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`lastEngagementAt` INTEGER NOT NULL, " +
                        "`lastPublishedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON DELETE CASCADE" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tv_provider_programs_profileId` ON `tv_provider_programs` (`profileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tv_provider_programs_profileId_surface_mediaType_groupId` ON `tv_provider_programs` (`profileId`, `surface`, `mediaType`, `groupId`)")
            }
        }

        /**
         * v3 → v4: v3 existed in the wild in two incompatible variants (catch-up vs Android TV home
         * bookkeeping). v4 unifies them by ensuring BOTH the catch-up columns and the provider table
         * exist, regardless of which v3 a user has.
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Channels catch-up columns (skip if already present).
                if (!hasColumn(db, "channels", "catchup")) {
                    db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchup` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "channels", "catchupDays")) {
                    db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupDays` INTEGER NOT NULL DEFAULT 0")
                }
                if (!hasColumn(db, "channels", "catchupSource")) {
                    db.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupSource` TEXT")
                }

                // Android TV provider bookkeeping table (safe to run repeatedly).
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tv_provider_programs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileId` INTEGER NOT NULL, " +
                        "`surface` TEXT NOT NULL, " +
                        "`mediaType` TEXT NOT NULL, " +
                        "`groupId` INTEGER NOT NULL, " +
                        "`targetItemId` INTEGER NOT NULL, " +
                        "`providerProgramId` INTEGER, " +
                        "`lastPositionMs` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, " +
                        "`lastEngagementAt` INTEGER NOT NULL, " +
                        "`lastPublishedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) ON DELETE CASCADE" +
                        ")",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tv_provider_programs_profileId` ON `tv_provider_programs` (`profileId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tv_provider_programs_profileId_surface_mediaType_groupId` ON `tv_provider_programs` (`profileId`, `surface`, `mediaType`, `groupId`)")

                // EPG-perf Guide read-index (v4.0.0). Declared on EpgProgrammeEntity, so v4 expects it; older
                // DBs (and the runtime ensureEpgIndexes) create it too — make sure the migrated DB has it.
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programmes_sourceId_epgChannelId` ON `epg_programmes` (`sourceId`, `epgChannelId`)")
            }
        }

        private fun hasColumn(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String, column: String): Boolean {
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
                return false
            }
        }
    }
}
