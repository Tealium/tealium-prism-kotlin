package com.tealium.prism.core.internal.persistence.database

import android.app.Application
import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteFullException
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.tests.common.getDefaultConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Tests for [DatabaseHelper.corruptionReported]: set when the platform reports a corruption during an
 * open that then fails, and cleared when the open succeeds - so that a report the platform recovered
 * from on its own is not mistaken for one that ended the open.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseHelperCorruptionStateTests {

    private lateinit var app: Application
    private lateinit var config: TealiumConfig
    private lateinit var dbName: String
    private lateinit var dbPath: String

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        config = getDefaultConfig(app)
        dbName = "corruption-state-${UUID.randomUUID()}.db"
        dbPath = app.getDatabasePath(dbName).absolutePath
    }

    @After
    fun tearDown() {
        app.deleteDatabase(dbName)
    }

    /**
     * A corrupt schema page is found by the framework's own open sequence, which reports it, wipes
     * the file, retries and succeeds - all before `onConfigure`, which clears the flag.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getWritableDatabase_ClearsCorruptionReported_When_PlatformRecoversDuringOpen() {
        realHelper(config, dbPath).closing { it.writableDatabase }
        corruptSchemaPage(dbPath)
        val handler = CountingErrorHandler()

        realHelper(config, dbPath, errorHandler = handler).closing { helper ->
            val database = helper.writableDatabase

            assertEquals("the platform must have reported the corruption", 1, handler.reports)
            assertTrue(database.isOpen)
            assertFalse(helper.corruptionReported)
        }
    }

    /**
     * The platform's wipe-and-retry runs before `onConfigure`, which clears the flag, so an open
     * that recovers from the corruption and then fails in `onCreate` for another reason - a full
     * disk - does not leave the flag set for the provider to mistake for a corruption.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getWritableDatabase_ClearsCorruptionReported_When_PlatformRecoversAndOnCreateFailsForAnotherReason() {
        realHelper(config, dbPath).closing { it.writableDatabase }
        corruptSchemaPage(dbPath)
        val handler = CountingErrorHandler()

        FullDiskOnCreateHelper(config, dbPath, handler).closing { helper ->
            assertThrows(SQLiteFullException::class.java) { helper.writableDatabase }

            assertEquals("the platform must have reported the corruption", 1, handler.reports)
            assertFalse(helper.corruptionReported)
        }
    }

    /**
     * A step that reads a corrupt page makes the platform report it and delete the file, and the
     * open fails, so `onOpen` never runs and the flag is still set for the provider to route on.
     */
    @Test
    @Config(sdk = [23, 34])
    fun getWritableDatabase_LeavesCorruptionReported_When_StepReadsCorruptPage() {
        val nextVersion = DatabaseHelper.DATABASE_VERSION + 1
        createDatabaseWithLegacyTable(app, dbPath, MARKER_TABLE)
        corruptPage(dbPath, legacyRootPage(dbPath))
        val step = upgradeStep(nextVersion) { db -> readLegacyTable(db) }

        realHelper(config, dbPath, step).closing { helper ->
            assertThrows(IllegalStateException::class.java) { helper.writableDatabase }

            assertTrue(helper.corruptionReported)
            assertTrue(helper.initializationFailure is DatabaseUpgradeException)
        }
    }

    // region helpers

    /** Counts the reports it receives and lets the platform's own handler deal with each. */
    private class CountingErrorHandler : DatabaseErrorHandler {
        private val delegate = DefaultDatabaseErrorHandler()
        var reports = 0
            private set

        override fun onCorruption(db: SQLiteDatabase) {
            reports++
            delegate.onCorruption(db)
        }
    }

    // endregion
}
