package com.tealium.prism.core.internal.modules.visitorid

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleProxy
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.settings.modules.VisitorIdSettingsBuilder
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.modules.ModuleProxyImpl
import com.tealium.prism.core.internal.persistence.ModuleStoreProviderImpl
import com.tealium.prism.core.internal.persistence.VisitorIdStorageImpl
import com.tealium.prism.core.internal.persistence.database.InMemoryDatabaseProvider
import com.tealium.prism.core.internal.persistence.repositories.SQLModulesRepository
import com.tealium.prism.core.internal.persistence.stores.VisitorStorage
import com.tealium.prism.core.internal.persistence.stores.VisitorStorageImpl
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.core.internal.settings.SdkSettings
import com.tealium.prism.core.internal.utils.sha256
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.buildConfiguration
import com.tealium.tests.common.getDefaultConfig
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VisitorIdManagerWrapperTests {

    @RelaxedMockK
    private lateinit var visitorIdModule: VisitorIdModule

    @RelaxedMockK
    private lateinit var observer: Observer<TealiumResult<String>>

    private lateinit var visitorId: StateSubject<String>
    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var moduleManager: ModuleManager
    private lateinit var onModuleManager: Subject<TealiumResult<ModuleManager>>
    private lateinit var proxy: ModuleProxy<VisitorIdModule>

    private lateinit var visitorIdManagerWrapper: VisitorIdManagerWrapper
    private val scheduler = Scheduler.SYNCHRONOUS

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        visitorId = Observables.stateSubject("visitor-id")
        every { visitorIdModule.id } returns Modules.Types.VISITOR_ID
        every { visitorIdModule.visitorId } returns visitorId

        // Modules provided upfront count as ready without a settings pass.
        modules = Observables.stateSubject(listOf(visitorIdModule))
        moduleManager = ModuleManagerImpl(scheduler, modules)
        onModuleManager = Observables.replaySubject(1)
        onModuleManager.onNext(TealiumResult.success(moduleManager))

        proxy = ModuleProxyImpl(VisitorIdModule::class.java, onModuleManager, scheduler)
        visitorIdManagerWrapper = VisitorIdManagerWrapper(proxy)
    }

    @Test
    fun methods_Report_Module_Not_Enabled_When_Not_Enabled() {
        modules.onNext(emptyList())

        visitorIdManagerWrapper.current().subscribe(observer)
        visitorIdManagerWrapper.regenerate().subscribe(observer)
        visitorIdManagerWrapper.eraseLocalHistory().subscribe(observer)

        verify(exactly = 3) {
            observer.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
        }
        verify {
            visitorIdModule wasNot Called
        }
    }

    @Test
    fun methods_Report_Tealium_Shutdown_When_Tealium_Shutdown() {
        onModuleManager.onNext(TealiumResult.failure(shutdownException()))

        visitorIdManagerWrapper.current().subscribe(observer)
        visitorIdManagerWrapper.regenerate().subscribe(observer)
        visitorIdManagerWrapper.eraseLocalHistory().subscribe(observer)

        verify(exactly = 3) {
            observer.onNext(match { it.exceptionOrNull() is Tealium.TealiumShutdownException })
        }
        verify {
            visitorIdModule wasNot Called
        }
    }

    @Test
    fun current_Returns_Current_Visitor_Id_When_Module_Enabled() {
        visitorIdManagerWrapper.current().subscribe(observer)

        verify {
            observer.onNext(match { it.getOrNull() == "visitor-id" })
        }
    }

    @Test
    fun regenerate_Returns_New_Visitor_Id_From_Module() {
        every { visitorIdModule.regenerate() } returns "regenerated-visitor-id"

        visitorIdManagerWrapper.regenerate().subscribe(observer)

        verify {
            visitorIdModule.regenerate()
            observer.onNext(match { it.getOrNull() == "regenerated-visitor-id" })
        }
    }

    @Test
    fun regenerate_Returns_Failure_When_Module_Throws() {
        val exception = PersistenceException("failed to store visitor id", IllegalStateException())
        every { visitorIdModule.regenerate() } throws exception

        visitorIdManagerWrapper.regenerate().subscribe(observer)

        verify {
            observer.onNext(match { it.exceptionOrNull() === exception })
        }
    }

    @Test
    fun eraseLocalHistory_Returns_New_Visitor_Id_From_Module() {
        every { visitorIdModule.eraseLocalHistory() } returns "erased-visitor-id"

        visitorIdManagerWrapper.eraseLocalHistory().subscribe(observer)

        verify {
            visitorIdModule.eraseLocalHistory()
            observer.onNext(match { it.getOrNull() == "erased-visitor-id" })
        }
    }

    @Test
    fun eraseLocalHistory_Returns_Failure_When_Module_Throws() {
        val exception = PersistenceException("failed to erase visitor ids", IllegalStateException())
        every { visitorIdModule.eraseLocalHistory() } throws exception

        visitorIdManagerWrapper.eraseLocalHistory().subscribe(observer)

        verify {
            observer.onNext(match { it.exceptionOrNull() === exception })
        }
    }

    @Test
    fun subscribe_Emits_Current_And_Updated_Visitor_Ids_As_Success() {
        visitorIdManagerWrapper.subscribe(observer)

        visitorId.onNext("new-visitor-id")

        verifyOrder {
            observer.onNext(match { it.getOrNull() == "visitor-id" })
            observer.onNext(match { it.getOrNull() == "new-visitor-id" })
        }
        verify(exactly = 2) { observer.onNext(any()) }
        verify(exactly = 0) { observer.onComplete() }
    }

    @Test
    fun subscribe_Emits_New_Visitor_Id_When_Second_Identity_Set_In_DataLayer() {
        val fixture = createDataLayerFixture()
        val emitted = mutableListOf<TealiumResult<String>>()
        fixture.wrapper.subscribe { emitted.add(it) }

        fixture.dataLayer.edit().put(IDENTITY_KEY, "a", Expiry.SESSION).commit()
        fixture.dataLayer.edit().put(IDENTITY_KEY, "b", Expiry.SESSION).commit()

        // the seeded identity owns the initial visitor id, so "a" and "b" are each a new visitor
        val visitorIds = emitted.map { it.getOrThrow() }.distinct()
        assertEquals(3, visitorIds.size)
    }

    @Test
    fun subscribe_Emits_New_Visitor_Id_When_Regenerate_Storage_Throws() {
        val fixture = createThrowingStorageFixture()
        val emitted = mutableListOf<TealiumResult<String>>()
        fixture.wrapper.subscribe { emitted.add(it) }
        val exception = PersistenceException("failed to store visitor id", IllegalStateException())
        every { fixture.visitorStorage.changeVisitor(any(), any()) } throws exception

        fixture.wrapper.regenerate().subscribe(observer)

        verify {
            observer.onNext(match { it.exceptionOrNull() === exception })
        }
        val visitorIds = emitted.map { it.getOrThrow() }
        assertEquals(2, visitorIds.size)
        assertEquals(STORED_VISITOR_ID, visitorIds[0])
        assertNotEquals(STORED_VISITOR_ID, visitorIds[1])
    }

    @Test
    fun regenerate_Binds_Identity_Already_In_DataLayer_When_Queued_Before_Module_Creation() {
        val config = getDefaultConfig(RuntimeEnvironment.getApplication())
        val storeProvider = createStoreProvider(config)
        val dataLayer = storeProvider.getModuleStore(Modules.Types.DATA_LAYER)
        dataLayer.edit().put(IDENTITY_KEY, "identityValue", Expiry.FOREVER).commit()
        // no modules and no settings pass yet, so the manager is not ready
        val notReadyManager = ModuleManagerImpl(scheduler)
        notReadyManager.addModuleFactory(VisitorIdModule.Factory())
        val wrapper = VisitorIdManagerWrapper(
            ModuleProxyImpl(
                VisitorIdModule::class.java,
                Observables.just(TealiumResult.success<ModuleManager>(notReadyManager)),
                scheduler
            )
        )

        // regenerate subscribes to the module straight away, but the readiness gate holds it
        // until the settings pass below creates the module, which seeds its identity from the
        // data layer; the regenerate is therefore bound to that identity
        val regenerateResult = wrapper.regenerate()
        notReadyManager.updateModuleSettings(
            createContext(config, storeProvider, dataLayer),
            createVisitorIdSettings()
        )
        var newId: String? = null
        regenerateResult.subscribe { newId = it.getOrThrow() }

        val hashedIdentity = "identityValue".sha256()
        val visitorStorage =
            VisitorStorageImpl(storeProvider.getModuleStore(Modules.Types.VISITOR_ID))
        assertNotNull(newId)
        assertEquals(newId, visitorStorage.visitorId)
        assertEquals(hashedIdentity, visitorStorage.ownerIdentity)
        assertEquals(newId, visitorStorage.getKnownVisitorId(hashedIdentity))
    }

    @Test
    fun subscribe_Emits_Nothing_Until_Modules_Ready() {
        // No modules preloaded and no settings pass completed, so the manager is not ready.
        val notReadyManager = ModuleManagerImpl(scheduler)
        val wrapper = VisitorIdManagerWrapper(
            ModuleProxyImpl(
                VisitorIdModule::class.java,
                Observables.just(TealiumResult.success(notReadyManager)),
                scheduler
            )
        )

        wrapper.subscribe(observer)

        verify(exactly = 0) {
            observer.onNext(any())
            observer.onComplete()
        }
    }

    @Test
    fun subscribe_DoesNot_Reemit_When_Identical_Settings_Reapplied() {
        val config = getDefaultConfig(RuntimeEnvironment.getApplication())
        val storeProvider = createStoreProvider(config)
        val dataLayer = storeProvider.getModuleStore(Modules.Types.DATA_LAYER)
        val context = createContext(config, storeProvider, dataLayer)
        val manager = ModuleManagerImpl(scheduler)
        manager.addModuleFactory(VisitorIdModule.Factory())
        val wrapper = VisitorIdManagerWrapper(
            ModuleProxyImpl(
                VisitorIdModule::class.java,
                Observables.just(TealiumResult.success<ModuleManager>(manager)),
                scheduler
            )
        )
        val settings = createVisitorIdSettings()
        manager.updateModuleSettings(context, settings)
        wrapper.subscribe(observer)
        verify(exactly = 1) { observer.onNext(match { it.isSuccess }) }

        // ModuleManager re-emits only when the module identity changes; updateConfiguration
        // returns the same instance for identical settings, so nothing new must be emitted.
        manager.updateModuleSettings(context, settings)

        verify(exactly = 1) { observer.onNext(any()) }
        verify(exactly = 0) { observer.onComplete() }
    }

    @Test
    fun subscribe_Emits_ModuleNotEnabled_Failure_When_Module_Disabled_Then_Visitor_Id_When_Re_Enabled() {
        visitorIdManagerWrapper.subscribe(observer)

        modules.onNext(emptyList())
        modules.onNext(listOf(visitorIdModule))

        verifyOrder {
            observer.onNext(match { it.getOrNull() == "visitor-id" })
            observer.onNext(match { it.exceptionOrNull() is ModuleNotEnabledException })
            observer.onNext(match { it.getOrNull() == "visitor-id" })
        }
        verify(exactly = 3) { observer.onNext(any()) }
        verify(exactly = 0) { observer.onComplete() }
    }

    @Test
    fun subscribe_Emits_Single_Failure_Then_Completes_When_Tealium_Shutdown() {
        val shutdownException = shutdownException()
        visitorIdManagerWrapper.subscribe(observer)

        onModuleManager.onNext(TealiumResult.failure(shutdownException))
        onModuleManager.onComplete()
        visitorId.onNext("new-visitor-id")

        verifyOrder {
            observer.onNext(match { it.getOrNull() == "visitor-id" })
            observer.onNext(match { it.exceptionOrNull() === shutdownException })
            observer.onComplete()
        }
        verify(exactly = 2) { observer.onNext(any()) }
        verify(exactly = 1) { observer.onComplete() }
    }

    @Test
    fun subscribe_Emits_Single_Failure_Then_Completes_When_Tealium_Failed_To_Initialize() {
        val initException = TealiumException("Failed to initialize")
        val failedInit: Subject<TealiumResult<ModuleManager>> = Observables.replaySubject(1)
        failedInit.onNext(TealiumResult.failure(initException))
        failedInit.onComplete()
        val wrapper = VisitorIdManagerWrapper(
            ModuleProxyImpl(VisitorIdModule::class.java, failedInit, scheduler)
        )

        wrapper.subscribe(observer)

        verifyOrder {
            observer.onNext(match { it.exceptionOrNull() === initException })
            observer.onComplete()
        }
        verify(exactly = 1) { observer.onNext(any()) }
        verify(exactly = 1) { observer.onComplete() }
    }

    @Test
    fun init_Delegates_Proxy_To_Tealium() {
        val proxy = mockk<ModuleProxy<VisitorIdModule>>(relaxed = true)
        val tealium = mockk<Tealium>()
        every { tealium.createModuleProxy(VisitorIdModule::class.java) } returns proxy

        VisitorIdManagerWrapper(tealium)

        verify {
            tealium.createModuleProxy(VisitorIdModule::class.java)
        }
    }

    private fun shutdownException() =
        Tealium.TealiumShutdownException("Tealium Instance has been shutdown.")

    /**
     * A [VisitorIdManagerWrapper] over a real [VisitorIdModule], backed by in-memory stores, whose
     * identity is read from [IDENTITY_KEY] in [DataLayerFixture.dataLayer]. The data layer is
     * seeded with an identity so that the initial visitor id already has an owner.
     */
    private fun createDataLayerFixture(): DataLayerFixture {
        val config = getDefaultConfig(RuntimeEnvironment.getApplication())
        val storeProvider = createStoreProvider(config)
        val dataLayer = storeProvider.getModuleStore(Modules.Types.DATA_LAYER)
        dataLayer.edit().put(IDENTITY_KEY, "seed", Expiry.SESSION).commit()
        val module = VisitorIdModule(
            VisitorIdStorageImpl(
                config,
                storeProvider.getModuleStore(Modules.Types.VISITOR_ID),
                SystemLogger
            ),
            VisitorIdConfiguration(IDENTITY_KEY),
            dataLayer
        )
        return DataLayerFixture(createWrapper(module), dataLayer)
    }

    /**
     * A [VisitorIdManagerWrapper] over a real [VisitorIdModule] whose [VisitorStorage] is a mock
     * holding [STORED_VISITOR_ID], so tests can make its writes throw.
     */
    private fun createThrowingStorageFixture(): ThrowingStorageFixture {
        val config = getDefaultConfig(RuntimeEnvironment.getApplication())
        val visitorStorage = mockk<VisitorStorage>(relaxed = true)
        every { visitorStorage.visitorId } returns STORED_VISITOR_ID
        val module = VisitorIdModule(
            VisitorIdStorageImpl(
                visitorStorage,
                VisitorIdStorageImpl.VisitorIdMigratorImpl(),
                null,
                SystemLogger
            ),
            VisitorIdConfiguration(null),
            createStoreProvider(config).getModuleStore(Modules.Types.DATA_LAYER)
        )
        return ThrowingStorageFixture(createWrapper(module), visitorStorage)
    }

    private fun createVisitorIdSettings(): SdkSettings = SdkSettings(
        modules = mapOf(
            Modules.Types.VISITOR_ID to ModuleSettings(
                Modules.Types.VISITOR_ID,
                configuration = VisitorIdSettingsBuilder()
                    .setVisitorIdentityKey(IDENTITY_KEY)
                    .buildConfiguration()
            )
        )
    )

    private fun createStoreProvider(config: TealiumConfig): ModuleStoreProviderImpl {
        val databaseProvider = InMemoryDatabaseProvider(config)
        return ModuleStoreProviderImpl(databaseProvider, SQLModulesRepository(databaseProvider))
    }

    private fun createContext(
        config: TealiumConfig,
        storeProvider: ModuleStoreProviderImpl,
        dataLayer: DataStore
    ): TealiumContext {
        val context = mockk<TealiumContext>(relaxed = true)
        every { context.config } returns config
        every { context.storageProvider } returns storeProvider
        every { context.dataLayer } returns dataLayer
        every { context.logger } returns SystemLogger
        return context
    }

    private fun createWrapper(module: VisitorIdModule): VisitorIdManagerWrapper {
        val moduleManager = ModuleManagerImpl(scheduler, Observables.stateSubject(listOf(module)))
        val proxy = ModuleProxyImpl(
            VisitorIdModule::class.java,
            Observables.stateSubject(TealiumResult.success<ModuleManager>(moduleManager)),
            scheduler
        )
        return VisitorIdManagerWrapper(proxy)
    }

    private data class DataLayerFixture(
        val wrapper: VisitorIdManagerWrapper,
        val dataLayer: DataStore
    )

    private data class ThrowingStorageFixture(
        val wrapper: VisitorIdManagerWrapper,
        val visitorStorage: VisitorStorage
    )

    private companion object {
        const val IDENTITY_KEY = "identity"
        const val STORED_VISITOR_ID = "stored-visitor-id"
    }
}
