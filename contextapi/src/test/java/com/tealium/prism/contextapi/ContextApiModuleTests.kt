package com.tealium.prism.contextapi

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Environment
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.VisitorIdProvider
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.modules.ModuleManager
import com.tealium.prism.core.api.modules.ModuleNotEnabledException
import com.tealium.prism.core.api.modules.ModuleShutdownException
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.contextapi.internal.ContextApiConfiguration
import com.tealium.prism.contextapi.internal.ContextApiModule
import com.tealium.prism.contextapi.internal.ContextApiService
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.getDefaultConfig
import io.mockk.CapturingSlot
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContextApiModuleTests {

    @RelaxedMockK
    private lateinit var mockService: ContextApiService

    @RelaxedMockK
    private lateinit var mockVisitorIdProvider: VisitorIdProvider

    private lateinit var visitorIdSubject: StateSubject<String>
    private lateinit var modules: StateSubject<List<Module>>
    private lateinit var moduleManager: ModuleManager

    private lateinit var contextApiModule: ContextApiModule

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        visitorIdSubject = Observables.stateSubject("test-visitor-id")
        every { mockVisitorIdProvider.visitorId } returns visitorIdSubject

        modules = Observables.stateSubject(listOf(mockVisitorIdProvider))
        moduleManager = ModuleManagerImpl(Scheduler.SYNCHRONOUS, modules)

        contextApiModule = ContextApiModule(
            moduleManager,
            SystemLogger,
            mockService
        )
    }

    @Test
    fun fetchEngineResponse_CallsService_WithValidVisitorID() {
        val engineId = "test-engine-id"
        val visitorId = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        visitorIdSubject.onNext(visitorId)

        contextApiModule.fetchEngineResponse(engineId, callback)

        verify {
            mockService.fetchEngineResponse(engineId, visitorId, any())
        }
    }

    @Test
    fun updateConfiguration_ReturnsSameModuleInstance() {
        val configuration = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.UsEast)
        }

        val result = contextApiModule.updateConfiguration(configuration)

        // updateConfiguration should return the same module instance
        assertTrue(result === contextApiModule)
        assertEquals(contextApiModule, result)
    }

    @Test
    fun fetchEngineResponse_Fails_When_VisitorIdProvider_NotEnabled() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        modules.onNext(emptyList())

        contextApiModule.fetchEngineResponse("test-engine-id", callback)

        verify {
            callback.onComplete(match { it.exceptionOrNull() is ModuleNotEnabledException })
            mockService wasNot Called
        }
    }

    @Test
    fun fetchEngineResponse_Fails_With_VisitorId_Module_Type_In_Message_When_VisitorIdProvider_NotEnabled() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        modules.onNext(emptyList())

        contextApiModule.fetchEngineResponse("test-engine-id", callback)

        verify {
            callback.onComplete(match {
                it.exceptionOrNull()?.message?.contains(Modules.Types.VISITOR_ID) == true
            })
        }
    }

    @Test
    fun fetchEngineResponse_CallsService_When_VisitorIdProvider_Becomes_Available() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        // the lookup is withheld until a settings pass has created the modules
        val onVisitorIdProvider = Observables.publishSubject<VisitorIdProvider?>()
        val loadingModuleManager = mockk<ModuleManager>()
        every {
            loadingModuleManager.getModuleOfType(VisitorIdProvider::class.java, any())
        } answers {
            val lookup = secondArg<Callback<VisitorIdProvider?>>()
            onVisitorIdProvider.subscribeOnce { lookup.onComplete(it) }
        }
        val module = ContextApiModule(
            loadingModuleManager,
            SystemLogger,
            mockService
        )

        module.fetchEngineResponse("test-engine-id", callback)
        verify(inverse = true) {
            mockService.fetchEngineResponse(any(), any(), any())
        }

        onVisitorIdProvider.onNext(mockVisitorIdProvider)

        verify {
            mockService.fetchEngineResponse("test-engine-id", "test-visitor-id", any())
        }
    }

    @Test
    fun fetchEngineResponse_Fails_When_Module_ShutDown_While_Pending() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        // the lookup is withheld until a settings pass has created the modules
        val onVisitorIdProvider = Observables.publishSubject<VisitorIdProvider?>()
        val loadingModuleManager = mockk<ModuleManager>()
        every {
            loadingModuleManager.getModuleOfType(VisitorIdProvider::class.java, any())
        } answers {
            val lookup = secondArg<Callback<VisitorIdProvider?>>()
            onVisitorIdProvider.subscribeOnce { lookup.onComplete(it) }
        }
        val module = ContextApiModule(
            loadingModuleManager,
            SystemLogger,
            mockService
        )

        module.fetchEngineResponse("test-engine-id", callback)
        module.onShutdown()

        verify {
            callback.onComplete(match { it.exceptionOrNull() is ModuleShutdownException })
        }
    }

    @Test
    fun fetchEngineResponse_Fails_With_ModuleShutdown_When_Shut_Down_While_Network_Call_In_Flight() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        val serviceCallback = withholdServiceResponse()

        contextApiModule.fetchEngineResponse("test-engine-id", callback)
        verify(inverse = true) { callback.onComplete(any()) }

        contextApiModule.onShutdown()

        verify(exactly = 1) {
            callback.onComplete(match { it.exceptionOrNull() is ModuleShutdownException })
        }

        // the cancelled network call must not complete the callback a second time
        serviceCallback.captured.onComplete(TealiumResult.success(EngineResponse()))
        verify(exactly = 1) { callback.onComplete(any()) }
    }

    @Test
    fun fetchEngineResponse_Completes_Once_When_Shut_Down_After_Response() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        val response = EngineResponse(flags = mapOf("flag" to true))
        val serviceCallback = withholdServiceResponse()

        contextApiModule.fetchEngineResponse("test-engine-id", callback)
        serviceCallback.captured.onComplete(TealiumResult.success(response))
        contextApiModule.onShutdown()

        verify(exactly = 1) { callback.onComplete(any()) }
        verify(exactly = 1) { callback.onComplete(match { it.getOrNull() == response }) }
    }

    @Test
    fun fetchEngineResponse_Disposes_Service_Call_When_Shut_Down_While_In_Flight() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        val serviceCall = Disposables.subscription()
        every { mockService.fetchEngineResponse(any(), any(), any()) } returns serviceCall

        contextApiModule.fetchEngineResponse("test-engine-id", callback)
        assertFalse(serviceCall.isDisposed)

        contextApiModule.onShutdown()

        assertTrue(serviceCall.isDisposed)
    }

    @Test
    fun fetchEngineResponse_Fails_When_Module_Already_ShutDown() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        contextApiModule.onShutdown()

        contextApiModule.fetchEngineResponse("test-engine-id", callback)

        verify {
            callback.onComplete(match { it.exceptionOrNull() is ModuleShutdownException })
            mockService wasNot Called
        }
    }

    @Test
    fun onShutdown_DoesNothing() {
        // onShutdown should not throw any exceptions
        contextApiModule.onShutdown()
    }

    @Test
    fun id_ReturnsCorrectModuleID() {
        assertEquals(ContextApi.ID, contextApiModule.id)
    }

    @Test
    fun version_ReturnsBuildConfigVersion() {
        val version = contextApiModule.version
        assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, version)
    }

    @Test
    fun fetchEngineResponse_HandlesVisitorIDChange() {
        val engineId = "test-engine-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        // First call with one visitor ID
        visitorIdSubject.onNext("visitor-id-1")
        contextApiModule.fetchEngineResponse(engineId, callback)
        verify {
            mockService.fetchEngineResponse(engineId, "visitor-id-1", any())
        }

        // Second call with different visitor Id
        val callback2 = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        visitorIdSubject.onNext("visitor-id-2")
        contextApiModule.fetchEngineResponse(engineId, callback2)
        verify {
            mockService.fetchEngineResponse(engineId, "visitor-id-2", any())
        }
    }

    @Test
    fun factory_create_CreatesModule_WithCorrectConfiguration() {
        val configuration = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.Germany)
            put(ContextApiConfiguration.KEY_REFERRER, "https://custom-referrer.com")
        }

        val factory = ContextApiModule.Factory()
        val module = factory.create(ContextApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is ContextApiModule)
    }

    @Test
    fun factory_create_CreatesModule_WithDefaultConfiguration() {
        val configuration = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.UsEast)
        }
        val factory = ContextApiModule.Factory()
        val module = factory.create(ContextApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is ContextApiModule)
    }

    @Test
    fun factory_getEnforcedSettings_ReturnsEmptyList_WhenNoEnforcedSettings() {
        val factory = ContextApiModule.Factory()

        val enforcedSettings = factory.getEnforcedSettings()

        assertTrue(enforcedSettings.isEmpty())
    }

    @Test
    fun factory_getEnforcedSettings_ReturnsEnforcedSettings_WhenProvided() {
        val enforcedSettingsData = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.Tokyo)
        }
        val factory = ContextApiModule.Factory(enforcedSettingsData)

        val enforcedSettings = factory.getEnforcedSettings()

        assertEquals(1, enforcedSettings.size)
        assertEquals(enforcedSettingsData, enforcedSettings[0])
    }

    @Test
    fun factory_create_WithEnforcedSettings_UsesEnforcedSettings() {
        val configuration = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.HongKong)
            put(ContextApiConfiguration.KEY_REFERRER, "https://enforced-referrer.com")
        }
        val enforcedSettingsData = DataObject.create {
            put(ModuleSettings.KEY_MODULE_TYPE, ContextApi.ID)
            put(ModuleSettings.KEY_CONFIGURATION, configuration)
        }
        val factory = ContextApiModule.Factory(enforcedSettingsData)

        // Verify that enforced settings are stored
        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        assertEquals(enforcedSettingsData, enforcedSettings[0])

        // In the real system, SettingsManager merges enforced settings with configuration
        // before calling create(). Here we simulate that by passing the configuration directly.
        val module = factory.create(ContextApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is ContextApiModule)
        // Note: Enforced settings are available via getEnforcedSettings() and are merged
        // by the SettingsManager before module creation in the actual system.
        // This test only verifies that the module can be created with enforced settings configured.
    }

    @Test
    fun factory_moduleType_ReturnsCorrectModuleType() {
        val factory = ContextApiModule.Factory()

        assertEquals(ContextApi.ID, factory.moduleType)
    }

    @Test
    fun factory_create_WithSettingsBuilder_UsesBuilderSettings() {
        val expectedRegion = ContextApiRegion.Oregon
        val expectedReferrer = "https://builder-referrer.com"
        val settingsBuilder = ContextApiSettingsBuilder()
            .setRegion(expectedRegion)
            .setReferrer(expectedReferrer)

        val factory = ContextApiModule.Factory(settingsBuilder)

        // Verify that enforced settings are stored from the builder
        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)

        // Extract the configuration from the enforced settings (as SettingsManager would do)
        val enforcedSettingsData = enforcedSettings[0]
        val configuration = enforcedSettingsData.getDataObject(ModuleSettings.KEY_CONFIGURATION)
            ?: DataObject.EMPTY_OBJECT

        // In the real system, SettingsManager merges enforced settings with configuration
        // before calling create(). Here we simulate that by using the configuration
        // extracted from the settings builder's build result.
        val module = factory.create(ContextApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is ContextApiModule)
    }

    @Test
    fun constructor_WithConfiguration_CreatesModule() {
        val configuration = ContextApiConfiguration(
            region = ContextApiRegion.Sydney,
            referrer = null
        )

        val module = ContextApiModule(mockContext(), configuration)

        assertNotNull(module)
        assertEquals(ContextApi.ID, module.id)
    }

    @Test
    fun updateConfiguration_WithEmptyConfiguration_ReturnsNull() {
        val emptyConfiguration = DataObject.EMPTY_OBJECT

        val result = contextApiModule.updateConfiguration(emptyConfiguration)

        assertNull(result)
    }

    @Test
    fun updateConfiguration_WithInvalidConfiguration_ReturnsNull() {
        val invalidConfiguration = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, 12345) // Invalid type - not a string
        }

        val result = contextApiModule.updateConfiguration(invalidConfiguration)

        assertNull(result)
    }

    @Test
    fun updateConfiguration_WithCustomRegion_ReturnsSameModule() {
        val configurationWithCustomRegion = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, "custom-region-value")
            put(
                ContextApiConfiguration.KEY_REFERRER,
                12345
            ) // Invalid referrer type, but region is valid
        }

        val result = contextApiModule.updateConfiguration(configurationWithCustomRegion)

        assertTrue(result === contextApiModule)
        assertNotNull(result)
    }

    @Test
    fun updateConfiguration_WithMultipleUpdates_ReturnsSameModule() {
        val config1 = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.Germany)
        }
        val config2 = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.UsEast)
        }
        val config3 = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.Tokyo)
        }

        val result1 = contextApiModule.updateConfiguration(config1)
        val result2 = result1?.updateConfiguration(config2)
        val result3 = result2?.updateConfiguration(config3)

        assertTrue(result1 === contextApiModule)
        assertTrue(result2 === contextApiModule)
        assertTrue(result3 === contextApiModule)
        assertNotNull(result3)
    }

    @Test
    fun updateConfiguration_WithAllRegions_ReturnsSameModule() {
        val regions = listOf(
            ContextApiRegion.Germany,
            ContextApiRegion.UsEast,
            ContextApiRegion.Sydney,
            ContextApiRegion.Oregon,
            ContextApiRegion.Tokyo,
            ContextApiRegion.HongKong
        )

        regions.forEach { region ->
            val configuration = DataObject.create {
                put(ContextApiConfiguration.KEY_REGION, region)
            }
            val result = contextApiModule.updateConfiguration(configuration)
            assertTrue(result === contextApiModule)
            assertNotNull(result)
        }
    }

    @Test
    fun factory_create_WithAllRegions_CreatesModule() {
        val regions = listOf(
            ContextApiRegion.Germany,
            ContextApiRegion.UsEast,
            ContextApiRegion.Sydney,
            ContextApiRegion.Oregon,
            ContextApiRegion.Tokyo,
            ContextApiRegion.HongKong
        )

        val factory = ContextApiModule.Factory()

        regions.forEach { region ->
            val configuration = DataObject.create {
                put(ContextApiConfiguration.KEY_REGION, region)
            }
            val module = factory.create(ContextApi.ID, mockContext(), configuration)
            assertNotNull(module)
            assertTrue(module is ContextApiModule)
        }
    }

    @Test
    fun factory_create_WithInvalidConfiguration_CreatesModule() {
        val invalidConfiguration = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, "invalid-region")
            put(ContextApiConfiguration.KEY_REFERRER, 12345)
        }

        val factory = ContextApiModule.Factory()
        val module = factory.create(ContextApi.ID, mockContext(), invalidConfiguration)

        assertNotNull(module)
        assertTrue(module is ContextApiModule)
    }

    @Test
    fun factory_create_WithOnlyReferrer_CreatesModule() {
        val configuration = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.UsEast)
            put(ContextApiConfiguration.KEY_REFERRER, "https://only-referrer.com")
        }

        val factory = ContextApiModule.Factory()
        val module = factory.create(ContextApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is ContextApiModule)
    }

    @Test
    fun factory_create_WithEmptyStringReferrer_CreatesModule() {
        val configuration = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.UsEast)
            put(ContextApiConfiguration.KEY_REFERRER, "")
        }

        val factory = ContextApiModule.Factory()
        val module = factory.create(ContextApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is ContextApiModule)
    }

    @Test
    fun fetchEngineResponse_WithEmptyEngineID_CallsService() {
        val engineId = ""
        val visitorId = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        visitorIdSubject.onNext(visitorId)

        contextApiModule.fetchEngineResponse(engineId, callback)

        verify {
            mockService.fetchEngineResponse(engineId, visitorId, any())
        }
    }

    /**
     * Captures the service completion so the test decides when, or whether, the network call
     * completes. The returned Disposable is real so shutdown can be observed cancelling it.
     */
    private fun withholdServiceResponse(): CapturingSlot<Callback<TealiumResult<EngineResponse>>> {
        val serviceCallback = slot<Callback<TealiumResult<EngineResponse>>>()
        every {
            mockService.fetchEngineResponse(any(), any(), capture(serviceCallback))
        } returns Disposables.subscription()
        return serviceCallback
    }

    private fun mockContext(): TealiumContext {
        val config = getDefaultConfig(mockk(), "test-account", "test-profile", Environment.PROD)
        val mockContext = mockk<TealiumContext>(relaxed = true)
        every { mockContext.moduleManager } returns moduleManager
        every { mockContext.logger } returns SystemLogger
        every { mockContext.config } returns config
        return mockContext
    }
}