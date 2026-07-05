package com.thirutricks.tllplayer.di

import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import com.thirutricks.tllplayer.core.database.OwnTVDatabase

/**
 * Provides the Room database (WAL journal mode for fast concurrent reads during large imports) and
 * each DAO. Foreign-key enforcement is on by default in Room.
 *
 * Destructive fallback is enabled while the schema is still evolving (pre-1.0); real migrations
 * arrive before release.
 */
val databaseModule = module {
    single {
        Room.databaseBuilder(androidContext(), OwnTVDatabase::class.java, OwnTVDatabase.NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(OwnTVDatabase.MIGRATION_1_2, OwnTVDatabase.MIGRATION_2_3, OwnTVDatabase.MIGRATION_3_4)
            // Destructive fallback ONLY on a DOWNGRADE (an older APK installed over a newer DB) — never on
            // upgrade. The previous `fallbackToDestructiveMigration(dropAllTables = true)` would silently
            // wipe ALL user data (profiles, favorites, history, downloads) on any schema jump, which is
            // unacceptable near release. If a future schema change lacks a real migration, Room will now
            // throw IllegalStateException at build/runtime instead of destroying data — surfacing the gap.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(db)
                    val now = System.currentTimeMillis()
                    db.execSQL(
                        "INSERT INTO profiles (id, name, avatarColor, avatarId, isKids, pinHash, createdAt) " +
                        "VALUES (1, 'TLL', 0, 0, 0, NULL, $now)"
                    )
                    db.execSQL(
                        "INSERT INTO sources (id, name, type, url, username, password, userAgent, epgUrl, createdAt, lastSyncAt) " +
                        "VALUES (1, 'TLL Source', 'TLL', 'https://tllapp.dpdns.org/tvnexa/v1/admin/channel-pllayer', NULL, NULL, NULL, NULL, $now, NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO profile_source (profileId, sourceId) " +
                        "VALUES (1, 1)"
                    )
                }
            })
            .build()
    }

    single { get<OwnTVDatabase>().profileDao() }
    single { get<OwnTVDatabase>().sourceDao() }
    single { get<OwnTVDatabase>().categoryDao() }
    single { get<OwnTVDatabase>().channelDao() }
    single { get<OwnTVDatabase>().movieDao() }
    single { get<OwnTVDatabase>().seriesDao() }
    single { get<OwnTVDatabase>().favoriteDao() }
    single { get<OwnTVDatabase>().historyDao() }
    single { get<OwnTVDatabase>().progressDao() }
    single { get<OwnTVDatabase>().tvProviderProgramDao() }
    single { get<OwnTVDatabase>().downloadDao() }
    single { get<OwnTVDatabase>().epgDao() }
}
