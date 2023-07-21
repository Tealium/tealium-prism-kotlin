package com.tealium.core.internal.persistence

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE

/**
 * Repository class for registering new modules.
 *
 *
 */
interface ModuleStorageRepository {
    val modules: Map<String, Long>

    fun registerModule(name: String) : Long // Id

    // TODO - do we need to clear things down?
    // fun delete(name: String)

    // TODO - could be a listener instead
    // fun removeExpired()
}

class ModuleStorageRepositoryImpl(
    private val dbProvider: DatabaseProvider
) : ModuleStorageRepository {

    private val db: SQLiteDatabase
        get() = dbProvider.database

    private val _modules: MutableMap<String, Long> = mutableMapOf()
    override val modules: Map<String, Long>
        get() {
            return _modules.ifEmpty {
                readModules().also {
                    _modules.putAll(it)
                }
            }.toMap()
        }

    internal fun readModules(): Map<String, Long> {
        val modules = mutableMapOf<String, Long>()
        db.query(
            Schema.ModuleTable.TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.count <= 0) return emptyMap()

            val idColumnIndex = cursor.getColumnIndex(Schema.ModuleTable.COLUMN_ID)
            val nameColumnIndex = cursor.getColumnIndex(Schema.ModuleTable.COLUMN_NAME)

            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getLong(idColumnIndex)
                    val name = cursor.getString(nameColumnIndex)

                    modules[name] = id
                } catch (ignore: Exception) { }
            }
        }

        return modules
    }

    override fun registerModule(name: String): Long {
        modules[name]?.let { id ->
            return id
        }

        return insertNewModule(db, name).also { newId ->
            if (newId > -1) {
                _modules[name] = newId
            }
        }
    }

    companion object {

        /**
         * Inserts a record into the [Schema.ModuleTable] and returns the Id. If insertion
         * was unsuccessful then -1 will be returns instead.
         */
        internal fun insertNewModule(db: SQLiteDatabase, name: String) : Long {
            return db.insertWithOnConflict(
                Schema.ModuleTable.TABLE_NAME,
                null,
                ContentValues().apply {
                    put(Schema.ModuleTable.COLUMN_NAME, name)
                },
                CONFLICT_IGNORE
            )
        }
    }
}