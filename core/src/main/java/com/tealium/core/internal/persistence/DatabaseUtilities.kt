@file:JvmName("DatabaseUtilities")

package com.tealium.core.internal.persistence

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.tealium.core.api.persistence.PersistenceException
import kotlin.math.max

/**
 * Handles all begin/end transaction calls, wrapping all SQL statements made by the [block] in a
 * single database transaction.
 */
internal inline fun SQLiteDatabase.transaction(
    block: SQLiteDatabase.() -> Unit
) {
    try {
        beginTransactionNonExclusive()
        try {
            block(this)

            setTransactionSuccessful()
        } catch (e: Exception) {
            throw PersistenceException("Error during transaction.", e)
        } finally {
            endTransaction()
        }
    } catch (e: Exception) {
        throw PersistenceException("Error starting/completing transaction", e)
    }
}

/**
 * Utility wrapper around [SQLiteDatabase.query] to enable omitting optional parameters and simplify
 * code-readability.
 * This method will include all columns and values from the given table. Equivalent to running:
 * ```sql
 * SELECT * FROM <table name>
 * ```
 *
 * Cursor closure is auto-handled after the block is completed.
 *
 * @param from The name of the table to select from.
 * @param block The block of code to receive the [Cursor] in.
 */
internal inline fun <T> SQLiteDatabase.selectAll(from: String, block: (Cursor) -> T) =
    select(from, block = block)

/**
 * Utility wrapper around [SQLiteDatabase.query] to enable omitting optional parameters and simplify
 * code-readability.
 * All params are passed as-is to the database instance.
 *
 * Cursor closure is auto-handled after the block is completed.
 *
 * @param from The name of the table to select from.
 * @param columns The list of column names to select
 * @param where The selection clause after the SQL WHERE statement, excluding the "WHERE" part
 * @param whereArgs The binding arguments for any ?'s in the [where] clause
 * @param groupBy The column name for the SQL GROUP BY statement, excluding the "GROUP BY" part
 * @param having The clause for the SQL HAVING statement, excluding the "HAVING" part
 * @param orderBy The column name for the SQL ORDER BY statement, excluding the "ORDER BY" part
 * @param block The block of code to receive the [Cursor] in.
 * @return the result returned from [block], else null if the cursor was null
 */
internal inline fun <T> SQLiteDatabase.select(
    from: String,
    columns: Array<String>? = null,
    where: String? = null,
    whereArgs: Array<String>? = null,
    groupBy: String? = null,
    having: String? = null,
    orderBy: String? = null,
    block: (Cursor) -> T
) = this.query(from, columns, where, whereArgs, groupBy, having, orderBy)?.use {
    block(it)
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
internal fun Collection<*>.placeholderList(
    prefix: String = "(",
    postfix: String = ")",
    separator: String = ","
): String {
    return this.joinToString(prefix = prefix, postfix = postfix, separator = separator) { "?" }
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

/**
 * Returns the last [count] entries in the list.
 *
 * Negative [count] returns all entries.
 * Zero [count] returns zero entries
 */
fun <E> List<E>.tail(count: Int): List<E> {
    if (count < 0) return this

    return subList(max(0, size - count), size)
}
