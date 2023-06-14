@file:JvmName("DatabaseUtilities")

package com.tealium.core.internal.persistence

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.tealium.core.api.Expiry
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList
import com.tealium.core.api.data.bundle.TealiumValue

/**
 * Handles all begin/end transaction calls, wrapping all SQL statements made by the [block] in a
 * single database transaction.
 */
internal fun SQLiteDatabase.transaction(
    exceptionHandler: ((Exception) -> Unit)? = null,
    block: SQLiteDatabase.() -> Unit
) {
    try {
        beginTransaction()

        block(this)

        setTransactionSuccessful()
    } catch (e: Exception) {
        exceptionHandler?.invoke(e)
    } finally {
        endTransaction()
    }
}

/**
 * Drops the table with the provided [tableName].
 */
internal fun SQLiteDatabase.dropTable(tableName: String) {
    execSQL(
        "DROP TABLE $tableName"
    )
}

/**
 * Safely drops the table with the provided [tableName] if it exists.
 */
internal fun SQLiteDatabase.dropTableIfExists(tableName: String) {
    execSQL(
        "DROP TABLE IF EXISTS $tableName"
    )
}

/**
 * Method used to consistently return a timestamp in seconds the same format.
 */
internal fun getTimestamp(): Long {
    return getTimestampMilliseconds() / 1000
}

/**
 * Method used to consistently return a timestamp in milliseconds in the same format.
 */
internal fun getTimestampMilliseconds(): Long {
    return System.currentTimeMillis()
}

internal fun deserializeTealiumValue(serialized: String, code: Int): TealiumValue? {
    return serializationFor(code)?.let { ser: Serialization ->
        val value: Any? = Serdes.serdeFor(ser.clazz)
            ?.deserializer
            ?.deserialize(serialized)

        return TealiumValue.convert(value)
    }
}

internal fun createContentValues(
    moduleId: Long,
    key: String,
    value: TealiumValue,
    expiry: Expiry?,
): ContentValues? {
    val (serializedValue, code) = value.serialize() ?: return null

    return ContentValues().apply {
        put(Schema.ModuleStorageTable.COLUMN_MODULE_ID, moduleId)
        put(Schema.ModuleStorageTable.COLUMN_KEY, key)
        put(Schema.ModuleStorageTable.COLUMN_VALUE, serializedValue)
        put(Schema.ModuleStorageTable.COLUMN_TYPE, code)
        expiry?.let {
            put(Schema.ModuleStorageTable.COLUMN_EXPIRY, it.expiryTime())
        }
    }
}

internal fun TealiumValue.serialize(): Pair<String, Int>? {
    return when (value) {
        is String -> value to Serialization.STRING.code
        is Int -> Serdes.intSerde().serializer.serialize(value) to Serialization.INT.code
        is Long -> Serdes.longSerde().serializer.serialize(value) to Serialization.LONG.code
        is Double -> Serdes.doubleSerde().serializer.serialize(value) to Serialization.DOUBLE.code
        is TealiumList -> Serdes.tealiumListSerde().serializer.serialize(value) to Serialization.TEALIUM_LIST.code
        is TealiumBundle -> Serdes.tealiumBundleSerde().serializer.serialize(value) to Serialization.TEALIUM_BUNDLE.code
        else -> null
    }
}

/**
 * Pre-calculated map of code to [Serialization] mappings
 */
private val serializationLookupByCode = Serialization.values()
    .associateBy { s -> s.code }

/**
 * Helper method to easily lookup the appropriate code for a given class.
 *
 * @return the Int [code] for the provided [clazz] or null
 */
internal fun serializationFor(code: Int): Serialization? {
    return serializationLookupByCode[code]
}