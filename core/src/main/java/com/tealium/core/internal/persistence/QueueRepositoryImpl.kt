package com.tealium.core.internal.persistence

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.tealium.core.BuildConfig
import com.tealium.core.api.Dispatch
import com.tealium.core.api.Dispatcher
import com.tealium.core.api.data.bundle.TealiumBundle

class QueueRepositoryImpl(
    private val databaseProvider: DatabaseProvider,
    maximumQueueSize: Int = 100 // TODO - set default from config
) : QueueRepository {

    private val db: SQLiteDatabase
        get() = databaseProvider.database

    var maxQueueSize: Int = maximumQueueSize
        private set

    override val size: Int
        get() {
            return db.rawQuery(SELECT_QUEUE_SIZE, arrayOf())?.use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            } ?: 0
        }

    internal fun dispatcherQueueSize(): Int {
        return db.rawQuery(SELECT_DISPATCHER_QUEUE_SIZE, arrayOf())?.use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        } ?: 0
    }

    private fun getDispatcherNames(): List<String> {
        val list = mutableListOf<String>()
        db.rawQuery(
            SELECT_ALL_DISPATCHER_NAMES,
            arrayOf()
        )?.use { cursor ->
            if (cursor.count <= 0) return emptyList()

            val nameColumnId = cursor.getColumnIndex(Schema.DispatcherTable.COLUMN_NAME)
            while (cursor.moveToNext()) {
                cursor.getString(nameColumnId)?.let { name ->
                    list.add(name)
                }
            }
        }

        return list.toList()
    }

    override fun updateDispatchers(dispatchers: List<Dispatcher>) {
        db.transaction {
            val dispatcherNames = getDispatcherNames()
            val mappedDispatchers = dispatchers.map { it.name }

            val toDisable = dispatcherNames.filter { !mappedDispatchers.contains(it) }
            val toInsert = dispatchers.filter { !dispatcherNames.contains(it.name) }
            val toUpdate = dispatchers.filter { !toInsert.contains(it) }

            toDisable.forEach { name ->
                updateDispatcher(name, false)
            }

            toUpdate.forEach { d ->
                updateDispatcher(d, true)
            }

            toInsert.forEach { d ->
                insertDispatcher(d, true)
            }
        }
        db.migrateDispatchQueue()
    }

    override fun enqueue(dispatch: Dispatch) = enqueue(listOf(dispatch))


    override fun enqueue(dispatches: List<Dispatch>) {
        db.transaction {
            createSpaceIfRequired(dispatches.size)

            dispatches.forEach {
                val rowId = insert(
                    Schema.DispatchTable.TABLE_NAME,
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
            arrayOf(dispatcher.name, count.toString())
        )?.use { cursor ->
            if (cursor.count < 0) return emptyList()

            val idColumnId = cursor.getColumnIndex(Schema.DispatchTable.COLUMN_ID)
            val timestampColumnId = cursor.getColumnIndex(Schema.DispatchTable.COLUMN_TIMESTAMP)
            val dispatchColumnId = cursor.getColumnIndex(Schema.DispatchTable.COLUMN_DISPATCH)

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
                    Schema.DispatchTable.TABLE_NAME,
                    "${Schema.DispatchTable.COLUMN_ID} = ?",
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
        private val SELECT_LATEST_EVENTS: String = """
            SELECT 
                ${Schema.DispatchTable.TABLE_NAME}.${Schema.DispatchTable.COLUMN_ID}, 
                ${Schema.DispatchTable.TABLE_NAME}.${Schema.DispatchTable.COLUMN_TIMESTAMP}, 
                ${Schema.DispatchTable.TABLE_NAME}.${Schema.DispatchTable.COLUMN_DISPATCH}
            FROM ${Schema.DispatchTable.TABLE_NAME} 
                INNER JOIN ${Schema.QueueTable.TABLE_NAME} 
                ON ${Schema.QueueTable.TABLE_NAME}.${Schema.QueueTable.COLUMN_DISPATCH_ID} = ${Schema.DispatchTable.TABLE_NAME}.${Schema.DispatchTable.COLUMN_ID} 
                INNER JOIN ${Schema.DispatcherTable.TABLE_NAME}
                ON ${Schema.DispatcherTable.TABLE_NAME}.${Schema.DispatcherTable.COLUMN_ID} = ${Schema.QueueTable.TABLE_NAME}.${Schema.QueueTable.COLUMN_DISPATCHER_ID}
                WHERE ${Schema.DispatcherTable.TABLE_NAME}.${Schema.DispatcherTable.COLUMN_NAME} = ?
            ORDER BY ${Schema.DispatchTable.TABLE_NAME}.${Schema.DispatchTable.COLUMN_TIMESTAMP} ASC 
            LIMIT ?;
        """.trimIndent()

        private val DELETE_DISPATCH_FOR_DISPATCHER: String = """
            DELETE FROM ${Schema.QueueTable.TABLE_NAME} 
            WHERE ${Schema.QueueTable.TABLE_NAME}.${Schema.QueueTable.COLUMN_DISPATCH_ID} = ?
            AND ${Schema.QueueTable.TABLE_NAME}.${Schema.QueueTable.COLUMN_DISPATCHER_ID} IN (
                SELECT ${Schema.DispatcherTable.COLUMN_ID} 
                FROM ${Schema.DispatcherTable.TABLE_NAME} 
                WHERE ${Schema.DispatcherTable.COLUMN_NAME} = ?
            );
        """.trimIndent()

        private val SELECT_QUEUE_SIZE: String = """
            SELECT COUNT(*) FROM ${Schema.DispatchTable.TABLE_NAME}
        """.trimIndent()

        private val SELECT_DISPATCHER_QUEUE_SIZE: String = """
            SELECT COUNT(*) FROM ${Schema.QueueTable.TABLE_NAME} 
        """.trimIndent()

        private val DELETE_OLDEST_X_DISPATCHES: String = """
            DELETE FROM ${Schema.DispatchTable.TABLE_NAME} 
            WHERE ${Schema.DispatchTable.COLUMN_ID} IN (
                SELECT ${Schema.DispatchTable.COLUMN_ID}
                FROM ${Schema.DispatchTable.TABLE_NAME} 
                ORDER BY ${Schema.DispatchTable.COLUMN_TIMESTAMP} ASC
                LIMIT ?	
            );
        """.trimIndent()

        private val SELECT_ALL_DISPATCHER_NAMES: String = """
            SELECT ${Schema.DispatcherTable.COLUMN_NAME} FROM ${Schema.DispatcherTable.TABLE_NAME}
        """.trimIndent()

        private val MIGRATE_DISPATCH_QUEUE: String = """
            INSERT INTO ${Schema.DispatchTable.TABLE_NAME} 
            SELECT `key`, timestamp, value from ${Schema.LegacyTables.DISPATCHES_TABLE_NAME};
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
        private fun SQLiteDatabase.migrateDispatchQueue() {
            rawQuery(
                COUNT_TABLE_NAME, arrayOf(Schema.LegacyTables.DISPATCHES_TABLE_NAME)
            ).use { cursor ->
                if (cursor.count <= 0) return

                cursor.moveToFirst()
                if (cursor.getInt(0) == 0) return
            }

            transaction {
                execSQL(MIGRATE_DISPATCH_QUEUE)
                dropTableIfExists(Schema.LegacyTables.DISPATCHES_TABLE_NAME)
            }
        }

        /**
         * Executes a SQL UPDATE, updating only the active column for the provided [dispatcher]
         *
         * @param dispatcher The Dispatcher to set active/inactive
         * @param active Whether or not this [dispatcher] is now active or not
         */
        internal fun SQLiteDatabase.updateDispatcher(
            dispatcher: Dispatcher,
            active: Boolean = true
        ) {
            update(
                Schema.DispatcherTable.TABLE_NAME,
                dispatcher.asContentValues(active),
                "${Schema.DispatcherTable.COLUMN_NAME} = ?",
                arrayOf(dispatcher.name)
            )
        }

        /**
         *  Executes a SQL UPDATE, updating only the active column for the provided dispatcher [name]
         *
         * @param name The name of Dispatcher to set active/inactive
         * @param active Whether or not this [Dispatcher] is now active or not
         */
        internal fun SQLiteDatabase.updateDispatcher(name: String, active: Boolean = true) {
            update(
                Schema.DispatcherTable.TABLE_NAME,
                dispatcherContentValues(name, active),
                "${Schema.DispatcherTable.COLUMN_NAME} = ?",
                arrayOf(name)
            )
        }

        /**
         * Inserts a new entry for a [Dispatcher] that has not been registered before.
         * If the dispatcher is already in the database, then it will not be replaced.
         *
         * @param dispatcher The Dispatcher to set active/inactive
         * @param active Whether or not this [dispatcher] is now active or not
         */
        internal fun SQLiteDatabase.insertDispatcher(
            dispatcher: Dispatcher,
            active: Boolean = true
        ) {
            insert(
                Schema.DispatcherTable.TABLE_NAME,
                null,
                dispatcher.asContentValues(active)
            )
        }

        /**
         * Creates the [ContentValues] describing a Dispatcher in the database and whether it is
         * active or not.
         *
         * @param name The name of Dispatcher to set active/inactive
         * @param active Whether or not this [Dispatcher] is now active or not
         */
        private fun dispatcherContentValues(name: String, active: Boolean): ContentValues {
            return ContentValues().also {
                it.put(Schema.DispatcherTable.COLUMN_NAME, name)
                it.put(Schema.DispatcherTable.COLUMN_ACTIVE, active)
            }
        }

        /**
         * Creates the [ContentValues] describing a Dispatcher in the database and whether it is
         * active or not.
         *
         * @param active Whether or not this [Dispatcher] is now active or not
         */
        private fun Dispatcher.asContentValues(active: Boolean = true): ContentValues {
            return dispatcherContentValues(name, active)
        }

        /**
         * Creates the [ContentValues] describing a Dispatch in the database along with its id and
         * creation timestamp.
         */
        private fun Dispatch.asContentValues(): ContentValues {
            return ContentValues().apply {
                put(Schema.DispatchTable.COLUMN_ID, id)
                put(Schema.DispatchTable.COLUMN_TIMESTAMP, timestamp)
                put(Schema.DispatchTable.COLUMN_DISPATCH, payload().toString())
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
    }
}