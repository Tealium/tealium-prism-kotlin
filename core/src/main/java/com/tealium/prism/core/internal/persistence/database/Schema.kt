package com.tealium.prism.core.internal.persistence.database

import com.tealium.prism.core.api.tracking.Dispatch

/**
 * Constants object holding all constant values for defining all things relating to the Database
 * schema, including table names, column names, SQL that should be used to construct tables and
 * triggers.
 *
 * It also contains legacy constants as well to help support backward compatibility and migrations
 * on upgrades.
 */
object Schema {

    val COUNT_TABLE_NAME: String = """
        SELECT count(*)
        FROM 
            sqlite_master
        WHERE 
            type = 'table' AND 
            name = ?;
    """.trimIndent()

    /**
     * The "dispatch" table contains the stringified version of the payload in JSON format. It also
     * stores the uuid of the [Dispatch] along with the timestamp that it was created for retrieving
     * Dispatches in a FIFO order.
     *
     */
    object DispatchTable {
        const val TABLE_NAME = "dispatch"

        const val COLUMN_ID = "id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_DISPATCH = "dispatch"

        val CREATE_DISPATCH_TABLE = """
            CREATE TABLE $TABLE_NAME(
                $COLUMN_ID           TEXT PRIMARY KEY, 
                $COLUMN_TIMESTAMP    LONG NOT NULL,
                $COLUMN_DISPATCH     TEXT NOT NULL
            );
        """.trimIndent()

        val CREATE_INDEX_TIMESTAMP = """
            CREATE INDEX idx_${TABLE_NAME}_$COLUMN_TIMESTAMP
            ON $TABLE_NAME ($COLUMN_TIMESTAMP)
        """.trimIndent()

        val INSERT_DISPATCH = """
            INSERT OR REPLACE INTO $TABLE_NAME ($COLUMN_ID, $COLUMN_TIMESTAMP, $COLUMN_DISPATCH)
            VALUES (?, ?, ?)
        """.trimIndent()

        val DELETE_EXPIRED: String = """
            DELETE FROM $TABLE_NAME
            WHERE $COLUMN_TIMESTAMP < ?
        """.trimIndent()

        val SELECT_QUEUE_SIZE: String = """
            SELECT COUNT(*) FROM $TABLE_NAME
        """.trimIndent()

        val SELECT_PROCESSOR_QUEUE_SIZE: String = """
            SELECT COUNT(*) FROM ${QueueTable.TABLE_NAME} q 
                INNER JOIN $TABLE_NAME d
                ON d.$COLUMN_ID = q.${QueueTable.COLUMN_DISPATCH_ID} 
        """.trimIndent()

        val SELECT_QUEUE_SIZE_FOR_PROCESSOR: String = """
            SELECT COUNT(*) 
            FROM ${QueueTable.TABLE_NAME}
                INNER JOIN $TABLE_NAME d 
                ON d.$COLUMN_ID = ${QueueTable.COLUMN_DISPATCH_ID}
            WHERE d.${COLUMN_TIMESTAMP} >= ?
            AND ${QueueTable.COLUMN_PROCESSOR_ID} = ?
        """.trimIndent()

        val SELECT_QUEUE_SIZES_BY_PROCESSOR: String = """
            SELECT ${QueueTable.COLUMN_PROCESSOR_ID}, COUNT(*) 
            FROM ${QueueTable.TABLE_NAME}
                INNER JOIN $TABLE_NAME d 
                ON d.$COLUMN_ID = ${QueueTable.COLUMN_DISPATCH_ID}
            WHERE d.${COLUMN_TIMESTAMP} >= ?
            GROUP BY ${QueueTable.COLUMN_PROCESSOR_ID} 
        """.trimIndent()

        val DELETE_OLDEST_X_DISPATCHES: String = """
            DELETE FROM $TABLE_NAME 
            WHERE $COLUMN_ID IN (
                SELECT $COLUMN_ID
                FROM $TABLE_NAME 
                ORDER BY $COLUMN_TIMESTAMP ASC
                LIMIT ?	
            );
        """.trimIndent()

        val MIGRATE_DISPATCH_QUEUE: String = """
            INSERT INTO $TABLE_NAME 
            SELECT `key`, timestamp, value from ${LegacyTables.DISPATCHES_TABLE_NAME}
            WHERE timestamp > ?;
        """.trimIndent()

        fun selectLatestDispatches(
            excluding: Set<Dispatch>,
        ): String = """
            SELECT 
                d.$COLUMN_ID, 
                d.$COLUMN_TIMESTAMP, 
                d.$COLUMN_DISPATCH
            FROM $TABLE_NAME d 
                INNER JOIN ${QueueTable.TABLE_NAME} q
                ON q.${QueueTable.COLUMN_DISPATCH_ID} = d.$COLUMN_ID 
            WHERE q.${QueueTable.COLUMN_PROCESSOR_ID} = ?
                AND $COLUMN_TIMESTAMP >= ? 
                ${excludingClause(excluding)}
            ORDER BY d.$COLUMN_TIMESTAMP ASC 
            LIMIT ?;
         """.trimIndent()


        private fun excludingClause(excluding: Set<Dispatch>): String = if (excluding.isNotEmpty())
            "AND d.$COLUMN_ID NOT IN ${excluding.placeholderList()}"
        else ""

    }

    /**
     * The "queue" table contains only the Dispatch uuid and the id's of the processors that it has
     * been persisted for.
     *
     * There will be an entry mapping each processor to each Dispatch, to ensure that every
     * Dispatch is processed successfully by each processor before being removed from the queue.
     *
     * Once there are no relevant entries left in the [QueueTable] for a given entry in the
     * [DispatchTable] then the [CREATE_TRIGGER_REMOVE_PROCESSED_DISPATCHES] trigger is expected to
     * remove it automatically.
     */
    object QueueTable {
        const val TABLE_NAME = "queue"
        const val TRIGGER_REMOVE_PROCESSED_DISPATCHES = "queue_remove_processed_dispatches"

        const val COLUMN_DISPATCH_ID = "dispatch_id"
        const val COLUMN_PROCESSOR_ID = "processor_id"

        val CREATE_QUEUE_TABLE = """
            CREATE TABLE $TABLE_NAME(
                 $COLUMN_DISPATCH_ID       TEXT NOT NULL, 
                 $COLUMN_PROCESSOR_ID      TEXT NOT NULL,
                 PRIMARY KEY ($COLUMN_DISPATCH_ID, $COLUMN_PROCESSOR_ID),
                 FOREIGN KEY ($COLUMN_DISPATCH_ID) 
                    REFERENCES ${DispatchTable.TABLE_NAME}(${DispatchTable.COLUMN_ID})
                    ON DELETE CASCADE
            );
        """.trimIndent()

        val CREATE_TRIGGER_REMOVE_PROCESSED_DISPATCHES = """
            CREATE TRIGGER IF NOT EXISTS $TRIGGER_REMOVE_PROCESSED_DISPATCHES
                AFTER DELETE ON $TABLE_NAME
            BEGIN
            	DELETE FROM ${DispatchTable.TABLE_NAME}
            	WHERE NOT EXISTS (
            		SELECT $COLUMN_DISPATCH_ID
            	  	FROM $TABLE_NAME
            	  	WHERE $COLUMN_DISPATCH_ID = ${DispatchTable.TABLE_NAME}.${DispatchTable.COLUMN_ID}
            	);
            END;
        """.trimIndent()

        val DELETE_DISPATCH_FOR_PROCESSOR: String = """
            DELETE FROM $TABLE_NAME
            WHERE $COLUMN_DISPATCH_ID = ?
            AND $COLUMN_PROCESSOR_ID = ?;
        """.trimIndent()

        val DELETE_ALL_DISPATCHES_FOR_PROCESSOR: String = """
            DELETE FROM $TABLE_NAME
            WHERE $COLUMN_PROCESSOR_ID = ?;
        """.trimIndent()

        val INSERT_QUEUE_ENTRY = """
            INSERT OR IGNORE INTO $TABLE_NAME ($COLUMN_DISPATCH_ID, $COLUMN_PROCESSOR_ID)
            VALUES (?, ?)
        """.trimIndent()

        fun deleteProcessors(namesNotIn: Set<String>) = """
            DELETE FROM $TABLE_NAME 
            WHERE $COLUMN_PROCESSOR_ID NOT IN ${namesNotIn.placeholderList()}
        """.trimIndent()
    }

    /**
     * The "module" table contains the module id and name of any module that has requested to write
     * data to database.
     *
     * The "id" column is autogenerated
     * The "name" column is taken from the name property of the Module class.
     */
    object ModuleTable {
        const val TABLE_NAME = "module"

        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"

        val CREATE_MODULE_TABLE = """
            CREATE TABLE $TABLE_NAME(
                 $COLUMN_ID   INTEGER PRIMARY KEY, 
                 $COLUMN_NAME TEXT UNIQUE NOT NULL
            );
        """.trimIndent()
    }

    /**
     * The "module_storage" table is a shared key value storage space used by any modules that
     * require basic storage functionality.
     *
     * The `module_id` column is a reference to and entry in the [ModuleTable].
     * The `key` column is the id used to store and retrieve the value
     * The `value` column is the data to be stored in String format
     * The 'expiry' column is a number describing when this data is valid for. See [Expiry]
     */
    object ModuleStorageTable {
        const val TABLE_NAME = "module_storage"

        const val COLUMN_MODULE_ID = "module_id"
        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"
        const val COLUMN_EXPIRY = "expiry"

        val CREATE_MODULE_STORAGE_TABLE = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_MODULE_ID   INTEGER,
                $COLUMN_KEY         TEXT,
                $COLUMN_VALUE       TEXT,
                $COLUMN_EXPIRY      LONG,
            PRIMARY KEY ($COLUMN_MODULE_ID, $COLUMN_KEY),
            FOREIGN KEY ($COLUMN_MODULE_ID)
                REFERENCES ${ModuleTable.TABLE_NAME}(${ModuleTable.COLUMN_ID})
                ON DELETE CASCADE
         );
        """.trimIndent()
    }

    /**
     * This object contains all the schema constructs for versions 1 and 2 of the database. This is
     * primarily made available here to support backwards compatibility and migration of data from
     * the old schema to the new one.
     */
    object LegacyTables {
        const val DISPATCHES_TABLE_NAME = "dispatches"
        const val DATALAYER_TABLE_NAME = "datalayer"
        const val VISITORS_TABLE_NAME = "visitors"

        const val COLUMN_KEY = "key"
        const val COLUMN_VALUE = "value"
        const val COLUMN_EXPIRY = "expiry"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_TYPE = "type"

        internal fun createLegacyTable(tableName: String): String = """
            CREATE TABLE IF NOT EXISTS $tableName (
                $COLUMN_KEY         TEXT PRIMARY KEY,
                $COLUMN_VALUE       TEXT,
                $COLUMN_EXPIRY      LONG,
                $COLUMN_TIMESTAMP   LONG,
                $COLUMN_TYPE        SMALLINT);
            """.trimIndent()
    }
}