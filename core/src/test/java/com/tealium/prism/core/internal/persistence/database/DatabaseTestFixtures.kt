package com.tealium.prism.core.internal.persistence.database

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteOpenHelper
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.internal.persistence.database.upgrades.DatabaseUpgrade
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

/**
 * Closes [this] once [block] returns or throws.
 *
 * Not `use`: [SQLiteOpenHelper] is [AutoCloseable] only from API 29, and Robolectric runs API 23 by
 * default here, so `use` would fail the cast on the SDKs these tests actually run against.
 */
internal inline fun <H : SQLiteOpenHelper, T> H.closing(block: (H) -> T): T {
    try {
        return block(this)
    } finally {
        close()
    }
}

/**
 * Creates a database file marked as [version], without using [DatabaseHelper].
 *
 * Going through the helper would create the *current* schema via `onCreate`, so a fixture that wants
 * the real upgrade path to run has to start from a blank file stamped with the old version.
 */
internal fun createEmptyDatabaseAtVersion(context: Context, dbName: String, version: Int) {
    val helper = object : SQLiteOpenHelper(context, dbName, null, version) {
        override fun onCreate(db: SQLiteDatabase?) = Unit
        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) = Unit
    }
    helper.closing { it.writableDatabase }
}

/**
 * A real [DatabaseHelper] for [databaseName], upgraded through [steps] alone, opened at the
 * highest of their versions - or [DatabaseHelper.DATABASE_VERSION] with no steps - unless [version]
 * says otherwise.
 */
internal fun realHelper(
    config: TealiumConfig,
    databaseName: String,
    vararg steps: DatabaseUpgrade,
    version: Int = defaultVersion(steps),
    errorHandler: DatabaseErrorHandler = DefaultDatabaseErrorHandler(),
): DatabaseHelper = DatabaseHelper(
    config,
    databaseName,
    version = version,
    upgrades = steps.toList(),
    errorHandler = errorHandler
)

/** The highest of [steps]' versions, or [DatabaseHelper.DATABASE_VERSION] when there are none. */
private fun defaultVersion(steps: Array<out DatabaseUpgrade>): Int =
    steps.maxOfOrNull { it.targetVersion } ?: DatabaseHelper.DATABASE_VERSION

/** A real [DatabaseHelper] whose `onCreate` fails as it would on a full disk. */
internal class FullDiskOnCreateHelper(
    config: TealiumConfig,
    databaseName: String,
    errorHandler: DatabaseErrorHandler
) : DatabaseHelper(config, databaseName, errorHandler = errorHandler) {

    override fun onCreate(db: SQLiteDatabase?) =
        throw SQLiteFullException("database or disk is full")
}

/** A [DatabaseUpgrade] to [targetVersion] whose only behavior is [body]. */
internal fun upgradeStep(targetVersion: Int, body: (SQLiteDatabase) -> Unit = {}): DatabaseUpgrade =
    object : DatabaseUpgrade {
        override val targetVersion: Int = targetVersion
        override fun upgrade(db: SQLiteDatabase) = body(db)
    }

/** A table no version of the schema defines, standing in for one carried over from an old file. */
internal const val MARKER_TABLE = "marker_from_the_old_file"

/**
 * A file at version 0 that already contains a table the schema creates, so the real helper's
 * `onCreate` fails trying to create it a second time.
 */
internal fun stageFileWithPreexistingDispatchTable(context: Context, databaseName: String) {
    context.openOrCreateDatabase(databaseName, 0, null).use { preexisting ->
        preexisting.execSQL("CREATE TABLE ${Schema.DispatchTable.TABLE_NAME} (id INTEGER);")
    }
}

/**
 * The user-defined tables, indexes and triggers of [this] database, keyed by name, each value being
 * `type|name|tbl_name|sql` so that two databases can be compared directly.
 *
 * Bookkeeping rows are excluded: SQLite's own (`sqlite_sequence`, implicit `sqlite_autoindex_*`) are
 * a by-product of how a table happens to be populated, and `android_metadata` is created by the
 * platform in every database. Neither is part of the schema this SDK defines.
 */
internal fun SQLiteDatabase.schemaObjects(): Map<String, String> {
    val objects = mutableMapOf<String, String>()

    rawQuery(
        "SELECT type, name, tbl_name, sql FROM sqlite_master " +
            "WHERE name NOT LIKE 'sqlite_%' AND name != 'android_metadata'",
        null
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val name = cursor.getString(1)
            objects[name] = listOf(
                cursor.getString(0),
                name,
                cursor.getString(2),
                cursor.getString(3),
            ).joinToString("|")
        }
    }

    return objects
}

/** A table no version of the schema defines, standing in for data an upgrade step reads. */
internal const val LEGACY_TABLE = "legacy"

/**
 * A file at [DatabaseHelper.DATABASE_VERSION] holding [LEGACY_TABLE], big enough that its rows live
 * on pages beyond its root, then [markerTable] so that a replaced file can be told from the original.
 * Every connection is closed on return.
 */
internal fun createDatabaseWithLegacyTable(context: Context, path: String, markerTable: String) {
    createEmptyDatabaseAtVersion(context, path, DatabaseHelper.DATABASE_VERSION)
    onDisk(path, SQLiteDatabase.OPEN_READWRITE) { db ->
        db.execSQL("CREATE TABLE $LEGACY_TABLE (value TEXT);")
        val row = "x".repeat(LEGACY_ROW_CHARS)
        repeat(LEGACY_ROWS) {
            db.execSQL("INSERT INTO $LEGACY_TABLE VALUES (?);", arrayOf(row))
        }
        db.execSQL("CREATE TABLE $markerTable (id INTEGER);")
    }
}

/** The root page of [LEGACY_TABLE]'s b-tree, which the open sequence never reads. */
internal fun legacyRootPage(path: String): Int = rootPage(path, LEGACY_TABLE)

/** The root page of [table]'s b-tree, as recorded in `sqlite_master`. */
internal fun rootPage(path: String, table: String): Int =
    onDisk(path, SQLiteDatabase.OPEN_READONLY) { db ->
        db.rawQuery("SELECT rootpage FROM sqlite_master WHERE name = ?", arrayOf(table))
            .use { cursor ->
                check(cursor.moveToFirst()) { "no table named $table in $path" }
                cursor.getInt(0)
            }
    }

/** Reads every row of [LEGACY_TABLE], as an upgrade step over it would. */
internal fun readLegacyTable(db: SQLiteDatabase) {
    db.rawQuery("SELECT count(*) FROM $LEGACY_TABLE", null).use { cursor ->
        while (cursor.moveToNext()) Unit
    }
}

/**
 * Overwrites the whole of 1-based [page] with seeded random bytes, reading the page size from the
 * file header (bytes 16-17, big-endian) rather than assuming it.
 */
internal fun corruptPage(path: String, page: Int) {
    RandomAccessFile(File(path), "rw").use { file ->
        file.seek(PAGE_SIZE_OFFSET)
        val pageSize = file.readUnsignedShort()
        file.seek((page - 1).toLong() * pageSize)
        file.write(Random(CORRUPTION_SEED).nextBytes(pageSize))
    }
}

/**
 * Destroys the root page of the schema b-tree while leaving the 100-byte file header valid, which
 * is what the engine reports as `SQLITE_CORRUPT` the first time it reads `sqlite_master`.
 */
internal fun corruptSchemaPage(path: String) {
    RandomAccessFile(File(path), "rw").use { file ->
        file.seek(HEADER_BYTES.toLong())
        file.write(ByteArray(PAGE_BYTES - HEADER_BYTES) { 0x5A })
    }
}

/** Opens the database file at [path] with [flags], running [block] and closing it on return. */
internal fun <T> onDisk(path: String, flags: Int, block: (SQLiteDatabase) -> T): T =
    SQLiteDatabase.openDatabase(path, null, flags).use(block)

private const val LEGACY_ROWS = 200
private const val LEGACY_ROW_CHARS = 200

/** The SQLite file header, which stays valid so the file is still recognisably a database. */
private const val HEADER_BYTES = 100

/** The default page size, so the whole of page 1 after the header is overwritten. */
private const val PAGE_BYTES = 4096

/** Where the header stores the page size, as a big-endian two-byte value. */
private const val PAGE_SIZE_OFFSET = 16L

/** Fixed, so the bytes written over the page are the same on every run. */
private const val CORRUPTION_SEED = 42
