package com.tealium.prism.core.internal.persistence.database

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.internal.logger.LogCategory
import java.io.File

/**
 * Lets the platform handle every corruption it detects as it always would, then logs the outcome
 * once.
 *
 * Without a handler, [SQLiteOpenHelper][android.database.sqlite.SQLiteOpenHelper] passes `null`,
 * which the framework reads as [DefaultDatabaseErrorHandler]: it closes the database and deletes
 * the file - the `.db` plus its `-journal`, `-shm`, `-wal` and master-journal companions - and
 * [SQLiteDatabase.openDatabase] retries the open exactly once. The SDK then sees a fresh, empty
 * database and an `onCreate` call, with no exception and no trace other than logcat. The same
 * handler also fires for a corruption detected by a statement later in the session, where the
 * database *is* open, and the default closes it. A report from SQL run inside the open's
 * transaction, by an upgrade step or by `onCreate`, or by the WAL settings applied after it in
 * `onOpen`, is followed by no retry: the platform has closed and deleted the file, and the open
 * fails.
 *
 * This wrapper exists so both of those are reported through the SDK's own [Logger]. It changes no
 * behaviour: [DefaultDatabaseErrorHandler] is `final`, so it is held as a delegate rather than
 * extended, and the delegate is always called. The delegate is called first, so the log can say
 * whether the file is gone.
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
     * The path is read first, as the database is closed afterwards. The delegate runs next, and
     * then the outcome is logged once: that there is no file in memory, that the file was deleted,
     * or that it could not be deleted. The outcome is logged even if the delegate throws, and the
     * exception propagates.
     *
     * Only the outcome for the file is reported, never one for storage: the handler also runs
     * while the helper opens the database, where the provider decides what happens to storage.
     */
    override fun onCorruption(db: SQLiteDatabase) {
        val path = db.path
        try {
            delegate.onCorruption(db)
        } finally {
            logger.error(LogCategory.PERSISTENCE, outcomeMessage(path))
        }
    }

    private fun outcomeMessage(path: String): String = when {
        path == IN_MEMORY_PATH ->
            "Database corruption detected in '$path'. There is no file to delete; data in it " +
                "is lost."
        !File(path).exists() ->
            "Database corruption detected in '$path'. The file was deleted; data in it is lost."
        else ->
            "Database corruption detected in '$path'. The file could not be deleted, so it " +
                "stays as it is."
    }

    private companion object {
        /**
         * The path the platform reports for an in-memory database. The handler is given only the
         * database, so it checks the path, as [DefaultDatabaseErrorHandler] does.
         */
        const val IN_MEMORY_PATH = ":memory:"
    }
}
