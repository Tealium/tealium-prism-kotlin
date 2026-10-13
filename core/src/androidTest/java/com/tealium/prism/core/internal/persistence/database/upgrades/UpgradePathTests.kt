package com.tealium.prism.core.internal.persistence.database.upgrades

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.assertCurrentTablesExist
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.createCurrentDatabase
import com.tealium.tests.common.getDefaultConfig
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * The home for on-device upgrade tests. The first real upgrade step adds one test here per shipped
 * schema version: stage a file with that version's frozen `SchemaV<N>` and representative rows,
 * open it with the real `DatabaseHelper`, and assert that it has the current schema and that the
 * rows survived. Failure routing, the [Upgrades] registry rules and corruption handling stay in the
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
}
