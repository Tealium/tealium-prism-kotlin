package com.tealium.prism.core.internal.persistence.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tealium.prism.core.api.TealiumConfig
import org.junit.Assert.fail

object DatabaseTestUtils {

    /** The databases a test opened, closed together in its `@After`. */
    class OpenedDatabases {
        private val databases = mutableListOf<SQLiteDatabase>()

        fun track(db: SQLiteDatabase): SQLiteDatabase = db.also { databases.add(it) }

        fun closeAll() {
            databases.forEach { if (it.isOpen) it.close() }
            databases.clear()
        }
    }

    /**
     * Checks whether this database is an in-memory one rather than persistent
     */
    val SQLiteDatabase.isInMemory: Boolean
        get() = this.path == ":memory:"

    /**
     * Creates an empty Database with the provided version
     */
    fun createBlankDatabase(
        context: Context,
        dbName: String,
        version: Int
    ): SQLiteDatabase {
        return object : SQLiteOpenHelper(
            context, dbName, null, version
        ) {
            override fun onCreate(db: SQLiteDatabase?) {
                // empty
            }

            override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {}
        }.writableDatabase
    }

    /**
     * Creates a database at the CURRENT schema version via [DatabaseHelper.onCreate].
     */
    fun createCurrentDatabase(config: TealiumConfig, dbName: String? = null): SQLiteDatabase {
        return DatabaseHelper(config, dbName).writableDatabase
    }

    /**
     * Asserts that all tables of the current schema exist.
     */
    fun assertCurrentTablesExist(db: SQLiteDatabase) {
        assertTableExists(db, Schema.DispatchTable.TABLE_NAME)
        assertTableExists(db, Schema.QueueTable.TABLE_NAME)
        assertTableExists(db, Schema.ModuleTable.TABLE_NAME)
        assertTableExists(db, Schema.ModuleStorageTable.TABLE_NAME)
    }

    /**
     * Helper method to assert that a specific table is present in the Database
     */
    fun assertTableExists(db: SQLiteDatabase, tableName: String) {
        if (getTableCount(db, tableName) <= 0) {
            fail("Table $tableName does not exist when it is expected to.")
        }
    }

    /**
     * Helper method to assert that a specific table is not present in the Database
     */
    fun assertTableNotExists(db: SQLiteDatabase, tableName: String) {
        if (getTableCount(db, tableName) >= 1) {
            fail("Table $tableName exists when it is not expected to.")
        }
    }

    /**
     * Returns the number of tables with the given [tableName] in the database.
     */
    private fun getTableCount(db: SQLiteDatabase, tableName: String): Int {
        db.rawQuery(
            """
                SELECT count(*) as count
                FROM 
                    sqlite_master
                WHERE 
                    type = 'table' AND 
                    name = ?;
            """.trimIndent(),
            arrayOf(tableName)
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(cursor.getColumnIndex("count"))
        }
    }
}
