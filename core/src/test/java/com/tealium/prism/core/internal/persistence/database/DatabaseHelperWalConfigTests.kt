package com.tealium.prism.core.internal.persistence.database

import android.app.Application
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.tests.common.getDefaultConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Tests for what [DatabaseHelper.onOpen] makes of a failure while configuring write-ahead logging:
 * a corruption, or a failure that left the database closed, fails the open, and anything else
 * keeps the working database and is recorded in [DatabaseHelper.walConfigFailure].
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseHelperWalConfigTests {

    private lateinit var app: Application
    private lateinit var config: TealiumConfig
    private lateinit var dbName: String
    private lateinit var dbPath: String

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        config = getDefaultConfig(app)
        dbName = "wal-config-${UUID.randomUUID()}.db"
        dbPath = app.getDatabasePath(dbName).absolutePath
    }

    @After
    fun tearDown() {
        app.deleteDatabase(dbName)
    }

    @Test
    @Config(sdk = [23, 34])
    fun getWritableDatabase_RecordsJournalModeWal_When_FileOpens() {
        realHelper(config, dbPath).closing { helper ->
            helper.writableDatabase

            assertEquals("wal", helper.journalModeAtOpen)
            assertNull(helper.walConfigFailure)
            assertNull(helper.initializationFailure)
        }
    }

    @Test
    @Config(sdk = [23, 34])
    fun getWritableDatabase_RecordsJournalModeMemory_When_InMemory() {
        DatabaseHelper(config, databaseName = null).closing { helper ->
            helper.writableDatabase

            assertEquals("memory", helper.journalModeAtOpen)
            assertNull(helper.walConfigFailure)
        }
    }

    /**
     * A WAL pragma that fails without a corruption leaves an intact database, with any setting not
     * applied at the framework's value, so the open succeeds and only the failure is recorded.
     */
    @Test
    fun getWritableDatabase_KeepsTheDatabaseAndRecordsWalConfigFailure_When_ConfigureThrowsNonCorrupt() {
        val boom = SQLiteException("disk I/O error")

        withConfigureWriteAheadLog({ throw boom }) {
            realHelper(config, dbPath).closing { helper ->
                val database = helper.writableDatabase

                assertTrue(database.isOpen)
                assertSame(boom, helper.walConfigFailure)
                assertNull(helper.initializationFailure)
                assertNull(helper.journalModeAtOpen)
            }
        }
    }

    /**
     * A corruption found while configuring WAL must fail the open, with the failure recorded, so
     * that the provider recreates the file instead of using a database the platform may have
     * closed.
     */
    @Test
    fun getWritableDatabase_ThrowsAndRecordsInitializationFailure_When_ConfigureThrowsCorrupt() {
        val boom = SQLiteDatabaseCorruptException("database disk image is malformed")

        withConfigureWriteAheadLog({ throw boom }) {
            realHelper(config, dbPath).closing { helper ->
                val thrown = assertThrows(SQLiteDatabaseCorruptException::class.java) {
                    helper.writableDatabase
                }

                assertSame(boom, thrown)
                assertSame(boom, helper.initializationFailure)
                assertNull(helper.walConfigFailure)
            }
        }
    }

    /**
     * A corruption reported to the error handler while configuring WAL fails the open, even when
     * the handler leaves the database open and the exception that escapes says nothing of it.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getWritableDatabase_ThrowsAndRecordsInitializationFailure_When_ConfigureReportsCorruption() {
        createDatabaseWithLegacyTable(app, dbPath, MARKER_TABLE)
        // the open itself never reads this page
        corruptPage(dbPath, legacyRootPage(dbPath))
        // not a corruption, and the database stays open: only `reported` can fail the open
        val boom = SQLiteException("failure after the report")
        // the helper's recorder still marks the report; this delegate just never closes the database
        val reportsWithoutClosing = DatabaseErrorHandler { }

        withConfigureWriteAheadLog({ db ->
            // a real corrupt read: the platform reports it to the recorder
            runCatching { readLegacyTable(db) }
            assertTrue("the no-op handler must leave the database open", db.isOpen)
            throw boom
        }) {
            realHelper(config, dbPath, errorHandler = reportsWithoutClosing).closing { helper ->
                val thrown = assertThrows(SQLiteException::class.java) {
                    helper.writableDatabase
                }

                assertSame(boom, thrown)
                assertTrue(helper.corruptionReported)
                assertSame(boom, helper.initializationFailure)
                assertNull(helper.walConfigFailure)
            }
        }
    }

    /**
     * A configuration failure that leaves the database closed fails the open, so that a closed
     * handle is never returned.
     */
    @Test
    fun getWritableDatabase_ThrowsAndRecordsInitializationFailure_When_ConfigureClosesTheDatabase() {
        // not a corruption, and nothing reported: only `!db.isOpen` can fail the open
        val boom = IllegalStateException("database not open")

        withConfigureWriteAheadLog({ db -> db.close(); throw boom }) {
            realHelper(config, dbPath).closing { helper ->
                val thrown = assertThrows(IllegalStateException::class.java) {
                    helper.writableDatabase
                }

                assertSame(boom, thrown)
                assertFalse(helper.corruptionReported)
                assertSame(boom, helper.initializationFailure)
                assertNull(helper.walConfigFailure)
            }
        }
    }

    @Test
    fun getWritableDatabase_ClearsWalConfigFailure_When_ReopenedWithoutFailure() {
        realHelper(config, dbPath).closing { helper ->
            withConfigureWriteAheadLog({ throw SQLiteException("disk I/O error") }) {
                helper.writableDatabase
            }
            helper.close()

            helper.writableDatabase

            assertNull(helper.walConfigFailure)
            assertEquals("wal", helper.journalModeAtOpen)
        }
    }
}
