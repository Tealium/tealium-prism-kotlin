package com.tealium.prism.core.internal.persistence.database

import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.internal.persistence.database.Schema.DispatchTable.CREATE_DISPATCH_TABLE
import com.tealium.prism.core.internal.persistence.database.Schema.DispatchTable.CREATE_INDEX_TIMESTAMP
import com.tealium.prism.core.internal.persistence.database.Schema.QueueTable.CREATE_QUEUE_TABLE
import com.tealium.prism.core.internal.persistence.database.Schema.ModuleStorageTable.CREATE_MODULE_STORAGE_TABLE
import com.tealium.prism.core.internal.persistence.database.Schema.ModuleTable.CREATE_MODULE_TABLE
import com.tealium.prism.core.internal.persistence.database.Schema.QueueTable.CREATE_TRIGGER_REMOVE_PROCESSED_DISPATCHES
import com.tealium.prism.core.internal.persistence.database.upgrades.DatabaseUpgrade
import com.tealium.prism.core.internal.persistence.database.upgrades.Upgrades
import com.tealium.prism.core.internal.persistence.database.UnsupportedSchemaVersionException.Direction.BELOW_MINIMUM
import com.tealium.prism.core.internal.persistence.database.UnsupportedSchemaVersionException.Direction.DOWNGRADE
import java.io.File

/**
 * Opens the SDK's database, creating the current [Schema] on a fresh install and applying
 * [Upgrades] to an existing file.
 */
internal open class DatabaseHelper private constructor(
    private val config: TealiumConfig,
    databaseName: String?,
    version: Int,
    private val upgrades: List<DatabaseUpgrade>,
    private val corruptionRecorder: CorruptionRecordingErrorHandler,
) :
    SQLiteOpenHelper(
        config.application.applicationContext,
        databaseName,
        null,
        version,
        corruptionRecorder
    ) {

    /**
     * @param config The TealiumConfig used to access the Android Context, as well as derive the
     * file-name
     * @param databaseName The name of the database file, or `null` for an in-memory database
     * @param version The schema version to open the database at. Only overridden by tests, so that
     * a file can be opened at a version production never would: above [DATABASE_VERSION] to inject
     * an upgrade step that is expected to fail, or below it to stage a file at an older version and
     * let the real upgrade steps bring it forward.
     * @param upgrades The upgrade steps available. Only overridden by tests.
     * @param errorHandler Handles the corruptions the platform detects and recovers from on its
     * own. Production passes a [LoggingDatabaseErrorHandler] so those recoveries are visible; the
     * default here is the platform's own, which is also what `null` would mean to
     * [SQLiteOpenHelper]. The platform is given it wrapped, so that [corruptionReported] sees every
     * report.
     */
    constructor(
        config: TealiumConfig,
        databaseName: String? = databaseName(config),
        version: Int = DATABASE_VERSION,
        upgrades: List<DatabaseUpgrade> = Upgrades.all,
        errorHandler: DatabaseErrorHandler = DefaultDatabaseErrorHandler(),
    ) : this(
        // resolves to the private constructor: its last parameter's type is more specific
        config,
        databaseName,
        version,
        upgrades,
        CorruptionRecordingErrorHandler(errorHandler)
    )

    /**
     * Stores the old version code of the database.
     *
     * Negative values signify it was neither created, nor updated.
     * Zero value signifies it was created this launch.
     * Positive values indicate the version it was upgraded from.
     */
    var oldVersion: Int = -1

    /**
     * Reports whether or not the database was upgraded this launch.
     */
    val wasUpgraded: Boolean
        get() = oldVersion > 0

    /**
     * The exception thrown during the latest open by [onCreate], or in [onUpgrade] by an upgrade
     * step; `null` otherwise. Cleared on entry to [getWritableDatabase]. The
     * [UnsupportedSchemaVersionException] rejections and the chain check are not recorded: they
     * throw before any SQL runs, so nothing can mask them.
     *
     * Classify this rather than the exception that escapes [getWritableDatabase], because the two
     * can differ: when a step hits a corruption, the platform's default error
     * handler closes and deletes the file, then `SQLiteOpenHelper`'s `finally` calls
     * `endTransaction` on the closed handle, and the [IllegalStateException] that throws replaces
     * the step's own exception.
     */
    var initializationFailure: Exception? = null
        private set

    /**
     * Whether the platform has reported a corruption to this helper's error handler since the
     * flag was last cleared, which happens on entry to [getWritableDatabase], in [onConfigure]
     * and again in [onOpen].
     *
     * A report means the platform has already closed and deleted the file, whatever then escaped
     * the open, so it outranks any exception type.
     *
     * On every API level, the framework's open sequence can fire the handler, wipe the file and
     * retry - as it does for a corrupt header, page count or schema - and it does so before
     * [onConfigure]. If the retry succeeds, the file is fresh and the report is cleared in
     * [onConfigure]; if it fails, [onConfigure] never runs and the flag survives for the caller's
     * catch, meaning the platform deleted the file. So a set flag always means a corruption the
     * platform did not recover from during this open.
     *
     * A report after the open - a statement mid-session finding a corruption - leaves it set until
     * the next call to [getWritableDatabase].
     */
    val corruptionReported: Boolean
        get() = corruptionRecorder.reported

    override fun getWritableDatabase(): SQLiteDatabase {
        initializationFailure = null
        corruptionRecorder.reported = false
        return super.getWritableDatabase()
    }

    override fun onConfigure(db: SQLiteDatabase?) {
        // the platform's wipe-and-retry runs before this, so anything reported so far was
        // recovered from: this open is on a fresh file
        corruptionRecorder.reported = false
        super.onConfigure(db)

        db?.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase?) {
        oldVersion = 0
        db?.let {
            try {
                createCurrentSchema(it)
            } catch (e: Exception) {
                initializationFailure = e
                throw e
            }
        }
    }

    /**
     * Applies each outstanding [DatabaseUpgrade] in order, normalising any failure into a
     * [DatabaseUpgradeException].
     *
     * A file below [MIN_SUPPORTED_VERSION] is rejected before any step runs with
     * [UnsupportedSchemaVersionException]: no upgrade path exists for it. The steps are then
     * checked with [requireContiguousChain].
     *
     * Only an [Exception] from a step is wrapped. An [Error] says something about the process or
     * the build, not about the file, so it must not reach a storage fallback and propagates
     * unwrapped.
     *
     * Steps need no error handling of their own: the transaction [SQLiteOpenHelper] holds around
     * this method rolls the whole upgrade back, and the file stays at the version it came in at.
     */
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        if (oldVersion < MIN_SUPPORTED_VERSION) {
            throw UnsupportedSchemaVersionException(oldVersion, newVersion, BELOW_MINIMUM)
        }

        val steps = Upgrades.since(oldVersion, newVersion, upgrades)
        requireContiguousChain(steps, oldVersion, newVersion)

        db?.let {
            steps.forEach { step ->
                try {
                    step.upgrade(it)
                } catch (e: Exception) {
                    val failure = DatabaseUpgradeException(step.targetVersion, e)
                    initializationFailure = failure
                    throw failure
                }
            }
        }
        this.oldVersion = oldVersion
    }

    /**
     * Requires [steps] to cover every version from [oldVersion] + 1 to [newVersion] exactly once.
     * This guards a gap, a duplicate, or [DATABASE_VERSION] bumped without a step - which would
     * otherwise stamp the new version on an unchanged schema. A broken chain fails like any failed
     * step, before any step runs: the file keeps its old version for a fixed release to upgrade.
     */
    private fun requireContiguousChain(
        steps: List<DatabaseUpgrade>,
        oldVersion: Int,
        newVersion: Int
    ) {
        val expected = ((oldVersion + 1)..newVersion).toList()
        val registered = steps.map { it.targetVersion }
        if (registered == expected) return

        val targetVersion = expected.firstOrNull { v -> registered.count { it == v } != 1 }
            ?: newVersion
        val cause = IllegalStateException(
            "Upgrade chain from $oldVersion to $newVersion is not contiguous: " +
                "expected $expected, registered $registered"
        )
        throw DatabaseUpgradeException(targetVersion, cause)
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // TODO - find a way to downgrade safely.
        throw UnsupportedSchemaVersionException(oldVersion, newVersion, DOWNGRADE)
    }

    /**
     * Clears [corruptionReported]. With the platform's default handler, a report after
     * [onConfigure] makes the open fail before reaching here, so this clear only guards a delegate
     * handler that does not close the database.
     */
    override fun onOpen(db: SQLiteDatabase?) {
        super.onOpen(db)
        corruptionRecorder.reported = false
    }

    /**
     * Closes this helper and deletes the database file, along with its journal, `-shm`, `-wal` and
     * master-journal companions.
     *
     * @return `true` if the file or any of its companions was deleted - which does not guarantee
     * the file itself is gone - and `false` if none was: either there were none, as when the
     * platform's [DatabaseErrorHandler] has already removed them on a corruption, or they could not
     * be deleted, in which case the next open may find the same file. The caller decides what to
     * report.
     */
    fun deleteDatabase(): Boolean {
        close()
        return SQLiteDatabase.deleteDatabase(File(databaseName))
    }

    companion object {

        /**
         * The Database version expected by this version of the SDK.
         */
        const val DATABASE_VERSION = 4

        /**
         * The oldest schema version this SDK will upgrade from. Anything older is deleted and
         * recreated rather than upgraded.
         *
         * Versions 1 and 2 are the *legacy* tealium-kotlin SDK's schema. Prism cannot encounter
         * them: it opens its database under `tealium-prism/{account}/{profile}` (since 0.3.0; 0.1.0
         * and 0.2.0 used the legacy layout, and their files are no longer opened), whereas the
         * legacy SDK used `tealium/{account}/{profile}/{environment}`, and every Prism version
         * before the first upgrade step shipped at version 3. A file presenting version 1 or 2 at
         * Prism's path is therefore not an upgrade path but a corrupt or hand-made file, with no
         * data in it worth preserving.
         *
         * Importing the legacy SDK's data, if it is ever wanted, is a separate mechanism: it reads
         * the legacy file at the legacy path and does not involve this helper's upgrade sequence.
         */
        const val MIN_SUPPORTED_VERSION = 3

        /**
         * Creates the schema as described by [Schema], i.e. the schema at [DATABASE_VERSION].
         *
         * Only ever used for a fresh install; an existing database is brought up to date by
         * [Upgrades] instead.
         */
        private fun createCurrentSchema(db: SQLiteDatabase) {
            db.execSQL(CREATE_DISPATCH_TABLE)
            db.execSQL(CREATE_INDEX_TIMESTAMP)
            db.execSQL(CREATE_QUEUE_TABLE)
            db.execSQL(CREATE_MODULE_TABLE)
            db.execSQL(CREATE_MODULE_STORAGE_TABLE)
            db.execSQL(CREATE_TRIGGER_REMOVE_PROCESSED_DISPATCHES)
        }

        /**
         * Returns a String unique to the Tealium Account/Profile
         *
         */
        fun databaseName(config: TealiumConfig): String {
            return "${config.tealiumDirectory}${File.separatorChar}tealium-${config.accountName}-${config.profileName}.db"
        }
    }

    /**
     * Records that the platform reported a corruption, then hands the report to [delegate], which
     * deals with it as it always would. Adds no failure of its own.
     */
    internal class CorruptionRecordingErrorHandler(
        private val delegate: DatabaseErrorHandler
    ) : DatabaseErrorHandler {

        @Volatile
        var reported: Boolean = false

        override fun onCorruption(db: SQLiteDatabase) {
            reported = true
            delegate.onCorruption(db)
        }
    }
}
