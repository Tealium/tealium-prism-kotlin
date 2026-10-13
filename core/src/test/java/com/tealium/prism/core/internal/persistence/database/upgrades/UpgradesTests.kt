package com.tealium.prism.core.internal.persistence.database.upgrades

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.internal.persistence.database.DatabaseHelper
import com.tealium.prism.core.internal.persistence.database.Schema
import com.tealium.prism.core.internal.persistence.database.closing
import com.tealium.prism.core.internal.persistence.database.schemaObjects
import com.tealium.tests.common.getDefaultConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Tests for the [Upgrades] registry: that a fresh install produces the frozen schema pinned below
 * as [EXPECTED_SCHEMA], that [Upgrades.all] is contiguous and follows its naming convention, and
 * that [Upgrades.since] selects exactly the steps between two versions.
 *
 * There is no upgrade-path comparison here yet: [Upgrades.all] is still empty, since every
 * released Prism version has shipped at schema version 3. The comparison that matters most - that
 * upgrading an old database lands on exactly the same schema a fresh install creates - arrives with
 * the first real upgrade step; see the "Adding an upgrade step" checklist in [Upgrades]'s KDoc.
 */
@RunWith(RobolectricTestRunner::class)
class UpgradesTests {

    private lateinit var app: Application
    private lateinit var config: TealiumConfig
    private lateinit var freshDbName: String

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        config = getDefaultConfig(app)
        freshDbName = "upgrades-fresh-${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        app.deleteDatabase(freshDbName)
    }

    /**
     * With no upgrade steps registered there is no upgrade path to compare a fresh install against, so the
     * fresh `onCreate` output is pinned directly instead: every `sqlite_master` row, `sql` text and all,
     * against a frozen literal.
     *
     * This is the same guarantee the equivalence comparison used to give from the other direction - that
     * the schema on disk is the one that was reviewed - and the first upgrade step that targets the version
     * 3 shape restores the upgrade-path comparison alongside it.
     */
    @Test
    fun onCreate_Produces_TheFrozenCurrentSchema() {
        val fresh = openHelper(freshDbName) { helper ->
            val objects = helper.writableDatabase.schemaObjects()
            assertEquals(0, helper.oldVersion)
            objects
        }

        assertEquals(EXPECTED_SCHEMA.keys, fresh.keys)
        EXPECTED_SCHEMA.forEach { (name, expected) ->
            assertEquals("schema object '$name' is not what shipped", expected, fresh[name])
        }
    }

    @Test
    fun all_Has_StrictlyIncreasingVersions_EndingAt_DatabaseVersion() {
        val versions = Upgrades.all.map { it.targetVersion }

        // Holds today because Upgrades.all sorts itself; checked separately so a change to how the
        // registry is built fails here with a direct message, not only in the range check below.
        assertEquals(versions.sorted(), versions)
        assertEquals(versions.distinct(), versions)
        assertEquals(
            ((DatabaseHelper.MIN_SUPPORTED_VERSION + 1)..DatabaseHelper.DATABASE_VERSION).toList(),
            versions
        )
    }

    @Test
    fun all_Follows_TheNamingConvention() {
        assertEquals(emptyList<String>(), Upgrades.all.mapNotNull { namingViolation(it) })
    }

    /**
     * Driven by synthetic named objects, since [Upgrades.all] is empty. The target version's digits
     * are matched greedily, so `V3ToV45` reads as a step to 45, not as `V3ToV4` with a suffix `5`.
     */
    @Test
    fun namingConvention_Accepts_ASuffix_And_Rejects_Mismatches() {
        assertNull(namingViolation(Conforming.V3ToV4))
        assertNull(namingViolation(Conforming.V3ToV4AddColumn))

        assertNotNull(namingViolation(Mismatched.V3ToV5))
        assertNotNull(namingViolation(Mismatched.V3ToV4))
        assertNotNull(namingViolation(Mismatched.V3ToV45))

        val anonymous = namingViolation(syntheticUpgrade(4))
        assertTrue(
            "expected the violation to ask for a named object, was $anonymous",
            anonymous?.contains("named objects") == true
        )
    }

    /**
     * Bounded at both ends. Driven by synthetic steps because [Upgrades.all] is empty, so the
     * filtering can be pinned before there is anything real to filter - which is exactly when it is
     * easiest to get wrong.
     */
    @Test
    fun since_Selects_OnlyTheStepsBetween_OldAndNewVersion() {
        val v4 = syntheticUpgrade(4)
        val v5 = syntheticUpgrade(5)
        val v6 = syntheticUpgrade(6)
        val steps = listOf(v4, v5, v6)

        assertEquals(steps, Upgrades.since(3, 6, steps))
        assertEquals(listOf(v4, v5), Upgrades.since(3, 5, steps))
        assertEquals(listOf(v5, v6), Upgrades.since(4, 6, steps))
        assertEquals(listOf(v5), Upgrades.since(4, 5, steps))
        assertEquals(emptyList<DatabaseUpgrade>(), Upgrades.since(6, 6, steps))
        assertEquals(emptyList<DatabaseUpgrade>(), Upgrades.since(4, 4, steps))
    }

    // region helpers

    /**
     * Why [step] breaks the `V{N-1}ToV{N}` naming convention - an optional suffix that does not start
     * with a digit is allowed - or `null` if it follows it.
     */
    private fun namingViolation(step: DatabaseUpgrade): String? {
        val name = step.javaClass.simpleName
        if (name.isEmpty()) {
            return "An anonymous step at version ${step.targetVersion}: steps must be named objects."
        }
        val match = STEP_NAME.matchEntire(name)
            ?: return "$name does not match V{N-1}ToV{N} with an optional non-digit suffix."
        val (from, to) = match.destructured
        return when {
            to.toInt() != step.targetVersion ->
                "$name upgrades to version $to but declares version ${step.targetVersion}."
            from.toInt() != step.targetVersion - 1 ->
                "$name upgrades from version $from but should upgrade from " +
                    "${step.targetVersion - 1}."
            else -> null
        }
    }

    /** A step that does nothing, so that only its class name and [targetVersion] matter. */
    private abstract class NoOpUpgrade(override val targetVersion: Int) : DatabaseUpgrade {
        override fun upgrade(db: SQLiteDatabase) = Unit
    }

    /** Steps named to the convention. */
    private object Conforming {
        object V3ToV4 : NoOpUpgrade(4)
        object V3ToV4AddColumn : NoOpUpgrade(4)
    }

    /** Steps whose names disagree with their versions. */
    private object Mismatched {
        object V3ToV5 : NoOpUpgrade(4)
        object V3ToV4 : NoOpUpgrade(5)
        object V3ToV45 : NoOpUpgrade(4)
    }

    private fun syntheticUpgrade(atVersion: Int) = object : DatabaseUpgrade {
        override val targetVersion: Int = atVersion
        override fun upgrade(db: SQLiteDatabase) = Unit
        override fun toString(): String = "V${atVersion - 1}ToV$atVersion"
    }

    /** Opens the named database with the real [DatabaseHelper], via [closing]. */
    private fun <T> openHelper(dbName: String, block: (DatabaseHelper) -> T): T =
        DatabaseHelper(config, dbName).closing(block)

    private companion object {
        /** `V{from}ToV{to}`, then optionally anything that does not start with a digit. */
        val STEP_NAME = Regex("^V(\\d+)ToV(\\d+)(?:\\D.*)?$")

        /**
         * The `sqlite_master` rows a fresh install must produce, frozen as literals rather than built
         * from the live [Schema] constants - an expectation derived from the code under test would
         * agree with it no matter what it produced.
         *
         * A diff here means the schema on disk changed, which is exactly the moment an upgrade step and a
         * new `SchemaV<N>` snapshot are needed.
         */
        val EXPECTED_SCHEMA: Map<String, String> = mapOf(
            "dispatch" to
                "table|dispatch|dispatch|CREATE TABLE dispatch(\n"
                 + "    id           TEXT PRIMARY KEY, \n"
                 + "    timestamp    LONG NOT NULL,\n"
                 + "    dispatch     TEXT NOT NULL\n"
                 + ")",
            "idx_dispatch_timestamp" to
                "index|idx_dispatch_timestamp|dispatch|CREATE INDEX idx_dispatch_timestamp\n"
                 + "ON dispatch (timestamp)",
            "module" to
                "table|module|module|CREATE TABLE module(\n"
                 + "     id   INTEGER PRIMARY KEY, \n"
                 + "     name TEXT UNIQUE NOT NULL\n"
                 + ")",
            "module_storage" to
                "table|module_storage|module_storage|CREATE TABLE module_storage (\n"
                 + "       module_id   INTEGER,\n"
                 + "       key         TEXT,\n"
                 + "       value       TEXT,\n"
                 + "       expiry      LONG,\n"
                 + "   PRIMARY KEY (module_id, key),\n"
                 + "   FOREIGN KEY (module_id)\n"
                 + "       REFERENCES module(id)\n"
                 + "       ON DELETE CASCADE\n"
                 + ")",
            "queue" to
                "table|queue|queue|CREATE TABLE queue(\n"
                 + "     dispatch_id       TEXT NOT NULL, \n"
                 + "     processor_id      TEXT NOT NULL,\n"
                 + "     PRIMARY KEY (dispatch_id, processor_id),\n"
                 + "     FOREIGN KEY (dispatch_id) \n"
                 + "        REFERENCES dispatch(id)\n"
                 + "        ON DELETE CASCADE\n"
                 + ")",
            "queue_remove_processed_dispatches" to
                "trigger|queue_remove_processed_dispatches|queue|CREATE TRIGGER queue_remove_processed_dispatches\n"
                 + "    AFTER DELETE ON queue\n"
                 + "BEGIN\n"
                 + "	DELETE FROM dispatch\n"
                 + "	WHERE NOT EXISTS (\n"
                 + "		SELECT dispatch_id\n"
                 + "	  	FROM queue\n"
                 + "	  	WHERE dispatch_id = dispatch.id\n"
                 + "	);\n"
                 + "END",
        )
    }

    // endregion
}
