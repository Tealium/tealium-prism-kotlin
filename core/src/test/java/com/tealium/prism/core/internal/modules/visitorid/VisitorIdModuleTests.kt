package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.persistence.ModuleStoreProvider
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.settings.modules.VisitorIdSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.VisitorIdMigrator
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.prism.core.internal.persistence.stores.VisitorStorage
import com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.utils.sha256
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.buildConfiguration
import com.tealium.tests.common.getDefaultConfig
import com.tealium.tests.common.mockkEditor
import io.mockk.MockKAnnotations
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VisitorIdModuleTests {

    @RelaxedMockK
    private lateinit var visitorStorage: VisitorStorage

    @RelaxedMockK
    private lateinit var visitorIdMigrator: VisitorIdMigrator

    @MockK
    private lateinit var dataLayer: DataStore

    private val identityKey = "identity"
    private val dispatchContext =
        DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

    private val currentVisitorId = "visitor123"
    private val ownerIdentity = "identity123"
    private val hashedOwnerIdentity = ownerIdentity.sha256()
    private val migratingVisitorId = "migrating_id"
    private val existingVisitorId = "existing_visitor_id"

    private lateinit var onDataLayerUpdated: Subject<DataObject>
    private lateinit var module: VisitorIdModule

    private lateinit var moduleStore: DataStore
    private lateinit var storageProvider: ModuleStoreProvider

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        every { visitorStorage.visitorId } returns "visitor-id"
        every { visitorStorage.ownerIdentity } returns null
        every { visitorStorage.getKnownVisitorId(any()) } returns null
        every { visitorIdMigrator.visitorId } returns null

        onDataLayerUpdated = Observables.publishSubject()
        every { dataLayer.onDataUpdated } returns onDataLayerUpdated
        every { dataLayer.getString(any()) } returns null

        module = createModule()
    }

    @Test
    fun collect_Returns_Current_Visitor_Id() {
        val result = module.collect(dispatchContext)

        assertEquals("visitor-id", result.getString(Dispatch.Keys.TEALIUM_VISITOR_ID))
    }

    @Test
    fun collect_Returns_Updated_Visitor_Id_After_Visitor_Changes() {
        val newVisitorId = module.regenerate()

        val result = module.collect(dispatchContext)

        assertEquals(newVisitorId, result.getString(Dispatch.Keys.TEALIUM_VISITOR_ID))
    }

    @Test
    fun visitorId_Returns_Storage_Visitor_Id() {
        assertEquals("visitor-id", module.visitorId.value)

        val newVisitorId = module.regenerate()

        assertEquals(newVisitorId, module.visitorId.value)
    }

    @Test
    fun regenerate_Passes_Null_To_Storage_When_Identity_Blank_In_DataLayer() {
        every { dataLayer.getString(identityKey) } returns "  "

        val newVisitorId = module.regenerate()

        verify { visitorStorage.changeVisitor(newVisitorId, null) }
    }

    @Test
    fun regenerate_Passes_Null_To_Storage_When_No_Identity_Key_Configured() {
        // the module from setUp watches identityKey, so stop it observing first
        module.onShutdown()
        every { dataLayer.getString(any()) } returns "user@example.com"
        val module = createModule(VisitorIdConfiguration(visitorIdentityKey = null))

        val newVisitorId = module.regenerate()

        verify { visitorStorage.changeVisitor(newVisitorId, null) }
    }

    @Test
    fun init_Identifies_Visitor_When_Identity_Updated_In_DataLayer() {
        every { dataLayer.getString(identityKey) } returns "user@example.com"
        onDataLayerUpdated.onNext(DataObject.create { put(identityKey, "user@example.com") })

        verify { visitorStorage.changeVisitor("visitor-id", "user@example.com".sha256()) }
    }

    @Test
    fun init_DoesNot_Identify_Visitor_When_Identity_Key_IsNull() {
        // the module from setUp watches identityKey, so stop it observing first
        module.onShutdown()
        every { dataLayer.getString(identityKey) } returns "user@example.com"
        val module = createModule(VisitorIdConfiguration(visitorIdentityKey = null))

        onDataLayerUpdated.onNext(DataObject.create { put(identityKey, "user@example.com") })

        verify(inverse = true) { visitorStorage.changeVisitor(any(), any()) }
        assertTrue(module.visitorId.value.isNotEmpty())
    }

    @Test
    fun updateConfiguration_Identifies_Visitor_From_New_Identity_Key() {
        every { dataLayer.getString("newIdentityKey") } returns "newIdentity"

        module.updateConfiguration(
            VisitorIdSettingsBuilder().setVisitorIdentityKey("newIdentityKey").buildConfiguration()
        )

        verify { visitorStorage.changeVisitor("visitor-id", "newIdentity".sha256()) }
    }

    @Test
    fun updateConfiguration_Stops_Identifying_From_Previous_Identity_Key() {
        module.updateConfiguration(
            VisitorIdSettingsBuilder().setVisitorIdentityKey("newIdentityKey").buildConfiguration()
        )

        every { dataLayer.getString(identityKey) } returns "user@example.com"
        onDataLayerUpdated.onNext(DataObject.create { put(identityKey, "user@example.com") })

        verify(inverse = true) {
            visitorStorage.changeVisitor(any(), "user@example.com".sha256())
        }
    }

    @Test
    fun updateConfiguration_Returns_Same_Module_Instance() {
        val result = module.updateConfiguration(DataObject.EMPTY_OBJECT)

        assertSame(module, result)
    }

    @Test
    fun onShutdown_Stops_Identity_Updates() {
        module.onShutdown()

        every { dataLayer.getString(identityKey) } returns "user@example.com"
        onDataLayerUpdated.onNext(DataObject.create { put(identityKey, "user@example.com") })

        verify(inverse = true) { visitorStorage.changeVisitor(any(), any()) }
    }

    @Test
    fun getId_Returns_Expected_Module_Id() {
        assertEquals(Modules.Types.VISITOR_ID, module.id)
    }

    @Test
    fun getVersion_Returns_Library_Version() {
        assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, module.version)
    }

    @Test
    fun factory_ModuleType_Returns_Expected_Module_Id() {
        assertEquals(
            Modules.Types.VISITOR_ID,
            VisitorIdModule.Factory().moduleType
        )
    }

    @Test
    fun factory_CanBeDisabled_Returns_True() {
        assertTrue(VisitorIdModule.Factory().canBeDisabled())
    }

    @Test
    fun factory_GetEnforcedSettings_Returns_Empty_When_No_Settings_Provided() {
        assertTrue(VisitorIdModule.Factory().getEnforcedSettings().isEmpty())
    }

    @Test
    fun factory_GetEnforcedSettings_Returns_Settings_From_Builder() {
        val factory = VisitorIdModule.Factory(VisitorIdSettingsBuilder())

        val enforcedSettings = factory.getEnforcedSettings()

        assertEquals(1, enforcedSettings.size)
        assertEquals(
            Modules.Types.VISITOR_ID,
            enforcedSettings[0].getString(ModuleSettings.KEY_MODULE_TYPE)
        )
    }

    @Test
    fun factory_Create_Returns_Module_Backed_By_Its_Own_Module_Store() {
        val context = createContext()

        val module = VisitorIdModule.Factory().create(
            Modules.Types.VISITOR_ID,
            context,
            DataObject.EMPTY_OBJECT
        )

        assertTrue(module is VisitorIdModule)
        val newVisitorId = (module as VisitorIdModule).visitorId.value
        assertTrue(newVisitorId.isNotEmpty())
        assertEquals(newVisitorId, moduleStore.getString(VisitorStorageImpl.KEY_VISITOR_ID))
        verify {
            storageProvider.getModuleStore(Modules.Types.VISITOR_ID)
            context.dataLayer
        }
    }

    @Test
    fun factory_Create_Configures_Identity_Key_From_Configuration() {
        val context = createContext()
        every { dataLayer.getString("configuredKey") } returns "configuredIdentity"

        VisitorIdModule.Factory().create(
            Modules.Types.VISITOR_ID,
            context,
            VisitorIdSettingsBuilder().setVisitorIdentityKey("configuredKey").buildConfiguration()
        )

        assertEquals(
            "configuredIdentity".sha256(),
            moduleStore.getString(VisitorStorageImpl.KEY_OWNER_IDENTITY)
        )
    }

    @Test
    fun init_MigratesKnownVisitorId_When_NoVisitorIdSaved_And_DeletesMigratedData() {
        every { visitorStorage.visitorId } returns null
        every { visitorIdMigrator.visitorId } returns migratingVisitorId

        val visitorIdModule = createModule(existingVisitorId = existingVisitorId)

        assertEquals(migratingVisitorId, visitorIdModule.visitorId.value)
        verify {
            visitorStorage.changeVisitor(migratingVisitorId, null)
            visitorIdMigrator.delete()
        }
    }

    @Test
    fun init_MigratesKnownVisitorId_FromExistingId_When_NoVisitorIdSaved() {
        every { visitorStorage.visitorId } returns null
        val visitorIdModule = createModule(existingVisitorId = existingVisitorId)

        assertEquals(existingVisitorId, visitorIdModule.visitorId.value)
        verify {
            visitorStorage.changeVisitor(existingVisitorId, null)
        }
    }

    @Test
    fun init_DoesNot_MigrateKnownVisitorId_FromExistingId_When_NoVisitorIdSaved_And_ExistingVisitorId_IsBlank() {
        every { visitorStorage.visitorId } returns null
        val visitorIdModule = createModule(existingVisitorId = " ")

        assertTrue(visitorIdModule.visitorId.value.isNotBlank())
        verify {
            visitorStorage.changeVisitor(match { it.isNotBlank() }, null)
        }
    }

    @Test
    fun init_DoesNot_MigrateKnownVisitorId_FromExistingId_When_NoVisitorIdSaved_And_ExistingVisitorId_IsEmpty() {
        every { visitorStorage.visitorId } returns null
        val visitorIdModule = createModule(existingVisitorId = "")

        assertTrue(visitorIdModule.visitorId.value.isNotEmpty())
        verify {
            visitorStorage.changeVisitor(match { it.isNotEmpty() }, null)
        }
    }

    @Test
    fun init_GeneratesNewId_When_NoVisitorIds() {
        every { visitorStorage.visitorId } returns null
        val visitorIdModule = createModule()

        assertTrue(visitorIdModule.visitorId.value.isNotBlank())
        assertNotEquals(currentVisitorId, visitorIdModule.visitorId.value)
        assertNotEquals(migratingVisitorId, visitorIdModule.visitorId.value)
        assertNotEquals(existingVisitorId, visitorIdModule.visitorId.value)
    }

    @Test
    fun identify_DoesNot_UpdateAnything_When_BlankOrEmptyIdentity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        visitorIdModule.identify("")
        visitorIdModule.identify("   ")

        verify(exactly = 0) {
            visitorStorage.getKnownVisitorId(any())
            visitorStorage.changeVisitor(any(), any())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_DoesNot_UpdateAnything_When_Identity_IsTheSame() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        visitorIdModule.identify(ownerIdentity)

        verify(exactly = 0) {
            visitorStorage.getKnownVisitorId(any())
            visitorStorage.changeVisitor(any(), any())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_Restores_Known_Visitor_When_Current_Visitor_Id_Is_Unowned() {
        // after a regenerate with no identity present, the current id is unowned; a returning
        // identity restores its own visitor id instead of adopting the unowned one
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns null
        every { visitorStorage.getKnownVisitorId(hashedOwnerIdentity) } returns "knownVisitor"
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        visitorIdModule.identify(ownerIdentity)

        verify {
            visitorStorage.changeVisitor("knownVisitor", hashedOwnerIdentity)
            onNext("knownVisitor")
        }
        verify(inverse = true) {
            visitorStorage.changeVisitor(currentVisitorId, hashedOwnerIdentity)
        }
    }

    @Test
    fun identify_Changes_To_Known_Visitor_When_Identity_IsNot_TheSame() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns "knownVisitor"
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        visitorIdModule.identify("newIdentity")

        verify {
            visitorStorage.getKnownVisitorId("newIdentity".sha256())
            visitorStorage.changeVisitor("knownVisitor", "newIdentity".sha256())
            onNext("knownVisitor")
        }
    }

    @Test
    fun identify_Creates_New_VisitorId_WhenIdentityChanged_And_IsNot_Known_Visitor() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns null
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        visitorIdModule.identify("newIdentity")

        verify {
            visitorStorage.changeVisitor(neq(currentVisitorId), "newIdentity".sha256())
            onNext(neq(currentVisitorId))
        }
    }

    @Test
    fun identify_Associates_Current_VisitorId_With_NewIdentity_WhenIdentityChanged_But_No_Previous_Identity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns null
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns null
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        visitorIdModule.identify("newIdentity")

        verify {
            visitorStorage.changeVisitor(currentVisitorId, "newIdentity".sha256())
        }
        verify(exactly = 1) {
            onNext(currentVisitorId)
        }
        confirmVerified(onNext)
    }

    @Test
    fun identify_Stores_Known_Identity_As_Owner_When_It_Already_Uses_Current_Visitor_Id() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns ownerIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns currentVisitorId
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        visitorIdModule.identify("newIdentity")

        verify {
            visitorStorage.changeVisitor(currentVisitorId, "newIdentity".sha256())
        }
        // once on subscribe; storing the switch to the current id is not a change
        verify(exactly = 1) {
            onNext(currentVisitorId)
        }
        confirmVerified(onNext)
    }

    @Test
    fun identify_Completes_Switch_When_Same_Identity_Rewritten_After_Failed_Store() {
        // identity A is known to use v1 while B owns the stored id v0; the first switch to v1 is
        // published but not stored, so the repeated write of A must store it rather than only
        // rewrite the owner against the stale v0
        every { visitorStorage.visitorId } returns "v0"
        every { visitorStorage.ownerIdentity } returns "B".sha256()
        every { visitorStorage.getKnownVisitorId("A".sha256()) } returns "v1"
        every {
            visitorStorage.changeVisitor("v1", "A".sha256())
        } throws PersistenceException("TestError", RuntimeException()) andThenAnswer { }
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        visitorIdModule.identify("A")
        assertEquals("v1", visitorIdModule.visitorId.value)

        visitorIdModule.identify("A")

        verify(exactly = 2) {
            visitorStorage.changeVisitor("v1", "A".sha256())
        }
        verify(exactly = 1) {
            onNext("v1")
        }
    }

    @Test
    fun regenerate_Replaces_Current_Visitor_Id_And_Associates_It_With_Identity_In_DataLayer() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        every { dataLayer.getString(identityKey) } returns ownerIdentity

        val newVisitorId = visitorIdModule.regenerate()

        assertNotEquals(currentVisitorId, newVisitorId)
        verify {
            visitorStorage.changeVisitor(newVisitorId, hashedOwnerIdentity)
            onNext(newVisitorId)
        }
    }

    @Test
    fun regenerate_Leaves_New_Visitor_Id_Unowned_When_No_Identity_In_DataLayer() {
        // the stored owner is not used: only the identity present at the time of the call counts
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        val newVisitorId = visitorIdModule.regenerate()

        assertNotEquals(currentVisitorId, newVisitorId)
        verify {
            visitorStorage.changeVisitor(newVisitorId, null)
            onNext(newVisitorId)
        }
        verify(inverse = true) {
            visitorStorage.changeVisitor(any(), hashedOwnerIdentity)
        }
    }

    @Test(expected = PersistenceException::class)
    fun regenerate_Throws_When_DataStore_Throws() {
        every { visitorStorage.changeVisitor(any(), any()) } throws PersistenceException("TestError", RuntimeException())

        val visitorIdModule = createModule()

        visitorIdModule.regenerate()
    }

    @Test
    fun regenerate_Publishes_New_VisitorId_When_DataStore_Throws() {
        val dataStore = createDataStoreHolding(currentVisitorId)
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule(visitorStorage = VisitorStorageImpl(dataStore))
        visitorIdModule.visitorId.subscribe(onNext)
        every { dataStore.edit() } throws PersistenceException("TestError", RuntimeException())

        assertThrows(PersistenceException::class.java) { visitorIdModule.regenerate() }

        assertNotEquals(currentVisitorId, visitorIdModule.visitorId.value)
        verify {
            onNext(visitorIdModule.visitorId.value)
        }
        // nothing stored, so the previous id is what the next launch restores
        assertEquals(currentVisitorId, dataStore.getString(VisitorStorageImpl.KEY_VISITOR_ID))
    }

    @Test
    fun eraseLocalHistory_Replaces_Current_Visitor_Id_And_Clears_Stored_Identities() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        val visitorId = visitorIdModule.eraseLocalHistory()

        assertNotEquals(currentVisitorId, visitorId)
        verify {
            visitorStorage.clear(visitorId, null)
            onNext(visitorId)
        }
    }

    @Test
    fun eraseLocalHistory_Reassociates_DataLayer_Identity_With_New_Visitor_Id() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)

        every { dataLayer.getString(identityKey) } returns ownerIdentity

        val visitorId = visitorIdModule.eraseLocalHistory()

        verify {
            visitorStorage.clear(visitorId, hashedOwnerIdentity)
            onNext(visitorId)
        }
    }

    @Test(expected = PersistenceException::class)
    fun eraseLocalHistory_Throws_When_DataStore_Throws() {
        every { visitorStorage.clear(any(), any()) } throws PersistenceException("TestError", RuntimeException())

        val visitorIdModule = createModule()

        visitorIdModule.eraseLocalHistory()
    }

    @Test
    fun eraseLocalHistory_Publishes_New_VisitorId_When_DataStore_Throws() {
        val dataStore = createDataStoreHolding(currentVisitorId, hashedOwnerIdentity)
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule(visitorStorage = VisitorStorageImpl(dataStore))
        visitorIdModule.visitorId.subscribe(onNext)
        every { dataStore.edit() } throws PersistenceException("TestError", RuntimeException())

        assertThrows(PersistenceException::class.java) { visitorIdModule.eraseLocalHistory() }

        assertNotEquals(currentVisitorId, visitorIdModule.visitorId.value)
        verify {
            onNext(visitorIdModule.visitorId.value)
        }
        // nothing erased, so the previous id and its associations are what the next launch restores
        assertEquals(currentVisitorId, dataStore.getString(VisitorStorageImpl.KEY_VISITOR_ID))
        assertEquals(
            hashedOwnerIdentity,
            dataStore.getString(VisitorStorageImpl.KEY_OWNER_IDENTITY)
        )
        assertEquals(
            currentVisitorId,
            dataStore.getString(VisitorStorageImpl.IDENTITY_KEY_PREFIX + hashedOwnerIdentity)
        )
    }

    @Test
    fun identify_Publishes_New_VisitorId_And_DoesNot_Throw_When_DataStore_Throws() {
        // another identity owns the current id and the new one is unknown, so a new id is minted
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.ownerIdentity } returns hashedOwnerIdentity
        every { visitorStorage.getKnownVisitorId("newIdentity".sha256()) } returns null
        val onNext = mockk<(String) -> Unit>(relaxed = true)

        val visitorIdModule = createModule()
        visitorIdModule.visitorId.subscribe(onNext)
        every {
            visitorStorage.changeVisitor(any(), any())
        } throws PersistenceException("TestError", RuntimeException())

        visitorIdModule.identify("newIdentity")

        assertNotEquals(currentVisitorId, visitorIdModule.visitorId.value)
        verify {
            onNext(visitorIdModule.visitorId.value)
        }
    }

    @Test
    fun init_Uses_New_VisitorId_Without_Persisting_When_DataStore_Throws() {
        val dataStore = createDataStoreHolding(storedVisitorId = null)
        every { dataStore.edit() } throws PersistenceException("TestError", RuntimeException())

        val visitorIdModule = createModule(visitorStorage = VisitorStorageImpl(dataStore))

        assertTrue(visitorIdModule.visitorId.value.isNotBlank())
        assertEquals(null, dataStore.getString(VisitorStorageImpl.KEY_VISITOR_ID))
    }

    @Test
    fun identify_Hashes_Identity_As_Lowercase_Sha256_Hex_When_New_Identity() {
        every { visitorStorage.visitorId } returns currentVisitorId
        every { visitorStorage.getKnownVisitorId(any()) } returns null

        val visitorIdModule = createModule()
        visitorIdModule.identify("newIdentity")

        verify {
            visitorStorage.getKnownVisitorId(NEW_IDENTITY_SHA256)
            visitorStorage.changeVisitor(any(), NEW_IDENTITY_SHA256)
        }
    }

    @Test
    fun identify_Stores_Identity_Key_With_Lowercase_Sha256_When_New_Identity() {
        val editor = mockkEditor()
        val dataStore = mockk<DataStore>()
        every { dataStore.getString(any()) } returns null
        every { dataStore.edit() } returns editor

        val visitorIdModule = createModule(
            visitorStorage = VisitorStorageImpl(dataStore),
            existingVisitorId = existingVisitorId
        )
        visitorIdModule.identify("newIdentity")

        verify {
            editor.put(
                VisitorStorageImpl.IDENTITY_KEY_PREFIX + NEW_IDENTITY_SHA256,
                DataItem.string(visitorIdModule.visitorId.value),
                Expiry.FOREVER
            )
        }
    }

    private fun createModule(
        configuration: VisitorIdConfiguration = VisitorIdConfiguration(identityKey),
        visitorStorage: VisitorStorage = this.visitorStorage,
        existingVisitorId: String? = null
    ): VisitorIdModule = VisitorIdModule(
        visitorStorage = visitorStorage,
        migrator = visitorIdMigrator,
        existingVisitorId = existingVisitorId,
        dataLayer = dataLayer,
        configuration = configuration,
        logger = SystemLogger
    )

    /**
     * A [DataStore] mock that reads back [storedVisitorId] and, when given, [storedOwnerIdentity]
     * with its association, so a [VisitorStorageImpl] over it behaves like one restored from disk.
     * Writes are left unstubbed for the test to control.
     */
    private fun createDataStoreHolding(
        storedVisitorId: String?,
        storedOwnerIdentity: String? = null
    ): DataStore {
        val stored = buildMap<String, String?> {
            storedVisitorId?.let { put(VisitorStorageImpl.KEY_VISITOR_ID, it) }
            storedOwnerIdentity?.let {
                put(VisitorStorageImpl.KEY_OWNER_IDENTITY, it)
                put(VisitorStorageImpl.IDENTITY_KEY_PREFIX + it, storedVisitorId)
            }
        }
        val dataStore = mockk<DataStore>()
        every { dataStore.getString(any()) } answers { stored[firstArg()] }
        every { dataStore.keys() } returns stored.keys.toList()
        return dataStore
    }

    /**
     * A [TealiumContext] backed by real in-memory stores, with the data layer store replaced by
     * [dataLayer] so that identity updates can be driven from the tests.
     */
    private fun createContext(): TealiumContext {
        val app = RuntimeEnvironment.getApplication()
        val databaseProvider = InMemoryDatabaseProvider(getDefaultConfig(app))
        val storeProvider =
            ModuleStoreProviderImpl(databaseProvider, SQLModulesRepository(databaseProvider))

        moduleStore = storeProvider.getModuleStore(Modules.Types.VISITOR_ID)

        storageProvider = mockk(relaxed = true)
        every {
            storageProvider.getModuleStore(Modules.Types.VISITOR_ID)
        } returns moduleStore

        val context = mockk<TealiumContext>(relaxed = true)
        every { context.storageProvider } returns storageProvider
        every { context.dataLayer } returns dataLayer
        every { context.config } returns getDefaultConfig(app)
        every { context.logger } returns SystemLogger

        return context
    }

    private companion object {
        /** Lowercase hex SHA-256 of "newIdentity", matching the Swift SDK output. */
        const val NEW_IDENTITY_SHA256 =
            "2d66ba8ba26bf2ee9c7b40d5225e73c1db3a56f158cc9f669fbe340590af62db"
    }
}
