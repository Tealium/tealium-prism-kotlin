package com.tealium.prism.core.internal.persistence.database

import android.app.Application
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.tests.common.getDefaultConfig
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Tests for [FileDatabaseProvider]'s decision about a database file it cannot open: recreate it, run
 * the session in memory, or let the failure through.
 *
 * Which of the three applies depends on what the failure says about the file, not on the exception's
 * place in a type hierarchy, so each test drives one shape of failure out of the helper and asserts
 * which of the three happened - by the path of the database handed back, by whether the file on disk
 * survived, and by what was logged.
 */
@RunWith(RobolectricTestRunner::class)
class FileDatabaseProviderTests {

    private lateinit var app: Application
    private lateinit var config: TealiumConfig
    private lateinit var dbName: String
    private lateinit var dbPath: String
    private lateinit var logger: Logger

    /** Every helper the provider asked for, in order, so the count and each one can be asserted on. */
    private val helpersCreated = mutableListOf<DatabaseHelper>()

    /** How many times the provider fell through to [InMemoryDatabaseProvider]. */
    private var inMemoryFallbacks = 0

    /** Every message logged at error level so far, captured as it happens rather than by verify. */
    private val errorMessages = mutableListOf<String>()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        config = getDefaultConfig(app)
        dbName = "file-provider-${UUID.randomUUID()}.db"
        // an absolute path: DatabaseHelper.deleteDatabase resolves databaseName as a File, which
        // production satisfies since databaseName(config) is absolute
        dbPath = app.getDatabasePath(dbName).absolutePath
        logger = mockk(relaxed = true)
        errorMessages.clear()
        every { logger.error(LogCategory.PERSISTENCE, capture(errorMessages)) } just Runs
    }

    @After
    fun tearDown() {
        app.deleteDatabase(dbName)
    }

    /**
     * A full disk says nothing about the file's contents, so it is kept and the session degrades to
     * in-memory storage - the same file may well open on the next launch.
     */
    @Test
    fun getDatabase_FallsBackToMemory_When_OpenThrowsNonCorruptSQLException() {
        createFileWithMarkerTable()

        providerWith(
            { throwingHelper(SQLiteFullException("database or disk is full")) }
        ).database.use { database ->
            assertInMemory(database, helpers = 1)
            assertTrue("the file must be kept", markerTableExists())
            assertEquals(1, loggedErrors().size)
            assertLogged(FILE_KEPT)
        }
    }

    /**
     * A corrupt `android_metadata` is the one open-time corruption the platform leaves alone on
     * API 23 to 28: the locale step reads it first and wraps the corruption in a bare
     * [SQLiteException], which the platform's own recovery does not recognise, so no error handler
     * runs and the file is kept. Only the provider's cause-chain walk recreates it there. On newer
     * platforms the platform walks the chain itself, wipes the file and retries, so the provider
     * sees a successful open. Either way the session ends up on a fresh file.
     *
     * Pinned to one level either side of AOSP commit 7a8261dce212 (Android 10, API 29), which made
     * the platform recognise this wrapped corruption: on 23 the provider's cause-chain walk
     * recreates the file, on 34 the platform wipes and retries first, with the later API 30 and 33
     * changes to the open sequence included. The exact 28/29 boundary follows from that commit and
     * was verified once across Robolectric SDK 23 to 34 and on API 23 and 37 devices; re-running it
     * on every build would retest old releases.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getDatabase_ReturnsAFreshFile_When_AndroidMetadataIsCorrupt() {
        createFileWithMarkerTable()
        val metadataPage = rootPage(dbPath, ANDROID_METADATA)
        assertNotEquals("page 1 would be the schema case, not android_metadata", 1, metadataPage)
        corruptPage(dbPath, metadataPage)

        providerWith({
            realHelper(config, dbPath, errorHandler = it)
        }).database.use { database ->
            assertOnFile(database)
            assertTrue(database.schemaObjects().containsKey(Schema.ModuleStorageTable.TABLE_NAME))
            // the marker existed only in the corrupt file, so its absence means a new database
            // was created in its place
            assertFalse(database.schemaObjects().containsKey(MARKER_TABLE))
            // onCreate ran during this open: the schema was built on an empty file, not carried
            // over from an existing one (which would leave oldVersion at -1) or upgraded (> 0)
            assertEquals(0, helpersCreated.last().oldVersion)

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // API 23-28: the platform keeps the file, so the provider's walk recreates it
                assertEquals(2, helpersCreated.size)
                assertNotLogged(NO_DELETE)
                assertLogged(
                    RECREATING, "the file is corrupt", "Failed to change locale",
                    "SQLiteDatabaseCorruptException"
                )
            } else {
                // API 29+: the platform wipes and retries before the provider sees anything
                assertEquals(1, helpersCreated.size)
                val messages = loggedErrors()
                assertTrue(
                    "expected the error handler's corruption report in $messages",
                    messages.any { it.contains("corruption", ignoreCase = true) }
                )
                assertNotLogged(RECREATING)
            }
        }
    }

    /**
     * The same walk applies underneath a [DatabaseUpgradeException]: an upgrade step that failed *because*
     * the file is corrupt is a corrupt file, not an upgrade step that a later SDK version could retry.
     */
    @Test
    fun getDatabase_RecreatesFile_When_UpgradeExceptionWrapsCorruption() {
        createFileWithMarkerTable()
        val corrupt = DatabaseUpgradeException(
            DatabaseHelper.DATABASE_VERSION + 1,
            SQLiteDatabaseCorruptException()
        )

        providerWith(
            { throwingHelper(corrupt) },
            { realHelper(config, dbPath, errorHandler = it) }
        ).database.use { database ->
            assertOnFile(database, helpers = 2)
            verify(exactly = 1) { helpersCreated[0].deleteDatabase() }
            assertFalse(database.schemaObjects().containsKey(MARKER_TABLE))
        }
    }

    /**
     * An upgrade step that failed for its own reasons leaves a file that is intact and still meaningful:
     * deleting it would throw away data a later SDK version may be able to upgrade, so the file stays
     * and only this session is degraded. The log has to name the version, since that is what identifies
     * the step that failed.
     */
    @Test
    fun getDatabase_FallsBackToMemory_When_UpgradeFailsWithoutCorruption() {
        val nextVersion = DatabaseHelper.DATABASE_VERSION + 1
        createFileWithMarkerTable()
        val failing = upgradeStep(nextVersion) { throw IllegalStateException("step exploded") }

        providerWith({
            realHelper(config, dbPath, failing, errorHandler = it)
        }).database.use { database ->
            assertInMemory(database, helpers = 1)
            assertTrue("the file must be kept for a later SDK version", markerTableExists())
            assertEquals(DatabaseHelper.DATABASE_VERSION, versionOnDisk())

            assertLogged("version $nextVersion")
            // the step's own exception, from the `Caused by:` lines of the appended stack trace
            assertLogged("IllegalStateException", "step exploded")
            assertTrue(
                "expected the log to say the file is kept",
                fallbackMessage(loggedErrors()).contains(FILE_KEPT)
            )
        }
    }

    /**
     * Recreation is one attempt. If the file the provider just created will not open either, the
     * condition was never the old file's contents, so deleting again would achieve nothing.
     */
    @Test
    fun getDatabase_FallsBackToMemory_When_RecreatedFileAlsoFails() {
        createFileWithMarkerTable()

        providerWith(
            { throwingHelper(SQLiteDatabaseCorruptException()) },
            { throwingHelper(SQLiteFullException("database or disk is full")) }
        ).database.use { database ->
            assertInMemory(database, helpers = 2)
            verify(exactly = 1) { helpersCreated[0].deleteDatabase() }
            verify(exactly = 0) { helpersCreated[1].deleteDatabase() }
        }
    }

    /**
     * A delete that does not take effect leaves the same unusable file in place, so the reopen
     * refuses it exactly as the first open did. That still says only that the file cannot be used,
     * so the session degrades to in-memory storage rather than the instance failing.
     */
    @Test
    fun getDatabase_FallsBackToMemory_When_FileCannotBeDeletedAndStaysUnsupported() {
        createFileWithMarkerTable()
        val unsupported = UnsupportedSchemaVersionException(
            DatabaseHelper.DATABASE_VERSION + 1,
            DatabaseHelper.DATABASE_VERSION,
            UnsupportedSchemaVersionException.Direction.DOWNGRADE
        )

        providerWith({
            throwingHelper(unsupported).also { every { it.deleteDatabase() } returns false }
        }).database.use { database ->
            assertInMemory(database, helpers = 2)
            verify(exactly = 1) { helpersCreated[0].deleteDatabase() }
            verify(exactly = 0) { helpersCreated[1].deleteDatabase() }

            // the recreation attempt, the delete that did not take, and the fallback
            assertEquals(3, loggedErrors().size)
            assertLogged(NO_DELETE)
        }
    }

    /**
     * An [IllegalStateException] - the helper's recursion guard, say - comes from outside the
     * upgrade engine and outside any upgrade step, so it says nothing about the file. Degrading to
     * in-memory storage would hide it, so it propagates.
     */
    @Test
    fun getDatabase_Propagates_When_HelperThrowsIllegalStateException() {
        createFileWithMarkerTable()
        val boom = IllegalStateException("getDatabase called recursively")
        val provider = providerWith({ throwingHelper(boom) })

        val thrown = assertThrows(IllegalStateException::class.java) { provider.database }

        assertSame(boom, thrown)
        assertEquals(0, inMemoryFallbacks)
        assertEquals(1, helpersCreated.size)
        assertTrue(markerTableExists())
    }

    /**
     * The platform recovers from a corrupt file on its own - it hands the file to the
     * [android.database.DatabaseErrorHandler], which deletes it, then retries the open exactly once -
     * so the SDK sees a fresh empty database and an `onCreate` call, with no exception at all.
     * [LoggingDatabaseErrorHandler] exists so that recovery is not silent.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getDatabase_LogsThroughErrorHandler_When_PlatformWipesCorruptFile() {
        createFileWithMarkerTable()
        corruptSchemaPage(dbPath)

        providerWith({
            realHelper(config, dbPath, errorHandler = it)
        }).database.use { database ->
            assertOnFile(database, helpers = 1)

            // onCreate ran on the file the platform recreated
            assertEquals(DatabaseHelper.DATABASE_VERSION, database.version)
            assertTrue(database.schemaObjects().containsKey(Schema.ModuleStorageTable.TABLE_NAME))
            assertFalse(database.schemaObjects().containsKey(MARKER_TABLE))

            val messages = loggedErrors()
            assertTrue(
                "expected a corruption report in $messages",
                messages.any { it.contains("corruption", ignoreCase = true) }
            )
        }
    }

    /**
     * The platform's recovery from a corrupt file does not make a later, unrelated failure in the
     * same open a corruption: the retry produced a fresh file, and `onCreate` failing on it for a
     * full disk says nothing about its contents. The file is kept and the session runs in memory,
     * rather than the provider deleting and recreating it.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getDatabase_FallsBackToMemory_When_PlatformRecoversAndOnCreateFailsForAnotherReason() {
        createFileWithMarkerTable()
        corruptSchemaPage(dbPath)

        providerWith({
            FullDiskOnCreateHelper(config, dbPath, it)
        }).database.use { database ->
            assertInMemory(database, helpers = 1)
            assertTrue("the file must be kept", File(dbPath).exists())

            val fallback = fallbackMessage(loggedErrors())
            assertTrue(fallback, fallback.contains("the file could not be opened"))
            assertTrue(fallback, fallback.contains(FILE_KEPT))
            assertNotLogged(RECREATING)
        }
    }

    /**
     * A registered chain with a gap is a bug in this SDK version, not in the file: the file is kept
     * at its old version for a release with a fixed chain, and this session runs in memory.
     */
    @Test
    fun getDatabase_FallsBackToMemory_When_UpgradeChainHasAGap() {
        val current = DatabaseHelper.DATABASE_VERSION
        createFileWithMarkerTable()
        val skipsAVersion = upgradeStep(current + 2)

        providerWith({
            realHelper(config, dbPath, skipsAVersion, errorHandler = it)
        }).database.use { database ->
            assertInMemory(database, helpers = 1)
            assertTrue("the file must be kept for a fixed release", markerTableExists())
            assertEquals(current, versionOnDisk())

            assertLogged("version ${current + 1}", "not contiguous")
        }
    }

    /**
     * A corruption on a page the open sequence never reads is found by the upgrade step itself.
     * The platform's error handler still fires - the step's query reports it - and closes and
     * deletes the file. What then reaches the provider is not the step's exception but the
     * [IllegalStateException] `SQLiteOpenHelper` throws when it ends its transaction on the closed
     * handle. The provider routes on the platform's report, which the helper recorded, and logs the
     * step's [DatabaseUpgradeException] - with the corruption as its cause - as the reason.
     *
     * Staged at [DatabaseHelper.DATABASE_VERSION] and opened one above it, because a file below
     * [DatabaseHelper.MIN_SUPPORTED_VERSION] is rejected before any step runs.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getDatabase_RecreatesFile_When_UpgradeStepReadsCorruptPage() {
        val nextVersion = DatabaseHelper.DATABASE_VERSION + 1
        createDatabaseWithLegacyTable(app, dbPath, MARKER_TABLE)
        corruptPage(dbPath, legacyRootPage(dbPath))
        var stepRan = false
        val step = upgradeStep(nextVersion) { db ->
            stepRan = true
            readLegacyTable(db)
            db.execSQL("DROP TABLE $LEGACY_TABLE")
        }

        providerWith({
            realHelper(config, dbPath, step, errorHandler = it)
        }).database.use { database ->
            assertOnFile(database, helpers = 2)
            assertTrue("the step must be the first reader of the corrupt page", stepRan)

            // onCreate ran on a new file, not the upgrade on the old one
            val schema = database.schemaObjects()
            assertTrue(schema.containsKey(Schema.ModuleStorageTable.TABLE_NAME))
            assertFalse(schema.containsKey(LEGACY_TABLE))
            assertFalse(schema.containsKey(MARKER_TABLE))
            assertEquals(nextVersion, database.version)

            val messages = loggedErrors()
            assertTrue(
                "expected the error handler's corruption report in $messages",
                messages.any { it.contains("corruption", ignoreCase = true) }
            )
            assertLogged(
                "the upgrade to version $nextVersion hit a corrupt file",
                "SQLiteDatabaseCorruptException"
            )
            // the platform deleted the file, so the provider's own delete finding nothing is expected
            assertNotLogged(NO_DELETE)
        }
    }

    /**
     * A step that catches the corruption and rethrows without keeping it as the cause leaves nothing
     * in the exception to classify. The platform's report, recorded by the helper, still says the
     * file was corrupt and is already gone, so the provider recreates it.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getDatabase_RecreatesFile_When_StepCatchesCorruptionAndRethrowsWithoutCause() {
        val nextVersion = DatabaseHelper.DATABASE_VERSION + 1
        createDatabaseWithLegacyTable(app, dbPath, MARKER_TABLE)
        corruptPage(dbPath, legacyRootPage(dbPath))
        var caughtCorruption = false
        val step = upgradeStep(nextVersion) { db ->
            try {
                readLegacyTable(db)
            } catch (e: SQLiteDatabaseCorruptException) {
                caughtCorruption = true
                throw IllegalStateException("x")
            }
        }

        providerWith({
            realHelper(config, dbPath, step, errorHandler = it)
        }).database.use { database ->
            assertOnFile(database, helpers = 2)
            assertTrue("the step's catch block must have run", caughtCorruption)
            assertFalse(database.schemaObjects().containsKey(LEGACY_TABLE))
            assertFalse(database.schemaObjects().containsKey(MARKER_TABLE))

            // the recreate must come from the platform's report, not from a corruption still
            // reachable through the recorded failure's own cause chain
            val recordedFailure = helpersCreated[0].initializationFailure
            assertFalse(
                "expected no corruption in the recorded failure's cause chain, was $recordedFailure",
                hasCorruptionInCauseChain(recordedFailure)
            )

            assertLogged("hit a corrupt file")
            assertNotLogged(NO_DELETE)
        }
    }

    /**
     * The masking in [getDatabase_RecreatesFile_When_UpgradeStepReadsCorruptPage], without a
     * corrupt file: the step closes the database itself, as the platform's error handler would, so
     * `SQLiteOpenHelper`'s `endTransaction` throws on the closed handle and replaces the step's
     * exception. The provider still sees the corruption through what the helper recorded.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getDatabase_RecreatesFile_When_StepClosesDatabaseAndThrowsCorruption() {
        val nextVersion = DatabaseHelper.DATABASE_VERSION + 1
        createFileWithMarkerTable()

        providerWith(
            closingStepHelper(nextVersion, SQLiteDatabaseCorruptException("page unreadable"))
        ).database.use { database ->
            assertOnFile(database, helpers = 2)
            assertFalse(database.schemaObjects().containsKey(MARKER_TABLE))
            assertTrue(database.schemaObjects().containsKey(Schema.ModuleStorageTable.TABLE_NAME))

            assertLogged(
                RECREATING, "upgrade to version $nextVersion",
                "SQLiteDatabaseCorruptException", "page unreadable"
            )
        }
    }

    /**
     * The same masking with a step that failed for its own reasons: what the helper recorded is an
     * upgrade step failure without a corruption, so the file is kept for a later SDK version rather
     * than the masking [IllegalStateException] propagating.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getDatabase_FallsBackToMemory_When_StepClosesDatabaseAndThrowsNonCorruptException() {
        val nextVersion = DatabaseHelper.DATABASE_VERSION + 1
        createFileWithMarkerTable()

        providerWith(
            closingStepHelper(nextVersion, IllegalStateException("step exploded"))
        ).database.use { database ->
            assertInMemory(database, helpers = 1)
            assertTrue("the file must be kept for a later SDK version", markerTableExists())
            assertEquals(DatabaseHelper.DATABASE_VERSION, versionOnDisk())

            assertLogged("version $nextVersion", "step exploded")
        }
    }

    /**
     * A corruption the platform reported during the open means it has already closed and deleted
     * the file, whatever then escaped - here an [IllegalStateException] with nothing recorded as
     * the helper's own failure. The provider recreates the file, and its delete finding nothing is
     * expected rather than reported.
     */
    @Test
    fun getDatabase_RecreatesFile_When_CorruptionReportedWithoutUpgradeException() {
        // no file: the platform's handler has already deleted it

        providerWith(
            { recordingHelper(IllegalStateException("closed handle"), corruptionReported = true) },
            { realHelper(config, dbPath, errorHandler = it) }
        ).database.use { database ->
            assertOnFile(database, helpers = 2)

            assertLogged(RECREATING, "the platform found the file corrupt while opening it")
            assertNotLogged(NO_DELETE)
        }
    }

    /**
     * A corruption found while configuring write-ahead logging fails the open rather than leaving
     * the session on a database the platform may have closed. The provider recreates the file, and
     * the new file is configured as usual: open, on disk and in WAL.
     */
    @Test
    fun getDatabase_RecreatesFile_When_ConfiguringWalFindsCorruption() {
        createFileWithMarkerTable()
        val boom = SQLiteDatabaseCorruptException("database disk image is malformed")

        var calls = 0
        withConfigureWriteAheadLog({ if (calls++ == 0) throw boom else callOriginal() }) {
            providerWith({
                realHelper(config, dbPath, errorHandler = it)
            }).database.use { database ->
                assertOnFile(database, helpers = 2)
                assertTrue(database.isOpen)
                assertEquals("wal", database.readJournalMode())
                assertEquals(2, calls)
                assertFalse(database.schemaObjects().containsKey(MARKER_TABLE))

                assertLogged(RECREATING, "the file is corrupt", "SQLiteDatabaseCorruptException")
                assertNotLogged(NO_DELETE)
                assertNotLogged(WAL_NOT_CONFIGURED)
                assertNotLogged(WAL_NOT_ENABLED)
            }
        }
    }

    /**
     * A WAL pragma that fails without a corruption keeps the file, with any setting not applied at
     * the framework's value, and the provider reports what the helper recorded.
     */
    @Test
    fun getDatabase_LogsWalConfigFailure_When_ConfiguringWalFailsWithoutCorruption() {
        withConfigureWriteAheadLog({ throw SQLiteException("disk I/O error") }) {
            providerWith({
                realHelper(config, dbPath, errorHandler = it)
            }).database.use { database ->
                assertOnFile(database, helpers = 1)
                assertTrue(database.isOpen)

                assertEquals(1, loggedErrors().size)
                assertLogged(WAL_NOT_CONFIGURED, "disk I/O error")
            }
        }
    }

    /**
     * A file that opened outside WAL works, on the rollback journal, but is slower; the provider
     * reports the mode the helper read.
     */
    @Test
    fun getDatabase_LogsJournalMode_When_FileIsNotInWal() {
        withConfigureWriteAheadLog({ "delete" }) {
            providerWith({
                realHelper(config, dbPath, errorHandler = it)
            }).database.use { database ->
                assertOnFile(database, helpers = 1)

                assertEquals(1, loggedErrors().size)
                assertLogged("$WAL_NOT_ENABLED; the database uses journal mode delete.")
            }
        }
    }

    /** The same report follows a successful open of the recreated file, from the new helper. */
    @Test
    fun getDatabase_LogsJournalMode_When_RecreatedFileIsNotInWal() {
        var calls = 0
        withConfigureWriteAheadLog({
            if (calls++ == 0) throw SQLiteDatabaseCorruptException("database disk image is malformed")
            "delete"
        }) {
            providerWith({
                realHelper(config, dbPath, errorHandler = it)
            }).database.use { database ->
                assertOnFile(database, helpers = 2)

                assertLogged(RECREATING, "the file is corrupt")
                assertLogged("$WAL_NOT_ENABLED; the database uses journal mode delete.")
                assertEquals(2, loggedErrors().size)
            }
        }
    }

    /** The in-memory fallback is not the file, so its journal mode is not reported. */
    @Test
    fun getDatabase_DoesNotLogJournalMode_When_FallingBackToMemory() {
        providerWith(
            { throwingHelper(SQLiteFullException("database or disk is full")) }
        ).database.use { database ->
            assertInMemory(database, helpers = 1)

            assertNotLogged(WAL_NOT_ENABLED)
            assertNotLogged(WAL_NOT_CONFIGURED)
        }
    }

    /**
     * An upgrade failure on the second open means the old file survived the delete, since a new
     * file would run `onCreate`. Without a corruption report it is still on disk, and the log says
     * so.
     */
    @Test
    fun getDatabase_FallsBackToMemory_When_RecreatedFileFailsUpgradeAgain() {
        val failedAgain = DatabaseUpgradeException(
            DatabaseHelper.DATABASE_VERSION + 1,
            IllegalStateException("step exploded")
        )

        providerWith(
            { throwingHelper(SQLiteDatabaseCorruptException()) },
            {
                recordingHelper(
                    IllegalStateException("closed handle"),
                    initializationFailure = failedAgain
                )
            }
        ).database.use { database ->
            assertInMemory(database, helpers = 2)

            val fallback = fallbackMessage(loggedErrors())
            assertTrue(fallback, fallback.contains("survived deletion"))
            assertTrue(fallback, fallback.contains("failed again"))
            assertTrue(fallback, fallback.contains(FILE_KEPT))
        }
    }

    /**
     * The same repeated upgrade failure, but with the platform reporting a corruption during it:
     * the platform has now deleted the file, so the log must not claim it is kept.
     */
    @Test
    fun getDatabase_FallsBackToMemory_When_RecreatedFileUpgradeHitsCorruption() {
        val corruptAgain = DatabaseUpgradeException(
            DatabaseHelper.DATABASE_VERSION + 1,
            SQLiteDatabaseCorruptException()
        )

        providerWith(
            { throwingHelper(SQLiteDatabaseCorruptException()) },
            {
                recordingHelper(
                    IllegalStateException("closed handle"),
                    corruptionReported = true,
                    initializationFailure = corruptAgain
                )
            }
        ).database.use { database ->
            assertInMemory(database, helpers = 2)

            val fallback = fallbackMessage(loggedErrors())
            assertTrue(fallback, fallback.contains("hit a corrupt file"))
            assertFalse(fallback, fallback.contains(FILE_KEPT))
        }
    }

    /**
     * A corruption the platform reports while opening the recreated file leaves nothing to use:
     * the session runs in memory, and the log does not claim a file that the platform deleted.
     */
    @Test
    fun getDatabase_FallsBackToMemory_When_CorruptionReportedInRecreatedFile() {
        providerWith(
            { throwingHelper(SQLiteDatabaseCorruptException()) },
            { recordingHelper(IllegalStateException("closed handle"), corruptionReported = true) }
        ).database.use { database ->
            assertInMemory(database, helpers = 2)

            val fallback = fallbackMessage(loggedErrors())
            assertTrue(fallback, fallback.contains("the platform found the recreated file corrupt too"))
            assertFalse(fallback, fallback.contains(FILE_KEPT))
        }
    }

    /**
     * A fresh-install file whose `onCreate` cannot run: it already contains a table the schema
     * creates. The failure says nothing about a corruption, so the session falls back to memory
     * and the file - useless as it is - is left alone rather than deleted.
     */
    @Test
    fun getDatabase_FallsBackToMemory_When_OnCreateFails() {
        stageFileWithPreexistingDispatchTable(app, dbName)

        providerWith({
            realHelper(config, dbPath, errorHandler = it)
        }).database.use { database ->
            assertInMemory(database, helpers = 1)

            assertLogged(FILE_KEPT)
            assertTrue(
                "the pre-existing table must still be on disk",
                readOnDisk { it.schemaObjects().containsKey(Schema.DispatchTable.TABLE_NAME) }
            )
        }
    }

    /**
     * A file newer than this SDK cannot be brought back down, so the provider treats it as it does
     * a below-minimum file: delete it and create it fresh at the current version.
     */
    @Test
    fun getDatabase_RecreatesFile_When_FileIsNewer() {
        createEmptyDatabaseAtVersion(app, dbPath, DatabaseHelper.DATABASE_VERSION + 1)

        providerWith({
            realHelper(config, dbPath, errorHandler = it)
        }).database.use { database ->
            assertOnFile(database)
            assertTrue(database.schemaObjects().containsKey(Schema.ModuleStorageTable.TABLE_NAME))
            assertEquals(DatabaseHelper.DATABASE_VERSION, database.version)
        }
    }

    /**
     * A failure on the recreated file's open that came from outside the upgrade engine and outside
     * any upgrade step - nothing recorded, and not one of the storage-shaped exceptions - says
     * nothing about the file either, so it propagates just as it would on the first open.
     */
    @Test
    fun getDatabase_Propagates_When_RecreatedFileThrowsNonStorageException() {
        val boom = IllegalStateException("outside the upgrade engine")

        val provider = providerWith(
            { throwingHelper(SQLiteDatabaseCorruptException()) },
            { throwingHelper(boom) }
        )

        val thrown = assertThrows(IllegalStateException::class.java) { provider.database }

        assertSame(boom, thrown)
        assertEquals(0, inMemoryFallbacks)
        assertEquals(2, helpersCreated.size)
    }

    /**
     * The database is opened once per process lifetime and handed back on every later access,
     * rather than reopened - which would lose track of anything the first open decided, such as
     * whether the session fell back to memory.
     */
    @Test
    fun getDatabase_ReturnsTheSameDatabase_When_AccessedAgain() {
        createFileWithMarkerTable()
        val provider = providerWith({
            realHelper(config, dbPath, errorHandler = it)
        })

        provider.database.use { first ->
            val second = provider.database

            assertSame(first, second)
            assertEquals(1, helpersCreated.size)
        }
    }

    /**
     * The same caching applies after a fallback: a second access must not retry the file or create
     * another helper, which would repeat whatever made the first open fall back to memory.
     */
    @Test
    fun getDatabase_KeepsInMemoryDatabase_When_AccessedAgainAfterFallback() {
        val provider = providerWith(
            { throwingHelper(SQLiteFullException("database or disk is full")) }
        )

        provider.database.use { first ->
            val second = provider.database

            assertSame(first, second)
            assertInMemory(first, helpers = 1)
        }
    }

    /**
     * A file below [DatabaseHelper.MIN_SUPPORTED_VERSION] has no upgrade path - no step is
     * registered to bridge it forward - so the provider treats it as it does a newer file: delete
     * it and create it fresh at the current version, rather than falling back to memory on every
     * launch as a file that fails to upgrade would.
     */
    @Test
    fun getDatabase_RecreatesFile_When_BelowMinSupportedVersion() {
        (1 until DatabaseHelper.MIN_SUPPORTED_VERSION).forEach { staleVersion ->
            app.deleteDatabase(dbName)
            createEmptyDatabaseAtVersion(app, dbPath, staleVersion)

            // a legacy-shaped table, to prove the file really was replaced rather than upgraded
            onDisk(dbPath, SQLiteDatabase.OPEN_READWRITE) { stale ->
                stale.execSQL("CREATE TABLE datalayer (key TEXT PRIMARY KEY, value TEXT);")
                stale.execSQL("INSERT INTO datalayer VALUES ('legacy', 'value');")
            }

            providerWith({
                realHelper(config, dbPath, errorHandler = it)
            }).database.use { database ->
                assertNotEquals(
                    "version $staleVersion fell back to memory", ":memory:", database.path
                )
                assertFalse(database.isReadOnly)
                assertEquals(DatabaseHelper.DATABASE_VERSION, database.version)

                val names = database.schemaObjects().keys
                assertTrue(names.contains(Schema.DispatchTable.TABLE_NAME))
                assertTrue(names.contains(Schema.QueueTable.TABLE_NAME))
                assertTrue(names.contains(Schema.ModuleTable.TABLE_NAME))
                assertTrue(names.contains(Schema.ModuleStorageTable.TABLE_NAME))

                // the legacy table went with the old file
                assertFalse(names.contains("datalayer"))
            }
        }
    }

    /**
     * The common case - a healthy file opening normally - must not log anything: an empty capture
     * list has to mean nothing was logged, not that [loggedErrors] itself failed.
     */
    @Test
    fun getDatabase_LogsNothing_When_FileOpensNormally() {
        createFileWithMarkerTable()

        providerWith({
            realHelper(config, dbPath, errorHandler = it)
        }).database.use { database ->
            assertOnFile(database)
            assertTrue(markerTableExists())

            assertEquals(0, loggedErrors().size)
        }
    }

    // region helpers

    /**
     * A provider whose helper creator yields [creators] in order, reusing the last one once they run
     * out, and whose in-memory fallback is counted. Each creator is given the provider's own error
     * handler, which a real helper opens with so the corruptions the platform reports are logged.
     */
    private fun providerWith(
        vararg creators: (DatabaseErrorHandler) -> DatabaseHelper
    ): FileDatabaseProvider {
        var next = 0
        return FileDatabaseProvider(
            config,
            logger,
            databaseHelperCreator = { handler ->
                val creator = creators[minOf(next, creators.lastIndex)]
                next++
                creator.invoke(handler).also { helpersCreated.add(it) }
            },
            inMemoryDatabaseProvider = { cfg ->
                inMemoryFallbacks++
                InMemoryDatabaseProvider(cfg)
            }
        )
    }

    /**
     * A real helper at [version] whose only step closes the database, as the platform's error
     * handler does on a corruption, and then throws [boom].
     *
     * The step ends `SQLiteOpenHelper`'s transaction before closing. Closing inside it would leak
     * the connection that holds the transaction, and with it the file's lock, which leaves a kept
     * file unreadable for the rest of the test. The helper's own `endTransaction` still runs on the
     * closed handle, so the masking is the same.
     */
    private fun closingStepHelper(
        version: Int,
        boom: Exception
    ): (DatabaseErrorHandler) -> DatabaseHelper {
        val step = upgradeStep(version) { db ->
            db.endTransaction()
            db.close()
            throw boom
        }
        return { handler -> realHelper(config, dbPath, step, errorHandler = handler) }
    }

    /**
     * A helper that fails the way the platform would, on a spy so that [DatabaseHelper.deleteDatabase]
     * can be counted. Only the open is replaced; the delete really does delete.
     */
    private fun throwingHelper(boom: Throwable): DatabaseHelper =
        spyk(ThrowingDatabaseHelper(config, dbPath, boom))

    /**
     * A [throwingHelper] whose records say what a real helper would have recorded by the time
     * [boom] escaped: whether the platform reported a corruption, and the helper's own failure.
     */
    private fun recordingHelper(
        boom: Throwable,
        corruptionReported: Boolean = false,
        initializationFailure: Exception? = null
    ): DatabaseHelper = throwingHelper(boom).also {
        every { it.corruptionReported } returns corruptionReported
        every { it.initializationFailure } returns initializationFailure
    }

    /** Whether [throwable] is a [SQLiteDatabaseCorruptException] or has one anywhere in its cause chain. */
    private fun hasCorruptionInCauseChain(throwable: Throwable?): Boolean =
        generateSequence(throwable) { it.cause }.any { it is SQLiteDatabaseCorruptException }

    /**
     * [db] is the on-disk file, opened without the provider ever falling back to memory - and,
     * when [helpers] is given, that exactly that many helpers were created.
     */
    private fun assertOnFile(db: SQLiteDatabase, helpers: Int? = null) {
        assertEquals(dbPath, db.path)
        assertEquals(0, inMemoryFallbacks)
        if (helpers != null) assertEquals(helpers, helpersCreated.size)
    }

    /**
     * [db] is the in-memory fallback, and the provider fell back to memory exactly once - and,
     * when [helpers] is given, that exactly that many helpers were created.
     */
    private fun assertInMemory(db: SQLiteDatabase, helpers: Int? = null) {
        assertEquals(":memory:", db.path)
        assertEquals(1, inMemoryFallbacks)
        if (helpers != null) assertEquals(helpers, helpersCreated.size)
    }

    /** Every message logged at error level so far. */
    private fun loggedErrors(): List<String> = errorMessages.toList()

    /** Fails unless some captured error message contains every one of [fragments]. */
    private fun assertLogged(vararg fragments: String) {
        val messages = loggedErrors()
        assertTrue(
            "expected a message containing ${fragments.toList()} in $messages",
            messages.any { message -> fragments.all { message.contains(it) } }
        )
    }

    /** Fails if any captured error message contains [fragment]. */
    private fun assertNotLogged(fragment: String) {
        val messages = loggedErrors()
        assertFalse(
            "expected no message containing \"$fragment\" in $messages",
            messages.any { it.contains(fragment) }
        )
    }

    /** The single message saying why the session is running on in-memory storage. */
    private fun fallbackMessage(messages: List<String>): String =
        messages.single { it.startsWith("Using in-memory storage") }

    /**
     * A real database at the current version, carrying a table no version of the schema defines, so
     * that "the file survived" and "the file was replaced" can be told apart.
     */
    private fun createFileWithMarkerTable() {
        realHelper(config, dbPath).closing {
            it.writableDatabase.execSQL("CREATE TABLE $MARKER_TABLE (id INTEGER);")
        }
    }

    private fun markerTableExists(): Boolean = readOnDisk { it.schemaObjects().containsKey(MARKER_TABLE) }

    private fun versionOnDisk(): Int = readOnDisk { it.version }

    private fun <T> readOnDisk(read: (SQLiteDatabase) -> T): T =
        onDisk(dbPath, SQLiteDatabase.OPEN_READONLY, read)

    /**
     * Fails the open the way the platform would, by overriding it rather than by stubbing: the failure
     * then holds however the provider reaches it, including through the spy in [throwingHelper].
     */
    private class ThrowingDatabaseHelper(
        config: TealiumConfig,
        databaseName: String,
        private val boom: Throwable
    ) : DatabaseHelper(config, databaseName) {

        override fun getWritableDatabase(): SQLiteDatabase = throw boom
    }

    private companion object {
        /** What the in-memory fallback's log says when the file is still on disk. */
        const val FILE_KEPT = " The database file is kept."

        /** What the provider logs when it deletes and recreates the database file. */
        const val RECREATING = "Deleting and recreating"

        /** What the provider logs when its own delete found nothing to remove. */
        const val NO_DELETE = "No database file was deleted"

        /** The table the framework creates in every database to record its locale. */
        const val ANDROID_METADATA = "android_metadata"

        /** What the provider logs when a usable file did not get all of its WAL settings. */
        const val WAL_NOT_CONFIGURED = "Unable to apply the write-ahead log settings"

        /** What the provider logs when the file opened in a journal mode other than WAL. */
        const val WAL_NOT_ENABLED = "Unable to enable write-ahead logging"
    }

    // endregion
}
