package com.tealium.core.internal.persistence

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.tealium.core.BuildConfig
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.data.bundle.TealiumBundle
import com.tealium.core.internal.persistence.Schema.DispatchTable
import com.tealium.core.internal.persistence.Schema.DispatcherTable
import com.tealium.core.internal.persistence.Schema.LegacyTables
import com.tealium.core.internal.persistence.Schema.QueueTable
import java.util.concurrent.TimeUnit

class QueueRepositoryImpl(
    private val databaseProvider: DatabaseProvider,
    // TODO - set default from config
    maximumQueueSize: Int = 100,
    // TODO - set default from config
    expiration: TimeFrame = TimeFrame(1, TimeUnit.DAYS)
) : QueueRepository {

    private val db: SQLiteDatabase
        get() = databaseProvider.database

    // TODO - needs to be set from config, and updatable from settings
    var maxQueueSize: Int = maximumQueueSize
        private set

    // TODO - needs to be set from config, and updatable from settings
    var expiration: TimeFrame = expiration
        private set;

    override val size: Int
        get() {
            return db.rawQuery(
                SELECT_QUEUE_SIZE,
                arrayOf(getExpiryTimestamp(expiration).toString())
            )?.use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            } ?: 0
        }

    private var migrationAttempted: Boolean = false

    /**
     * For testing purposes only.
     *
     * Returns the full size of the `queue` table, inclusive of duplicated `dispatch_id` values
     */
    internal fun dispatcherQueueSize(): Int {
        return db.rawQuery(
            SELECT_DISPATCHER_QUEUE_SIZE,
            arrayOf(getExpiryTimestamp(expiration).toString())
        )?.use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        } ?: 0
    }

    override fun updateDispatchers(dispatchers: List<Dispatcher>) {
        db.transaction {
            delete(
                DispatcherTable.TABLE_NAME,
                "${DispatcherTable.COLUMN_NAME} NOT IN ${dispatchers.placeholderList()}",
                dispatchers.map { it.name }
                    .toTypedArray()
            )

            dispatchers.forEach {
                insertDispatcher(it)
            }
        }
        if (!migrationAttempted) {
            migrationAttempted = true
            try {
                db.migrateDispatchQueue(expiration)
            } catch (ignore: Exception) {
                // log this
            }
        }
    }

    override fun enqueue(dispatch: Dispatch) = enqueue(listOf(dispatch))

    override fun enqueue(dispatches: List<Dispatch>) {
        db.transaction {
            createSpaceIfRequired(dispatches.size)

            dispatches.forEach {
                val rowId = insert(
                    DispatchTable.TABLE_NAME,
                    null,
                    it.asContentValues()
                )
                if (rowId < 0) {
                    Log.d(BuildConfig.TAG, "No rows inserted.")
                }
            }
        }
    }

    override fun getQueuedDispatches(count: Int, dispatcher: Dispatcher): List<Dispatch> {
        val dispatches = mutableListOf<Dispatch>()
        db.rawQuery(
            SELECT_LATEST_EVENTS,
            arrayOf(dispatcher.name, getExpiryTimestamp(expiration).toString(), count.toString())
        )?.use { cursor ->
            if (cursor.count < 0) return emptyList()

            val idColumnId = cursor.getColumnIndex(DispatchTable.COLUMN_ID)
            val timestampColumnId = cursor.getColumnIndex(DispatchTable.COLUMN_TIMESTAMP)
            val dispatchColumnId = cursor.getColumnIndex(DispatchTable.COLUMN_DISPATCH)

            while (cursor.moveToNext()) {
                readDispatch(
                    cursor,
                    idColumnId,
                    timestampColumnId,
                    dispatchColumnId
                )?.let { dispatch ->
                    dispatches.add(dispatch)
                }
            }
        }

        return dispatches.toList()
    }

    override fun deleteDispatch(dispatcher: Dispatcher, dispatch: Dispatch) =
        deleteDispatches(dispatcher, listOf(dispatch))

    override fun deleteDispatches(dispatches: List<Dispatch>) {
        db.transaction {
            dispatches.forEach {
                val count = delete(
                    DispatchTable.TABLE_NAME,
                    "${DispatchTable.COLUMN_ID} = ?",
                    arrayOf(it.id)
                )
                if (count < 0) {
                    Log.d(BuildConfig.TAG, "Error deleting dispatches")
                }
            }
        }
    }

    override fun deleteDispatches(dispatcher: Dispatcher, dispatches: List<Dispatch>) {
        db.transaction {
            dispatches.forEach { d ->
                execSQL(
                    DELETE_DISPATCH_FOR_DISPATCHER,
                    arrayOf(d.id, dispatcher.name)
                )
            }
        }
    }

    override fun resize(newSize: Int) {
        maxQueueSize = newSize
        createSpaceIfRequired(0)
    }

    /**
     * If the incoming item count will cause the queue size to exceed the maximum allowed by
     * [maxQueueSize], then this will delete the oldest N items required to make enough space for
     * the incoming items
     */
    private fun createSpaceIfRequired(count: Int) {
        val spaceRequired = spaceRequired(count)
        if (spaceRequired > 0) {
            db.transaction {
                execSQL(
                    DELETE_OLDEST_X_DISPATCHES,
                    arrayOf(spaceRequired)
                )
            }
        }
    }

    /**
     * Calculates the number of items required to be removed in order to fit [count] number of new
     * items into the queue
     */
    private fun spaceRequired(incomingCount: Int): Int {
        return if (maxQueueSize == -1) 0
        else size + incomingCount - maxQueueSize
    }

    companion object {
        private val NOT_EXPIRED_CLAUSE: String = """
            ${DispatchTable.COLUMN_TIMESTAMP} >= ? 
        """.trimIndent()

        private val SELECT_LATEST_EVENTS: String = """
            SELECT 
                d.${DispatchTable.COLUMN_ID}, 
                d.${DispatchTable.COLUMN_TIMESTAMP}, 
                d.${DispatchTable.COLUMN_DISPATCH}
            FROM ${DispatchTable.TABLE_NAME} d 
                INNER JOIN ${QueueTable.TABLE_NAME} q
                ON q.${QueueTable.COLUMN_DISPATCH_ID} = d.${DispatchTable.COLUMN_ID} 
                INNER JOIN ${DispatcherTable.TABLE_NAME} dr
                ON dr.${DispatcherTable.COLUMN_ID} = q.${QueueTable.COLUMN_DISPATCHER_ID}
                WHERE dr.${DispatcherTable.COLUMN_NAME} = ?
                AND $NOT_EXPIRED_CLAUSE
            ORDER BY d.${DispatchTable.COLUMN_TIMESTAMP} ASC 
            LIMIT ?;
        """.trimIndent()

        private val DELETE_DISPATCH_FOR_DISPATCHER: String = """
            DELETE FROM ${QueueTable.TABLE_NAME}
            WHERE ${QueueTable.COLUMN_DISPATCH_ID} = ?
            AND ${QueueTable.COLUMN_DISPATCHER_ID} IN (
                SELECT ${DispatcherTable.COLUMN_ID}
                FROM ${DispatcherTable.TABLE_NAME}
                WHERE ${DispatcherTable.COLUMN_NAME} = ?
            );
        """.trimIndent()

        private val SELECT_QUEUE_SIZE: String = """
            SELECT COUNT(*) FROM ${DispatchTable.TABLE_NAME}
            WHERE $NOT_EXPIRED_CLAUSE
        """.trimIndent()

        private val SELECT_DISPATCHER_QUEUE_SIZE: String = """
            SELECT COUNT(*) FROM ${QueueTable.TABLE_NAME} q 
                INNER JOIN ${DispatchTable.TABLE_NAME} d
                ON d.${DispatchTable.COLUMN_ID} = q.${QueueTable.COLUMN_DISPATCH_ID} 
            WHERE $NOT_EXPIRED_CLAUSE
        """.trimIndent()

        private val DELETE_OLDEST_X_DISPATCHES: String = """
            DELETE FROM ${DispatchTable.TABLE_NAME} 
            WHERE ${DispatchTable.COLUMN_ID} IN (
                SELECT ${DispatchTable.COLUMN_ID}
                FROM ${DispatchTable.TABLE_NAME} 
                ORDER BY ${DispatchTable.COLUMN_TIMESTAMP} ASC
                LIMIT ?	
            );
        """.trimIndent()

        private val MIGRATE_DISPATCH_QUEUE: String = """
            INSERT INTO ${DispatchTable.TABLE_NAME} 
            SELECT `key`, timestamp, value from ${LegacyTables.DISPATCHES_TABLE_NAME}
            WHERE timestamp > ?;
        """.trimIndent()

        private val COUNT_TABLE_NAME: String = """
            SELECT count(*)
            FROM 
                sqlite_master
            WHERE 
                type = 'table' AND 
                name = ?;
        """.trimIndent()

        /**
         * Checks for presence of legacy "dispatches" table, and migrates entries to the new
         * "dispatch" and "queue" tables.
         *
         * This method should only be called once all Dispatchers have been registered, since
         * entries into the "queue" table are generated by triggers based on the entries in the
         * "dispatchers" table.
         */
        private fun SQLiteDatabase.migrateDispatchQueue(expiryTimeFrame: TimeFrame) {
            rawQuery(
                COUNT_TABLE_NAME, arrayOf(LegacyTables.DISPATCHES_TABLE_NAME)
            ).use { cursor ->
                if (cursor.count <= 0) return

                cursor.moveToFirst()
                if (cursor.getInt(0) == 0) return
            }

            transaction {
                execSQL(MIGRATE_DISPATCH_QUEUE, arrayOf(getExpiryTimestamp(expiryTimeFrame)))
                dropTableIfExists(LegacyTables.DISPATCHES_TABLE_NAME)
            }
        }

        /**
         * Inserts a new entry for a [Dispatcher] that has not been registered before.
         * If the dispatcher is already in the database, then it will not be replaced.
         *
         * @param dispatcher The Dispatcher to insert
         */
        internal fun SQLiteDatabase.insertDispatcher(
            dispatcher: Dispatcher,
        ) {
            insertWithOnConflict(
                DispatcherTable.TABLE_NAME,
                null,
                dispatcher.asContentValues(),
                CONFLICT_IGNORE
            )
        }

        /**
         * Creates the [ContentValues] describing a Dispatcher in the database.
         *
         * @param name The name of Dispatcher to insert
         */
        private fun dispatcherContentValues(name: String): ContentValues {
            return ContentValues().also {
                it.put(DispatcherTable.COLUMN_NAME, name)
            }
        }

        /**
         * Creates the [ContentValues] describing a Dispatcher in the database
         *
         */
        private fun Dispatcher.asContentValues(): ContentValues {
            return dispatcherContentValues(name)
        }

        /**
         * Creates the [ContentValues] describing a Dispatch in the database along with its id and
         * creation timestamp.
         */
        private fun Dispatch.asContentValues(): ContentValues {
            return ContentValues().apply {
                put(DispatchTable.COLUMN_ID, id)
                put(DispatchTable.COLUMN_TIMESTAMP, timestamp)
                put(DispatchTable.COLUMN_DISPATCH, payload().toString())
            }
        }

        /**
         * Reads a [Dispatch] from the database using the supplied column id's for each of the
         * relevant column.
         *
         * @param cursor The Database cursor to read the Dispatch from. It is assumed that the
         * cursor has already been moved to the appropriate position via [Cursor.moveToFirst] or
         * similar.
         * @param idColumnId The column id to use to fetch the "id" column; retrieved from
         * [Cursor.getColumnIndex]
         * @param timestampColumnId The column id to use to fetch the "timestamp" column; retrieved
         * from [Cursor.getColumnIndex]
         * @param dispatchColumnId The column id to use to fetch the "dispatch" column; retrieved
         * from [Cursor.getColumnIndex]
         *
         * @return The Dispatch that has been read. If there is no data available in the cursor for
         * the current position, or if the Dispatch cannot be parsed correctly then null is returned
         */
        internal fun readDispatch(
            cursor: Cursor,
            idColumnId: Int,
            timestampColumnId: Int,
            dispatchColumnId: Int
        ): Dispatch? {
            val id = cursor.getStringOrNull(idColumnId)
            val timestamp = cursor.getLongOrNull(timestampColumnId)
            val dispatch = cursor.getStringOrNull(dispatchColumnId)

            if (id == null || timestamp == null || dispatch == null) {
                // TODO - should delete entry too.
                return null
            }

            TealiumBundle.fromString(dispatch)?.let { bundle ->
                return Dispatch.create(
                    id = id,
                    bundle = bundle,
                    timestamp = timestamp
                )
            }

            return null
        }

        /**
         * Returns the oldest unix timestamp (in milliseconds) that would be considered not-expired.
         */
        internal fun getExpiryTimestamp(
            timeFrame: TimeFrame,
            now: Long = getTimestampMilliseconds()
        ): Long {
            val timeFrameMillis = timeFrame.unit.toMillis(timeFrame.number.toLong())
            return now - timeFrameMillis
        }
    }
}