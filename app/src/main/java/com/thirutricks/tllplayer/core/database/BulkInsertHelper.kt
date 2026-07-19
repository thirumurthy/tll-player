package com.thirutricks.tllplayer.core.database

import android.os.SystemClock
import android.util.Log
import androidx.annotation.WorkerThread

class BulkInsertHelper(
    private val db: OwnTVDatabase,
) {
    suspend fun <T> withOptimizedBulkInsert(
        table: String,
        ftsTable: String? = null,
        eligible: Boolean,
        ftsOnly: Boolean = false,
        block: suspend () -> T,
    ): T {
        val state = if (eligible && tableIsEmpty(table)) {
            dropIndexesForBulkInsert(table, ftsTable)
        } else {
            null
        }
        return try {
            block()
        } finally {
            if (state != null) restoreIndexes(state, ftsOnly = ftsOnly)
        }
    }

    @WorkerThread
    fun analyzeTables(vararg tables: String) {
        val sdb = db.openHelper.writableDatabase
        tables.forEach { table ->
            requireKnownAnalyzeTable(table)
            sdb.execSQL("ANALYZE `$table`")
        }
    }

    fun tableIsEmpty(table: String): Boolean {
        requireKnownTable(table)
        val cursor = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`")
        return cursor.use { it.moveToFirst() && it.getLong(0) == 0L }
    }

    private fun dropIndexesForBulkInsert(table: String, ftsTable: String?): BulkIndexState {
        requireKnownTable(table)
        if (ftsTable != null) requireKnownFtsTable(ftsTable)

        val sdb = db.openHelper.writableDatabase
        val indexSqls = mutableListOf<String>()
        sdb.query("SELECT name, sql FROM sqlite_master WHERE type='index' AND tbl_name='$table' AND sql IS NOT NULL").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                val sql = cursor.getString(1)
                if (sql != null && !sql.contains("UNIQUE", ignoreCase = true)) {
                    indexSqls.add(sql)
                    sdb.execSQL("DROP INDEX IF EXISTS `$name`")
                }
            }
        }

        var triggerSql: String? = null
        val triggerName = ftsTable?.let { "room_fts_content_sync_${it}_AFTER_INSERT" }
        if (triggerName != null) {
            sdb.query("SELECT sql FROM sqlite_master WHERE type='trigger' AND name='$triggerName'").use { cursor ->
                if (cursor.moveToFirst()) triggerSql = cursor.getString(0)
            }
            if (triggerSql != null) sdb.execSQL("DROP TRIGGER IF EXISTS `$triggerName`")
        }

        Log.i(TAG, "Dropped ${indexSqls.size} indexes${if (triggerSql != null) " + FTS trigger" else ""} on $table for bulk insert")
        return BulkIndexState(table, ftsTable, indexSqls, triggerSql)
    }

    private fun restoreIndexes(state: BulkIndexState, ftsOnly: Boolean = false) {
        val sdb = db.openHelper.writableDatabase
        val start = SystemClock.elapsedRealtime()
        // Restore the canonical Room-expected set, not the pre-drop snapshot: if the DB was already
        // drifted when the snapshot was taken, the snapshot would preserve the gap and the next
        // migration's schema validation would crash the app.
        val canonical = OwnTVDatabase.EXPECTED_NON_UNIQUE_INDEXES[state.table].orEmpty()
        if (!ftsOnly) {
            canonical.forEach { sdb.execSQL(it) }
        }
        if (state.ftsTable != null) {
            sdb.execSQL("INSERT INTO `${state.ftsTable}`(`${state.ftsTable}`) VALUES('rebuild')")
        }
        if (state.triggerSql != null) sdb.execSQL(state.triggerSql)

        val restoredIndexCount = if (ftsOnly) 0 else canonical.size
        Log.i(TAG, "Restored $restoredIndexCount indexes${if (state.ftsTable != null) " + FTS" else ""} on ${state.table} ms=${SystemClock.elapsedRealtime() - start}")
    }

    private fun requireKnownTable(table: String) {
        require(table in KNOWN_TABLES) { "Unknown table: $table" }
    }

    private fun requireKnownAnalyzeTable(table: String) {
        require(table in KNOWN_ANALYZE_TABLES) { "Unknown table: $table" }
    }

    private fun requireKnownFtsTable(ftsTable: String) {
        require(ftsTable in KNOWN_FTS) { "Unknown FTS table: $ftsTable" }
    }

    private data class BulkIndexState(
        val table: String,
        val ftsTable: String?,
        val indexCreateSqls: List<String>,
        val triggerSql: String?,
    )

    companion object {
        const val CHUNK = 5_000
        const val CHUNK_FRESH = 10_000

        private const val TAG = "BulkInsertHelper"
        private val KNOWN_TABLES = setOf("channels", "movies", "series", "epg_programmes")
        private val KNOWN_ANALYZE_TABLES = KNOWN_TABLES + setOf("categories", "epg_channels")
        private val KNOWN_FTS = setOf("channels_fts", "movies_fts", "series_fts")
    }
}
