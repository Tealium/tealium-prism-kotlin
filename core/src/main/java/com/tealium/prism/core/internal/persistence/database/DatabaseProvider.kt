package com.tealium.prism.core.internal.persistence.database

import android.database.DatabaseErrorHandler
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.logger.withCause

/**
 * The [DatabaseProvider] should simplify the method of accessing an instance of a [SQLiteDatabase]
 * which would typically be provided by an instance of [SQLiteOpenHelper]. The helper has some
 * important caveats, though, and the [DatabaseProvider] should handle these cases:
 *  - It should only be opened on a non-main Thread to avoid long running upgrades slowing the UI
 *  - If there is an error whilst opening the database file, it should still provide a usable
 *  database where it can, such as an in-memory one, rather than leave the caller without one.
 */
interface DatabaseProvider {
    val database: SQLiteDatabase
}

/**
 * This is the default implementation of [DatabaseProvider] which will write to a persistent file,
 * so all updates to the Database will be available on the next launch.
 *
 * When the file cannot be opened, what to do about it follows from what the failure says about the
 * file, not from the exception's place in a hierarchy:
 *
 * - **The file is unusable and retrying later would not help.**
 *   [UnsupportedSchemaVersionException] in either direction, or a corruption - whether the
 *   platform reported it or it appears anywhere in the cause chain. Delete it, recreate it, open
 *   once more.
 * - **The file is probably fine and a retry might succeed.** A full disk, a locked file, or an
 *   upgrade step that threw for any reason: bad data, a transient error, or a bug in the step,
 *   which this class cannot tell apart. Log, run this session on in-memory storage, keep the file.
 * - **The failure came from outside the engine and outside any upgrade step.** A misused helper,
 *   a bad constructor argument, or any [Error]. It says nothing about the file, so it propagates.
 *
 * Recreation is one attempt: if the freshly created file also fails to open, the session falls back
 * to in-memory storage (or the failure propagates) rather than deleting again.
 *
 * @param config The TealiumConfig used to access the Android Context, as well as derive the
 * file-name
 * @param logger Used to report a failed open, and what was done about it, at error level, as well
 * as a file that opened without write-ahead logging configured
 * @param databaseHelperCreator Function to return the DatabaseHelper, given the error handler to
 * open the file with, which logs the corruptions the platform recovers from on its own. This may be
 * called more than once, in the scenarios where the database file itself needs to be deleted and
 * recreated.
 * @param inMemoryDatabaseProvider Fallback database provider in case this one is unable to access
 * a writeable database file.
 */
internal class FileDatabaseProvider(
    private val config: TealiumConfig,
    private val logger: Logger,
    private val databaseHelperCreator: (DatabaseErrorHandler) -> DatabaseHelper = { handler ->
        DatabaseHelper(config, errorHandler = handler)
    },
    private val inMemoryDatabaseProvider: (TealiumConfig) -> InMemoryDatabaseProvider = ::InMemoryDatabaseProvider
) : DatabaseProvider {

    private val errorHandler = LoggingDatabaseErrorHandler(logger)

    private var databaseHelper: DatabaseHelper = databaseHelperCreator.invoke(errorHandler)
    private var _database: SQLiteDatabase? = null

    /**
     * The database for this session, opened on first access and cached for the process lifetime.
     *
     * TODO: after a statement-time corruption the platform's error handler closes and deletes the
     *  file, but this keeps returning the closed handle, so every later query on it fails.
     *  Recovering - discarding the cached handle and reopening, or moving the session to in-memory
     *  storage - needs its own change. [DatabaseHelper.corruptionReported] is set by such a report.
     */
    override val database: SQLiteDatabase
        get() {
            return _database ?: openDatabase(::recoverFromOpenFailure).also { db -> _database = db }
        }

    /**
     * Opens the file through the current [databaseHelper], handing a failed open to [recover]:
     * [recoverFromOpenFailure] on first access, [recoverFromRecreateFailure] after a recreate. The
     * result is the file-backed database, or the in-memory database when recovery ends in
     * [fallBackToMemory].
     *
     * A successful open is followed by [logIfWalDegraded]. What [recover] returns is not logged
     * here: it either came through this function again, as a recreated file, or is the in-memory
     * fallback.
     */
    private inline fun openDatabase(recover: (Exception) -> SQLiteDatabase): SQLiteDatabase {
        val database = try {
            databaseHelper.writableDatabase
        } catch (e: Exception) {
            return recover(e)
        }
        logIfWalDegraded()
        return database
    }

    /**
     * Logs a file that opened without write-ahead logging, or without all of its WAL settings,
     * from what [databaseHelper] recorded during the open. Neither case is a failed open: the file
     * works, in another journal mode or with some settings left at the framework's values.
     */
    private fun logIfWalDegraded() {
        val walConfigFailure = databaseHelper.walConfigFailure
        val journalMode = databaseHelper.journalModeAtOpen
        val message = when {
            walConfigFailure != null ->
                ("Unable to apply the write-ahead log settings; any setting not applied keeps " +
                    "the framework's value.").withCause(walConfigFailure)
            journalMode != "wal" ->
                "Unable to enable write-ahead logging; the database uses journal mode $journalMode."
            else -> return
        }
        logger.error(LogCategory.PERSISTENCE, message)
    }

    /**
     * Decides what a failed first open means for the file: recreate it, keep it and run this
     * session on in-memory storage, or rethrow [e], the exception that escaped the open. Only
     * failures that say something about the file are handled here; the two corruption signals are
     * explained where they are checked.
     *
     * The failure classified is [DatabaseHelper.initializationFailure] when the helper recorded
     * one, and [e] if not: the two differ when `SQLiteOpenHelper`'s `endTransaction` on a closed
     * handle throws an [IllegalStateException] over the step's own exception.
     *
     * Any other exception - such as an [IllegalStateException] from the helper's recursion guard -
     * arose outside the upgrade engine and outside any upgrade step, so it propagates unless a
     * corruption is in its cause chain: degrading storage would hide a condition this class knows
     * nothing about.
     */
    private fun recoverFromOpenFailure(e: Exception): SQLiteDatabase {
        val failure = databaseHelper.initializationFailure ?: e
        val corruptionReported = databaseHelper.corruptionReported

        // Corruption: the file is unusable, so it is deleted and recreated.
        // - The report: the platform has already closed and deleted the file, whatever escaped;
        //   this covers a corruption in the framework's own SQL inside the open's transaction, a
        //   step that rethrew without the cause, and endTransaction masking it.
        // - The cause walk: a corruption that never reached the platform's error handler.
        if (corruptionReported || failure.hasCorruptCause()) {
            val reason = when {
                // The upgrade failed because the file is corrupt, not because of the step, so
                // there is nothing for a later SDK version to retry. The step's own SQL normally
                // raises both signals
                failure is DatabaseUpgradeException ->
                    "the upgrade to version ${failure.targetVersion} hit a corrupt file"

                corruptionReported -> "the platform found the file corrupt while opening it"

                // In practice the API 23-28 corrupt android_metadata table, wrapped in a plain
                // SQLiteException
                else -> "the file is corrupt"
            }
            return recreate(reason, failure, deletedByPlatform = corruptionReported)
        }

        // No corruption
        return when (failure) {
            is UnsupportedSchemaVersionException ->
                recreate("the file's schema version is not supported", failure)

            is DatabaseUpgradeException -> fallBackToMemory(
                "the upgrade to version ${failure.targetVersion} failed",
                failure,
                fileKept = true
            )

            is SQLException ->
                fallBackToMemory("the file could not be opened", failure, fileKept = true)

            else -> throw e
        }
    }

    /**
     * Deletes the database file, recreates it from a new helper, and opens it once. A failed open
     * is classified by [recoverFromRecreateFailure].
     *
     * [deletedByPlatform] says the platform already deleted the file on a corruption it
     * detected, so a delete that finds nothing is expected and not logged.
     */
    private fun recreate(
        reason: String,
        cause: Throwable,
        deletedByPlatform: Boolean = false
    ): SQLiteDatabase {
        logger.error(
            LogCategory.PERSISTENCE,
            "Deleting and recreating the database file because $reason. Data in it is lost."
                .withCause(cause)
        )

        val deletedByHelper = databaseHelper.deleteDatabase()
        if (!deletedByHelper && !deletedByPlatform) {
            logger.error(
                LogCategory.PERSISTENCE,
                "No database file was deleted, so the open below may find the same file."
            )
        }

        databaseHelper = databaseHelperCreator.invoke(errorHandler)
        return openDatabase { e ->
            recoverFromRecreateFailure(e, deletedByHelper, deletedByPlatform)
        }
    }

    /**
     * Decides what a failed open after [recreate]'s delete means, rethrowing [e], the exception
     * that escaped the open, when it is none of the cases below.
     *
     * One attempt only: every failure of the second open goes to in-memory storage or propagates,
     * as a second delete would achieve nothing.
     * - [DatabaseUpgradeException] or [UnsupportedSchemaVersionException]: the old file survived
     *   the delete, since a newly created file runs `onCreate`, not `onUpgrade` - whatever the
     *   delete returned, as it reports success when only a companion was removed. A corruption
     *   reported during that repeated upgrade means the platform has now deleted the file.
     * - A corruption the platform reports: it has now deleted the file.
     * - [SQLException]: the file still cannot be opened - a full disk, a directory that cannot be
     *   written, or an old file still there because only a companion was deleted.
     * - Anything else propagates.
     *
     * [deletedByHelper] and [deletedByPlatform] say whether the original file, with its data, may
     * still be on disk; a fresh file created by the failed reopen does not count as kept.
     */
    private fun recoverFromRecreateFailure(
        e: Exception,
        deletedByHelper: Boolean,
        deletedByPlatform: Boolean
    ): SQLiteDatabase {
        val failure = databaseHelper.initializationFailure ?: e
        val corruptionReported = databaseHelper.corruptionReported
        val logAsCorrupt = corruptionReported || failure.hasCorruptCause()

        return when {
            failure is DatabaseUpgradeException -> fallBackToMemory(
                "the file survived deletion and its upgrade to version " +
                    "${failure.targetVersion} " +
                    if (logAsCorrupt) "hit a corrupt file" else "failed again",
                failure,
                // the platform deletes the file when it reports a corruption
                fileKept = !corruptionReported
            )

            corruptionReported -> fallBackToMemory(
                "the platform found the recreated file corrupt too",
                failure,
                fileKept = false
            )

            failure is UnsupportedSchemaVersionException -> fallBackToMemory(
                "the file survived deletion and is still unsupported",
                failure,
                fileKept = true
            )

            failure is SQLException -> fallBackToMemory(
                if (logAsCorrupt) "the file is still corrupt after the attempt to recreate it"
                else "the file could not be opened after the attempt to recreate it",
                failure,
                fileKept = !deletedByHelper && !deletedByPlatform
            )

            else -> throw e
        }
    }

    /**
     * Logs why the file cannot be used and returns the in-memory database from
     * [inMemoryDatabaseProvider]. From the first open the file is left alone, so a later launch -
     * or a later SDK version, in the case of an upgrade step that cannot yet succeed - can use it;
     * [fileKept] says whether that is the case, so the log does not claim a file that is gone.
     */
    private fun fallBackToMemory(
        reason: String,
        cause: Throwable,
        fileKept: Boolean
    ): SQLiteDatabase {
        val file = if (fileKept) " The database file is kept." else ""
        logger.error(
            LogCategory.PERSISTENCE,
            ("Using in-memory storage for this session because $reason.$file " +
                "Nothing stored will survive this process.").withCause(cause)
        )
        return inMemoryDatabaseProvider.invoke(config).database
    }
}

/**
 * This is the fallback implementation of [DatabaseProvider] which will create the database
 * in-memory so all updates will be lost upon exiting the application.
 *
 * @param config The TealiumConfig used to access the Android Context
 */
class InMemoryDatabaseProvider(
    private val config: TealiumConfig,
) : DatabaseProvider {
    private var _database: SQLiteDatabase? = null
    private val databaseHelper: DatabaseHelper by lazy {
        DatabaseHelper(config, databaseName = null)
    }

    override val database: SQLiteDatabase
        get() {
            return _database ?: getInMemoryDatabase().also { db ->
                _database = db
            }
        }

    private fun getInMemoryDatabase(): SQLiteDatabase {
        return databaseHelper.writableDatabase // in-mem assumed writeable
    }
}
