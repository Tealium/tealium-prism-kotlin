package com.tealium.core.internal.persistence

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.tealium.core.api.TealiumConfig
import com.tealium.core.internal.persistence.DatabaseTestUtils.assertV3TablesExist
import com.tealium.core.internal.persistence.DatabaseTestUtils.createV3Database
import com.tealium.core.internal.persistence.DatabaseTestUtils.isInMemory
import com.tealium.core.internal.persistence.database.DatabaseHelper
import com.tealium.core.internal.persistence.database.DatabaseProvider
import com.tealium.core.internal.persistence.database.FileDatabaseProvider
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class DatabaseProviderTests {

    internal lateinit var mockDatabaseHelper: DatabaseHelper

    lateinit var app: Application
    lateinit var config: TealiumConfig
    lateinit var databaseProvider: DatabaseProvider
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext() as Application
        config = getDefaultConfig(app)
        dbFile = File(app.filesDir, "test.db")
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
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun getDatabase_Returns_PersistentDatabase_WhenWritable() {
        val mockDatabase = mockk<SQLiteDatabase>(relaxed = true)
        every { mockDatabase.isReadOnly } returns false
        every { mockDatabaseHelper.writableDatabase } returns mockDatabase

        val db = databaseProvider.database

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertFalse(db.isInMemory)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun getDatabase_Returns_InMemoryDatabase_WhenOnlyReadable() {
        val mockDatabase = mockk<SQLiteDatabase>()
        every { mockDatabase.isReadOnly } returns true
        every { mockDatabaseHelper.writableDatabase } returns mockDatabase

        val db = databaseProvider.database

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertTrue(db.isInMemory)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun getDatabase_Returns_InMemoryDatabase_WhenWritableIsNull() {
        val mockDatabase = mockk<SQLiteDatabase>()
        every { mockDatabase.isReadOnly } returns true
        every { mockDatabaseHelper.writableDatabase } returns null

        val db = databaseProvider.database

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertTrue(db.isInMemory)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P)
    fun getDatabase_Returns_InMemoryDatabase_WhenWritableThrows() {
        val mockDatabase = mockk<SQLiteDatabase>()
        every { mockDatabase.isReadOnly } returns true
        every { mockDatabaseHelper.writableDatabase } throws SQLiteException()

        val db = databaseProvider.database

        assertNotNull(db)
        assertFalse(db.isReadOnly)
        assertTrue(db.isInMemory)
    }

    @Test(expected = DatabaseHelper.UnsupportedDowngrade::class)
    fun downgrade_ThrowsUnsupportedDowngrade() {
        val persistentDb =
            DatabaseTestUtils.createBlankDatabase(
                config.application.applicationContext,
                dbFile.path,
                DatabaseHelper.DATABASE_VERSION + 1
            )
        persistentDb.close()
        createV3Database(config, dbFile.path)
    }

    @Test
    fun databaseProvider_onDowngrade_IsDestructive_AndRecreates() {
        var persistentDb =
            DatabaseTestUtils.createBlankDatabase(
                config.application.applicationContext,
                dbFile.path,
                DatabaseHelper.DATABASE_VERSION + 1
            )
        persistentDb.close()
        persistentDb =
            FileDatabaseProvider(config, { DatabaseHelper(config, dbFile.path) }).database

        assertTrue(dbFile.exists())
        assertFalse(persistentDb.isInMemory)
        assertV3TablesExist(persistentDb)
    }
}