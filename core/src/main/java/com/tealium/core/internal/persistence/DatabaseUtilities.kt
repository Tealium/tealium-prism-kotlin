@file:JvmName("DatabaseUtilities")

package com.tealium.core.internal.persistence

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.tealium.core.api.Expiry
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.api.data.bundle.TealiumList
import com.tealium.core.api.data.bundle.TealiumValue
import kotlin.math.exp

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
    if (tableName.isBlank()) return

    execSQL(
        "DROP TABLE $tableName"
    )
}

/**
 * Safely drops the table with the provided [tableName] if it exists.
 */
internal fun SQLiteDatabase.dropTableIfExists(tableName: String) {
    if (tableName.isBlank()) return

    execSQL(
        "DROP TABLE IF EXISTS $tableName"
    )
}

/**
 * Generates a SQL placeholder list for a collection. The returned value will create a string with
 * placeholders for the number of items in the Collection.
 * e.g. a collection of two elements will return "(?, ?)"
 */
internal fun Collection<*>.placeholderList(): String {
    return this.joinToString(prefix = "(", postfix = ")", separator = ",") { "?" }
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