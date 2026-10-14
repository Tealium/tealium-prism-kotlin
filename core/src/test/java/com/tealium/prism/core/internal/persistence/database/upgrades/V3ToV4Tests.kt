package com.tealium.prism.core.internal.persistence.database.upgrades

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.VisitorIdStorageImpl
import com.tealium.prism.core.internal.persistence.database.DatabaseHelper
import com.tealium.prism.core.internal.persistence.database.DatabaseProvider
import com.tealium.prism.core.internal.persistence.database.DatabaseUpgradeException
import com.tealium.prism.core.internal.persistence.database.FileDatabaseProvider
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.database.Schema
import com.tealium.prism.core.internal.persistence.database.closing
import com.tealium.prism.core.internal.persistence.database.onDisk
import com.tealium.prism.core.internal.persistence.database.realHelper
import com.tealium.prism.core.internal.persistence.database.upgradeStep
import com.tealium.prism.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.prism.core.internal.utils.sha256
import com.tealium.tests.common.SchemaStructure
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.V3Ddl
import com.tealium.tests.common.createV3Database
import com.tealium.tests.common.getDefaultConfig
import com.tealium.tests.common.insertV3StorageRow
import com.tealium.tests.common.structure
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Behavior of [V3ToV4] on the real, file-backed [DatabaseHelper].
 *
 * Each case stages a genuine version 3 file by hand, from frozen DDL literals rather than the live
 * [Schema], seeds it with raw SQL, and then opens it with [DatabaseHelper], so that
 * `onUpgrade(3, 4)` runs the real registered chain.
 *
 * Outcome assertions read `module_storage` raw, joined to `module.name`, and compare against
 * hard-coded literals: the live store constants are exactly what an upgrade step must *not*
 * follow, so an expectation built from them would pass even if both were renamed together.
 */
@RunWith(RobolectricTestRunner::class)
class V3ToV4Tests {

    private lateinit var app: Application
    private lateinit var config: TealiumConfig
    private lateinit var dbName: String
    private lateinit var dbPath: String
    private lateinit var freshDbName: String

    // v3 on-disk digests were upper-case; sha256() itself now returns lower-case, so the fixtures
    // are forced back to upper-case to stay faithful to what was actually written to disk.
    private val hashA = "identity_a".sha256().uppercase()
    private val hashB = "identity_b".sha256().uppercase()
    private val hashC = "identity_c".sha256().uppercase()
    private val hashD = "identity_d".sha256().uppercase()
    private val hashE = "identity_e".sha256().uppercase()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        config = getDefaultConfig(app)
        dbName = "v3-to-v4-${UUID.randomUUID()}.db"
        // an absolute path: DatabaseHelper.deleteDatabase resolves databaseName as a File, which
        // production satisfies since databaseName(config) is absolute
        dbPath = app.getDatabasePath(dbName).absolutePath
        freshDbName = "v4-fresh-${UUID.randomUUID()}.db"
    }

    @After
    fun tearDown() {
        app.deleteDatabase(dbName)
        app.deleteDatabase(freshDbName)
    }

    // region moves

    @Test
    fun onUpgrade_MovesAndRenamesVisitorRows_When_SharedStoreIsPopulated() {
        createV3Database(dbPath) { seedPopulatedSharedStore() }

        openUpgraded { helper, db ->
            assertEquals(3, helper.oldVersion)
            assertTrue(helper.wasUpgraded)
            assertEquals(4, db.userVersion())
            assertEquals(
                rows(
                    StorageRow("VisitorId", "identity.${hashA.lowercase()}", "\"$VISITOR_ID_1\""),
                    StorageRow("VisitorId", "identity.${hashB.lowercase()}", "\"$VISITOR_ID_0\""),
                    StorageRow("VisitorId", "owner_identity", "\"${hashA.lowercase()}\""),
                    StorageRow("VisitorId", "visitor_id", "\"$VISITOR_ID_1\""),
                ),
                db.storageRows("VisitorId")
            )
            assertEquals(
                rows(
                    StorageRow("shared", "settings", "\"some value\""),
                    StorageRow("shared", "some_number", "42"),
                ),
                db.storageRows("shared")
            )
        }
    }

    /**
     * An anonymous visitor has never identified, so `shared` holds the visitor id alone: no
     * `current_identity`, no association rows. The one row still moves, and nothing is invented.
     */
    @Test
    fun onUpgrade_MovesVisitorIdAlone_When_VisitorIsAnonymous() {
        createV3Database(dbPath) {
            insertShared("tealium_visitor_id", "\"$VISITOR_ID_1\"")
            insertShared("settings", "\"some value\"")
        }

        openUpgraded { _, db ->
            assertEquals(4, db.userVersion())
            assertEquals(setOf("shared", "VisitorId"), db.moduleNames())
            assertEquals(
                rows(StorageRow("VisitorId", "visitor_id", "\"$VISITOR_ID_1\"", -1L)),
                db.storageRows("VisitorId")
            )
            assertEquals(
                rows(StorageRow("shared", "settings", "\"some value\"")),
                db.storageRows("shared")
            )
        }
    }

    /** `tealium_visitor_id` is present, so the guard passes and the row predicate alone decides. */
    @Test
    fun onUpgrade_LeavesOtherRowsInSharedStore_When_TheyDoNotMatchTheVisitorShape() {
        val notHexKey = "z".repeat(64)
        val lowerCaseHexKey = hashC.lowercase()

        createV3Database(dbPath) {
            insertShared("tealium_visitor_id", "\"$VISITOR_ID_1\"")
            // right shape of value, but the key is not a hex digest
            insertShared(notHexKey, "\"$VISITOR_ID_1\"")
            // right shape of value, but the key is lower-case hex, which v3 never wrote
            insertShared(lowerCaseHexKey, "\"$VISITOR_ID_1\"")
            // right shape of key, but the value is not a JSON quoted string
            insertShared(hashC, "42")
            // right shape of value and upper-case hex, but not 64 characters
            insertShared("CAFE", "\"$VISITOR_ID_1\"")
            insertShared(hashC.dropLast(1), "\"$VISITOR_ID_1\"")
            insertShared(hashC + "A", "\"$VISITOR_ID_1\"")
            // 64 characters, but one of them is lower-case
            insertShared(hashC.dropLast(1) + "a", "\"$VISITOR_ID_1\"")
            // right shape of key, but the value is not a complete JSON string
            insertShared("D".repeat(64), "\"")
            insertShared("E".repeat(64), "\"abc")
        }

        openUpgraded { _, db ->
            assertEquals(
                rows(StorageRow("VisitorId", "visitor_id", "\"$VISITOR_ID_1\"")),
                db.storageRows("VisitorId")
            )
            assertEquals(
                rows(
                    StorageRow("shared", hashC, "42"),
                    StorageRow("shared", lowerCaseHexKey, "\"$VISITOR_ID_1\""),
                    StorageRow("shared", notHexKey, "\"$VISITOR_ID_1\""),
                    StorageRow("shared", "CAFE", "\"$VISITOR_ID_1\""),
                    StorageRow("shared", hashC.dropLast(1), "\"$VISITOR_ID_1\""),
                    StorageRow("shared", hashC + "A", "\"$VISITOR_ID_1\""),
                    StorageRow("shared", hashC.dropLast(1) + "a", "\"$VISITOR_ID_1\""),
                    StorageRow("shared", "D".repeat(64), "\""),
                    StorageRow("shared", "E".repeat(64), "\"abc"),
                ),
                db.storageRows("shared")
            )
        }
    }

    @Test
    fun onUpgrade_MovesNothing_When_ThereIsNoSharedStore() {
        createV3Database(dbPath, withSharedStore = false) {
            // same keys, but in some other store: must not be claimed
            execSQL("INSERT INTO module (name) VALUES ('other');")
            insertV3StorageRow("other", "tealium_visitor_id", "\"$VISITOR_ID_1\"")
            insertV3StorageRow("other", hashA, "\"$VISITOR_ID_1\"")
        }

        openUpgraded { _, db ->
            assertEquals(4, db.userVersion())
            assertEquals(setOf("other"), db.moduleNames())
            assertEquals(
                rows(
                    StorageRow("other", hashA, "\"$VISITOR_ID_1\""),
                    StorageRow("other", "tealium_visitor_id", "\"$VISITOR_ID_1\""),
                ),
                db.storageRows()
            )
        }
    }

    /**
     * A 64 character upper-case hex key holding a quoted string is not enough to claim a row:
     * without `tealium_visitor_id` in the source store there was never a visitor module writing
     * there, so any such row belongs to a host app or third-party module.
     */
    @Test
    fun onUpgrade_MovesNothing_When_SharedStoreHasNoVisitorId() {
        createV3Database(dbPath) {
            insertShared(hashE, "\"$VISITOR_ID_1\"")
            insertShared("current_identity", "\"$hashE\"")
        }

        openUpgraded { _, db ->
            assertEquals(4, db.userVersion())
            assertFalse(db.moduleNames().contains("VisitorId"))
            assertEquals(
                rows(
                    StorageRow("shared", hashE, "\"$VISITOR_ID_1\""),
                    StorageRow("shared", "current_identity", "\"$hashE\""),
                ),
                db.storageRows()
            )
        }
    }

    @Test
    fun onUpgrade_MovesFiniteAndExpiredRowsVerbatim_When_TheyHaveAnExpiry() {
        createV3Database(dbPath) {
            insertShared("tealium_visitor_id", "\"$VISITOR_ID_1\"")
            insertShared(hashC, "\"$VISITOR_ID_FUTURE_EXPIRY\"", FUTURE_EXPIRY)
            insertShared(hashD, "\"$VISITOR_ID_PAST_EXPIRY\"", PAST_EXPIRY)
        }

        openUpgraded { _, db ->
            assertEquals(
                rows(
                    StorageRow(
                        "VisitorId",
                        "identity.${hashC.lowercase()}",
                        "\"$VISITOR_ID_FUTURE_EXPIRY\"",
                        FUTURE_EXPIRY
                    ),
                    StorageRow(
                        "VisitorId",
                        "identity.${hashD.lowercase()}",
                        "\"$VISITOR_ID_PAST_EXPIRY\"",
                        PAST_EXPIRY
                    ),
                    StorageRow("VisitorId", "visitor_id", "\"$VISITOR_ID_1\""),
                ),
                db.storageRows()
            )
        }
    }

    // endregion

    // region case handling

    /**
     * An upper-case association row's key becomes `identity.<lower-case digest>`, while its value,
     * a visitor id and not a digest, is untouched.
     */
    @Test
    fun onUpgrade_LowerCasesAssociationKeyOnly_When_MovingAnAssociationRow() {
        createV3Database(dbPath) {
            insertShared("tealium_visitor_id", "\"$VISITOR_ID_1\"")
            insertShared(hashA, "\"$VISITOR_ID_1\"")
        }

        openUpgraded { _, db ->
            assertEquals(
                rows(
                    StorageRow("VisitorId", "identity.${hashA.lowercase()}", "\"$VISITOR_ID_1\""),
                    StorageRow("VisitorId", "visitor_id", "\"$VISITOR_ID_1\""),
                ),
                db.storageRows()
            )
        }
    }

    /**
     * `current_identity` is the one row whose *value* is a digest rather than a visitor id, so it
     * is the one value the upgrade lower-cases.
     */
    @Test
    fun onUpgrade_LowerCasesOwnerIdentityValue_When_MovingTheOwnerIdentityRow() {
        createV3Database(dbPath) {
            insertShared("tealium_visitor_id", "\"$VISITOR_ID_1\"")
            insertShared("current_identity", "\"$hashA\"")
        }

        openUpgraded { _, db ->
            assertEquals(
                rows(
                    StorageRow("VisitorId", "owner_identity", "\"${hashA.lowercase()}\""),
                    StorageRow("VisitorId", "visitor_id", "\"$VISITOR_ID_1\""),
                ),
                db.storageRows()
            )
        }
    }

    /**
     * An association row's value is a visitor id, which may be a host-supplied
     * [TealiumConfig.existingVisitorId] rather than a generated one, so it must be left exactly as
     * written, mixed case and all.
     */
    @Test
    fun onUpgrade_LeavesAssociationValuesUntouched_When_VisitorIdIsHostSuppliedMixedCase() {
        createV3Database(dbPath) {
            insertShared("tealium_visitor_id", "\"$HOST_SUPPLIED_VISITOR_ID\"")
            insertShared(hashD, "\"$HOST_SUPPLIED_VISITOR_ID\"")
        }

        openUpgraded { _, db ->
            assertEquals(
                rows(
                    StorageRow(
                        "VisitorId",
                        "identity.${hashD.lowercase()}",
                        "\"$HOST_SUPPLIED_VISITOR_ID\""
                    ),
                    StorageRow("VisitorId", "visitor_id", "\"$HOST_SUPPLIED_VISITOR_ID\""),
                ),
                db.storageRows()
            )
        }
    }

    // endregion

    // region idempotence and structure

    @Test
    fun upgrade_ChangesNothing_When_AppliedASecondTime() {
        createV3Database(dbPath) { seedPopulatedSharedStore() }

        openUpgraded { _, db ->
            val afterFirstRun = db.storageRows()
            val modulesAfterFirstRun = db.moduleNames()

            V3ToV4.upgrade(db)

            assertEquals(afterFirstRun, db.storageRows())
            assertEquals(modulesAfterFirstRun, db.moduleNames())
            assertEquals(4, db.storageRows("VisitorId").size)
        }
    }

    /**
     * A dev install that ran the visitor id module at version 3 already has a `VisitorId` store
     * holding rows under the new keys, while `shared` still holds the legacy rows. `UPDATE OR
     * REPLACE` lets the legacy rows win on the `(module_id, key)` primary key, so the `shared`
     * values replace the stale target values without a conflict, a target row whose key does not
     * collide is left exactly as it was, and no key is duplicated.
     */
    @Test
    fun onUpgrade_ReplacesExistingVisitorIdRows_When_TargetStoreAlreadyHasThem() {
        createV3Database(dbPath) {
            execSQL("INSERT INTO module (name) VALUES ('VisitorId');")
            // already in the target layout, with values the legacy rows must overwrite
            insertVisitorId("visitor_id", "\"$VISITOR_ID_STALE\"")
            insertVisitorId("owner_identity", "\"${hashB.lowercase()}\"")
            insertVisitorId("identity.${hashA.lowercase()}", "\"$VISITOR_ID_STALE\"")
            // no legacy counterpart, so it must survive untouched, expiry included
            insertVisitorId("identity.${hashC.lowercase()}", "\"$VISITOR_ID_0\"", FUTURE_EXPIRY)
            // the legacy layout, still in place
            insertShared("tealium_visitor_id", "\"$VISITOR_ID_1\"")
            insertShared("current_identity", "\"$hashA\"")
            insertShared(hashA, "\"$VISITOR_ID_1\"")
            insertShared("settings", "\"some value\"")
        }

        openUpgraded { helper, db ->
            assertTrue(helper.wasUpgraded)
            assertEquals(4, db.userVersion())
            val visitorIdRows = db.storageRows("VisitorId")
            assertEquals(
                rows(
                    StorageRow("VisitorId", "identity.${hashA.lowercase()}", "\"$VISITOR_ID_1\""),
                    StorageRow("VisitorId", "identity.${hashC.lowercase()}", "\"$VISITOR_ID_0\"", FUTURE_EXPIRY),
                    StorageRow("VisitorId", "owner_identity", "\"${hashA.lowercase()}\""),
                    StorageRow("VisitorId", "visitor_id", "\"$VISITOR_ID_1\""),
                ),
                visitorIdRows
            )
            assertEquals(visitorIdRows.size, visitorIdRows.map { it.key }.distinct().size)
            assertEquals(
                rows(StorageRow("shared", "settings", "\"some value\"")),
                db.storageRows("shared")
            )
        }
    }

    /**
     * Compared structurally, per the checklist in [Upgrades]: `PRAGMA table_info`, `index_list`
     * with `index_info`, `foreign_key_list` and trigger SQL, not `sqlite_master` text.
     */
    @Test
    fun onUpgrade_ProducesTheFreshInstallStructure_When_FileIsAtVersion3() {
        createV3Database(dbPath) { seedPopulatedSharedStore() }
        val fresh: SchemaStructure = DatabaseHelper(config, freshDbName).closing { helper ->
            helper.writableDatabase.structure()
        }

        openUpgraded { _, db ->
            val upgraded = db.structure()

            assertEquals(fresh.tables.keys, upgraded.tables.keys)
            fresh.tables.forEach { (table, expected) ->
                assertEquals("table '$table' differs from a fresh install", expected, upgraded.tables[table])
            }
            assertEquals(fresh.triggers, upgraded.triggers)
            assertEquals(fresh, upgraded)
        }
    }

    // endregion

    // region live stores

    /**
     * Everything else reads `module_storage` raw, so this proves that the live store actually finds
     * the moved rows under the keys it looks for.
     */
    @Test
    fun visitorStorage_ReadsMovedData_When_Upgraded() {
        createV3Database(dbPath) { seedPopulatedSharedStore() }

        openUpgraded { _, db ->
            val visitorStorage = VisitorStorageImpl(db.visitorIdStore())

            assertEquals(VISITOR_ID_1, visitorStorage.visitorId)
            assertEquals(hashA.lowercase(), visitorStorage.ownerIdentity)
            assertEquals(VISITOR_ID_1, visitorStorage.getKnownVisitorId(hashA.lowercase()))
            assertEquals(VISITOR_ID_0, visitorStorage.getKnownVisitorId(hashB.lowercase()))
            assertNull(visitorStorage.getKnownVisitorId(hashC.lowercase()))
        }
    }

    /**
     * End to end: [VisitorIdStorageImpl] built over the `VisitorId` store that holds the moved rows
     * resolves an identity whose association was carried over by the upgrade back to its previous
     * visitor id. `identify` hashes the raw identity through the live, lower-case [sha256], and the
     * lookup only matches because the upgrade lower-cased the same digest when it moved the
     * association row.
     */
    @Test
    fun identify_RestoresPreviousVisitorId_When_IdentityWasAssociatedBeforeTheUpgrade() {
        createV3Database(dbPath) { seedPopulatedSharedStore() }

        openUpgraded { _, db ->
            val visitorIdStorage = VisitorIdStorageImpl(
                visitorStorage = VisitorStorageImpl(db.visitorIdStore()),
                migrator = VisitorIdStorageImpl.VisitorIdMigratorImpl(),
                logger = SystemLogger
            )
            assertEquals(VISITOR_ID_1, visitorIdStorage.visitorId.value)

            // "identity_b" was associated with VISITOR_ID_0 before the upgrade, as hashB
            visitorIdStorage.identify("identity_b")

            assertEquals(VISITOR_ID_0, visitorIdStorage.visitorId.value)
        }
    }

    /**
     * The whole SDK started on a version 3 file: the upgrade runs transparently inside normal SDK
     * start, and the public visitor id is the migrated one rather than a freshly generated one.
     */
    @Test
    fun tealium_ExposesTheStoredVisitorId_When_StartedOnVersion3() {
        val sdkConfig = getDefaultConfig(app, accountName = "v3_to_v4_sdk", profileName = "start")
        val sdkDbPath = DatabaseHelper.databaseName(sdkConfig)
        sdkConfig.tealiumDirectory.deleteRecursively()
        sdkConfig.tealiumDirectory.mkdirs()
        createV3Database(sdkDbPath) {
            insertShared("tealium_visitor_id", "\"$VISITOR_ID_1\"")
            insertShared("current_identity", "\"$hashA\"")
            insertShared(hashA, "\"$VISITOR_ID_1\"")
        }

        try {
            val tealium = awaitValue<TealiumResult<Tealium>> { done ->
                Tealium.create(sdkConfig) { done(it) }
            }.getOrThrow()

            val visitorId = awaitValue<TealiumResult<String>> { done ->
                tealium.visitorId.current().subscribe { done(it) }
            }

            assertEquals(VISITOR_ID_1, visitorId.getOrThrow())
        } finally {
            Tealium.shutdown(sdkConfig.key)
            // get is deferred until the shutdown's teardown has finished, so this waits for it
            assertNull(awaitValue<Tealium?> { done -> Tealium.get(sdkConfig.key) { done(it) } })
        }

        try {
            onDisk(sdkDbPath, SQLiteDatabase.OPEN_READONLY) { raw ->
                assertEquals(4, raw.userVersion())
            }
        } finally {
            sdkConfig.tealiumDirectory.deleteRecursively()
        }
    }

    @Test
    fun visitorStorage_RoundTripsDottedIdentityKeys_When_WritingToTheUpgradedStore() {
        createV3Database(dbPath) { seedPopulatedSharedStore() }

        openUpgraded { _, db ->
            val store = db.visitorIdStore()
            val key = VisitorStorageImpl.IDENTITY_KEY_PREFIX + hashC.lowercase()

            VisitorStorageImpl(store).changeVisitor(VISITOR_ID_1, hashC.lowercase())

            assertEquals(VISITOR_ID_1, store.getString(key))
            assertTrue(store.keys().contains(key))
            assertEquals(VISITOR_ID_1, store.getAll().getString(key))

            store.edit().remove(key).commit()

            assertNull(store.getString(key))
            assertFalse(store.keys().contains(key))
            assertNull(store.getAll().getString(key))
        }
    }

    // endregion

    // region constants

    /**
     * An upgrade step is a snapshot: its *source* names are frozen literals that must never follow
     * a rename of the live constants, whereas its *target* names must agree with the live
     * constants that the SDK reads after the upgrade.
     */
    @Test
    fun v3ToV4_TargetsMatchLiveConstants_When_Compared() {
        assertEquals(Modules.Types.VISITOR_ID, V3ToV4.TARGET_MODULE_NAME)
        assertEquals(VisitorStorageImpl.KEY_VISITOR_ID, V3ToV4.TARGET_KEY_VISITOR_ID)
        assertEquals(VisitorStorageImpl.KEY_OWNER_IDENTITY, V3ToV4.TARGET_KEY_OWNER_IDENTITY)
        assertEquals(VisitorStorageImpl.IDENTITY_KEY_PREFIX, V3ToV4.TARGET_PREFIX_IDENTITY)
        assertEquals(ModuleStoreProviderImpl.SHARED_STORE_NAME, V3ToV4.SOURCE_MODULE_NAME)

        // sources are frozen by design, so they are spelled out
        assertEquals("shared", V3ToV4.SOURCE_MODULE_NAME)
        assertEquals("tealium_visitor_id", V3ToV4.SOURCE_KEY_VISITOR_ID)
        assertEquals("current_identity", V3ToV4.SOURCE_KEY_OWNER_IDENTITY)

        assertEquals(4, V3ToV4.targetVersion)
        assertEquals(V3ToV4.targetVersion, DatabaseHelper.DATABASE_VERSION)
    }

    /**
     * [SchemaV3] is a snapshot, so this only holds until the tables next change. When they do, add
     * a `SchemaVN` and relax this test. Never edit [SchemaV3].
     */
    @Test
    fun schemaV3_IdentifiersMatchTheLiveSchema_When_Compared() {
        assertEquals(Schema.DispatchTable.TABLE_NAME, SchemaV3.Dispatch.TABLE)
        assertEquals(Schema.DispatchTable.COLUMN_ID, SchemaV3.Dispatch.ID)
        assertEquals(Schema.DispatchTable.COLUMN_TIMESTAMP, SchemaV3.Dispatch.TIMESTAMP)
        assertEquals(Schema.DispatchTable.COLUMN_DISPATCH, SchemaV3.Dispatch.DISPATCH)
        assertEquals(
            "idx_${Schema.DispatchTable.TABLE_NAME}_${Schema.DispatchTable.COLUMN_TIMESTAMP}",
            SchemaV3.Dispatch.INDEX_TIMESTAMP
        )

        assertEquals(Schema.QueueTable.TABLE_NAME, SchemaV3.Queue.TABLE)
        assertEquals(Schema.QueueTable.COLUMN_DISPATCH_ID, SchemaV3.Queue.DISPATCH_ID)
        assertEquals(Schema.QueueTable.COLUMN_PROCESSOR_ID, SchemaV3.Queue.PROCESSOR_ID)
        assertEquals(
            Schema.QueueTable.TRIGGER_REMOVE_PROCESSED_DISPATCHES,
            SchemaV3.Queue.TRIGGER_REMOVE_PROCESSED_DISPATCHES
        )

        assertEquals(Schema.ModuleTable.TABLE_NAME, SchemaV3.Module.TABLE)
        assertEquals(Schema.ModuleTable.COLUMN_ID, SchemaV3.Module.ID)
        assertEquals(Schema.ModuleTable.COLUMN_NAME, SchemaV3.Module.NAME)

        assertEquals(Schema.ModuleStorageTable.TABLE_NAME, SchemaV3.ModuleStorage.TABLE)
        assertEquals(Schema.ModuleStorageTable.COLUMN_MODULE_ID, SchemaV3.ModuleStorage.MODULE_ID)
        assertEquals(Schema.ModuleStorageTable.COLUMN_KEY, SchemaV3.ModuleStorage.KEY)
        assertEquals(Schema.ModuleStorageTable.COLUMN_VALUE, SchemaV3.ModuleStorage.VALUE)
        assertEquals(Schema.ModuleStorageTable.COLUMN_EXPIRY, SchemaV3.ModuleStorage.EXPIRY)
    }

    // endregion

    // region failure

    /**
     * A version 3 file whose `module_storage` table is not where the step expects it makes the
     * step's guard query throw. The helper wraps that as a [DatabaseUpgradeException] at version 4,
     * the transaction rolls back, and the file is left as it came: version 3, rows intact.
     */
    @Test
    fun onUpgrade_LeavesFileAtVersion3_When_StepThrows() {
        stageV3FileThatFailsTheStep()

        DatabaseHelper(config, dbPath).closing { helper ->
            assertThrows(DatabaseUpgradeException::class.java) { helper.writableDatabase }

            val failure = helper.initializationFailure
            assertTrue("expected DatabaseUpgradeException, was $failure", failure is DatabaseUpgradeException)
            assertEquals(4, (failure as DatabaseUpgradeException).targetVersion)
            assertTrue("expected the step's SQLiteException as cause, was ${failure.cause}", failure.cause is SQLiteException)
            assertFalse(helper.wasUpgraded)
        }

        assertStagedFileIsUntouched()
    }

    /**
     * Unlike [onUpgrade_LeavesFileAtVersion3_When_StepThrows], where the step fails before writing
     * anything, here [V3ToV4] completes and a later step in the same chain throws after the rows
     * have moved. The step observes the moved rows inside the open transaction, then the helper
     * rolls the whole chain back: the file stays at version 3, with every seeded `shared` row
     * exactly where it was and no `VisitorId` module.
     */
    @Test
    fun onUpgrade_RollsBackMovedRows_When_ALaterStepThrows() {
        createV3Database(dbPath) { seedPopulatedSharedStore() }
        var laterStepRan = false
        val failingStep = upgradeStep(5) { db ->
            laterStepRan = true
            assertTrue(db.moduleNames().contains("VisitorId"))
            assertEquals(4, db.storageRows("VisitorId").size)
            assertTrue(db.storageRows("shared").none { it.key == "tealium_visitor_id" })
            throw SQLiteException("boom")
        }

        realHelper(config, dbPath, V3ToV4, failingStep, version = 5).closing { helper ->
            val thrown = assertThrows(DatabaseUpgradeException::class.java) { helper.writableDatabase }

            assertTrue("expected the later step to run after V3ToV4", laterStepRan)
            assertEquals(5, thrown.targetVersion)
            assertTrue("expected the step's SQLiteException as cause, was ${thrown.cause}", thrown.cause is SQLiteException)
            assertEquals(thrown, helper.initializationFailure)
            assertFalse(helper.wasUpgraded)
        }

        onDisk(dbPath, SQLiteDatabase.OPEN_READONLY) { raw ->
            assertEquals(3, raw.userVersion())
            assertEquals(setOf("shared"), raw.moduleNames())
            assertEquals(populatedSharedStoreRows(), raw.storageRows("shared"))
        }
    }

    /**
     * Through [FileDatabaseProvider], the same failure keeps the file for a later SDK version and
     * runs this session on in-memory storage.
     */
    @Test
    fun getDatabase_FallsBackToMemoryAndKeepsFile_When_V3ToV4Throws() {
        stageV3FileThatFailsTheStep()
        val errorMessages = mutableListOf<String>()
        val logger: Logger = mockk(relaxed = true)
        every { logger.error(LogCategory.PERSISTENCE, capture(errorMessages)) } just Runs
        var inMemoryFallbacks = 0

        val provider = FileDatabaseProvider(
            config,
            logger,
            databaseHelperCreator = { handler -> DatabaseHelper(config, dbPath, errorHandler = handler) },
            inMemoryDatabaseProvider = { cfg ->
                inMemoryFallbacks++
                InMemoryDatabaseProvider(cfg)
            }
        )

        provider.database.use { database ->
            assertEquals(":memory:", database.path)
            assertEquals(1, inMemoryFallbacks)
            assertTrue(
                "expected the log to name the step and say the file is kept, was $errorMessages",
                errorMessages.any {
                    it.contains("upgrade to version 4 failed") && it.contains("The database file is kept.")
                }
            )
        }

        assertStagedFileIsUntouched()
    }

    // endregion

    // region helpers

    /** The pre-upgrade visitor layout, plus two unrelated rows that the upgrade must leave alone. */
    private fun SQLiteDatabase.seedPopulatedSharedStore() {
        insertShared("tealium_visitor_id", "\"$VISITOR_ID_1\"")
        insertShared("current_identity", "\"$hashA\"")
        insertShared(hashA, "\"$VISITOR_ID_1\"")
        insertShared(hashB, "\"$VISITOR_ID_0\"")
        insertShared("settings", "\"some value\"")
        insertShared("some_number", "42")
    }

    /** Exactly what [seedPopulatedSharedStore] writes, as [storageRows] reports it before any move. */
    private fun populatedSharedStoreRows(): List<StorageRow> = rows(
        StorageRow("shared", "tealium_visitor_id", "\"$VISITOR_ID_1\""),
        StorageRow("shared", "current_identity", "\"$hashA\""),
        StorageRow("shared", hashA, "\"$VISITOR_ID_1\""),
        StorageRow("shared", hashB, "\"$VISITOR_ID_0\""),
        StorageRow("shared", "settings", "\"some value\""),
        StorageRow("shared", "some_number", "42"),
    )

    private fun SQLiteDatabase.insertShared(key: String, value: String, expiry: Long = -1L) =
        insertV3StorageRow(V3Ddl.SHARED_MODULE, key, value, expiry)

    /**
     * A row already in the target store, as a dev install that ran the visitor id module at
     * version 3 would have written it. The module row must have been inserted first.
     */
    private fun SQLiteDatabase.insertVisitorId(key: String, value: String, expiry: Long = -1L) =
        insertV3StorageRow("VisitorId", key, value, expiry)

    /**
     * Opens the staged file with the real [DatabaseHelper] and its registered upgrade chain, so
     * `onUpgrade(3, 4)` runs, and closes it afterwards.
     */
    private fun <T> openUpgraded(block: (DatabaseHelper, SQLiteDatabase) -> T): T =
        DatabaseHelper(config, dbPath).closing { helper -> block(helper, helper.writableDatabase) }

    /**
     * A populated version 3 file whose `module_storage` table has been renamed, so the step's first
     * query fails deterministically with "no such table" on every SQLite build.
     */
    private fun stageV3FileThatFailsTheStep() {
        createV3Database(dbPath) {
            seedPopulatedSharedStore()
            execSQL("ALTER TABLE module_storage RENAME TO $RENAMED_STORAGE_TABLE;")
        }
    }

    /** The staged file is still at version 3 with every seeded row where it was. */
    private fun assertStagedFileIsUntouched() {
        onDisk(dbPath, SQLiteDatabase.OPEN_READONLY) { raw ->
            assertEquals(3, raw.userVersion())
            assertEquals(setOf("shared"), raw.moduleNames())
            assertEquals(populatedSharedStoreRows(), raw.storageRows(table = RENAMED_STORAGE_TABLE))
        }
    }

    private fun SQLiteDatabase.visitorIdStore(): DataStore {
        val provider = object : DatabaseProvider {
            override val database: SQLiteDatabase = this@visitorIdStore
        }
        return ModuleStoreProviderImpl(provider, SQLModulesRepository(provider))
            .getModuleStore(Modules.Types.VISITOR_ID)
    }

    private fun SQLiteDatabase.userVersion(): Int =
        rawQuery("PRAGMA user_version;", null).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    /** The names in the `module` table, read raw so that asking cannot itself register a module. */
    private fun SQLiteDatabase.moduleNames(): Set<String> =
        rawQuery("SELECT name FROM module;", null).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }

    /**
     * Every row of the storage [table], joined to its module name, sorted by module then key, and
     * optionally limited to one module.
     */
    private fun SQLiteDatabase.storageRows(
        moduleName: String? = null,
        table: String = "module_storage"
    ): List<StorageRow> =
        rawQuery(
            "SELECT m.name, s.key, s.value, s.expiry FROM $table s " +
                "INNER JOIN module m ON m.id = s.module_id",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        StorageRow(
                            module = cursor.getString(0),
                            key = cursor.getString(1),
                            value = cursor.getString(2),
                            expiry = cursor.getLong(3),
                        )
                    )
                }
            }
        }.filter { moduleName == null || it.module == moduleName }
            .sortedWith(ROW_ORDER)

    /** Runs [block] and waits up to 5 seconds for it to call the provided completion with a value. */
    private fun <T> awaitValue(block: (done: (T) -> Unit) -> Unit): T {
        val future = CompletableFuture<T>()
        block { future.complete(it) }
        return future.get(5, TimeUnit.SECONDS)
    }

    /** The expected rows in the order [storageRows] reports them, so a comparison is order-free. */
    private fun rows(vararg rows: StorageRow): List<StorageRow> = rows.sortedWith(ROW_ORDER)

    /** One `module_storage` row, with the module's name in place of its id. */
    private data class StorageRow(
        val module: String,
        val key: String,
        val value: String,
        val expiry: Long = -1L,
    )

    private companion object {
        /** A visitor id previously associated with a non-current identity. */
        const val VISITOR_ID_0 = "0e1d2c3b4a59687776859403a2b1c0d9"

        /** The current visitor id. */
        const val VISITOR_ID_1 = "73ef029f5c7d4648bebeb431b96f0631"

        /** A visitor id already sitting in the target store, which a moved legacy row must replace. */
        const val VISITOR_ID_STALE = "aaaabbbbccccddddeeeeffff00001111"

        /** A visitor id shaped like one supplied via [TealiumConfig.existingVisitorId]. */
        const val HOST_SUPPLIED_VISITOR_ID = "MiXeD-CaSe-User-42"

        const val VISITOR_ID_FUTURE_EXPIRY = "11112222333344445555666677778888"
        const val VISITOR_ID_PAST_EXPIRY = "99998888777766665555444433332222"

        /** A finite epoch-second expiry far enough ahead never to lapse mid-test. */
        const val FUTURE_EXPIRY = 4_000_000_000L

        /** A finite epoch-second expiry already in the past. */
        const val PAST_EXPIRY = 1_000_000_000L

        /** Where the failure fixture moves `module_storage` to. */
        const val RENAMED_STORAGE_TABLE = "module_storage_v3"

        val ROW_ORDER: Comparator<StorageRow> = compareBy({ it.module }, { it.key })
    }

    // endregion
}
