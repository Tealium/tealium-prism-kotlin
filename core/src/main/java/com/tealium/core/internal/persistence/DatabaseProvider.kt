package com.tealium.core.internal.persistence

import android.database.sqlite.SQLiteDatabase
import com.tealium.core.TealiumConfig

interface DatabaseProvider {
    val database: SQLiteDatabase
}

internal class FileDatabaseProvider(
    private val config: TealiumConfig,
    private var databaseHelper: DatabaseHelper = DatabaseHelper(config),
    private val inMemoryDatabaseProvider: InMemoryDatabaseProvider = InMemoryDatabaseProvider(config)
) : DatabaseProvider {

    private var _database: SQLiteDatabase? = null

    override val database: SQLiteDatabase
        get() {
            return _database ?: (getPersistentDatabase() ?: inMemoryDatabaseProvider.database).also { db ->
                _database = db
            }
        }

    private fun getPersistentDatabase(): SQLiteDatabase? {
        return try {
            databaseHelper.writableDatabaseOrNull()
        } catch (ex: DatabaseHelper.UnsupportedDowngrade) {
            try {
                databaseHelper.writableDatabaseOrNull()
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