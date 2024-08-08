package com.tealium.core.internal.persistence

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.tealium.core.api.TealiumConfig

/**
 * The [DatabaseProvider] should simplify the method of accessing an instance of a [SQLiteDatabase]
 * which would typically be provided by an instance of [SQLiteOpenHelper]. The helper, though has
 * some important caveats though, and the [DatabaseProvider] should handle these cases:
 *  - It should only be opened on a non-main Thread to avoid long running upgrades slowing the UI
 *  - If there is an error whilst opening the database file, then it should fall back to an
 *  in-memory version of the database.
 */
interface DatabaseProvider {
    val database: SQLiteDatabase
}

/**
 * This is the default implementation of [DatabaseProvider] which will write to a persistent file,
 * so all updates to the Database will be available on the next launch.
 *
 * @param config The TealiumConfig used to access the Android Context, as well as derive the file-name
 * @param databaseHelperCreator Function to return the DatabaseHelper. This may be called more than
 * once in scenarios where the Database file itself needs to be destroyed/recreated i.e. where a
 * destructive downgrade is required.
 * @param inMemoryDatabaseProvider Fallback database provider in case this one is unable to access
 * a writeable database file.
 */
internal class FileDatabaseProvider(
    private val config: TealiumConfig,
    private val databaseHelperCreator: () -> DatabaseHelper = { DatabaseHelper(config) },
    private val inMemoryDatabaseProvider: (TealiumConfig) -> InMemoryDatabaseProvider = ::InMemoryDatabaseProvider
) : DatabaseProvider {

    private var databaseHelper: DatabaseHelper = databaseHelperCreator.invoke()
    private var _database: SQLiteDatabase? = null

    override val database: SQLiteDatabase
        get() {
            return _database ?: (getPersistentDatabase() ?: inMemoryDatabaseProvider.invoke(config).database).also { db ->
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

/**
 * This is the fallback implementation of [DatabaseProvider] which will create the database
 * in-memory so all updates will be lost upon exiting the application.
 *
 * @param config The TealiumConfig used to access the Android Context
 * @param databaseHelper The Database helper instance to use for accessing the SQLiteDatabase
 * instance, and creating the schema
 */
internal class InMemoryDatabaseProvider(
    private val config: TealiumConfig,
) : DatabaseProvider {
    private var _database: SQLiteDatabase? = null
    private val databaseHelper: DatabaseHelper by lazy {
        DatabaseHelper(config, databaseName = null)
    }

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