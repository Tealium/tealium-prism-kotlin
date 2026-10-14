package com.tealium.prism.core.internal.persistence.database.upgrades

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.internal.persistence.database.DatabaseHelper
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.assertCurrentTablesExist
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createCurrentDatabase
import com.tealium.tests.common.createV3Database
import com.tealium.tests.common.getDefaultConfig
import com.tealium.tests.common.insertV3StorageRow
import com.tealium.tests.common.structure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * The home for on-device upgrade tests, one per shipped schema version: stage a file with that
 * version's frozen DDL and representative rows, open it with the real [DatabaseHelper], and assert
 * that it has the current schema and that the rows survived. Version 3 is covered by
 * [onUpgrade_MovesVisitorRows_When_FileIsAtVersion3]; the next schema change adds a version 4 case
 * beside it. Failure routing, the [Upgrades] registry rules and corruption handling stay in the
 * unit tests.
 *
 * These run on a device because the unit tests run on the single SQLite build bundled with
 * Robolectric, whatever API level they target, while a device runs its own platform's SQLite. Upgrade
 * step SQL that passes the unit tests can therefore fail on older devices: `ALTER TABLE ... RENAME
 * COLUMN` needs SQLite 3.25 (API 30+), and `DROP COLUMN` needs 3.35 (API 34+, so not even CI's API 33
 * lane). CI runs this source set on API 23 and 33.
 */
class UpgradePathTests {

    private lateinit var app: Application
    private lateinit var config: TealiumConfig
    private lateinit var dbFile: File
    private val opened = DatabaseTestUtils.OpenedDatabases()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        config = getDefaultConfig(app)

        dbFile = File(app.filesDir, "upgrade-path-${UUID.randomUUID()}.db")
    }

    @After
    fun tearDown() {
        opened.closeAll()
        SQLiteDatabase.deleteDatabase(dbFile)
    }

    @Test
    fun onCreate_CreatesAllCurrentTables_When_FreshInstall() {
        assertCurrentTablesExist(opened.track(createCurrentDatabase(config, dbFile.path)))
    }

    /**
     * A version 3 file holding the visitor layout of the day, upgraded through the real helper on
     * this device's SQLite: the visitor rows land in the `VisitorId` store under their new keys, the
     * unrelated row stays in `shared`, the file is stamped 4, and its structure is that of a fresh
     * install, compared via `PRAGMA table_info`, `index_list`/`index_info`, `foreign_key_list` and
     * trigger SQL rather than `sqlite_master` text.
     */
    @Test
    fun onUpgrade_MovesVisitorRows_When_FileIsAtVersion3() {
        val freshFile = File(app.filesDir, "upgrade-path-fresh-${UUID.randomUUID()}.db")
        try {
            createV3Database(dbFile.path) {
                insertV3StorageRow("shared", "tealium_visitor_id", "\"$VISITOR_ID\"")
                insertV3StorageRow("shared", "current_identity", "\"$HASH_A\"")
                insertV3StorageRow("shared", HASH_A, "\"$VISITOR_ID\"")
                insertV3StorageRow("shared", HASH_B, "\"$PREVIOUS_VISITOR_ID\"", FUTURE_EXPIRY)
                insertV3StorageRow("shared", "settings", "{\"a\":1}")
            }
            val fresh = opened.track(createCurrentDatabase(config, freshFile.path)).structure()

            val helper = DatabaseHelper(config, dbFile.path)
            val upgraded = opened.track(helper.writableDatabase)

            assertEquals(3, helper.oldVersion)
            assertTrue(helper.wasUpgraded)
            assertEquals(4, upgraded.version)
            assertEquals(
                listOf(
                    "identity.${HASH_A.lowercase()}|\"$VISITOR_ID\"|-1",
                    "identity.${HASH_B.lowercase()}|\"$PREVIOUS_VISITOR_ID\"|$FUTURE_EXPIRY",
                    "owner_identity|\"${HASH_A.lowercase()}\"|-1",
                    "visitor_id|\"$VISITOR_ID\"|-1",
                ),
                upgraded.rowsFor("VisitorId")
            )
            assertEquals(listOf("settings|{\"a\":1}|-1"), upgraded.rowsFor("shared"))
            assertEquals(fresh, upgraded.structure())
        } finally {
            SQLiteDatabase.deleteDatabase(freshFile)
        }
    }

    /** The raw `module_storage` rows of the named module as `<key>|<value>|<expiry>`, ordered by key. */
    private fun SQLiteDatabase.rowsFor(moduleName: String): List<String> = rawQuery(
        "SELECT s.key, s.value, s.expiry FROM module_storage s " +
            "INNER JOIN module m ON m.id = s.module_id WHERE m.name = ? ORDER BY s.key",
        arrayOf(moduleName)
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add("${cursor.getString(0)}|${cursor.getString(1)}|${cursor.getLong(2)}")
            }
        }
    }

    private companion object {
        /** Upper-case 64 character digests, as the version 3 `sha256()` wrote them. */
        const val HASH_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val HASH_B = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"

        const val VISITOR_ID = "73ef029f5c7d4648bebeb431b96f0631"
        const val PREVIOUS_VISITOR_ID = "0e1d2c3b4a59687776859403a2b1c0d9"

        /** A finite expiry, to show the column travels verbatim. */
        const val FUTURE_EXPIRY = 4_000_000_000L
    }
}
