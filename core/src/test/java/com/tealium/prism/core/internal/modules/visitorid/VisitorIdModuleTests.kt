package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.BuildConfig
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.ModuleStoreProvider
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.settings.modules.VisitorIdSettingsBuilder
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.VisitorIdStorage
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.utils.sha256
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.buildConfiguration
import com.tealium.tests.common.getDefaultConfig
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VisitorIdModuleTests {

    @RelaxedMockK
    private lateinit var storage: VisitorIdStorage

    @MockK
    private lateinit var dataLayer: DataStore

    private val identityKey = "identity"
    private val dispatchContext =
        DispatchContext(DispatchContext.Source.application(), DataObject.EMPTY_OBJECT)

    private lateinit var visitorId: StateSubject<String>
    private lateinit var onDataLayerUpdated: Subject<DataObject>
    private lateinit var module: VisitorIdModule

    private lateinit var moduleStore: DataStore
    private lateinit var storageProvider: ModuleStoreProvider

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        visitorId = Observables.stateSubject("visitor-id")
        every { storage.visitorId } returns visitorId

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
        visitorId.onNext("new-visitor-id")

        val result = module.collect(dispatchContext)

        assertEquals("new-visitor-id", result.getString(Dispatch.Keys.TEALIUM_VISITOR_ID))
    }

    @Test
    fun visitorId_Returns_Storage_Visitor_Id() {
        assertSame(visitorId, module.visitorId)
        assertEquals("visitor-id", module.visitorId.value)

        visitorId.onNext("new-visitor-id")

        assertEquals("new-visitor-id", module.visitorId.value)
    }

    @Test
    fun regenerate_Returns_New_Visitor_Id_From_Storage() {
        every { storage.regenerate(any()) } returns "regenerated-visitor-id"

        assertEquals("regenerated-visitor-id", module.regenerate())
        verify { storage.regenerate(null) }
    }

    @Test
    fun regenerate_Passes_Hashed_Identity_Read_From_DataLayer_To_Storage() {
        every { dataLayer.getString(identityKey) } returns "user@example.com"

        module.regenerate()

        verify { storage.regenerate("user@example.com".sha256()) }
    }

    @Test
    fun regenerate_Passes_Null_To_Storage_When_Identity_Blank_In_DataLayer() {
        every { dataLayer.getString(identityKey) } returns "  "

        module.regenerate()

        verify { storage.regenerate(null) }
    }

    @Test
    fun regenerate_Passes_Null_To_Storage_When_No_Identity_Key_Configured() {
        // the module from setUp watches identityKey, so stop it observing first
        module.onShutdown()
        every { dataLayer.getString(any()) } returns "user@example.com"
        val module = createModule(VisitorIdConfiguration(visitorIdentityKey = null))

        module.regenerate()

        verify { storage.regenerate(null) }
    }

    @Test
    fun eraseLocalHistory_Returns_New_Visitor_Id_From_Storage() {
        every { storage.eraseLocalHistory(any()) } returns "erased-visitor-id"

        assertEquals("erased-visitor-id", module.eraseLocalHistory())
        verify { storage.eraseLocalHistory(null) }
    }

    @Test
    fun eraseLocalHistory_Passes_Hashed_Identity_Read_From_DataLayer_To_Storage() {
        every { dataLayer.getString(identityKey) } returns "user@example.com"

        module.eraseLocalHistory()

        verify { storage.eraseLocalHistory("user@example.com".sha256()) }
    }

    @Test
    fun identify_Delegates_To_Storage() {
        module.identify("user@example.com")

        verify { storage.identify("user@example.com") }
    }

    @Test
    fun init_Identifies_Visitor_When_Identity_Updated_In_DataLayer() {
        every { dataLayer.getString(identityKey) } returns "user@example.com"
        onDataLayerUpdated.onNext(DataObject.create { put(identityKey, "user@example.com") })

        verify { storage.identify("user@example.com") }
    }

    @Test
    fun init_DoesNot_Identify_Visitor_When_Identity_Key_IsNull() {
        // the module from setUp watches identityKey, so stop it observing first
        module.onShutdown()
        every { dataLayer.getString(identityKey) } returns "user@example.com"
        val module = createModule(VisitorIdConfiguration(visitorIdentityKey = null))

        onDataLayerUpdated.onNext(DataObject.create { put(identityKey, "user@example.com") })

        verify(inverse = true) { storage.identify(any()) }
        assertTrue(module.visitorId.value.isNotEmpty())
    }

    @Test
    fun updateConfiguration_Identifies_Visitor_From_New_Identity_Key() {
        every { dataLayer.getString("newIdentityKey") } returns "newIdentity"

        module.updateConfiguration(
            VisitorIdSettingsBuilder().setVisitorIdentityKey("newIdentityKey").buildConfiguration()
        )

        verify { storage.identify("newIdentity") }
    }

    @Test
    fun updateConfiguration_Stops_Identifying_From_Previous_Identity_Key() {
        module.updateConfiguration(
            VisitorIdSettingsBuilder().setVisitorIdentityKey("newIdentityKey").buildConfiguration()
        )

        every { dataLayer.getString(identityKey) } returns "user@example.com"
        onDataLayerUpdated.onNext(DataObject.create { put(identityKey, "user@example.com") })

        verify(inverse = true) { storage.identify("user@example.com") }
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

        verify(inverse = true) { storage.identify(any()) }
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

    private fun createModule(
        configuration: VisitorIdConfiguration = VisitorIdConfiguration(identityKey)
    ): VisitorIdModule = VisitorIdModule(storage, configuration, dataLayer)

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
}
