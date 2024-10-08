package com.tealium.core.internal.persistence.repositories

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.tealium.core.api.persistence.Expiry
import com.tealium.core.api.persistence.PersistenceException
import com.tealium.core.api.data.DataItem
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.core.internal.persistence.Schema
import com.tealium.core.internal.persistence.getTimestamp
import com.tealium.core.internal.persistence.select
import com.tealium.core.internal.persistence.transaction

/**
 * This is the default implementation for reading and writing [DataItem] objects to and from
 * disk using an SQLite Database.
 *
 * All data is linked to a [moduleId] which is uniquely generated for each
 * [Module][com.tealium.core.api.modules.Module]
 *
 * @param dbProvider The DatabaseProvider to provide a valid [SQLiteDatabase] instance
 * @param moduleId The id of the module that this data belongs to
 * @param tableName The underlying name of the SQL table to query
 */
internal class SQLKeyValueRepository(
    private val dbProvider: DatabaseProvider,
    private val moduleId: Long,
    private val tableName: String = Schema.ModuleStorageTable.TABLE_NAME,
) : KeyValueRepository {

    private val db: SQLiteDatabase
        get() = dbProvider.database

    override fun getAll(): Map<String, DataItem> {
        return getAll(
            selection = IS_OWNER_AND_NOT_EXPIRED,
            selectionArgs = arrayOf(moduleId.toString(), getTimestamp().toString())
        )
    }

    private fun getAll(
        selection: String?,
        selectionArgs: Array<String>?
    ): Map<String, DataItem> {
        val map = mutableMapOf<String, DataItem>()

        db.select(
            tableName,
            arrayOf(
                Schema.ModuleStorageTable.COLUMN_KEY,
                Schema.ModuleStorageTable.COLUMN_VALUE,
            ),
            selection,
            selectionArgs,
        ) { cursor ->
            if (cursor.count <= 0) return emptyMap()

            val columnKeyIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_KEY)
            val columnValueIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_VALUE)

            while (cursor.moveToNext()) {
                val key = cursor.getString(columnKeyIndex)
                readDataItem(cursor, columnValueIndex)?.let {
                    map[key] = it
                }
            }
        }

        return map
    }

    override fun get(key: String): DataItem? {
        return db.select(
            tableName,
            arrayOf(
                Schema.ModuleStorageTable.COLUMN_VALUE,
            ),
            "${Schema.ModuleStorageTable.COLUMN_KEY} = ? AND $IS_OWNER_AND_NOT_EXPIRED",
            arrayOf(key, moduleId.toString(), getTimestamp().toString()),
        ) { cursor ->
            if (cursor.count <= 0) return null

            cursor.moveToFirst()

            readDataItem(cursor)
        }
    }

    override fun upsert(key: String, value: DataItem, expiry: Expiry): Long {
        return try {
            db.insertWithOnConflict(
                tableName,
                null,
                createContentValues(
                    moduleId, key, value, expiry
                ),
                SQLiteDatabase.CONFLICT_REPLACE
            )
        } catch (e: Exception) {
            throw PersistenceException(
                "Error while trying to update/insert item: ($key:$value)",
                e
            )
        }
    }

    override fun delete(key: String): Int {
        return try {
            db.delete(
                tableName,
                "$IS_MODULE_OWNER AND ${Schema.ModuleStorageTable.COLUMN_KEY} = ?",
                arrayOf(moduleId.toString(), key)
            )
        } catch (e: Exception) {
            throw PersistenceException(
                "Error while trying to remove item for key ($key)",
                e
            )
        }
    }

    override fun clear() {
        try {
            db.delete(
                tableName,
                IS_MODULE_OWNER,
                arrayOf(moduleId.toString())
            )
        } catch (e: Exception) {
            throw PersistenceException(
                "Error while trying to clear store.",
                e
            )
        }
    }

    override fun keys(): List<String> {
        val keys = mutableListOf<String>()

        db.select(
            tableName,
            arrayOf(Schema.ModuleStorageTable.COLUMN_KEY),
            IS_OWNER_AND_NOT_EXPIRED,
            arrayOf(moduleId.toString(), getTimestamp().toString()),
        ) { cursor ->
            val columnIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_KEY)

            while (cursor.moveToNext()) {
                keys.add(cursor.getString(columnIndex))
            }
        }

        return keys
    }

    override fun count(): Int {
        return db.rawQuery(
            "SELECT COUNT(*) from $tableName WHERE $IS_OWNER_AND_NOT_EXPIRED",
            arrayOf(moduleId.toString(), getTimestamp().toString())
        )?.use {
            it.moveToFirst()
            val count = it.getInt(0)
            count
        } ?: 0
    }

    override fun contains(key: String): Boolean {
        return db.select(
            tableName,
            arrayOf(Schema.ModuleStorageTable.COLUMN_KEY),
            "${Schema.ModuleStorageTable.COLUMN_KEY} = ? AND $IS_OWNER_AND_NOT_EXPIRED",
            arrayOf(key, moduleId.toString(), getTimestamp().toString()),
        ) { cursor ->
            val count = cursor.count
            cursor.close()
            count > 0
        } ?: false
    }

    @Throws(PersistenceException::class)
    override fun transactionally(
        block: (KeyValueRepository) -> Unit
    ) {
        db.transaction {
            block(this@SQLKeyValueRepository)
        }
    }

    override fun getExpiry(key: String): Expiry? {
        return db.select(
            tableName,
            arrayOf(Schema.ModuleStorageTable.COLUMN_EXPIRY),
            "${Schema.ModuleStorageTable.COLUMN_KEY} = ? AND $IS_MODULE_OWNER",
            arrayOf(key, moduleId.toString()),
        ) { cursor ->
            if (cursor.count <= 0) return null

            val expiryIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_EXPIRY)
            cursor.moveToFirst()

            cursor.getLong(expiryIndex).let {
                Expiry.fromLongValue(it)
            }
        }
    }

    companion object {
        internal const val IS_NOT_EXPIRED_CLAUSE =
            "(${Schema.ModuleStorageTable.COLUMN_EXPIRY} < 0 OR ${Schema.ModuleStorageTable.COLUMN_EXPIRY} > ?)"

        internal const val IS_EXPIRED_CLAUSE =
            "(${Schema.ModuleStorageTable.COLUMN_EXPIRY} >= 0 AND ${Schema.ModuleStorageTable.COLUMN_EXPIRY} < ?)"

        internal const val IS_MODULE_OWNER =
            "${Schema.ModuleStorageTable.COLUMN_MODULE_ID} = ?"

        internal const val IS_OWNER_AND_NOT_EXPIRED =
            "$IS_MODULE_OWNER AND $IS_NOT_EXPIRED_CLAUSE"

        internal fun readDataItem(cursor: Cursor): DataItem? {
            val columnValueIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_VALUE)

            return readDataItem(cursor, columnValueIndex)
        }

        internal fun readDataItem(cursor: Cursor, columnValueIndex: Int): DataItem? {
            val value = cursor.getString(columnValueIndex)

            return deserializeDataItem(value)
        }

        internal fun deserializeDataItem(serialized: String): DataItem? {
            return DataItem.lazy(serialized)
        }

        internal fun createContentValues(
            moduleId: Long,
            key: String,
            value: DataItem,
            expiry: Expiry,
        ): ContentValues {

            return ContentValues().apply {
                put(Schema.ModuleStorageTable.COLUMN_MODULE_ID, moduleId)
                put(Schema.ModuleStorageTable.COLUMN_KEY, key)
                put(Schema.ModuleStorageTable.COLUMN_VALUE, value.toString())
                put(Schema.ModuleStorageTable.COLUMN_EXPIRY, expiry.expiryTime())
            }
        }
    }
}