package com.tealium.prism.core.internal.persistence.database.upgrades

/**
 * The registry of every upgrade step of the Tealium Database, one [DatabaseUpgrade] object per
 * file.
 *
 * Two rules govern this package:
 *
 *  1. **An upgrade step is frozen once it has been released.** It has already run on real devices,
 *  and its output is the input of every upgrade step after it. Never edit a released upgrade step
 *  to change what it does - add a new file with the next version number instead.
 *
 *  2. **An upgrade step must not reference live constants.**
 *  [com.tealium.prism.core.internal.persistence.database.Schema] describes the schema as it is
 *  *now*, and it will keep moving; an upgrade step describes the schema as it was at one moment
 *  in time. Each upgrade step therefore carries its own frozen copies of the table names, column
 *  names, keys and SQL that it needs, even where those happen to duplicate a live constant today.
 *
 * `onUpgrade` runs inside the helper's transaction; upgrade steps must not open their own. A step
 * may throw any [Exception], which the helper wraps as a
 * [com.tealium.prism.core.internal.persistence.database.DatabaseUpgradeException] - see
 * [DatabaseUpgrade.upgrade].
 *
 * Schema versions below `DatabaseHelper.MIN_SUPPORTED_VERSION` are not upgraded; the file is
 * deleted and recreated.
 *
 * Table and column identifiers for a given schema version belong in one frozen snapshot object per
 * version, `SchemaV<N>`, shared by every upgrade step that targets that shape. The first such
 * snapshot arrives with the first upgrade step that needs it.
 *
 * [com.tealium.prism.core.internal.persistence.database.DatabaseHelper] owns the lifecycle: it
 * creates the current schema from [com.tealium.prism.core.internal.persistence.database.Schema] on
 * a fresh install, and otherwise applies [Upgrades.since] in order.
 *
 * Adding an upgrade step:
 *
 *  - Name the step's object `V{N-1}ToV{N}`, optionally followed by a suffix that does not start
 *  with a digit, e.g. `V3ToV4` or `V3ToV4AddConsentColumn`. `UpgradesTests` enforces this.
 *  - Bump `DatabaseHelper.DATABASE_VERSION` to `N`.
 *  - Register the object in [Upgrades.all].
 *  - Update the live [com.tealium.prism.core.internal.persistence.database.Schema] and
 *  `DatabaseHelper.createCurrentSchema`, and regenerate `EXPECTED_SCHEMA` in `UpgradesTests`.
 *  - Add an upgrade-path test that stages a file at `N-1`, upgrades it, and compares it with a
 *  fresh install structurally - `PRAGMA table_info`, `index_list`/`index_info`,
 *  `foreign_key_list` and trigger SQL - not by `sqlite_master` text, because SQLite rewrites the
 *  stored SQL on `ALTER TABLE`.
 *  - Add an on-device test to `UpgradePathTests` (androidTest) that upgrades a file staged at
 *  every shipped version, because devices run their platform's SQLite rather than the one bundled
 *  with Robolectric, and step SQL that passes the unit tests can fail on older ones.
 */
internal object Upgrades {

    /**
     * Every upgrade step, in the order that they must be applied.
     *
     * The chain starts at version 3, the schema every Prism version before the first upgrade step
     * shipped at; versions 1 and 2 belong to the legacy SDK's own database file, which Prism never
     * opens.
     *
     * The last entry is expected to match
     * [com.tealium.prism.core.internal.persistence.database.DatabaseHelper.DATABASE_VERSION].
     */
    val all: List<DatabaseUpgrade> = listOf<DatabaseUpgrade>(V3ToV4)
        .sortedBy { it.targetVersion }

    /**
     * Returns the upgrade steps needed to take a database from [oldVersion] to [newVersion], in the
     * order that they must be applied.
     *
     * Bounded at both ends: steps at or below [oldVersion] have already been applied, and steps
     * above [newVersion] must not run - which matters when a database is opened at a version below
     * [com.tealium.prism.core.internal.persistence.database.DatabaseHelper.DATABASE_VERSION], as
     * only tests do.
     */
    fun since(
        oldVersion: Int,
        newVersion: Int,
        upgrades: List<DatabaseUpgrade> = all
    ): List<DatabaseUpgrade> =
        upgrades.filter { it.targetVersion in (oldVersion + 1)..newVersion }
}
