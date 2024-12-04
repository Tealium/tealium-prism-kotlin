package com.tealium.core.internal.persistence.database

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.api.TealiumConfig
import com.tealium.core.internal.persistence.database.DatabaseTestUtils.assertV3TablesExist
import com.tealium.core.internal.persistence.database.DatabaseTestUtils.createV3Database
import com.tealium.core.internal.persistence.database.DatabaseTestUtils.isInMemory
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.spyk
import org.junit.After
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

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        config = getDefaultConfig(app)
        dbFile = File(app.filesDir, "test.db")
        persistentDatabase = createPersistentDatabase(file = dbFile) // persistent
        mockDatabaseHelper = spyk(DatabaseHelper(config, dbFile.path))

        databaseProvider = FileDatabaseProvider(config, { mockDatabaseHelper })
    }

    @After
    fun tearDown() {
        SQLiteDatabase.deleteDatabase(
            dbFile
        )
    }

    @Test
    fun getDatabase_Returns_PersistentDatabase_WhenWritable() {
        every { mockDatabaseHelper.writableDatabase } returns persistentDatabase

        val db = databaseProvider.database

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertFalse(db.isInMemory)
    }

    @Test
    fun getDatabase_Returns_InMemoryDatabase_WhenOnlyReadable() {
        val readOnlyDatabase = SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        every { mockDatabaseHelper.writableDatabase } returns readOnlyDatabase

        val db = databaseProvider.database

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertTrue(db.isInMemory)
    }

    @Test
    fun getDatabase_Returns_InMemoryDatabase_WhenWritableIsNull() {
        every { mockDatabaseHelper.writableDatabase } returns null

        val db = databaseProvider.database

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertTrue(db.isInMemory)
    }

    @Test
    fun getDatabase_Returns_InMemoryDatabase_WhenWritableThrows() {
        every { mockDatabaseHelper.writableDatabase } throws SQLiteException()

        val db = databaseProvider.database

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertTrue(db.isInMemory)
    }

    @Test(expected = DatabaseHelper.UnsupportedDowngrade::class)
    fun downgrade_ThrowsUnsupportedDowngrade() {
        val persistentDb =
            createPersistentDatabase(
                version = DatabaseHelper.DATABASE_VERSION + 1
            )
        persistentDb.close()
        createV3Database(config, dbFile.path)
    }

    @Test
    fun databaseProvider_onDowngrade_IsDestructive_AndRecreates() {
        var persistentDb =
            createPersistentDatabase(
                version = DatabaseHelper.DATABASE_VERSION + 1
            )
        persistentDb.close()
        persistentDb =
            FileDatabaseProvider(config, { DatabaseHelper(config, dbFile.path) }).database

        assertTrue(dbFile.exists())
        assertFalse(persistentDb.isInMemory)
        assertV3TablesExist(persistentDb)
    }

    private fun createPersistentDatabase(
        context: Context = config.application.applicationContext,
        file: File = dbFile,
        version: Int = DatabaseHelper.DATABASE_VERSION
    ): SQLiteDatabase =
        DatabaseTestUtils.createBlankDatabase(
            context,
            file.path,
            version
        )
}