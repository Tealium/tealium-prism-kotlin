package com.tealium.prism.core.internal.persistence.database

import android.app.Application
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.internal.persistence.database.upgrades.DatabaseUpgrade
import com.tealium.tests.common.getDefaultConfig
import io.mockk.mockk
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
import java.util.UUID

/**
 * Tests for [DatabaseHelper]'s upgrade lifecycle: what it applies, and what happens when a step fails.
 *
 * The upgrade steps themselves are covered by
 * [UpgradesTests][com.tealium.prism.core.internal.persistence.database.upgrades.UpgradesTests].
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseHelperUpgradeTests {

    private lateinit var app: Application
    private lateinit var config: TealiumConfig
    private lateinit var dbName: String
    private lateinit var logger: Logger

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        config = getDefaultConfig(app)
        dbName = "helper-upgrade-${UUID.randomUUID()}.db"
        logger = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        app.deleteDatabase(dbName)
    }

    @Test
    fun onCreate_Creates_TheCurrentSchema_When_FreshInstall() {
        realHelper(config, dbName).closing { helper ->
            val db = helper.writableDatabase

            assertEquals(0, helper.oldVersion)
            assertFalse(helper.wasUpgraded)
            assertEquals(DatabaseHelper.DATABASE_VERSION, db.version)
            assertTrue(db.schemaObjects().keys.contains(Schema.ModuleStorageTable.TABLE_NAME))
        }
    }

    /**
     * A fresh-install file that already contains a table the schema creates - a hand-made or
     * otherwise malformed file at version 0 - fails `onCreate`'s own SQL. The helper records that
     * failure for [FileDatabaseProvider] rather than letting it escape unclassified.
     */
    @Test
    fun onCreate_RecordsInitializationFailure_When_SchemaCannotBeCreated() {
        stageFileWithPreexistingDispatchTable(app, dbName)

        realHelper(config, dbName).closing { helper ->
            val thrown = assertThrows(SQLException::class.java) { helper.writableDatabase }

            assertSame(thrown, helper.initializationFailure)
        }
    }

    /**
     * A file older than [DatabaseHelper.MIN_SUPPORTED_VERSION] has no upgrade path, so the helper
     * refuses it outright rather than running steps that cannot bridge the gap.
     */
    @Test
    fun onUpgrade_Throws_UnsupportedSchemaVersionException_When_FileIsBelowMinSupportedVersion() {
        (1 until DatabaseHelper.MIN_SUPPORTED_VERSION).forEach { staleVersion ->
            app.deleteDatabase(dbName)
            createEmptyDatabaseAtVersion(app, dbName, staleVersion)

            realHelper(config, dbName).closing { helper ->
                val thrown = assertThrows(UnsupportedSchemaVersionException::class.java) {
                    helper.writableDatabase
                }

                assertEquals(staleVersion, thrown.oldVersion)
                assertEquals(DatabaseHelper.DATABASE_VERSION, thrown.newVersion)
                assertEquals(
                    UnsupportedSchemaVersionException.Direction.BELOW_MINIMUM,
                    thrown.direction
                )
                // below-minimum rejections throw before any SQL runs, so nothing can mask them -
                // they are not recorded for FileDatabaseProvider to classify
                assertNull(
                    "expected no recorded failure, was ${helper.initializationFailure}",
                    helper.initializationFailure
                )
            }
        }
    }

    /**
     * A contiguous chain of steps is applied in version order, each recorded before the next
     * starts, and the file ends up stamped at the last step's version.
     */
    @Test
    fun onUpgrade_AppliesEveryStep_InOrder_When_ChainIsContiguous() {
        val current = DatabaseHelper.DATABASE_VERSION
        stageFileWithOneRow()
        val ran = mutableListOf<Int>()

        realHelper(
            config,
            dbName,
            recordingUpgrade(current + 1, ran),
            recordingUpgrade(current + 2, ran)
        ).closing { helper ->
            val db = helper.writableDatabase

            assertEquals(listOf(current + 1, current + 2), ran)
            assertEquals(current, helper.oldVersion)
            assertTrue(helper.wasUpgraded)
            assertEquals(current + 2, db.version)
            assertNull(helper.initializationFailure)
            assertFalse(helper.corruptionReported)
        }
    }

    /**
     * A file newer than this SDK cannot be brought back down: [DatabaseHelper.onDowngrade] refuses
     * it outright, mirroring the refusal below [DatabaseHelper.MIN_SUPPORTED_VERSION].
     */
    @Test
    fun onDowngrade_Throws_UnsupportedSchemaVersionException_When_FileIsNewer() {
        val newerVersion = DatabaseHelper.DATABASE_VERSION + 1
        createEmptyDatabaseAtVersion(app, dbName, newerVersion)

        val thrown = realHelper(config, dbName).closing { helper ->
            assertThrows(UnsupportedSchemaVersionException::class.java) {
                helper.writableDatabase
            }
        }

        assertEquals(newerVersion, thrown.oldVersion)
        assertEquals(DatabaseHelper.DATABASE_VERSION, thrown.newVersion)
        assertEquals(UnsupportedSchemaVersionException.Direction.DOWNGRADE, thrown.direction)
    }

    /**
     * An [Exception] out of an upgrade step says the file could not be brought forward, which is a storage
     * condition: it is normalised into a [DatabaseUpgradeException], the file is left as it was, and
     * the session degrades to in-memory storage rather than failing SDK startup.
     */
    @Test
    fun onUpgrade_WrapsException_When_StepThrowsException() {
        val boom = IllegalStateException("upgrade step exploded")
        val step = stageFileWithFailingUpgrade { throw boom }

        // (i) the failure is normalised, carrying the original cause
        val thrown = assertOpenThrows(DatabaseUpgradeException::class.java, step)
        assertEquals(step.targetVersion, thrown.targetVersion)
        assertSame(boom, thrown.cause)

        // (ii) the transaction rolled back: still at the old version, data untouched
        assertFileIsUntouched()

        // (iii) FileDatabaseProvider still hands back a usable database - the in-memory fallback.
        // The helper it creates internally is owned by the provider, so only the database is closed
        // here; the file-backed one was never successfully opened.
        val provider = FileDatabaseProvider(
            config, logger, { realHelper(config, dbName, step) }
        )
        provider.database.use { database ->
            assertTrue(database.isOpen)
            assertFalse(database.isReadOnly)
            assertEquals(":memory:", database.path)
        }
    }

    /**
     * An [Error] out of an upgrade step says something about the process or the build, not about the file:
     * an [AssertionError] is a failed invariant, an [OutOfMemoryError] a process in trouble. It is
     * therefore neither wrapped by [DatabaseHelper.onUpgrade] - hence `catch (Exception)` there - nor
     * routed to the in-memory fallback by [FileDatabaseProvider]: it propagates.
     */
    @Test
    fun onUpgrade_PropagatesErrorUnwrapped_When_StepThrowsError() {
        val boom = AssertionError("upgrade step assertion failed")
        val step = stageFileWithFailingUpgrade { throw boom }

        // (i) it escapes the helper unwrapped
        assertSame(boom, assertOpenThrows(AssertionError::class.java, step))

        // (ii) the transaction still rolled back: the file is no worse off
        assertFileIsUntouched()

        // (iii) and it escapes the provider too, rather than becoming an in-memory session
        val provider = FileDatabaseProvider(
            config, logger, { realHelper(config, dbName, step) }
        )
        val propagated = assertThrows(AssertionError::class.java) { provider.database }
        assertSame(boom, propagated)
    }

    /**
     * A registered chain with a gap - here a step to two versions ahead but none to the version
     * between - would stamp the new version on a schema that skipped a step. It is rejected before
     * any step runs, as a failed upgrade to the first missing version.
     */
    @Test
    fun onUpgrade_Throws_DatabaseUpgradeException_When_UpgradeChainHasAGap() {
        val current = DatabaseHelper.DATABASE_VERSION
        val ran = mutableListOf<Int>()

        assertRejectsBrokenChain(
            recordingUpgrade(current + 2, ran),
            expectedTargetVersion = current + 1,
            ran = ran
        )
    }

    /** Two steps to the same version are as broken a chain as a missing one, and neither runs. */
    @Test
    fun onUpgrade_Throws_DatabaseUpgradeException_When_UpgradeChainHasADuplicate() {
        val current = DatabaseHelper.DATABASE_VERSION
        val ran = mutableListOf<Int>()

        assertRejectsBrokenChain(
            recordingUpgrade(current + 1, ran),
            recordingUpgrade(current + 1, ran),
            expectedTargetVersion = current + 1,
            ran = ran
        )
    }

    /**
     * A chain with every version registered, but not in ascending order, is as broken a chain as a
     * gap or a duplicate: the registered versions must line up position-for-position with the
     * expected sequence, not merely match it as a set.
     */
    @Test
    fun onUpgrade_Throws_DatabaseUpgradeException_When_StepsAreRegisteredOutOfOrder() {
        val current = DatabaseHelper.DATABASE_VERSION
        val ran = mutableListOf<Int>()

        assertRejectsBrokenChain(
            recordingUpgrade(current + 2, ran),
            recordingUpgrade(current + 1, ran),
            expectedTargetVersion = current + 2,
            ran = ran
        )
    }

    // region helpers

    /** A file at [DatabaseHelper.DATABASE_VERSION] with one row, to prove nothing is lost. */
    private fun stageFileWithOneRow() {
        realHelper(config, dbName).closing { helper ->
            helper.writableDatabase.execSQL(
                "INSERT INTO ${Schema.ModuleTable.TABLE_NAME} " +
                    "(${Schema.ModuleTable.COLUMN_NAME}) VALUES ('some-module');"
            )
        }
    }

    /** A step at [version] that does nothing but add its version to [ran]. */
    private fun recordingUpgrade(version: Int, ran: MutableList<Int>) = upgradeStep(version) {
        ran.add(version)
    }

    /**
     * Stages a file with one row, then asserts that opening a helper with [steps] - staged, as
     * [realHelper] defaults it, at their highest version - fails with the broken-chain
     * [DatabaseUpgradeException] at [expectedTargetVersion], caused by an [IllegalStateException]
     * naming the problem; that the helper recorded nothing, as the chain check throws before any
     * SQL runs; that none of [steps] ran, per [ran]; and that the file itself was left untouched.
     */
    private fun assertRejectsBrokenChain(
        vararg steps: DatabaseUpgrade,
        expectedTargetVersion: Int,
        ran: MutableList<Int>
    ) {
        stageFileWithOneRow()

        realHelper(config, dbName, *steps).closing { helper ->
            val thrown =
                assertThrows(DatabaseUpgradeException::class.java) { helper.writableDatabase }

            assertEquals(expectedTargetVersion, thrown.targetVersion)
            val cause = thrown.cause
            assertTrue("expected IllegalStateException, was $cause", cause is IllegalStateException)
            assertTrue(
                "expected the cause to name the problem, was ${cause?.message}",
                cause?.message?.contains("not contiguous") == true
            )
            assertNull(helper.initializationFailure)
        }

        assertEquals("no step may run", emptyList<Int>(), ran)
        assertFileIsUntouched()
    }

    /**
     * Stages a file at [DatabaseHelper.DATABASE_VERSION] with one row, then returns the single step
     * above it that always fails - so a fresh [realHelper] built from that step has an outstanding
     * upgrade to fail on.
     */
    private fun stageFileWithFailingUpgrade(boom: () -> Nothing): DatabaseUpgrade {
        val nextVersion = DatabaseHelper.DATABASE_VERSION + 1

        stageFileWithOneRow()

        return upgradeStep(nextVersion) { boom() }
    }

    /**
     * Opens a fresh helper for [step] through the helper directly - not the provider - and
     * returns what came out.
     */
    private fun <T : Throwable> assertOpenThrows(
        expected: Class<T>,
        step: DatabaseUpgrade
    ): T = realHelper(config, dbName, step).closing { helper ->
        assertThrows(expected) { helper.writableDatabase }
    }

    /** Asserts the file is still at [DatabaseHelper.DATABASE_VERSION] with its row intact. */
    private fun assertFileIsUntouched() {
        onDisk(app.getDatabasePath(dbName).absolutePath, SQLiteDatabase.OPEN_READWRITE) { raw ->
            assertEquals(DatabaseHelper.DATABASE_VERSION, raw.version)
            raw.rawQuery(
                "SELECT COUNT(*) FROM ${Schema.ModuleTable.TABLE_NAME} " +
                    "WHERE ${Schema.ModuleTable.COLUMN_NAME} = ?",
                arrayOf("some-module")
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    // endregion
}
