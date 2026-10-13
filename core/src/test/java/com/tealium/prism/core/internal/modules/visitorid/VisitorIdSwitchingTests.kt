package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.VisitorIdStorageImpl
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.database.getTimestamp
import com.tealium.prism.core.internal.persistence.repositories.ModulesRepository
import com.tealium.prism.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.prism.core.internal.utils.sha256
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkStatic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.TimeUnit

/**
 * Visitor Switching scenarios over real in-memory stores. Identities are only ever driven through
 * the data layer, and every step asserts the current visitor id (V), the persisted owner identity
 * (owner) and the stored identity associations (map).
 */
@RunWith(RobolectricTestRunner::class)
class VisitorIdSwitchingTests {

    private lateinit var config: TealiumConfig
    private lateinit var databaseProvider: InMemoryDatabaseProvider
    private lateinit var modulesRepository: SQLModulesRepository
    private lateinit var dataLayer: DataStore
    private lateinit var visitorStore: DataStore
    private lateinit var visitorStorage: VisitorStorageImpl

    private val a = "a@example.com".sha256()
    private val b = "b@example.com".sha256()

    /** Storage clock in epoch seconds, as read by [Expiry] and the key value repository. */
    private var now: Long = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

    @Before
    fun setUp() {
        // core has no injectable clock, so the storage timestamp function is mocked instead
        mockkStatic(::getTimestamp)
        every { getTimestamp() } answers { now }

        config = getDefaultConfig(RuntimeEnvironment.getApplication())
        databaseProvider = InMemoryDatabaseProvider(config)
        modulesRepository = SQLModulesRepository(databaseProvider)
        val storeProvider = ModuleStoreProviderImpl(databaseProvider, modulesRepository)
        dataLayer = storeProvider.getModuleStore(Modules.Types.DATA_LAYER)
        visitorStore = storeProvider.getModuleStore(Modules.Types.VISITOR_ID)
        visitorStorage = VisitorStorageImpl(visitorStore)
    }

    @After
    fun tearDown() {
        unmockkStatic(::getTimestamp)
    }

    @Test
    fun case_B_Logout_Reset_Other_Identity_Adopts_Then_Logout_Reset_Original_Identity_Restores_Its_Visitor_Id() {
        val module = createModule()

        login("a@example.com")
        val v1 = module.visitorId.value
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)

        logout()
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)

        val v2 = module.regenerate()
        assertNotEquals(v1, v2)
        assertState(Snapshot(v2, null, mapOf(a to v1)), module)

        login("b@example.com")
        assertState(Snapshot(v2, b, mapOf(a to v1, b to v2)), module)

        logout()
        assertState(Snapshot(v2, b, mapOf(a to v1, b to v2)), module)

        val v3 = module.regenerate()
        assertNotEquals(v2, v3)
        assertState(Snapshot(v3, null, mapOf(a to v1, b to v2)), module)

        login("a@example.com")
        assertState(Snapshot(v1, a, mapOf(a to v1, b to v2)), module)
    }

    @Test
    fun case_C_Logout_Reset_Then_Original_Identity_Restores_Its_Visitor_Id_And_Leaves_Reset_Id_Unadopted() {
        val module = createModule()

        login("a@example.com")
        val v1 = module.visitorId.value
        logout()
        val v2 = module.regenerate()
        assertState(Snapshot(v2, null, mapOf(a to v1)), module)

        login("a@example.com")
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)
    }

    @Test
    fun case_A_Logout_Then_Different_Identity_Mints_New_Visitor_Id() {
        val module = createModule()

        login("a@example.com")
        val v1 = module.visitorId.value
        logout()

        login("b@example.com")
        val v2 = module.visitorId.value
        assertNotEquals(v1, v2)
        assertState(Snapshot(v2, b, mapOf(a to v1, b to v2)), module)
    }

    @Test
    fun case_D_Reset_While_Identity_Present_Rebinds_It_To_New_Visitor_Id() {
        val module = createModule()

        login("a@example.com")
        val v1 = module.visitorId.value

        val v2 = module.regenerate()
        assertNotEquals(v1, v2)
        assertState(Snapshot(v2, a, mapOf(a to v2)), module)
    }

    @Test
    fun case_H_Reset_Before_Any_Login_Then_Identity_Adopts_New_Visitor_Id() {
        val module = createModule()
        val v0 = module.visitorId.value
        assertState(Snapshot(v0, null, emptyMap()), module)

        val v1 = module.regenerate()
        assertNotEquals(v0, v1)
        assertState(Snapshot(v1, null, emptyMap()), module)

        login("b@example.com")
        assertState(Snapshot(v1, b, mapOf(b to v1)), module)
    }

    @Test
    fun logout_Then_Relogin_Without_Reset_Keeps_Visitor_Id() {
        val module = createModule()

        login("a@example.com")
        val v1 = module.visitorId.value
        logout()
        login("a@example.com")

        assertState(Snapshot(v1, a, mapOf(a to v1)), module)
    }

    @Test
    fun relogin_Restores_Visitor_Id_When_Same_Identity_Returns_After_Logout_And_Regenerate_In_One_Process() {
        val module = createModule()

        login("a@example.com")
        val v1 = module.visitorId.value
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)

        logout()
        val v2 = module.regenerate()
        assertNotEquals(v1, v2)
        assertState(Snapshot(v2, null, mapOf(a to v1)), module)

        // the same identity written again is not deduplicated away
        login("a@example.com")
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)
    }

    @Test
    fun identify_Leaves_Storage_Untouched_When_Identity_Removed() {
        val module = createModule()
        login("a@example.com")
        val before = snapshot(module)

        logout()

        assertEquals(before, snapshot(module))
    }

    @Test
    fun identify_Leaves_Storage_Untouched_When_Identity_Expired() {
        val module = createModule()
        loginExpiringAfterOneHour("a@example.com")
        val v1 = module.visitorId.value
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)

        advanceClockByHours(2)
        assertNull(dataLayer.getString(IDENTITY_KEY))
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)

        // the purge publishes the removal, which is not reported either
        modulesRepository.deleteExpired(ModulesRepository.ExpirationType.UntilRestart, now)
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)
    }

    @Test
    fun regenerate_Leaves_New_Visitor_Id_Unowned_When_Identity_Expired_But_Not_Purged() {
        val module = createModule()
        loginExpiringAfterOneHour("a@example.com")
        val v1 = module.visitorId.value
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)

        // no purge and no session end: nothing is published for the expired identity
        advanceClockByHours(2)
        val v2 = module.regenerate()

        assertNotEquals(v1, v2)
        assertState(Snapshot(v2, null, mapOf(a to v1)), module)
    }

    @Test
    fun regenerate_Associates_With_Identity_When_Queued_Right_After_DataLayer_Put() {
        val module = createModule()

        login("a@example.com")
        val v2 = module.regenerate()

        assertEquals(a, visitorStorage.ownerIdentity)
        assertEquals(v2, visitorStorage.getKnownVisitorId(a))
        assertState(Snapshot(v2, a, mapOf(a to v2)), module)
    }

    @Test
    fun regenerate_DoesNot_Write_Map_When_Identity_Removed() {
        val module = createModule()
        login("a@example.com")
        val v1 = module.visitorId.value
        logout()

        val v2 = module.regenerate()

        assertState(Snapshot(v2, null, mapOf(a to v1)), module)
    }

    @Test
    fun regenerate_Rebinds_Identity_When_Present() {
        val module = createModule()
        login("a@example.com")
        logout()
        login("b@example.com")
        val v1 = visitorStorage.getKnownVisitorId(a)!!

        val v2 = module.regenerate()

        assertState(Snapshot(v2, b, mapOf(a to v1, b to v2)), module)
    }

    @Test
    fun eraseLocalHistory_Reassociates_Present_Identity() {
        val module = createModule()
        login("a@example.com")
        logout()
        login("b@example.com")

        val v3 = module.eraseLocalHistory()

        assertState(Snapshot(v3, b, mapOf(b to v3)), module)
    }

    @Test
    fun eraseLocalHistory_Leaves_New_Visitor_Id_Unowned_When_No_Identity_Present() {
        val module = createModule()
        login("a@example.com")
        logout()

        val v2 = module.eraseLocalHistory()

        assertState(Snapshot(v2, null, emptyMap()), module)
    }

    @Test
    fun regenerate_Observes_Identity_Seeded_From_DataLayer_When_Called_Right_After_Init() {
        login("a@example.com")
        val module = createModule()
        val v1 = module.visitorId.value
        assertState(Snapshot(v1, a, mapOf(a to v1)), module)

        val v2 = module.regenerate()

        assertState(Snapshot(v2, a, mapOf(a to v2)), module)
    }

    @Test
    fun identify_Stores_Association_Under_Lowercase_Sha256_Key_When_Identity_Written() {
        val module = createModule()

        login("newIdentity")

        assertEquals(NEW_IDENTITY_SHA256, visitorStorage.ownerIdentity)
        assertEquals(
            module.visitorId.value,
            visitorStore.getString(VisitorStorageImpl.IDENTITY_KEY_PREFIX + NEW_IDENTITY_SHA256)
        )
    }

    @Test
    fun identify_Stores_A_Switch_That_Failed_To_Be_Stored_When_The_Identity_Is_Written_Again() {
        visitorStorage.changeVisitor("v1", a) // a known with v1
        visitorStorage.changeVisitor("v0", b) // b owns the current v0
        val failOnceStorage = spyk(visitorStorage)
        every { failOnceStorage.changeVisitor(any(), any()) }
            .throws(RuntimeException("Simulated write failure"))
            .andThenAnswer { callOriginal() }
        val module = VisitorIdModule(
            VisitorIdStorageImpl(failOnceStorage, VisitorIdStorageImpl.VisitorIdMigratorImpl(), null, SystemLogger),
            VisitorIdConfiguration(IDENTITY_KEY),
            dataLayer
        )
        login("a@example.com") // a in -> v1 used in memory, not stored
        assertEquals("v1", module.visitorId.value)
        assertEquals("v0", visitorStorage.visitorId)
        login("a@example.com") // a again -> v1 stored, so it is kept on relaunch
        assertState(Snapshot("v1", a, mapOf(a to "v1", b to "v0")), module)
    }

    private fun createModule(): VisitorIdModule = VisitorIdModule(
        VisitorIdStorageImpl(config, visitorStore, SystemLogger),
        VisitorIdConfiguration(IDENTITY_KEY),
        dataLayer
    )

    private fun login(identity: String) {
        dataLayer.edit().put(IDENTITY_KEY, identity, Expiry.FOREVER).commit()
    }

    private fun logout() {
        dataLayer.edit().remove(IDENTITY_KEY).commit()
    }

    private fun loginExpiringAfterOneHour(identity: String) {
        dataLayer.edit()
            .put(IDENTITY_KEY, identity, Expiry.afterTimeUnit(1, TimeUnit.HOURS))
            .commit()
    }

    /**
     * Moves the storage clock forward by [hours], so that rows with a time based expiry inside
     * that window are excluded from reads, without purging them or publishing any event.
     */
    private fun advanceClockByHours(hours: Long) {
        now += TimeUnit.HOURS.toSeconds(hours)
    }

    private fun snapshot(module: VisitorIdModule): Snapshot = Snapshot(
        visitorId = module.visitorId.value,
        owner = visitorStorage.ownerIdentity,
        map = visitorStore.keys()
            .filter { it.startsWith(VisitorStorageImpl.IDENTITY_KEY_PREFIX) }
            .associate {
                it.removePrefix(VisitorStorageImpl.IDENTITY_KEY_PREFIX) to visitorStore.getString(it)!!
            }
    )

    private fun assertState(expected: Snapshot, module: VisitorIdModule) {
        val actual = snapshot(module)
        assertEquals(expected, actual)
        // the stored visitor id always matches the one in memory when writes succeed
        assertEquals(actual.visitorId, visitorStorage.visitorId)
    }

    private data class Snapshot(
        val visitorId: String,
        val owner: String?,
        val map: Map<String, String>
    )

    private companion object {
        const val IDENTITY_KEY = "email"

        /** Lowercase hex SHA-256 of "newIdentity", matching the Swift SDK output. */
        const val NEW_IDENTITY_SHA256 =
            "2d66ba8ba26bf2ee9c7b40d5225e73c1db3a56f158cc9f669fbe340590af62db"
    }
}
