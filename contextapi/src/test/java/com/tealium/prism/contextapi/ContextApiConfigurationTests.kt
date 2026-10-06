package com.tealium.prism.contextapi

import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.contextapi.internal.ContextApiConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContextApiConfigurationTests {

    @Test
    fun fromDataObject_Returns_Null_When_Given_Empty_DataObject() {
        val config = ContextApiConfiguration.fromDataObject(DataObject.EMPTY_OBJECT)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Returns_Values_From_DataObject_When_All_Keys_Present() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.Germany)
            put(ContextApiConfiguration.KEY_REFERRER, "https://custom-referrer.com")
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(ContextApiRegion.Germany, config?.region)
        assertEquals("https://custom-referrer.com", config?.referrer)
    }

    @Test
    fun fromDataObject_Returns_Mixed_Values_When_Some_Keys_Present() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.Sydney)
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(ContextApiRegion.Sydney, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Returns_DefaultRegion_When_InvalidRegion() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, "invalid-region")
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertTrue(config?.region is ContextApiRegion.Custom)
        assertEquals("invalid-region", (config?.region as ContextApiRegion.Custom).value)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionNotProvided() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REFERRER, "https://test.com")
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Handles_AllRegions() {
        val regions = listOf(
            ContextApiRegion.Germany,
            ContextApiRegion.UsEast,
            ContextApiRegion.Sydney,
            ContextApiRegion.Oregon,
            ContextApiRegion.Tokyo,
            ContextApiRegion.HongKong
        )

        regions.forEach { region ->
            val dataObject = DataObject.create {
                put(ContextApiConfiguration.KEY_REGION, region)
            }
            val config = ContextApiConfiguration.fromDataObject(dataObject)
            assertNotNull(config)
            assertEquals(region, config?.region)
        }
    }

    @Test
    fun fromDataObject_Handles_EmptyReferrerString() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.Tokyo)
            put(ContextApiConfiguration.KEY_REFERRER, "")
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(ContextApiRegion.Tokyo, config?.region)
        assertEquals("", config?.referrer)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionIsNull() {
        val dataObject = DataObject.create {
            putNull(ContextApiConfiguration.KEY_REGION)
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionIsWrongType() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, 123)
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Handles_ReferrerAsNull() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.Oregon)
            putNull(ContextApiConfiguration.KEY_REFERRER)
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(ContextApiRegion.Oregon, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Handles_ReferrerAsWrongType() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.HongKong)
            put(ContextApiConfiguration.KEY_REFERRER, 12345)
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(ContextApiRegion.HongKong, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionIsDataList() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, DataList.EMPTY_LIST)
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionIsDataObject() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, DataObject.EMPTY_OBJECT)
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Returns_Custom_When_RegionStringHasWhitespace() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, " us-east-1 ")
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertTrue(config?.region is ContextApiRegion.Custom)
        assertEquals(" us-east-1 ", (config?.region as ContextApiRegion.Custom).value)
    }

    @Test
    fun fromDataObject_Handles_ReferrerAsDataList() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.UsEast)
            put(ContextApiConfiguration.KEY_REFERRER, DataList.EMPTY_LIST)
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(ContextApiRegion.UsEast, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Handles_ReferrerAsDataObject() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.UsEast)
            put(ContextApiConfiguration.KEY_REFERRER, DataObject.EMPTY_OBJECT)
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(ContextApiRegion.UsEast, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Preserves_ReferrerStringWithWhitespace() {
        val dataObject = DataObject.create {
            put(ContextApiConfiguration.KEY_REGION, ContextApiRegion.UsEast)
            put(ContextApiConfiguration.KEY_REFERRER, " https://test.com ")
        }

        val config = ContextApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(ContextApiRegion.UsEast, config?.region)
        assertEquals(" https://test.com ", config?.referrer)
    }
}
