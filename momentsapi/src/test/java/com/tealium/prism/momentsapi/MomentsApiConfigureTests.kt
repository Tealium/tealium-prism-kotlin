package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.momentsapi.internal.Converters
import com.tealium.prism.momentsapi.internal.MomentsApiConfiguration
import com.tealium.prism.momentsapi.internal.MomentsApiModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MomentsApiConfigureTests {

    @Test
    fun configure_ReturnsModuleFactory() {
        val factory = MomentsApi.configure { it.setRegion(MomentsApiRegion.UsEast) }

        assertNotNull(factory)
        assertTrue(factory is MomentsApiModule.Factory)
    }

    @Test
    fun configure_WithoutSettings_ReturnsFactory_WithEnforcedSettings() {
        val factory = MomentsApi.configure { it.setRegion(MomentsApiRegion.UsEast) }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
    }

    @Test
    fun configure_WithEnforcedSettings_ReturnsFactory_WithCorrectSettings() {
        val expectedRegion = MomentsApiRegion.Tokyo
        val expectedReferrer = "https://test-referrer.com"
        
        val factory = MomentsApi.configure { builder ->
            builder.setRegion(expectedRegion).setReferrer(expectedReferrer)
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals(expectedRegion, region)
        assertEquals(expectedReferrer, referrer)
    }

    @Test
    fun modules_momentsApi_Extension_ReturnsModuleFactory() {
        val factory = Modules.momentsApi { it.setRegion(MomentsApiRegion.UsEast) }

        assertNotNull(factory)
        assertTrue(factory is MomentsApiModule.Factory)
    }

    @Test
    fun modules_momentsApi_WithoutSettings_ReturnsFactory_WithEnforcedSettings() {
        val factory = Modules.momentsApi { it.setRegion(MomentsApiRegion.UsEast) }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
    }

    @Test
    fun modules_momentsApi_WithSettings_Extension_ReturnsModuleFactory() {
        val expectedRegion = MomentsApiRegion.Sydney
        
        val factory = Modules.momentsApi { it.setRegion(expectedRegion) }

        assertNotNull(factory)
        assertTrue(factory is MomentsApiModule.Factory)
        
        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        
        assertEquals(expectedRegion, region)
    }

    @Test
    fun modules_Types_MOMENTS_API_ReturnsCorrectID() {
        assertEquals(MomentsApi.ID, Modules.Types.MOMENTS_API)
    }

    @Test
    fun configure_ReturnsFactory_WithCorrectModuleType() {
        val factory = MomentsApi.configure { it.setRegion(MomentsApiRegion.UsEast) }

        assertEquals(MomentsApi.ID, factory.moduleType)
    }

    @Test
    fun configure_WithOnlyReferrer_ReturnsFactory_WithCorrectReferrer() {
        val expectedRegion = MomentsApiRegion.UsEast
        val expectedReferrer = "https://only-referrer.com"
        
        val factory = MomentsApi.configure { builder ->
            builder.setRegion(expectedRegion).setReferrer(expectedReferrer)
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals(expectedRegion, region)
        assertEquals(expectedReferrer, referrer)
    }

    @Test
    fun configure_WithEmptyStringReferrer_ReturnsFactory_WithEmptyStringReferrer() {
        val factory = MomentsApi.configure { builder ->
            builder.setRegion(MomentsApiRegion.UsEast).setReferrer("")
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals("", referrer)
    }

    @Test
    fun configure_WithAllRegions_ReturnsFactory_WithCorrectRegion() {
        val regions = listOf(
            MomentsApiRegion.Germany,
            MomentsApiRegion.UsEast,
            MomentsApiRegion.Sydney,
            MomentsApiRegion.Oregon,
            MomentsApiRegion.Tokyo,
            MomentsApiRegion.HongKong
        )

        regions.forEach { expectedRegion ->
            val factory = MomentsApi.configure { it.setRegion(expectedRegion) }

            val enforcedSettings = factory.getEnforcedSettings()
            assertEquals(1, enforcedSettings.size)
            
            val moduleSettings = enforcedSettings[0]
            val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
            
            val region = config.get(
                MomentsApiConfiguration.KEY_REGION,
                Converters.MomentsApiRegionConverter
            )

            assertEquals(expectedRegion, region)
        }
    }

    @Test
    fun configure_WithLambdaReturningUnmodifiedBuilder_ReturnsFactory_WithEnforcedSettings() {
        val expectedRegion = MomentsApiRegion.UsEast
        val factory = MomentsApi.configure { builder ->
            builder.setRegion(expectedRegion)
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals(expectedRegion, region)
        assertNull(referrer)
    }

    @Test
    fun modules_momentsApi_WithOnlyReferrer_ReturnsFactory_WithCorrectReferrer() {
        val expectedRegion = MomentsApiRegion.UsEast
        val expectedReferrer = "https://extension-referrer.com"
        
        val factory = Modules.momentsApi { builder ->
            builder.setRegion(expectedRegion).setReferrer(expectedReferrer)
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals(expectedRegion, region)
        assertEquals(expectedReferrer, referrer)
    }

    @Test
    fun modules_momentsApi_WithEmptyStringReferrer_ReturnsFactory_WithEmptyStringReferrer() {
        val factory = Modules.momentsApi { builder ->
            builder.setRegion(MomentsApiRegion.HongKong).setReferrer("")
        }

        val enforcedSettings = factory.getEnforcedSettings()
        assertEquals(1, enforcedSettings.size)
        
        val moduleSettings = enforcedSettings[0]
        val config = moduleSettings.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals("", referrer)
    }
}
