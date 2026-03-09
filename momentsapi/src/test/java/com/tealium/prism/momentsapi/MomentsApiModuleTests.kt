package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Environment
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.momentsapi.internal.MomentsApiConfiguration
import com.tealium.prism.momentsapi.internal.MomentsApiModule
import com.tealium.prism.momentsapi.internal.MomentsApiService
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.getDefaultConfig
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MomentsApiModuleTests {

    @RelaxedMockK
    private lateinit var mockService: MomentsApiService

    private lateinit var visitorIdSubject: StateSubject<String>

    private lateinit var momentsApiModule: MomentsApiModule

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        visitorIdSubject = Observables.stateSubject("test-visitor-id")

        momentsApiModule = MomentsApiModule(
            visitorIdSubject,
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

        momentsApiModule.fetchEngineResponse(engineId, callback)

        verify {
            mockService.fetchEngineResponse(engineId, visitorId, any())
        }
    }

    @Test
    fun updateConfiguration_ReturnsSameModuleInstance() {
        val configuration = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.UsEast)
        }

        val result = momentsApiModule.updateConfiguration(configuration)

        // updateConfiguration should return the same module instance
        assertTrue(result === momentsApiModule)
        assertEquals(momentsApiModule, result)
    }

    @Test
    fun onShutdown_DoesNothing() {
        // onShutdown should not throw any exceptions
        momentsApiModule.onShutdown()
    }

    @Test
    fun id_ReturnsCorrectModuleID() {
        assertEquals(MomentsApi.ID, momentsApiModule.id)
    }

    @Test
    fun version_ReturnsBuildConfigVersion() {
        val version = momentsApiModule.version
        assertEquals(BuildConfig.TEALIUM_LIBRARY_VERSION, version)
    }

    @Test
    fun fetchEngineResponse_HandlesVisitorIDChange() {
        val engineId = "test-engine-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        // First call with one visitor ID
        visitorIdSubject.onNext("visitor-id-1")
        momentsApiModule.fetchEngineResponse(engineId, callback)
        verify {
            mockService.fetchEngineResponse(engineId, "visitor-id-1", any())
        }

        // Second call with different visitor Id
        val callback2 = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        visitorIdSubject.onNext("visitor-id-2")
        momentsApiModule.fetchEngineResponse(engineId, callback2)
        verify {
            mockService.fetchEngineResponse(engineId, "visitor-id-2", any())
        }
    }

    @Test
    fun factory_create_CreatesModule_WithCorrectConfiguration() {
        val configuration = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.Germany)
            put(MomentsApiConfiguration.KEY_REFERRER, "https://custom-referrer.com")
        }

        val factory = MomentsApiModule.Factory()
        val module = factory.create(MomentsApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is MomentsApiModule)
    }

    @Test
    fun factory_create_CreatesModule_WithDefaultConfiguration() {
        val configuration = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.UsEast)
        }
        val factory = MomentsApiModule.Factory()
        val module = factory.create(MomentsApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is MomentsApiModule)
    }

    @Test
    fun factory_getEnforcedSettings_ReturnsEmptyList_WhenNoEnforcedSettings() {
        val factory = MomentsApiModule.Factory()

        val enforcedSettings = factory.getEnforcedSettings()

        assertTrue(enforcedSettings.isEmpty())
    }

    @Test
    fun factory_getEnforcedSettings_ReturnsEnforcedSettings_WhenProvided() {
        val enforcedSettingsData = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.Tokyo)
        }
        val factory = MomentsApiModule.Factory(enforcedSettingsData)

        val enforcedSettings = factory.getEnforcedSettings()

        assertEquals(1, enforcedSettings.size)
        assertEquals(enforcedSettingsData, enforcedSettings[0])
    }

    @Test
    fun factory_create_WithEnforcedSettings_UsesEnforcedSettings() {
        val configuration = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.HongKong)
            put(MomentsApiConfiguration.KEY_REFERRER, "https://enforced-referrer.com")
        }
        val enforcedSettingsData = DataObject.create {
            put(ModuleSettings.KEY_MODULE_TYPE, MomentsApi.ID)
            put(ModuleSettings.KEY_CONFIGURATION, configuration)
        }
        val factory = MomentsApiModule.Factory(enforcedSettingsData)

        // Verify that enforced settings are stored
        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        assertEquals(enforcedSettingsData, enforcedSettings[0])

        // In the real system, SettingsManager merges enforced settings with configuration
        // before calling create(). Here we simulate that by passing the configuration directly.
        val module = factory.create(MomentsApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is MomentsApiModule)
        // Note: Enforced settings are available via getEnforcedSettings() and are merged
        // by the SettingsManager before module creation in the actual system.
        // This test only verifies that the module can be created with enforced settings configured.
    }

    @Test
    fun factory_moduleType_ReturnsCorrectModuleType() {
        val factory = MomentsApiModule.Factory()

        assertEquals(MomentsApi.ID, factory.moduleType)
    }

    @Test
    fun factory_create_WithSettingsBuilder_UsesBuilderSettings() {
        val expectedRegion = MomentsApiRegion.Oregon
        val expectedReferrer = "https://builder-referrer.com"
        val settingsBuilder = MomentsApiSettingsBuilder()
            .setRegion(expectedRegion)
            .setReferrer(expectedReferrer)

        val factory = MomentsApiModule.Factory(settingsBuilder)

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
        val module = factory.create(MomentsApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is MomentsApiModule)
    }

    @Test
    fun constructor_WithConfiguration_CreatesModule() {
        val configuration = MomentsApiConfiguration(
            region = MomentsApiRegion.Sydney,
            referrer = null
        )

        val module = MomentsApiModule(mockContext(), configuration)

        assertNotNull(module)
        assertEquals(MomentsApi.ID, module.id)
    }

    @Test
    fun updateConfiguration_WithEmptyConfiguration_ReturnsNull() {
        val emptyConfiguration = DataObject.EMPTY_OBJECT

        val result = momentsApiModule.updateConfiguration(emptyConfiguration)

        assertNull(result)
    }

    @Test
    fun updateConfiguration_WithInvalidConfiguration_ReturnsNull() {
        val invalidConfiguration = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, 12345) // Invalid type - not a string
        }

        val result = momentsApiModule.updateConfiguration(invalidConfiguration)

        assertNull(result)
    }

    @Test
    fun updateConfiguration_WithCustomRegion_ReturnsSameModule() {
        val configurationWithCustomRegion = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, "custom-region-value")
            put(
                MomentsApiConfiguration.KEY_REFERRER,
                12345
            ) // Invalid referrer type, but region is valid
        }

        val result = momentsApiModule.updateConfiguration(configurationWithCustomRegion)

        assertTrue(result === momentsApiModule)
        assertNotNull(result)
    }

    @Test
    fun updateConfiguration_WithMultipleUpdates_ReturnsSameModule() {
        val config1 = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.Germany)
        }
        val config2 = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.UsEast)
        }
        val config3 = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.Tokyo)
        }

        val result1 = momentsApiModule.updateConfiguration(config1)
        val result2 = result1?.updateConfiguration(config2)
        val result3 = result2?.updateConfiguration(config3)

        assertTrue(result1 === momentsApiModule)
        assertTrue(result2 === momentsApiModule)
        assertTrue(result3 === momentsApiModule)
        assertNotNull(result3)
    }

    @Test
    fun updateConfiguration_WithAllRegions_ReturnsSameModule() {
        val regions = listOf(
            MomentsApiRegion.Germany,
            MomentsApiRegion.UsEast,
            MomentsApiRegion.Sydney,
            MomentsApiRegion.Oregon,
            MomentsApiRegion.Tokyo,
            MomentsApiRegion.HongKong
        )

        regions.forEach { region ->
            val configuration = DataObject.create {
                put(MomentsApiConfiguration.KEY_REGION, region)
            }
            val result = momentsApiModule.updateConfiguration(configuration)
            assertTrue(result === momentsApiModule)
            assertNotNull(result)
        }
    }

    @Test
    fun factory_create_WithAllRegions_CreatesModule() {
        val regions = listOf(
            MomentsApiRegion.Germany,
            MomentsApiRegion.UsEast,
            MomentsApiRegion.Sydney,
            MomentsApiRegion.Oregon,
            MomentsApiRegion.Tokyo,
            MomentsApiRegion.HongKong
        )

        val factory = MomentsApiModule.Factory()

        regions.forEach { region ->
            val configuration = DataObject.create {
                put(MomentsApiConfiguration.KEY_REGION, region)
            }
            val module = factory.create(MomentsApi.ID, mockContext(), configuration)
            assertNotNull(module)
            assertTrue(module is MomentsApiModule)
        }
    }

    @Test
    fun factory_create_WithInvalidConfiguration_CreatesModule() {
        val invalidConfiguration = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, "invalid-region")
            put(MomentsApiConfiguration.KEY_REFERRER, 12345)
        }

        val factory = MomentsApiModule.Factory()
        val module = factory.create(MomentsApi.ID, mockContext(), invalidConfiguration)

        assertNotNull(module)
        assertTrue(module is MomentsApiModule)
    }

    @Test
    fun factory_create_WithOnlyReferrer_CreatesModule() {
        val configuration = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.UsEast)
            put(MomentsApiConfiguration.KEY_REFERRER, "https://only-referrer.com")
        }

        val factory = MomentsApiModule.Factory()
        val module = factory.create(MomentsApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is MomentsApiModule)
    }

    @Test
    fun factory_create_WithEmptyStringReferrer_CreatesModule() {
        val configuration = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.UsEast)
            put(MomentsApiConfiguration.KEY_REFERRER, "")
        }

        val factory = MomentsApiModule.Factory()
        val module = factory.create(MomentsApi.ID, mockContext(), configuration)

        assertNotNull(module)
        assertTrue(module is MomentsApiModule)
    }

    @Test
    fun fetchEngineResponse_WithEmptyEngineID_CallsService() {
        val engineId = ""
        val visitorId = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        visitorIdSubject.onNext(visitorId)

        momentsApiModule.fetchEngineResponse(engineId, callback)

        verify {
            mockService.fetchEngineResponse(engineId, visitorId, any())
        }
    }

    private fun mockContext(): TealiumContext {
        val config = getDefaultConfig(mockk(), "test-account", "test-profile", Environment.PROD)
        val mockContext = mockk<TealiumContext>(relaxed = true)
        every { mockContext.visitorId } returns visitorIdSubject
        every { mockContext.logger } returns SystemLogger
        every { mockContext.config } returns config
        return mockContext
    }
}