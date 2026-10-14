package com.tealium.prism.core.internal.persistence.database.upgrades

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.tealium.prism.core.internal.persistence.database.upgrades.SchemaV3.Module
import com.tealium.prism.core.internal.persistence.database.upgrades.SchemaV3.ModuleStorage

/**
 * Moves the visitor id rows out of the `shared` store into the `VisitorId` module's own store,
 * renaming their keys to the layout
 * [VisitorStorageImpl][com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl]
 * reads from version 4 on.
 *
 * - **Mechanism:** one `UPDATE OR REPLACE` re-points the rows in place, a `CASE` carrying the
 *   rename. `module_id`, `key` and, for the owner identity alone, `value` are the only columns
 *   ever assigned; `expiry` is never named, so it cannot be mangled, re-encoded or dropped, and
 *   the step need not know how a [com.tealium.prism.core.api.data.DataItem] or an
 *   [com.tealium.prism.core.api.persistence.Expiry] is serialized. Expired rows move verbatim.
 * - **Statements:** one guard query, then two statements.
 * - **Depends on live code:** no. Frozen literals plus [SchemaV3] identifiers only.
 * - **Failure surfaces as:** [android.database.sqlite.SQLiteException] from the guard query,
 *   the module row insert or the row move, which rolls back the helper's upgrade transaction.
 *
 * **Rows are selected by inclusion, never by exclusion.** The shared store is public API: any host
 * app or third-party module can write to it through
 * [TealiumContext.storageProvider][com.tealium.prism.core.api.modules.TealiumContext.storageProvider],
 * so anything [IS_VISITOR_ROW] does not positively recognize stays exactly where it is.
 *
 * The association rows cannot be recognized by their key name, because before this step they were
 * stored under the bare hashed identity, so they are recognized by shape instead:
 *  - the key is a 64 character upper-case hex SHA-256 digest, as produced by the pre-V4
 *  [com.tealium.prism.core.internal.utils.sha256]. From V4 on, `sha256()` produces lower-case
 *  hex, so the digest moved into the target store is lower-cased to match.
 *  - the value is any JSON quoted string, as produced by
 *  [com.tealium.prism.core.api.data.DataItem.toString].
 *
 * The value is deliberately *not* constrained to the shape of a generated visitor id: a
 * [com.tealium.prism.core.api.TealiumConfig.existingVisitorId] is supplied by the host app and so
 * may be any string. A row another module stores with the same shape would be moved too; the SDK
 * itself never writes such rows to the shared store, so this is an accepted risk.
 *
 * The step is idempotent: a second run matches nothing, because the rows are no longer in the
 * source store. Were a target row already present under the same key, `UPDATE OR REPLACE` would
 * replace it rather than raise a primary key conflict. When the source store does not exist its
 * id subquery is NULL, `module_id = NULL` is NULL, and the statement updates nothing. The foreign
 * key from [SchemaV3.ModuleStorage.MODULE_ID] to [SchemaV3.Module] holds throughout because the
 * target module row is inserted by the statement before it.
 *
 * Table and column names come from [SchemaV3]; everything else is this object's own frozen
 * literals, so a later rename of a live constant can't change what this step reads and writes.
 */
internal object V3ToV4 : DatabaseUpgrade {

    override val targetVersion: Int = 4

    /**
     * Checks the guard, then runs the two statements directly. `onUpgrade` is already inside the
     * helper's own transaction, so this opens none of its own.
     *
     * The guard is a separate query, not an `EXISTS` inside the `UPDATE`: if SQLite re-ran an
     * `EXISTS` for every row, it would turn false as soon as the `tealium_visitor_id` row moved,
     * and the rows after it would stay behind.
     *
     * Returning early also means the target module row is not created when there is nothing to
     * move, so an untouched database is left with no trace of this step having run.
     */
    override fun upgrade(db: SQLiteDatabase) {
        val hasVisitorId = DatabaseUtils.longForQuery(db, COUNT_SOURCE_VISITOR_ID, null) > 0
        if (!hasVisitorId) return

        db.execSQL(INSERT_TARGET_MODULE)
        db.execSQL(MOVE_VISITOR_ROWS)
    }

    /**
     * The name of the store that the visitor id data is moved out of.
     */
    const val SOURCE_MODULE_NAME = "shared"

    /**
     * The name of the store that the visitor id data is moved in to.
     */
    const val TARGET_MODULE_NAME = "VisitorId"

    const val SOURCE_KEY_VISITOR_ID = "tealium_visitor_id"
    const val SOURCE_KEY_OWNER_IDENTITY = "current_identity"

    const val TARGET_KEY_VISITOR_ID = "visitor_id"
    const val TARGET_KEY_OWNER_IDENTITY = "owner_identity"
    const val TARGET_PREFIX_IDENTITY = "identity."

    /**
     * The one definition of "this row belongs to the visitor id store".
     *
     * GLOB is case sensitive, and the key digest is upper-case. The value pattern `"*"` needs a
     * leading quote and a separate trailing one, so a lone `"` does not match and no length check
     * is required.
     */
    private const val IS_VISITOR_ROW = """
            ${ModuleStorage.KEY} IN ('$SOURCE_KEY_VISITOR_ID', '$SOURCE_KEY_OWNER_IDENTITY')
                OR (
                    length(${ModuleStorage.KEY}) = 64
                        AND ${ModuleStorage.KEY} NOT GLOB '*[^0-9A-F]*'
                        AND ${ModuleStorage.VALUE} GLOB '"*"'
                )"""

    /**
     * Counts the visitor module's own id row in the source store. Zero means there is nothing here
     * to move.
     *
     * The shape test alone is not enough to claim a row. An association row cannot exist unless
     * the visitor module previously wrote its id, so without [SOURCE_KEY_VISITOR_ID] present any
     * row that merely *looks* like ours belongs to somebody else, such as a host app or third-party
     * module writing 64 character upper-case hex keys of its own, and must be left alone.
     */
    private val COUNT_SOURCE_VISITOR_ID = """
        SELECT COUNT(*) FROM ${ModuleStorage.TABLE}
        WHERE ${ModuleStorage.MODULE_ID} = ${SchemaV3.moduleId(SOURCE_MODULE_NAME)}
            AND ${ModuleStorage.KEY} = '$SOURCE_KEY_VISITOR_ID';
    """.trimIndent()

    /**
     * Ensures the target module row exists, both so that its id can be selected below and so that
     * the foreign key on the updated rows stays satisfied. Matches the conflict behavior of the
     * live module registration.
     */
    private val INSERT_TARGET_MODULE = """
        INSERT OR IGNORE INTO ${Module.TABLE} (${Module.NAME})
        VALUES ('$TARGET_MODULE_NAME');
    """.trimIndent()

    /**
     * Re-points every visitor id row at the target store and renames its key, leaving
     * [SchemaV3.ModuleStorage.EXPIRY] untouched and [SchemaV3.ModuleStorage.VALUE] untouched
     * except for the owner identity, whose value is lower-cased along with the association keys'
     * digests. The `ELSE` branch is reached only by an association row, since [IS_VISITOR_ROW]
     * admits nothing else.
     *
     * Every row's `key` and `value` are read as they stood before the statement ran, so the `CASE
     * key` in the value assignment still sees [SOURCE_KEY_OWNER_IDENTITY], not its renamed target.
     *
     * Association *values* are never lower-cased: they are visitor ids, possibly a host-supplied
     * [com.tealium.prism.core.api.TealiumConfig.existingVisitorId], and must stay as written.
     */
    private val MOVE_VISITOR_ROWS = """
        UPDATE OR REPLACE ${ModuleStorage.TABLE}
        SET ${ModuleStorage.MODULE_ID} = ${SchemaV3.moduleId(TARGET_MODULE_NAME)},
            ${ModuleStorage.KEY} = CASE ${ModuleStorage.KEY}
                WHEN '$SOURCE_KEY_VISITOR_ID' THEN '$TARGET_KEY_VISITOR_ID'
                WHEN '$SOURCE_KEY_OWNER_IDENTITY' THEN '$TARGET_KEY_OWNER_IDENTITY'
                ELSE '$TARGET_PREFIX_IDENTITY' || lower(${ModuleStorage.KEY}) END,
            ${ModuleStorage.VALUE} = CASE ${ModuleStorage.KEY}
                WHEN '$SOURCE_KEY_OWNER_IDENTITY' THEN lower(${ModuleStorage.VALUE})
                ELSE ${ModuleStorage.VALUE} END
        WHERE ${ModuleStorage.MODULE_ID} = ${SchemaV3.moduleId(SOURCE_MODULE_NAME)}
            AND ($IS_VISITOR_ROW);
    """.trimIndent()
}
