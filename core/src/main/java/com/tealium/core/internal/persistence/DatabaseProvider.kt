package com.tealium.core.internal.persistence

import android.database.sqlite.SQLiteDatabase
import com.tealium.core.TealiumConfig

interface DatabaseProvider {
    val database: SQLiteDatabase
}

internal class FileDatabaseProvider(
    private val config: TealiumConfig,
    private val databaseHelperCreator: () -> DatabaseHelper = { DatabaseHelper(config) },
    private val inMemoryDatabaseProvider: InMemoryDatabaseProvider = InMemoryDatabaseProvider(config)
) : DatabaseProvider {

    private var databaseHelper: DatabaseHelper = databaseHelperCreator.invoke()
    private var _database: SQLiteDatabase? = null

    override val database: SQLiteDatabase
        get() {
            return _database ?: (getPersistentDatabase() ?: inMemoryDatabaseProvider.database).also { db ->
                _database = db
            }
        }

    private fun getPersistentDatabase(): SQLiteDatabase? {
        return try {
            databaseHelper.getWritableDatabaseOrNull()
        } catch (ex: DatabaseHelper.UnsupportedDowngrade) {
            try {
                databaseHelper.deleteDatabase()
                databaseHelper = databaseHelperCreator.invoke()
                databaseHelper.getWritableDatabaseOrNull()
            } catch (ex: Exception) {
                null
            }
        }
    }
}

internal class InMemoryDatabaseProvider(
    private val config: TealiumConfig,
    private val databaseHelper: DatabaseHelper = DatabaseHelper(config, databaseName = null)
) : DatabaseProvider {
    private var _database: SQLiteDatabase? = null

    override val database: SQLiteDatabase
        get() {
            return _database ?: getInMemoryDatabase().also { db ->
                _database = db
            }
        }

    private fun getInMemoryDatabase(): SQLiteDatabase {
        return databaseHelper.writableDatabase // in-mem assumed writeable
    }
}