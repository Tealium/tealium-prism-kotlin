package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.internal.settings.ModuleSettings
import com.tealium.prism.momentsapi.internal.Converters
import com.tealium.prism.momentsapi.internal.MomentsApiConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MomentsApiSettingsBuilderTests {

    private lateinit var builder: MomentsApiSettingsBuilder

    @Before
    fun setUp() {
        builder = MomentsApiSettingsBuilder().setRegion(MomentsApiRegion.UsEast)
    }

    @Test
    fun region_IsSet_In_Configuration_WithRegionMethod() {
        val config = builder.build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )

        assertEquals(MomentsApiRegion.UsEast, region)
    }

    @Test
    fun region_CanBeSet_WithRegionMethod() {
        val config = builder.setRegion(MomentsApiRegion.Tokyo).build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )

        assertEquals(MomentsApiRegion.Tokyo, region)
    }

    @Test
    fun referrer_Sets_Referrer_In_Configuration() {
        val referrerValue = "https://custom-referrer.com"
        val config = builder.setReferrer(referrerValue).build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        assertEquals(
            referrerValue,
            config.getString(MomentsApiConfiguration.KEY_REFERRER)
        )
    }

    @Test
    fun build_Returns_DataObject_With_Both_Region_And_Referrer() {
        val referrerValue = "https://test-referrer.com"
        val builder = MomentsApiSettingsBuilder()
        val config = builder
            .setRegion(MomentsApiRegion.Sydney)
            .setReferrer(referrerValue)
            .build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals(MomentsApiRegion.Sydney, region)
        assertEquals(referrerValue, referrer)
    }

    @Test
    fun build_Returns_DataObject_With_All_Regions() {
        val regions = listOf(
            MomentsApiRegion.Germany,
            MomentsApiRegion.UsEast,
            MomentsApiRegion.Sydney,
            MomentsApiRegion.Oregon,
            MomentsApiRegion.Tokyo,
            MomentsApiRegion.HongKong
        )

        regions.forEach { expectedRegion ->
            val builder = MomentsApiSettingsBuilder()
            val config = builder.setRegion(expectedRegion).build()
                .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

            val region = config.get(
                MomentsApiConfiguration.KEY_REGION,
                Converters.MomentsApiRegionConverter
            )

            assertEquals(expectedRegion, region)
        }
    }

    @Test
    fun build_Returns_Configuration_With_Region_When_Set_InBuilder() {
        val config = builder.build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        // Region is set in setUp via region() method, so it should be present
        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        // Region should be set from builder
        assertEquals(MomentsApiRegion.UsEast, region)
        // Referrer should be null (not set in builder)
        assertNull(referrer)
    }

    @Test
    fun build_Returns_DataObject_With_CorrectModuleType() {
        val built = builder.build()

        assertEquals(Modules.Types.MOMENTS_API, built.getString(ModuleSettings.KEY_MODULE_TYPE))
    }

    @Test
    fun region_IsSet_WithRegionMethod() {
        val builder = MomentsApiSettingsBuilder()
        val config = builder.setRegion(MomentsApiRegion.Tokyo).build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )

        assertEquals(MomentsApiRegion.Tokyo, region)
    }

    @Test
    fun referrer_Overwrites_PreviousValue() {
        val builder = MomentsApiSettingsBuilder()
        val config = builder
            .setReferrer("https://first-referrer.com")
            .setReferrer("https://second-referrer.com")
            .build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals("https://second-referrer.com", referrer)
    }

    @Test
    fun setOrder_Works_WithMomentsApiSettingsBuilder() {
        val built = builder
            .setOrder(5)
            .build()

        assertEquals(5, built.getInt(ModuleSettings.KEY_ORDER))
    }

    @Test
    fun setEnabled_Works_WithMomentsApiSettingsBuilder() {
        val builder = MomentsApiSettingsBuilder()
        val built = builder
            .setEnabled(false)
            .build()

        assertEquals(false, built.getBoolean(ModuleSettings.KEY_ENABLED))
    }

    @Test
    fun methodChaining_Works_WithAllMethods() {
        val builder = MomentsApiSettingsBuilder()
        val built = builder
            .setRegion(MomentsApiRegion.Oregon)
            .setReferrer("https://chained-referrer.com")
            .setOrder(10)
            .setEnabled(true)
            .build()

        val config = built.getDataObject(ModuleSettings.KEY_CONFIGURATION)!!
        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals(MomentsApiRegion.Oregon, region)
        assertEquals("https://chained-referrer.com", referrer)
        assertEquals(10, built.getInt(ModuleSettings.KEY_ORDER))
        assertEquals(true, built.getBoolean(ModuleSettings.KEY_ENABLED))
    }


    @Test
    fun referrer_ReturnsSelf_ForMethodChaining() {
        val result = builder.setReferrer("https://test.com")

        assertNotNull(result)
        assertEquals(builder, result) // Should return same instance
    }

    @Test
    fun setOrder_Works_WithZero() {
        val built = builder
            .setOrder(0)
            .build()

        assertEquals(0, built.getInt(ModuleSettings.KEY_ORDER))
    }

    @Test
    fun setOrder_Works_WithNegativeValue() {
        val built = builder
            .setOrder(-1)
            .build()

        assertEquals(-1, built.getInt(ModuleSettings.KEY_ORDER))
    }

    @Test
    fun setOrder_Works_WithLargeValue() {
        val built = builder
            .setOrder(Int.MAX_VALUE)
            .build()

        assertEquals(Int.MAX_VALUE, built.getInt(ModuleSettings.KEY_ORDER))
    }

    @Test
    fun setOrder_Overwrites_PreviousValue() {
        val built = builder
            .setOrder(5)
            .setOrder(10)
            .build()

        assertEquals(10, built.getInt(ModuleSettings.KEY_ORDER))
    }

    @Test
    fun setEnabled_Works_WithTrue() {
        val builder = MomentsApiSettingsBuilder()
        val built = builder
            .setEnabled(true)
            .build()

        assertEquals(true, built.getBoolean(ModuleSettings.KEY_ENABLED))
    }

    @Test
    fun setEnabled_Overwrites_PreviousValue() {
        val builder = MomentsApiSettingsBuilder()
        val built = builder
            .setEnabled(false)
            .setEnabled(true)
            .build()

        assertEquals(true, built.getBoolean(ModuleSettings.KEY_ENABLED))
    }

    @Test
    fun build_Returns_DataObject_With_EmptyStringReferrer() {
        val builder = MomentsApiSettingsBuilder()
        val config = builder
            .setRegion(MomentsApiRegion.Tokyo)
            .setReferrer("")
            .build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals(MomentsApiRegion.Tokyo, region)
        assertEquals("", referrer) // Empty string is preserved
    }

    @Test
    fun build_Returns_DataObject_With_Region_Set() {
        val builder = MomentsApiSettingsBuilder()
        val config = builder
            .setRegion(MomentsApiRegion.UsEast)
            .build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals(MomentsApiRegion.UsEast, region)
        assertNull(referrer) // Referrer not set
    }

    @Test
    fun build_Returns_DataObject_With_Region_And_Referrer() {
        val referrerValue = "https://only-referrer.com"
        val builder = MomentsApiSettingsBuilder()
        val config = builder
            .setRegion(MomentsApiRegion.UsEast)
            .setReferrer(referrerValue)
            .build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )
        val referrer = config.getString(MomentsApiConfiguration.KEY_REFERRER)

        assertEquals(MomentsApiRegion.UsEast, region)
        assertEquals(referrerValue, referrer)
    }

    @Test
    fun region_Overwrites_PreviousValue() {
        val builder = MomentsApiSettingsBuilder()
        val config = builder
            .setRegion(MomentsApiRegion.Germany)
            .setRegion(MomentsApiRegion.Tokyo)
            .build()
            .getDataObject(ModuleSettings.KEY_CONFIGURATION)!!

        val region = config.get(
            MomentsApiConfiguration.KEY_REGION,
            Converters.MomentsApiRegionConverter
        )

        assertEquals(MomentsApiRegion.Tokyo, region)
    }
}

