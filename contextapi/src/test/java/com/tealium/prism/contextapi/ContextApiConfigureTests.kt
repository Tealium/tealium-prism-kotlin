package com.tealium.prism.contextapi

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.contextapi.internal.Converters
import com.tealium.prism.contextapi.internal.ContextApiConfiguration
import com.tealium.prism.contextapi.internal.ContextApiModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContextApiConfigureTests {

    @Test
    fun configure_ReturnsModuleFactory() {
        val factory = ContextApi.configure { it.setRegion(ContextApiRegion.UsEast) }

        assertNotNull(factory)
        assertTrue(factory is ContextApiModule.Factory)
    }

    @Test
    fun configure_WithoutSettings_ReturnsFactory_WithEnforcedSettings() {
        val factory = ContextApi.configure { it.setRegion(ContextApiRegion.UsEast) }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
    }

    @Test
    fun configure_WithEnforcedSettings_ReturnsFactory_WithCorrectSettings() {
        val expectedRegion = ContextApiRegion.Tokyo
        val expectedReferrer = "https://test-referrer.com"
        
        val factory = ContextApi.configure { builder ->
            builder.setRegion(expectedRegion).setReferrer(expectedReferrer)
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            ContextApiConfiguration.KEY_REGION,
            Converters.ContextApiRegionConverter
        )
        val referrer = config.getString(ContextApiConfiguration.KEY_REFERRER)

        assertEquals(expectedRegion, region)
        assertEquals(expectedReferrer, referrer)
    }

    @Test
    fun modules_contextApi_Extension_ReturnsModuleFactory() {
        val factory = Modules.contextApi { it.setRegion(ContextApiRegion.UsEast) }

        assertNotNull(factory)
        assertTrue(factory is ContextApiModule.Factory)
    }

    @Test
    fun modules_contextApi_WithoutSettings_ReturnsFactory_WithEnforcedSettings() {
        val factory = Modules.contextApi { it.setRegion(ContextApiRegion.UsEast) }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
    }

    @Test
    fun modules_contextApi_WithSettings_Extension_ReturnsModuleFactory() {
        val expectedRegion = ContextApiRegion.Sydney
        
        val factory = Modules.contextApi { it.setRegion(expectedRegion) }

        assertNotNull(factory)
        assertTrue(factory is ContextApiModule.Factory)
        
        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            ContextApiConfiguration.KEY_REGION,
            Converters.ContextApiRegionConverter
        )
        
        assertEquals(expectedRegion, region)
    }

    @Test
    fun modules_Types_CONTEXT_API_ReturnsCorrectID() {
        assertEquals(ContextApi.ID, Modules.Types.CONTEXT_API)
    }

    @Test
    fun configure_ReturnsFactory_WithCorrectModuleType() {
        val factory = ContextApi.configure { it.setRegion(ContextApiRegion.UsEast) }

        assertEquals(ContextApi.ID, factory.moduleType)
    }

    @Test
    fun configure_WithOnlyReferrer_ReturnsFactory_WithCorrectReferrer() {
        val expectedRegion = ContextApiRegion.UsEast
        val expectedReferrer = "https://only-referrer.com"
        
        val factory = ContextApi.configure { builder ->
            builder.setRegion(expectedRegion).setReferrer(expectedReferrer)
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            ContextApiConfiguration.KEY_REGION,
            Converters.ContextApiRegionConverter
        )
        val referrer = config.getString(ContextApiConfiguration.KEY_REFERRER)

        assertEquals(expectedRegion, region)
        assertEquals(expectedReferrer, referrer)
    }

    @Test
    fun configure_WithEmptyStringReferrer_ReturnsFactory_WithEmptyStringReferrer() {
        val factory = ContextApi.configure { builder ->
            builder.setRegion(ContextApiRegion.UsEast).setReferrer("")
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val referrer = config.getString(ContextApiConfiguration.KEY_REFERRER)

        assertEquals("", referrer)
    }

    @Test
    fun configure_WithAllRegions_ReturnsFactory_WithCorrectRegion() {
        val regions = listOf(
            ContextApiRegion.Germany,
            ContextApiRegion.UsEast,
            ContextApiRegion.Sydney,
            ContextApiRegion.Oregon,
            ContextApiRegion.Tokyo,
            ContextApiRegion.HongKong
        )

        regions.forEach { expectedRegion ->
            val factory = ContextApi.configure { it.setRegion(expectedRegion) }

            val enforcedSettings = factory.getEnforcedSettings()
            assertEquals(1, enforcedSettings.size)
            
            val moduleSettings = enforcedSettings[0]
            val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
            
            val region = config.get(
                ContextApiConfiguration.KEY_REGION,
                Converters.ContextApiRegionConverter
            )

            assertEquals(expectedRegion, region)
        }
    }

    @Test
    fun configure_WithLambdaReturningUnmodifiedBuilder_ReturnsFactory_WithEnforcedSettings() {
        val expectedRegion = ContextApiRegion.UsEast
        val factory = ContextApi.configure { builder ->
            builder.setRegion(expectedRegion)
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            ContextApiConfiguration.KEY_REGION,
            Converters.ContextApiRegionConverter
        )
        val referrer = config.getString(ContextApiConfiguration.KEY_REFERRER)

        assertEquals(expectedRegion, region)
        assertNull(referrer)
    }

    @Test
    fun modules_contextApi_WithOnlyReferrer_ReturnsFactory_WithCorrectReferrer() {
        val expectedRegion = ContextApiRegion.UsEast
        val expectedReferrer = "https://extension-referrer.com"
        
        val factory = Modules.contextApi { builder ->
            builder.setRegion(expectedRegion).setReferrer(expectedReferrer)
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            ContextApiConfiguration.KEY_REGION,
            Converters.ContextApiRegionConverter
        )
        val referrer = config.getString(ContextApiConfiguration.KEY_REFERRER)

        assertEquals(expectedRegion, region)
        assertEquals(expectedReferrer, referrer)
    }

    @Test
    fun modules_contextApi_WithEmptyStringReferrer_ReturnsFactory_WithEmptyStringReferrer() {
        val factory = Modules.contextApi { builder ->
            builder.setRegion(ContextApiRegion.HongKong).setReferrer("")
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val referrer = config.getString(ContextApiConfiguration.KEY_REFERRER)

        assertEquals("", referrer)
    }
}
