package com.tealium.prism.core.internal.persistence.database

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.assertCurrentTablesExist
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.assertTableNotExists
import com.tealium.prism.core.internal.persistence.database.DatabaseTestUtils.isInMemory
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DatabaseProviderTests {

    internal lateinit var mockDatabaseHelper: DatabaseHelper

    lateinit var app: Application
    lateinit var config: TealiumConfig
    lateinit var databaseProvider: DatabaseProvider
    lateinit var persistentDatabase: SQLiteDatabase
    private lateinit var dbFile: File
    private val opened = DatabaseTestUtils.OpenedDatabases()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        config = getDefaultConfig(app)
        dbFile = File(app.filesDir, "test.db")
        persistentDatabase = createPersistentDatabase(file = dbFile) // persistent
        mockDatabaseHelper = spyk(DatabaseHelper(config, dbFile.path))

        databaseProvider = FileDatabaseProvider(
            config,
            SystemLogger,
            databaseHelperCreator = { _ -> mockDatabaseHelper }
        )
    }

    @After
    fun tearDown() {
        opened.closeAll()
        SQLiteDatabase.deleteDatabase(
            dbFile
        )
    }

    @Test
    fun getDatabase_Returns_PersistentDatabase_WhenWritable() {
        every { mockDatabaseHelper.writableDatabase } returns persistentDatabase

        val db = opened.track(databaseProvider.database)

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertFalse(db.isInMemory)
    }

    @Test
    fun getDatabase_Returns_InMemoryDatabase_WhenWritableThrows() {
        every { mockDatabaseHelper.writableDatabase } throws SQLiteException()

        val db = opened.track(databaseProvider.database)

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertTrue(db.isInMemory)
    }

    @Test
    fun databaseProvider_onDowngrade_IsDestructive_AndRecreates() {
        // the provider deletes and recreates this file; hold no other connection to it
        persistentDatabase.close()
        var persistentDb =
            createPersistentDatabase(
                version = DatabaseHelper.DATABASE_VERSION + 1
            )
        persistentDb.close()
        persistentDb = opened.track(
            FileDatabaseProvider(
                config,
                SystemLogger,
                databaseHelperCreator = { handler ->
                    DatabaseHelper(config, dbFile.path, errorHandler = handler)
                }
            ).database
        )

        assertTrue(dbFile.exists())
        assertFalse(persistentDb.isInMemory)
        assertCurrentTablesExist(persistentDb)
    }

    /**
     * A file older than [DatabaseHelper.MIN_SUPPORTED_VERSION] - which on a real device can only be a
     * corrupt or hand-made file, since Prism has always shipped at version 3 and the legacy SDK used a
     * different directory - is deleted and recreated rather than upgraded.
     */
    @Test
    fun getDatabase_RecreatesFile_When_BelowMinSupportedVersion() {
        // replace the setup's current-version file; a helper can't open it at a lower version
        persistentDatabase.close()
        SQLiteDatabase.deleteDatabase(dbFile)
        val stale = createPersistentDatabase(
            version = DatabaseHelper.MIN_SUPPORTED_VERSION - 1
        )
        stale.execSQL("CREATE TABLE datalayer (key TEXT PRIMARY KEY, value TEXT);")
        stale.execSQL("INSERT INTO datalayer VALUES ('legacy', 'value');")
        stale.close()

        val recreatedDatabase = opened.track(
            FileDatabaseProvider(
                config,
                SystemLogger,
                databaseHelperCreator = { handler ->
                    DatabaseHelper(config, dbFile.path, errorHandler = handler)
                }
            ).database
        )

        assertFalse(recreatedDatabase.isInMemory)
        assertEquals(DatabaseHelper.DATABASE_VERSION, recreatedDatabase.version)
        assertCurrentTablesExist(recreatedDatabase)
        assertTableNotExists(recreatedDatabase, "datalayer")
    }

    private fun createPersistentDatabase(
        context: Context = config.application.applicationContext,
        file: File = dbFile,
        version: Int = DatabaseHelper.DATABASE_VERSION
    ): SQLiteDatabase = opened.track(
        DatabaseTestUtils.createBlankDatabase(
            context,
            file.path,
            version
        )
    )
}
