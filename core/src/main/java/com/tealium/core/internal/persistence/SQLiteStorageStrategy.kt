package com.tealium.core.internal.persistence

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.tealium.core.api.Expiry
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList
import com.tealium.core.api.data.bundle.TealiumValue

/**
 * This is the default implementation for reading and writing [TealiumValue] objects to and from
 * disk using an SQLite Database.
 *
 * All data is linked to a [moduleId] which is uniquely generated for each
 * [Module][com.tealium.core.api.Module]
 *
 * @param dbProvider The DatabaseProvider to provide a valid [SQLiteDatabase] instance
 * @param moduleId The id of the module that this data belongs to
 * @param tableName The underlying name of the SQL table to query
 * @param onDataUpdated Delegate to notify when data has been updated
 * @param onDataRemoved Delegate to notify when data has been removed
 */
internal class SQLiteStorageStrategy(
    private val dbProvider: DatabaseProvider,
    private val moduleId: Long,
    private val tableName: String = Schema.ModuleStorageTable.TABLE_NAME,
    private val onDataUpdated: ((String, TealiumValue) -> Unit)? = null,
    private val onDataRemoved: ((Set<String>) -> Unit)? = null
) : DataStorageStrategy<String, TealiumValue> {

    private val db: SQLiteDatabase
        get() = dbProvider.database

    override fun getAll(): Map<String, TealiumValue> {
        return getAll(
            selection = IS_OWNER_AND_NOT_EXPIRED,
            selectionArgs = arrayOf(moduleId.toString(), getTimestamp().toString())
        )
    }

    private fun getAll(
        selection: String?,
        selectionArgs: Array<String>?
    ): Map<String, TealiumValue> {
        val map = mutableMapOf<String, TealiumValue>()

        db.query(
            tableName,
            arrayOf(
                Schema.ModuleStorageTable.COLUMN_KEY,
                Schema.ModuleStorageTable.COLUMN_VALUE,
            ),
            selection,
            selectionArgs,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.count <= 0) return emptyMap()

            val columnKeyIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_KEY)
            val columnValueIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_VALUE)

            while (cursor.moveToNext()) {
                val key = cursor.getString(columnKeyIndex)
                readTealiumValue(cursor, columnValueIndex)?.let {
                    map[key] = it
                }
            }
        }

        return map
    }

    /**
     * TODO - this needs moving to a class that isn't specific to the module
     */
    private fun getExpired(timestamp: Long = getTimestamp()): Map<String, TealiumValue> {
        return getAll(
            selection = IS_EXPIRED_CLAUSE,
            selectionArgs = arrayOf(timestamp.toString())
        )
    }

    override fun get(key: String): TealiumValue? {
        val selection = "${Schema.ModuleStorageTable.COLUMN_KEY} = ? AND $IS_OWNER_AND_NOT_EXPIRED"
        val selectionArgs = arrayOf(key, moduleId.toString(), getTimestamp().toString())

        return db.query(
            tableName,
            arrayOf(
                Schema.ModuleStorageTable.COLUMN_VALUE,
            ),
            selection,
            selectionArgs,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.count <= 0) return null

            cursor.moveToFirst()

            readTealiumValue(cursor)
        }
    }

    override fun insert(key: String, value: TealiumValue, expiry: Expiry) {
        try {
            val inserted =
                db.insertWithOnConflict(
                    tableName,
                    null,
                    createContentValues(
                        moduleId, key, value, expiry
                    ),
                    SQLiteDatabase.CONFLICT_REPLACE
                )

            if (inserted > 0) {
                onDataUpdated?.invoke(key, value)
            }

        } catch (e: Exception) {
//                Logger.dev(BuildConfig.TAG, "Error while trying to insert item")
        }
    }

    override fun update(key: String, value: TealiumValue, expiry: Expiry) {
        try {
            val updated = db.update(
                tableName,
                createContentValues(
                    moduleId, key, value, expiry
                ),
                "$IS_MODULE_OWNER AND ${Schema.ModuleStorageTable.COLUMN_KEY} = ?",
                arrayOf(moduleId.toString(), key)
            )

            if (updated > 0) {
                onDataUpdated?.invoke(key, value)
            }

        } catch (e: Exception) {
//                Logger.dev(BuildConfig.TAG, "Error while trying to update item")
        }
    }

    override fun upsert(key: String, value: TealiumValue, expiry: Expiry) {
        if (contains(key)) {
            update(key, value, expiry)
        } else {
            insert(key, value, expiry)
        }
    }

    override fun delete(key: String) {
        try {
            val deleted = db.delete(
                tableName,
                "$IS_MODULE_OWNER AND ${Schema.ModuleStorageTable.COLUMN_KEY} = ?",
                arrayOf(moduleId.toString(), key)
            )

            if (deleted > 0) {
                onDataRemoved?.invoke(setOf(key))
            }

        } catch (e: Exception) {
//            Logger.dev(BuildConfig.TAG, "Error while trying to delete key: $key")
        }
    }

    override fun clear() {
        try {
            val keys = keys()
            db.delete(
                tableName,
                IS_MODULE_OWNER,
                arrayOf(moduleId.toString())
            )
            onDataRemoved?.invoke(keys.toSet())

        } catch (e: Exception) {
//                Logger.dev(BuildConfig.TAG, "Error while trying to clear database")
        }
    }

    override fun keys(): List<String> {
        val selection = IS_OWNER_AND_NOT_EXPIRED
        val selectionArgs = arrayOf(moduleId.toString(), getTimestamp().toString())

        val keys = mutableListOf<String>()

        db.query(
            tableName,
            arrayOf(Schema.ModuleStorageTable.COLUMN_KEY),
            selection,
            selectionArgs,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            val columnIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_KEY)

            while (cursor.moveToNext()) {
                keys.add(cursor.getString(columnIndex))
            }
        }

        return keys
    }

    override fun count(): Int {
        val selection = "WHERE $IS_OWNER_AND_NOT_EXPIRED"
        val selectionArgs = arrayOf(moduleId.toString(), getTimestamp().toString())

        return db.rawQuery(
            "SELECT COUNT(*) from $tableName $selection",
            selectionArgs
        )?.use {
            it.moveToFirst()
            val count = it.getInt(0)
            count
        } ?: 0
    }

    override fun contains(key: String): Boolean {
        val selection = "${Schema.ModuleStorageTable.COLUMN_KEY} = ? AND $IS_OWNER_AND_NOT_EXPIRED"
        val selectionArgs = arrayOf(key, moduleId.toString(), getTimestamp().toString())

        return db.query(
            tableName,
            arrayOf(Schema.ModuleStorageTable.COLUMN_KEY),
            selection,
            selectionArgs,
            null,
            null,
            null,
            null
        )?.use { cursor ->
            val count = cursor.count
            cursor.close()
            count > 0
        } ?: false
    }

    override fun transactionally(block: (DataStorageStrategy<String, TealiumValue>) -> Unit) {
        transactionally({}, block)
    }

    override fun transactionally(exceptionHandler: (Exception) -> Unit, block: (DataStorageStrategy<String, TealiumValue>) -> Unit) {
        db.transaction(exceptionHandler = exceptionHandler) {
            block(this@SQLiteStorageStrategy)
        }
    }

    override fun getExpiry(key: String): Expiry? {
        return db.query(
            tableName,
            arrayOf(Schema.ModuleStorageTable.COLUMN_EXPIRY),
            "${Schema.ModuleStorageTable.COLUMN_KEY} = ? AND $IS_MODULE_OWNER",
            arrayOf(key, moduleId.toString()),
            null,
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.count <= 0) return null

            val expiryIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_EXPIRY)
            cursor.moveToFirst()

            cursor.getLong(expiryIndex).let {
                Expiry.fromLongValue(it)
            }
        }
    }

    private fun readTealiumValue(cursor: Cursor): TealiumValue? {
        val columnValueIndex = cursor.getColumnIndex(Schema.ModuleStorageTable.COLUMN_VALUE)

        return readTealiumValue(cursor, columnValueIndex)
    }

    private fun readTealiumValue(cursor: Cursor, columnValueIndex: Int): TealiumValue? {
        val value = cursor.getString(columnValueIndex)

        return deserializeTealiumValue(value)
    }

    companion object {
        internal val IS_NOT_EXPIRED_CLAUSE =
            "(${Schema.ModuleStorageTable.COLUMN_EXPIRY} < 0 OR ${Schema.ModuleStorageTable.COLUMN_EXPIRY} > ?)"

        internal val IS_EXPIRED_CLAUSE =
            "(${Schema.ModuleStorageTable.COLUMN_EXPIRY} >= 0 AND ${Schema.ModuleStorageTable.COLUMN_EXPIRY} < ?)"

        internal val IS_MODULE_OWNER =
            "${Schema.ModuleStorageTable.COLUMN_MODULE_ID} = ?"

        internal val IS_OWNER_AND_NOT_EXPIRED =
            "$IS_MODULE_OWNER AND $IS_NOT_EXPIRED_CLAUSE"

        internal fun deserializeTealiumValue(serialized: String, code: Int): TealiumValue? {
            return serializationFor(code)?.let { ser: Serialization ->
                val value: Any? = Serdes.serdeFor(ser.clazz)
                    ?.deserializer
                    ?.deserialize(serialized)

                return TealiumValue.convert(value)
            }
        }

        internal fun deserializeTealiumValue(serialized: String): TealiumValue? {
            return TealiumValue.lazy(serialized)
        }

        internal fun createContentValues(
            moduleId: Long,
            key: String,
            value: TealiumValue,
            expiry: Expiry,
        ): ContentValues {

            return ContentValues().apply {
                put(Schema.ModuleStorageTable.COLUMN_MODULE_ID, moduleId)
                put(Schema.ModuleStorageTable.COLUMN_KEY, key)
                put(Schema.ModuleStorageTable.COLUMN_VALUE, value.toString())
                put(Schema.ModuleStorageTable.COLUMN_EXPIRY, expiry.expiryTime())
            }
        }

        internal fun TealiumValue.serialize(): Pair<String, Int>? {
            return when (value) {
                is String -> value as String to Serialization.STRING.code
                is Int -> Serdes.intSerde().serializer.serialize(value as Int) to Serialization.INT.code
                is Long -> Serdes.longSerde().serializer.serialize(value as Long) to Serialization.LONG.code
                is Double -> Serdes.doubleSerde().serializer.serialize(value as Double) to Serialization.DOUBLE.code
                is Boolean -> Serdes.booleanSerde().serializer.serialize(value as Boolean) to Serialization.BOOLEAN.code
                is TealiumList -> Serdes.tealiumListSerde().serializer.serialize(value as TealiumList) to Serialization.TEALIUM_LIST.code
                is TealiumBundle -> Serdes.tealiumBundleSerde().serializer.serialize(value as TealiumBundle) to Serialization.TEALIUM_BUNDLE.code
                else -> null
            }
        }
    }
}