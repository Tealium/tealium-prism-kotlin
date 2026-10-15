package com.tealium.prism.core.internal.persistence.database

import android.database.sqlite.SQLiteDatabase
import java.util.Locale

/**
 * The row `PRAGMA wal_checkpoint` reports.
 *
 * @param busy `true` when another connection held the log, so the checkpoint did not complete
 * @param log The number of frames in the log, or -1 when the database is not in WAL mode
 * @param checkpointed The number of frames folded back into the database
 */
internal data class CheckpointResult(
    val busy: Boolean,
    val log: Int,
    val checkpointed: Int
)

/**
 * Folds the write-ahead log back into the main database file. A no-op when the database is not in
 * WAL mode.
 *
 * `RESTART`, not `TRUNCATE`: the next writer reuses the `-wal` instead of regrowing it.
 *
 * @return The row `wal_checkpoint` reports.
 * @throws android.database.SQLException if the pragma cannot be run.
 * @throws IllegalStateException if the database is closed.
 */
internal fun DatabaseProvider.checkpoint(): CheckpointResult =
    database.rawQuery("PRAGMA wal_checkpoint(RESTART)", null).use { cursor ->
        cursor.moveToFirst()
        CheckpointResult(
            busy = cursor.getLong(0) != 0L,
            log = cursor.getInt(1),
            checkpointed = cursor.getInt(2)
        )
    }

/**
 * Sets `synchronous = NORMAL`, `journal_size_limit` and `wal_autocheckpoint` when the database is
 * in WAL mode. Called from [DatabaseHelper.onOpen], which decides what a failure means.
 *
 * @return The journal mode read before configuring, lowercased, or `null` if the pragma returned
 * no row; the pragmas were set only if it is `"wal"`.
 * @throws android.database.SQLException if the journal mode cannot be read or a pragma fails.
 * @throws IllegalStateException if the database is closed. When the error handler closed it on a
 * pragma's corruption, that failure is thrown instead, with the [IllegalStateException] attached
 * as suppressed.
 */
internal fun SQLiteDatabase.configureWriteAheadLog(): String? {
    val mode = readJournalMode()
    // NORMAL only under WAL: with the rollback journal it can corrupt the database on power loss.
    if (mode != "wal") return mode

    execSQL("PRAGMA synchronous = NORMAL")
    // Explicit, for parity with iOS: OEM builds can override the framework's values.
    // The transaction pins these to the primary connection, the only one that writes.
    beginTransactionNonExclusive()
    try {
        rawQuery("PRAGMA journal_size_limit = $JOURNAL_SIZE_LIMIT_BYTES", null)
            .use { it.moveToFirst() }
        rawQuery("PRAGMA wal_autocheckpoint = $WAL_AUTOCHECKPOINT_PAGES", null)
            .use { it.moveToFirst() }
    } catch (t: Throwable) {
        // A corruption found by a pragma has had the database closed by the error handler, and
        // endTransaction() then throws: keep the pragma's failure, with that one suppressed.
        // Exception only: a caller may record t rather than rethrow it, so an Error must escape.
        try {
            endTransaction()
        } catch (e: Exception) {
            t.addSuppressed(e)
        }
        throw t
    }
    endTransaction()
    return mode
}

/**
 * The database's `journal_mode`, lowercased, or `null` if the pragma returns no row. Read by
 * [configureWriteAheadLog] before it sets anything.
 *
 * @throws android.database.SQLException if the pragma cannot be run.
 * @throws IllegalStateException if the database is closed.
 */
internal fun SQLiteDatabase.readJournalMode(): String? =
    rawQuery("PRAGMA journal_mode", null).use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0)?.lowercase(Locale.ROOT) else null
    }

internal const val JOURNAL_SIZE_LIMIT_BYTES = 524_288
internal const val WAL_AUTOCHECKPOINT_PAGES = 100
