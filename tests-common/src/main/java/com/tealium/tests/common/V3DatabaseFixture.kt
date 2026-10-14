package com.tealium.tests.common

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.File

// region version 3 fixtures

/**
 * The DDL a version 3 file carries, frozen as literals in the form `sqlite_master` stores them,
 * rather than built from the live `Schema`: a fixture derived from the code under test would follow
 * any change to it, and the whole point of staging an old file is that it must not. Copied from the
 * version 3 schema and never edited; a later schema change adds a V4 fixture beside this one.
 *
 * The text is the one `UpgradesTests.EXPECTED_SCHEMA` pins for a fresh install at version 3, so a
 * file staged from it has the same stored trigger SQL a real version 3 install has.
 *
 * Shared by the unit and the instrumented upgrade tests so that both stage the same file.
 */
object V3Ddl {
    const val CREATE_DISPATCH = "CREATE TABLE dispatch(\n" +
        "    id           TEXT PRIMARY KEY, \n" +
        "    timestamp    LONG NOT NULL,\n" +
        "    dispatch     TEXT NOT NULL\n" +
        ")"

    const val CREATE_INDEX_TIMESTAMP = "CREATE INDEX idx_dispatch_timestamp\n" +
        "ON dispatch (timestamp)"

    const val CREATE_QUEUE = "CREATE TABLE queue(\n" +
        "     dispatch_id       TEXT NOT NULL, \n" +
        "     processor_id      TEXT NOT NULL,\n" +
        "     PRIMARY KEY (dispatch_id, processor_id),\n" +
        "     FOREIGN KEY (dispatch_id) \n" +
        "        REFERENCES dispatch(id)\n" +
        "        ON DELETE CASCADE\n" +
        ")"

    const val CREATE_MODULE = "CREATE TABLE module(\n" +
        "     id   INTEGER PRIMARY KEY, \n" +
        "     name TEXT UNIQUE NOT NULL\n" +
        ")"

    const val CREATE_MODULE_STORAGE = "CREATE TABLE module_storage (\n" +
        "       module_id   INTEGER,\n" +
        "       key         TEXT,\n" +
        "       value       TEXT,\n" +
        "       expiry      LONG,\n" +
        "   PRIMARY KEY (module_id, key),\n" +
        "   FOREIGN KEY (module_id)\n" +
        "       REFERENCES module(id)\n" +
        "       ON DELETE CASCADE\n" +
        ")"

    const val CREATE_TRIGGER_REMOVE_PROCESSED_DISPATCHES =
        "CREATE TRIGGER queue_remove_processed_dispatches\n" +
            "    AFTER DELETE ON queue\n" +
            "BEGIN\n" +
            "\tDELETE FROM dispatch\n" +
            "\tWHERE NOT EXISTS (\n" +
            "\t\tSELECT dispatch_id\n" +
            "\t  \tFROM queue\n" +
            "\t  \tWHERE dispatch_id = dispatch.id\n" +
            "\t);\n" +
            "END"

    /** The name of the store every version 3 install registers first. */
    const val SHARED_MODULE = "shared"

    const val VERSION = 3
}

/**
 * Stages a genuine version 3 file at [path]: the frozen [V3Ddl] statements, optionally the
 * `shared` module row, then [seed], stamped with `PRAGMA user_version = 3`. Closed on return.
 *
 * Does not go through `DatabaseHelper`, which would create the *current* schema via `onCreate`.
 */
fun createV3Database(
    path: String,
    withSharedStore: Boolean = true,
    seed: SQLiteDatabase.() -> Unit = {},
) {
    File(path).parentFile?.mkdirs()
    SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
        db.execSQL(V3Ddl.CREATE_DISPATCH)
        db.execSQL(V3Ddl.CREATE_INDEX_TIMESTAMP)
        db.execSQL(V3Ddl.CREATE_QUEUE)
        db.execSQL(V3Ddl.CREATE_MODULE)
        db.execSQL(V3Ddl.CREATE_MODULE_STORAGE)
        db.execSQL(V3Ddl.CREATE_TRIGGER_REMOVE_PROCESSED_DISPATCHES)
        if (withSharedStore) {
            db.execSQL("INSERT INTO module (name) VALUES ('${V3Ddl.SHARED_MODULE}');")
        }
        db.seed()
        db.version = V3Ddl.VERSION
    }
}

/** Inserts one `module_storage` row into the store called [moduleName], as a version 3 file holds it. */
fun SQLiteDatabase.insertV3StorageRow(
    moduleName: String,
    key: String,
    value: String,
    expiry: Long = -1L
) = execSQL(
    "INSERT INTO module_storage (module_id, key, value, expiry) " +
        "VALUES ((SELECT id FROM module WHERE name = ?), ?, ?, ?);",
    arrayOf(moduleName, key, value, expiry)
)

// endregion

// region structural comparison

/**
 * The structure of one table, as SQLite itself reports it rather than as the DDL text reads.
 *
 * @param columns every `PRAGMA table_info` row as `name|type|notnull|dflt_value|pk`, in column order
 * @param indexes every index on the table from `PRAGMA index_list`, keyed by name, each value being
 * `unique|origin` followed by its `PRAGMA index_info` columns in order
 * @param foreignKeys every `PRAGMA foreign_key_list` row as `table|from|to|on_update|on_delete|match`,
 * in declaration order
 */
data class TableStructure(
    val columns: List<String>,
    val indexes: Map<String, String>,
    val foreignKeys: List<String>,
)

/**
 * The structure of a database: its user tables and their triggers' SQL.
 *
 * @param tables every table the SDK defines, keyed by name
 * @param triggers every trigger, keyed by name, with the SQL `sqlite_master` stores for it
 */
data class SchemaStructure(
    val tables: Map<String, TableStructure>,
    val triggers: Map<String, String>,
)

/**
 * Snapshots the structure of [this] database so that an upgraded file can be compared with a fresh
 * install: `PRAGMA table_info`, `PRAGMA index_list` with `index_info`, and `PRAGMA foreign_key_list`
 * per table, plus each trigger's SQL from `sqlite_master`.
 *
 * Not the `sqlite_master` text of the tables and indexes: SQLite rewrites the stored SQL on
 * `ALTER TABLE`, so two databases of identical shape can carry different text once one has been
 * altered. Trigger SQL is compared as text because there is no PRAGMA that describes a trigger.
 *
 * `android_metadata` and SQLite's internal `sqlite_*` tables are excluded. The implicit
 * `sqlite_autoindex_*` indexes are included in each table's `indexes`, since they carry its
 * PRIMARY KEY and UNIQUE constraints.
 */
fun SQLiteDatabase.structure(): SchemaStructure {
    val tableNames = rawQuery(
        "SELECT name FROM sqlite_master WHERE type = 'table' " +
            "AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY name",
        null
    ).use { cursor -> cursor.strings { it.getString(0) } }

    val tables = tableNames.associateWith { table ->
        TableStructure(
            columns = pragmaRows("table_info", table) { c ->
                listOf("name", "type", "notnull", "dflt_value", "pk").map(c::stringAt)
                    .joinToString("|")
            },
            indexes = pragmaRows("index_list", table) { c ->
                c.stringAt("name").orEmpty() to listOf("unique", "origin").map(c::stringAt)
            }.associate { (index, flags) ->
                val columns = pragmaRows("index_info", index) { c -> c.stringAt("name") }
                index to (flags + columns).joinToString("|")
            },
            foreignKeys = pragmaRows("foreign_key_list", table) { c ->
                listOf("table", "from", "to", "on_update", "on_delete", "match")
                    .map(c::stringAt).joinToString("|")
            },
        )
    }

    val triggers = rawQuery(
        "SELECT name, sql FROM sqlite_master WHERE type = 'trigger' ORDER BY name",
        null
    ).use { cursor -> cursor.strings { it.getString(0) to it.getString(1) } }.toMap()

    return SchemaStructure(tables, triggers)
}

/** Every row of `PRAGMA <pragma>(<argument>)`, mapped by [row]. Rows come back in the order SQLite reports them. */
private fun <T> SQLiteDatabase.pragmaRows(
    pragma: String,
    argument: String,
    row: (Cursor) -> T
): List<T> = rawQuery("PRAGMA $pragma(\"$argument\")", null).use { it.strings(row) }

private fun <T> Cursor.strings(row: (Cursor) -> T): List<T> =
    buildList { while (moveToNext()) add(row(this@strings)) }

/** The column called [name] as text, or `null` when it is NULL or the running SQLite does not report it. */
private fun Cursor.stringAt(name: String): String? =
    getColumnIndex(name).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }

// endregion
