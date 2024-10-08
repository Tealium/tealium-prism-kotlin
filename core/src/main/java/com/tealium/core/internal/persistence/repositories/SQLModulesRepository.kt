package com.tealium.core.internal.persistence.repositories

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.core.internal.persistence.Schema
import com.tealium.core.internal.persistence.select
import com.tealium.core.internal.persistence.selectAll

/**
 * This is the default implementation of [ModulesRepository] and is backed by a SQLite database
 */
class SQLModulesRepository(
    private val dbProvider: DatabaseProvider,
    onDataExpired: Subject<Map<Long, DataObject>> = Observables.publishSubject(),
) : ModulesRepository {

    private val db: SQLiteDatabase
        get() = dbProvider.database

    override val modules: Map<String, Long>
        get() = readModules()

    private val _onDataExpired: Subject<Map<Long, DataObject>> = onDataExpired
    override val onDataExpired: Observable<Map<Long, DataObject>>
        get() = _onDataExpired

    internal fun readModules(): Map<String, Long> {
        val modules = mutableMapOf<String, Long>()
        db.selectAll(
            Schema.ModuleTable.TABLE_NAME,
        ) { cursor ->
            if (cursor.count <= 0) return emptyMap()

            val idColumnIndex = cursor.getColumnIndex(Schema.ModuleTable.COLUMN_ID)
            val nameColumnIndex = cursor.getColumnIndex(Schema.ModuleTable.COLUMN_NAME)

            while (cursor.moveToNext()) {
                try {
                    val id = cursor.getLong(idColumnIndex)
                    val name = cursor.getString(nameColumnIndex)

                    modules[name] = id
                } catch (ignore: Exception) {
                }
            }
        }

        return modules
    }

    internal fun getModuleIdForName(name: String): Long {
        db.select(
            from = Schema.ModuleTable.TABLE_NAME,
            columns = arrayOf(Schema.ModuleTable.COLUMN_ID),
            where = "${Schema.ModuleTable.COLUMN_NAME} = ?",
            whereArgs = arrayOf(name)
        ) { cursor ->
            val idColumnIndex = cursor.getColumnIndex(Schema.ModuleTable.COLUMN_ID)

            while (cursor.moveToNext()) {
                try {
                    return cursor.getLong(idColumnIndex)
                } catch (ignore: Exception) {
                }
            }
        }

        return -1
    }

    override fun registerModule(name: String): Long {
        val id = getModuleIdForName(name)
        if (id >= 0) return id

        return insertNewModule(db, name)
    }

    /**
     * Inserts a record into the [Schema.ModuleTable] and returns the Id. If insertion
     * was unsuccessful then -1 will be returns instead.
     */
    private fun insertNewModule(db: SQLiteDatabase, name: String): Long {
        return db.insertWithOnConflict(
            Schema.ModuleTable.TABLE_NAME,
            null,
            ContentValues().apply {
                put(Schema.ModuleTable.COLUMN_NAME, name)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    override fun deleteExpired(expirationType: ModulesRepository.ExpirationType, timestamp: Long) {
        val whereClause = "(${Schema.ModuleStorageTable.COLUMN_EXPIRY} >= 0 AND ${Schema.ModuleStorageTable.COLUMN_EXPIRY} < ?) OR ${Schema.ModuleStorageTable.COLUMN_EXPIRY} = ?"
        val whereArgs: Array<String> = arrayOf(timestamp.toString(), expirationType.expiryTime.toString())

        val expiredData = getExpiredData(whereClause, whereArgs)
        if (expiredData.isEmpty()) return

        db.delete(
            Schema.ModuleStorageTable.TABLE_NAME,
            whereClause,
            whereArgs
        )

        notifyExpired(expiredData)
    }

    private fun notifyExpired(expiredData: Map<Long, DataObject>) {
        _onDataExpired.onNext(expiredData)
    }

    /**
     * Fetches all expired data points in a [DataObject], grouping them by their module id.
     *
     * @param whereClause Should be the SQL WHERE clause, including any required binding '?'
     * @param whereArgs Should be the binding variables for any required in the [whereClause]
     */
    internal fun getExpiredData(
        whereClause: String,
        whereArgs: Array<String>
    ): Map<Long, DataObject> {
        val results = mutableMapOf<Long, DataObject.Builder>()

        db.select(
            Schema.ModuleStorageTable.TABLE_NAME,
            arrayOf(
                Schema.ModuleStorageTable.COLUMN_MODULE_ID,
                Schema.ModuleStorageTable.COLUMN_KEY,
                Schema.ModuleStorageTable.COLUMN_VALUE,
            ),
            whereClause,
            whereArgs,
        ) {
            if (it.count <= 0) return emptyMap()

            val moduleIdColumn = it.getColumnIndex(Schema.ModuleStorageTable.COLUMN_MODULE_ID)
            val keyColumn = it.getColumnIndex(Schema.ModuleStorageTable.COLUMN_KEY)
            val valueColumn = it.getColumnIndex(Schema.ModuleStorageTable.COLUMN_VALUE)

            while (it.moveToNext()) {
                val moduleId = it.getLong(moduleIdColumn)
                val key = it.getString(keyColumn)
                SQLKeyValueRepository.readDataItem(it, valueColumn)?.let { value ->
                    val builder = results[moduleId] ?: DataObject.Builder().also { builder ->
                        results[moduleId] = builder
                    }

                    builder.put(key, value)
                }
            }
        }

        return results.mapValues { it.value.build() }
    }
}