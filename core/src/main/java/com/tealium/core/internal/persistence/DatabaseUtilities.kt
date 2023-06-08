@file:JvmName("DatabaseUtilities")

package com.tealium.core.internal.persistence

import android.database.sqlite.SQLiteDatabase

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