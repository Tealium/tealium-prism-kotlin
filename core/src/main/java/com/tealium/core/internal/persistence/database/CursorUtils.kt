package com.tealium.core.internal.persistence.database

import android.database.Cursor

/**
 * Convenience method to map each row of the given [Cursor]
 */
inline fun <T> Cursor.map(block: Cursor.() -> T): List<T> {
    if (count <= 0) return emptyList()

    val mutableList = mutableListOf<T>()

    while (moveToNext()) {
        mutableList.add(block(this))
    }

    return mutableList.toList()
}

/**
 * Convenience method to map each row of the given [Cursor], filtering out null values
 */
inline fun <T> Cursor.mapNotNull(block: Cursor.() -> T?): List<T> {
    return map(block).filterNotNull()
}

/**
 * Returns either the String value at the given index, or null
 */
internal fun Cursor.getStringOrNull(index: Int): String? {
    return if (isNull(index)) null else getString(index)
}

/**
 * Returns either the Int value at the given index, or null
 */
internal fun Cursor.getIntOrNull(index: Int): Int? {
    return if (isNull(index)) null else getInt(index)
}

/**
 * Returns either the Long value at the given index, or null
 */
internal fun Cursor.getLongOrNull(index: Int): Long? {
    return if (isNull(index)) null else getLong(index)
}

/**
 * Returns either the String value at the given index, or null
 */
internal fun Cursor.getStringOrNull(column: String): String? {
    val index = getColumnIndex(column)
    return getStringOrNull(index)
}

/**
 * Returns either the Int value at the given index, or null
 */
internal fun Cursor.getIntOrNull(column: String): Int? {
    val index = getColumnIndex(column)
    return getIntOrNull(index)
}

/**
 * Returns either the Long value at the given index, or null
 */
internal fun Cursor.getLongOrNull(column: String): Long? {
    val index = getColumnIndex(column)
    return getLongOrNull(index)
}

/**
 * Moves the Cursor to the first row (if exists) and returns the integer value in the first column.
 * This is a convenience method when requesting a single numerical value, i.e. SELECT COUNT(...) etc
 */
internal fun Cursor.getFirstIntOrNull(): Int? {
    moveToFirst()
    return getIntOrNull(0)
}