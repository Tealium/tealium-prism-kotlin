package com.tealium.core.internal.persistence.repositories

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.days
import com.tealium.core.internal.persistence.DatabaseProvider
import com.tealium.core.internal.persistence.Schema.COUNT_TABLE_NAME
import com.tealium.core.internal.persistence.Schema.DispatchTable
import com.tealium.core.internal.persistence.Schema.LegacyTables
import com.tealium.core.internal.persistence.Schema.QueueTable
import com.tealium.core.internal.persistence.dropTableIfExists
import com.tealium.core.internal.persistence.getFirstIntOrNull
import com.tealium.core.internal.persistence.getLongOrNull
import com.tealium.core.internal.persistence.getStringOrNull
import com.tealium.core.internal.persistence.getTimestampMilliseconds
import com.tealium.core.internal.persistence.mapNotNull
import com.tealium.core.internal.persistence.tail
import com.tealium.core.internal.persistence.transaction


class SQLQueueRepository(
    private val databaseProvider: DatabaseProvider,
    maxQueueSize: Int = 100,
    expiration: TimeFrame = 1.days
) : QueueRepository {

    var maxQueueSize: Int = maxQueueSize
        private set

    var expiration: TimeFrame = expiration
        private set

    private val db: SQLiteDatabase
        get() = databaseProvider.database

    override val size: Int
        get() {
            return db.rawQuery(
                DispatchTable.SELECT_QUEUE_SIZE,
                arrayOf()
            ).use { cursor ->
                cursor.getFirstIntOrNull() ?: 0
            }
        }

    private var migrationAttempted: Boolean = false

    /**
     * For testing purposes only.
     *
     * Returns the full size of the `queue` table, inclusive of duplicated `dispatch_id` values
     */
    internal fun processorQueueSize(): Int {
        return db.rawQuery(
            DispatchTable.SELECT_PROCESSOR_QUEUE_SIZE,
            arrayOf()
        ).use { cursor ->
            cursor.getFirstIntOrNull() ?: 0
        }
    }

    override fun deleteQueues(forProcessorsNotIn: Set<String>) {
        db.transaction {
            execSQL(
                QueueTable.deleteProcessors(forProcessorsNotIn),
                forProcessorsNotIn.toTypedArray()
            )
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

    override fun storeDispatch(dispatches: List<Dispatch>, processors: Set<String>) {
        if (dispatches.isEmpty() || processors.isEmpty()) return

        db.transaction {
            createSpaceIfRequired(dispatches.size)

            dispatches.tail(maxQueueSize)
                .forEach { dispatch ->
                    insertOrUpdateDispatch(dispatch, processors)
                }
        }
    }

    private fun insertOrUpdateDispatch(dispatch: Dispatch, processors: Set<String>) {
        db.execSQL(
            DispatchTable.INSERT_DISPATCH,
            arrayOf(dispatch.id, dispatch.timestamp, dispatch.payload())
        )
        insertQueueEntries(dispatch, processors)
    }

    private fun insertQueueEntries(dispatch: Dispatch, processors: Set<String>) {
        processors.forEach { processor ->
            db.execSQL(
                QueueTable.INSERT_QUEUE_ENTRY,
                arrayOf(dispatch.id, processor)
            )
        }
    }

    override fun getQueuedDispatches(count: Int, processor: String): List<Dispatch> {
        return getQueuedDispatches(count, emptySet(), processor)
    }

    override fun getQueuedDispatches(
        count: Int,
        excluding: Set<Dispatch>,
        processor: String
    ): List<Dispatch> {
        return db.rawQuery(
            DispatchTable.selectLatestDispatches(excluding),
            arrayOf(
                processor,
                getExpiryTimestamp().toString()
            ) + excluding.map { it.id } + arrayOf(
                count.toString()
            )
        ).use { cursor ->
            cursor.mapNotNull(Companion::readDispatch)
        }
    }

    override fun deleteDispatch(dispatch: Dispatch, processor: String) {
        db.execSQL(
            QueueTable.DELETE_DISPATCH_FOR_PROCESSOR,
            arrayOf(dispatch.id, processor)
        )
    }

    override fun deleteDispatches(dispatches: List<Dispatch>, processor: String) {
        db.transaction {
            dispatches.forEach { d ->
                deleteDispatch(d, processor)
            }
        }
    }

    override fun deleteAllDispatches(processor: String) {
        db.transaction {
            execSQL(
                QueueTable.DELETE_ALL_DISPATCHES_FOR_PROCESSOR,
                arrayOf(processor)
            )
        }
    }

    override fun resize(newSize: Int) {
        maxQueueSize = newSize
        createSpaceIfRequired(0)
    }

    override fun setExpiration(expiration: TimeFrame) {
        deleteExpired(getExpiryTimestamp(minOf(this.expiration, expiration)))

        this.expiration = expiration
    }

    private fun deleteExpired(expiryTime: Long = getExpiryTimestamp()) {
        db.transaction {
            execSQL(
                DispatchTable.DELETE_EXPIRED,
                arrayOf(expiryTime)
            )
        }
    }

    private fun getExpiryTimestamp(): Long {
        return getExpiryTimestamp(expiration, getTimestampMilliseconds())
    }

    /**
     * If the incoming item count will cause the queue size to exceed the maximum allowed by
     * [maxQueueSize], then this will delete the oldest N items required to make enough space for
     * the incoming items
     */
    private fun createSpaceIfRequired(count: Int) {
        val spaceRequired = spaceRequired(size, count, maxQueueSize)
        if (spaceRequired > 0) {
            db.transaction {
                execSQL(
                    DispatchTable.DELETE_OLDEST_X_DISPATCHES,
                    arrayOf(spaceRequired)
                )
            }
        }
    }

    companion object {

        /**
         * Calculates the number of items required to be removed in order to fit [count] number of new
         * items into the queue
         *
         * @param size The current size of the queue
         * @param incomingCount The number of items required to be inserted
         * @param max The maximum size that the queue can grow to
         */
        private fun spaceRequired(size: Int, incomingCount: Int, max: Int): Int {
            return if (max == -1) 0
            else size + incomingCount - max
        }

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
                val count = cursor.getFirstIntOrNull()
                if (count == null || count == 0) return
            }

            transaction {
                execSQL(
                    DispatchTable.MIGRATE_DISPATCH_QUEUE,
                    arrayOf(getExpiryTimestamp(expiryTimeFrame))
                )
                dropTableIfExists(LegacyTables.DISPATCHES_TABLE_NAME)
            }
        }

        /**
         * Reads a [Dispatch] from the database using the supplied column id's for each of the
         * relevant column.
         *
         * @param cursor The Database cursor to read the Dispatch from. It is assumed that the
         * cursor has already been moved to the appropriate position via [Cursor.moveToFirst], [Cursor.moveToNext] or
         * similar.
         *
         * @return The Dispatch that has been read. If there is no data available in the cursor for
         * the current position, or if the Dispatch cannot be parsed correctly then null is returned
         */
        internal fun readDispatch(
            cursor: Cursor,
        ): Dispatch? {
            val id = cursor.getStringOrNull(DispatchTable.COLUMN_ID)
            val timestamp = cursor.getLongOrNull(DispatchTable.COLUMN_TIMESTAMP)
            val dispatch = cursor.getStringOrNull(DispatchTable.COLUMN_DISPATCH)

            if (id == null || timestamp == null || dispatch == null) {
                // TODO - should delete entry too.
                return null
            }

            return DataObject.fromString(dispatch)?.let { dataObject ->
                return Dispatch.create(
                    id = id,
                    dataObject = dataObject,
                    timestamp = timestamp
                )
            }
        }

        /**
         * Returns the oldest unix timestamp (in milliseconds) that would be considered not-expired.
         */
        internal fun getExpiryTimestamp(
            timeFrame: TimeFrame,
            now: Long = getTimestampMilliseconds()
        ): Long {
            val timeFrameMillis = timeFrame.unit.toMillis(timeFrame.number)
            return now - timeFrameMillis
        }
    }
}