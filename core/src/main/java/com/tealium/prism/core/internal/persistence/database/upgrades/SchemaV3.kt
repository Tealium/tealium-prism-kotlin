package com.tealium.prism.core.internal.persistence.database.upgrades

/**
 * A frozen snapshot of the version 3 identifiers: the table, column, index and trigger names that
 * were on disk at `DATABASE_VERSION` 3.
 *
 * This is a *snapshot*, not a description of the current schema. That is
 * [com.tealium.prism.core.internal.persistence.database.Schema]'s job, and the two are free to
 * diverge from the next schema change onwards. Every upgrade step that reads or writes the
 * version 3 table shape shares these names, so that a typo in one upgrade step is a compile error
 * rather than a runtime surprise, while none of them is exposed to a later rename of a live
 * constant. Nothing here references [com.tealium.prism.core.internal.persistence.database.Schema];
 * every value is a literal.
 *
 * **Never edit this object.** When the schema changes at version N, add a `SchemaVN` holding the
 * identifiers as they are from then on, and point the new upgrade step at that. An upgrade step
 * written against version 3 must keep seeing version 3.
 */
internal object SchemaV3 {

    object Dispatch {
        const val TABLE = "dispatch"

        const val ID = "id"
        const val TIMESTAMP = "timestamp"
        const val DISPATCH = "dispatch"

        const val INDEX_TIMESTAMP = "idx_dispatch_timestamp"
    }

    object Queue {
        const val TABLE = "queue"

        const val DISPATCH_ID = "dispatch_id"
        const val PROCESSOR_ID = "processor_id"

        const val TRIGGER_REMOVE_PROCESSED_DISPATCHES = "queue_remove_processed_dispatches"
    }

    object Module {
        const val TABLE = "module"

        const val ID = "id"
        const val NAME = "name"
    }

    object ModuleStorage {
        const val TABLE = "module_storage"

        const val MODULE_ID = "module_id"
        const val KEY = "key"
        const val VALUE = "value"
        const val EXPIRY = "expiry"
    }

    /**
     * A scalar subquery resolving the id of the module store called [name].
     *
     * Selects nothing when no such store exists, which is what makes an upgrade step that moves
     * rows between stores a no-op rather than an error on a database that never had the source
     * store.
     *
     * [name] is interpolated into the SQL unescaped, so callers pass compile-time constants only,
     * never a runtime or caller-supplied value.
     */
    fun moduleId(name: String): String =
        "(SELECT ${Module.ID} FROM ${Module.TABLE} WHERE ${Module.NAME} = '$name')"
}
