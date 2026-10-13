package com.tealium.prism.core.internal.persistence.database

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.internal.logger.LogCategory

/**
 * Logs every corruption the platform detects, then lets the platform handle it as it always would.
 *
 * Without a handler, [SQLiteOpenHelper][android.database.sqlite.SQLiteOpenHelper] passes `null`,
 * which the framework reads as [DefaultDatabaseErrorHandler]: it deletes the file - the `.db` plus
 * its `-journal`, `-shm`, `-wal` and master-journal companions - and [SQLiteDatabase.openDatabase]
 * retries the open exactly once. The SDK then sees a fresh, empty database and an `onCreate` call,
 * with no exception and no trace other than logcat. The same handler also fires for a corruption
 * detected by a statement later in the session, where the database *is* open, and the default
 * closes it. A report from SQL run inside the open's transaction, by an upgrade step or by
 * `onCreate`, is followed by no retry: the platform has closed and deleted the file, and the open
 * fails.
 *
 * This wrapper exists so both of those are reported through the SDK's own [Logger]. It changes no
 * behaviour: [DefaultDatabaseErrorHandler] is `final`, so it is held as a delegate rather than
 * extended, and the delegate is always called.
 *
 * @param logger The logger to report the corruption through
 * @param delegate The handler that deals with the corruption, the platform's own by default
 */
internal class LoggingDatabaseErrorHandler(
    private val logger: Logger,
    private val delegate: DatabaseErrorHandler = DefaultDatabaseErrorHandler()
) : DatabaseErrorHandler {

    /**
     * Called by the platform on a corruption, both while opening and mid-session.
     *
     * The logging is guarded and the delegate is called outside the guard, so a failure to read the
     * database's own state - it may already be closed - cannot suppress the platform's recovery.
     */
    override fun onCorruption(db: SQLiteDatabase) {
        try {
            logger.error(
                LogCategory.PERSISTENCE,
                "Database corruption detected in '${db.path}' (open: ${db.isOpen}). The platform " +
                    "will delete the file; data in it is lost."
            )
        } catch (e: Exception) {
            // diagnostics must never stand between the platform and its own recovery
        }

        delegate.onCorruption(db)
    }
}
