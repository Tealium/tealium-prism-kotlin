package com.tealium.prism.core.internal.persistence.database

import android.app.Application
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.persistence.repositories.SQLQueueRepository
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Coverage for the write-ahead-logging configuration and the checkpoint.
 *
 * These exist because the pragmas are easy to break silently: [DatabaseHelper.onOpen] keeps a
 * working database when they fail for any reason other than a corruption or a closed database, so
 * a broken pragma leaves the settings it did not apply at the framework's values and nothing else
 * fails.
 */
@RunWith(RobolectricTestRunner::class)
class WriteAheadLogTests {

    private lateinit var app: Application
    private lateinit var config: TealiumConfig
    private lateinit var databaseFile: File
    private val openedProviders = mutableListOf<DatabaseProvider>()

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        config = getDefaultConfig(app)
        config.tealiumDirectory.mkdirs()
        databaseFile = File(DatabaseHelper.databaseName(config))
        SQLiteDatabase.deleteDatabase(databaseFile)
    }

    @After
    fun tearDown() {
        openedProviders.forEach { it.database.close() }
        SQLiteDatabase.deleteDatabase(databaseFile)
    }

    @Test
    @Config(sdk = [23, 34])
    fun open_Uses_Wal_Journal_Mode_When_Database_Is_Persistent() {
        val database = openPersistent().database

        assertEquals(
            "A persistent database must use WAL. In the rollback journal every commit syncs " +
                "the journal.",
            "wal",
            database.readJournalMode()
        )
    }

    @Test
    @Config(sdk = [23, 34])
    fun open_Sets_Synchronous_Normal_When_Database_Is_Persistent() {
        val database = openPersistent().database

        // 1 == NORMAL. Under WAL this can only lose the most recent commit on OS crash or power
        // loss, never on app termination, where the log survives and is replayed on next open.
        assertEquals(1L, database.pragmaOnPrimaryConnection("PRAGMA synchronous"))
    }

    @Test
    @Config(sdk = [23, 34])
    fun open_Sets_The_Wal_Limits_When_Database_Is_Persistent() {
        val database = openPersistent().database

        assertEquals(524_288L, database.pragmaOnPrimaryConnection("PRAGMA journal_size_limit"))
        assertEquals(100L, database.pragmaOnPrimaryConnection("PRAGMA wal_autocheckpoint"))
    }

    @Test
    fun configureWriteAheadLog_Sets_The_Wal_Limits_When_The_Connection_Has_Other_Values() {
        // The framework applies the same values at open, so the test above cannot tell whether
        // the SDK set them. Start from other values, as an OEM overlay would, and require the
        // SDK's own pragmas to restore them.
        val database = openPersistent().database
        database.pragmaOnPrimaryConnection("PRAGMA journal_size_limit = 4096")
        database.pragmaOnPrimaryConnection("PRAGMA wal_autocheckpoint = 7")

        database.configureWriteAheadLog()

        assertEquals(524_288L, database.pragmaOnPrimaryConnection("PRAGMA journal_size_limit"))
        assertEquals(100L, database.pragmaOnPrimaryConnection("PRAGMA wal_autocheckpoint"))
    }

    @Test
    fun configureWriteAheadLog_Returns_The_Journal_Mode_When_Database_Is_Persistent() {
        val database = openPersistent().database

        assertEquals("wal", database.configureWriteAheadLog())
    }

    @Test
    fun configureWriteAheadLog_Throws_When_The_Database_Is_Closed() {
        // DatabaseHelper.onOpen decides what a failure means, so nothing may be swallowed here.
        val database = openPersistent().database
        database.close()

        assertThrows(IllegalStateException::class.java) { database.configureWriteAheadLog() }
    }

    @Test
    fun configureWriteAheadLog_Throws_The_Pragma_Failure_When_EndTransaction_Also_Throws() {
        // A failing pragma can close the database, and endTransaction() then throws
        // IllegalStateException over the SQLException.
        val walCursor = mockk<Cursor>(relaxed = true)
        every { walCursor.moveToFirst() } returns true
        every { walCursor.getString(0) } returns "wal"
        val database = mockk<SQLiteDatabase>(relaxed = true)
        every { database.rawQuery("PRAGMA journal_mode", null) } returns walCursor
        every {
            database.rawQuery(match { it.startsWith("PRAGMA journal_size_limit") }, null)
        } throws SQLiteException("disk I/O error")
        every { database.endTransaction() } throws IllegalStateException("database not open")

        val thrown = assertThrows(SQLiteException::class.java) { database.configureWriteAheadLog() }

        assertEquals("disk I/O error", thrown.message)
        assertEquals("database not open", thrown.suppressed.single().message)
        verify(exactly = 1) { database.endTransaction() }
    }

    @Test
    fun configureWriteAheadLog_Throws_The_EndTransaction_Failure_When_The_Pragmas_Succeed() {
        val walCursor = mockk<Cursor>(relaxed = true)
        every { walCursor.moveToFirst() } returns true
        every { walCursor.getString(0) } returns "wal"
        val database = mockk<SQLiteDatabase>(relaxed = true)
        every { database.rawQuery("PRAGMA journal_mode", null) } returns walCursor
        every { database.endTransaction() } throws IllegalStateException("database not open")

        val thrown = assertThrows(IllegalStateException::class.java) {
            database.configureWriteAheadLog()
        }

        assertEquals("database not open", thrown.message)
        verify(exactly = 1) { database.endTransaction() }
    }

    @Test
    @Config(sdk = [23, 34])
    fun open_Does_Not_Fail_On_The_Pragmas_When_Database_Is_In_Memory() {
        // The framework skips WAL for an in-memory database, which answers "memory", so the same
        // configuration path must be safe for both.
        val database = InMemoryDatabaseProvider(config).database

        assertEquals("memory", database.readJournalMode())
    }

    @Test
    fun configureWriteAheadLog_Leaves_Synchronous_Unchanged_When_Not_Wal() {
        // NORMAL is only safe under WAL. In the rollback journal it risks a corrupt database on
        // power loss, so a database that did not reach WAL must keep its sync mode.
        val database = openRollbackJournalDatabase()
        val synchronousBefore = database.pragmaOnPrimaryConnection("PRAGMA synchronous")

        val mode = database.configureWriteAheadLog()

        assertEquals("truncate", mode)
        assertEquals("truncate", database.readJournalMode())
        assertEquals(synchronousBefore, database.pragmaOnPrimaryConnection("PRAGMA synchronous"))
        database.close()
    }

    @Test
    fun storeDispatch_Rows_Survive_A_Clean_Close() {
        val dispatches = createDispatches(20)

        // Closing the last connection checkpoints, so this is the CLEAN close and the -wal is
        // empty by the time we reopen. The unclean case needs a connection that never closes: see
        // open_Reads_Frames_Left_In_The_Wal_When_The_First_Connection_Never_Closed.
        val helper = DatabaseHelper(config)
        val repository = SQLQueueRepository(helper.asProvider(), maxQueueSize = 100)
        repository.storeDispatch(dispatches, setOf(PROCESSOR))
        assertEquals(dispatches.size, repository.size)
        helper.close()

        val reopened = SQLQueueRepository(openPersistent(), maxQueueSize = 100)
        assertEquals(
            "Committed dispatches must survive a clean close and reopen: this is the delivery " +
                "guarantee, and synchronous=NORMAL must not weaken it.",
            dispatches.size,
            reopened.size
        )
    }

    @Test
    @Config(sdk = [23, 34])
    fun open_Reads_Frames_Left_In_The_Wal_When_The_First_Connection_Never_Closed() {
        val dispatches = createDispatches(20)

        // Hold the first connection open for the whole test, so the checkpoint that a close
        // performs never runs. That is the only way an in-process test can leave committed frames
        // sitting in the -wal, which is the state a killed app leaves on disk.
        val leaked = openPersistent()
        // Auto-checkpoint off too, so nothing folds the log back before the assertions.
        leaked.database.pragmaOnPrimaryConnection("PRAGMA wal_autocheckpoint = 0")
        SQLQueueRepository(leaked, maxQueueSize = 100).storeDispatch(dispatches, setOf(PROCESSOR))

        assertTrue(
            "The first connection must still be holding frames in the -wal, otherwise the " +
                "reopen below just reads the main database file and proves nothing about recovery.",
            walSize() > 0
        )

        val reopened = SQLQueueRepository(openPersistent(), maxQueueSize = 100)
        assertEquals(
            "A new connection must read the frames left in the log. Recovery after a process " +
                "kill runs the same mechanism, and stays a manual check.",
            dispatches.size,
            reopened.size
        )
        assertTrue(
            "The rows came out of the log, not out of an already-checkpointed database file: " +
                "the second connection must not have folded it back.",
            walSize() > 0
        )
        assertTrue(leaked.database.isOpen)
    }

    @Test
    @Config(sdk = [23, 34])
    fun checkpoint_Folds_The_Wal_Back_Into_The_Database_And_Restarts_The_Log() {
        val provider = openPersistent()

        // Auto-checkpoint off so the log is guaranteed to hold frames when we assert the
        // precondition below. Without this SQLite folds the log back on its own at the framework's
        // 100 pages, and the test would pass even if checkpoint() did nothing.
        provider.database.pragmaOnPrimaryConnection("PRAGMA wal_autocheckpoint = 0")

        val repository = SQLQueueRepository(provider, maxQueueSize = 500)
        repository.storeDispatch(createDispatches(100), setOf(PROCESSOR))

        // Precondition: there is genuinely something to fold back. This is what stops the
        // assertions below from being vacuous.
        val sizeBeforeCheckpoint = walSize()
        assertTrue(
            "With auto-checkpoint disabled, 100 committed dispatches must leave frames in the " +
                "-wal; otherwise the checkpoint assertions prove nothing.",
            sizeBeforeCheckpoint > 0
        )

        val result = provider.checkpoint()

        assertFalse("Nothing else holds the log, so the checkpoint must complete.", result.busy)
        assertTrue(result.log > 0)
        assertEquals(
            "Every committed frame must be folded back into the main database file, not " +
                "stranded in the log between launches.",
            result.log,
            result.checkpointed
        )
        assertEquals(
            "RESTART keeps the -wal at its working size for the next writer to reuse; " +
                "journal_size_limit bounds it at that writer's reset.",
            sizeBeforeCheckpoint,
            walSize()
        )

        // The data is still there: the checkpoint moved it, it did not discard it.
        assertEquals(100, repository.size)

        // The next writer starts from the beginning of the log. A PASSIVE checkpoint reports the
        // frames the log now holds: only the new write's if the log restarted, or the earlier
        // frames plus the new ones if it did not.
        repository.storeDispatch(listOf(createDispatch("after_checkpoint")), setOf(PROCESSOR))
        val afterWrite = provider.database.rawQuery("PRAGMA wal_checkpoint(PASSIVE)", null)
            .use { cursor ->
                cursor.moveToFirst()
                (0 until cursor.columnCount).map { cursor.getLong(it) }
            }
        assertEquals(3, afterWrite.size)
        assertTrue("The new write must be in the log.", afterWrite[1] > 0)
        assertTrue(
            "After the checkpoint a small write must restart the log from the beginning, not " +
                "append to the frames already folded back.",
            afterWrite[1] < result.log
        )
        assertTrue(
            "A write into a restarted log overwrites the file from the start, so the -wal must " +
                "not grow.",
            walSize() <= sizeBeforeCheckpoint
        )
    }

    @Test
    @Config(sdk = [23, 34])
    fun checkpoint_Does_Not_Throw_When_Database_Is_In_Memory() {
        // A harmless no-op wherever WAL is not in use.
        val result = InMemoryDatabaseProvider(config).checkpoint()

        assertFalse(result.busy)
    }

    @Test
    fun deleteDatabase_Removes_The_Wal_And_Shm_Sidecars() {
        val helper = DatabaseHelper(config)
        helper.writableDatabase
        for (suffix in SIDECARS) {
            assertTrue(
                "Precondition: WAL must have created the $suffix file.",
                File(databaseFile.path + suffix).exists()
            )
        }

        helper.deleteDatabase()

        for (suffix in listOf("") + SIDECARS) {
            val name = if (suffix.isEmpty()) "database" else suffix
            assertFalse(
                "deleteDatabase() must remove the $name file; a stranded sidecar leaves queued " +
                    "event data on disk.",
                File(databaseFile.path + suffix).exists()
            )
        }
    }

    /**
     * Opens the persistent database through the SDK's [DatabaseHelper], with no fallback, so a
     * failed open fails the test instead of passing on an in-memory database.
     */
    private fun openPersistent(): DatabaseProvider =
        DatabaseHelper(config).asProvider().also { openedProviders.add(it) }

    private fun DatabaseHelper.asProvider(): DatabaseProvider {
        val database = writableDatabase
        return object : DatabaseProvider {
            override val database: SQLiteDatabase = database
        }
    }

    /**
     * Size of the `-wal` sidecar of the database these tests open. Fails the test when the file is
     * missing, so a missing `-wal` does not pass as an empty log.
     */
    private fun walSize(): Long {
        val wal = File(databaseFile.path + "-wal")
        assertTrue("The -wal file must exist.", wal.exists())
        return wal.length()
    }

    /**
     * Runs [sql] inside a transaction, which pins it to the primary connection. In WAL the
     * framework may serve a read from another connection in its pool, and connection-level
     * pragmas such as `synchronous` differ per connection.
     */
    private fun SQLiteDatabase.pragmaOnPrimaryConnection(sql: String): Long? {
        beginTransactionNonExclusive()
        try {
            return rawQuery(sql, null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } finally {
            endTransaction()
        }
    }

    private fun openRollbackJournalDatabase(): SQLiteDatabase =
        object : SQLiteOpenHelper(app, databaseFile.path, null, 1) {
            override fun onCreate(db: SQLiteDatabase?) {}
            override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
        }.writableDatabase.also {
            it.rawQuery("PRAGMA journal_mode = TRUNCATE", null).use { cursor -> cursor.moveToFirst() }
        }

    /**
     * Explicit ids, so the rows stay distinct even when another test class has left
     * `UUID.randomUUID` mocked in the same JVM.
     */
    private fun createDispatches(count: Int): List<Dispatch> =
        (0 until count).map { createDispatch("event_$it") }

    private fun createDispatch(id: String): Dispatch =
        Dispatch.create(id, DataObject.EMPTY_OBJECT, System.currentTimeMillis())

    private companion object {
        const val PROCESSOR = "Collect"
        val SIDECARS = listOf("-wal", "-shm")
    }
}
