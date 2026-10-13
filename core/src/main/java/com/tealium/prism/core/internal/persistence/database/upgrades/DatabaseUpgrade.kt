package com.tealium.prism.core.internal.persistence.database.upgrades

import android.database.sqlite.SQLiteDatabase

/**
 * Defines a single upgrade step of the Tealium Database.
 *
 * Each implementation takes the database from version [targetVersion] - 1 to [targetVersion], and
 * is applied once to a given database file by
 * [com.tealium.prism.core.internal.persistence.database.DatabaseHelper].
 */
internal interface DatabaseUpgrade {

    /**
     * The schema version this step upgrades the database *to*.
     */
    val targetVersion: Int

    /**
     * Applies this step to the given [db].
     *
     * Implementations may throw any [Exception] and need no error handling of their own:
     * [com.tealium.prism.core.internal.persistence.database.DatabaseHelper] normalises every such
     * failure into a
     * [com.tealium.prism.core.internal.persistence.database.DatabaseUpgradeException], and leaves
     * an [Error] unwrapped. A step must not catch exceptions from the database; if it rethrows, it
     * must keep the original as the cause, otherwise the provider cannot classify the failure.
     *
     * The step runs inside the transaction that
     * [SQLiteOpenHelper][android.database.sqlite.SQLiteOpenHelper] opens around `onUpgrade`, so it
     * must not open one of its own, and a failure rolls the whole upgrade back.
     */
    fun upgrade(db: SQLiteDatabase)
}
