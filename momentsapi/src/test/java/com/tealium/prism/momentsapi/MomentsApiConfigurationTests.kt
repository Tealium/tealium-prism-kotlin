package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.momentsapi.internal.MomentsApiConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MomentsApiConfigurationTests {

    @Test
    fun fromDataObject_Returns_Null_When_Given_Empty_DataObject() {
        val config = MomentsApiConfiguration.fromDataObject(DataObject.EMPTY_OBJECT)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Returns_Values_From_DataObject_When_All_Keys_Present() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.Germany)
            put(MomentsApiConfiguration.KEY_REFERRER, "https://custom-referrer.com")
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(MomentsApiRegion.Germany, config?.region)
        assertEquals("https://custom-referrer.com", config?.referrer)
    }

    @Test
    fun fromDataObject_Returns_Mixed_Values_When_Some_Keys_Present() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.Sydney)
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(MomentsApiRegion.Sydney, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Returns_DefaultRegion_When_InvalidRegion() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, "invalid-region")
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertTrue(config?.region is MomentsApiRegion.Custom)
        assertEquals("invalid-region", (config?.region as MomentsApiRegion.Custom).value)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionNotProvided() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REFERRER, "https://test.com")
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Handles_AllRegions() {
        val regions = listOf(
            MomentsApiRegion.Germany,
            MomentsApiRegion.UsEast,
            MomentsApiRegion.Sydney,
            MomentsApiRegion.Oregon,
            MomentsApiRegion.Tokyo,
            MomentsApiRegion.HongKong
        )

        regions.forEach { region ->
            val dataObject = DataObject.create {
                put(MomentsApiConfiguration.KEY_REGION, region)
            }
            val config = MomentsApiConfiguration.fromDataObject(dataObject)
            assertNotNull(config)
            assertEquals(region, config?.region)
        }
    }

    @Test
    fun fromDataObject_Handles_EmptyReferrerString() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.Tokyo)
            put(MomentsApiConfiguration.KEY_REFERRER, "")
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(MomentsApiRegion.Tokyo, config?.region)
        assertEquals("", config?.referrer)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionIsNull() {
        val dataObject = DataObject.create {
            putNull(MomentsApiConfiguration.KEY_REGION)
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionIsWrongType() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, 123)
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Handles_ReferrerAsNull() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.Oregon)
            putNull(MomentsApiConfiguration.KEY_REFERRER)
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(MomentsApiRegion.Oregon, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Handles_ReferrerAsWrongType() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.HongKong)
            put(MomentsApiConfiguration.KEY_REFERRER, 12345)
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(MomentsApiRegion.HongKong, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionIsDataList() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, DataList.EMPTY_LIST)
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Returns_Null_When_RegionIsDataObject() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, DataObject.EMPTY_OBJECT)
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)
        assertNull(config)
    }

    @Test
    fun fromDataObject_Returns_Custom_When_RegionStringHasWhitespace() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, " us-east-1 ")
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertTrue(config?.region is MomentsApiRegion.Custom)
        assertEquals(" us-east-1 ", (config?.region as MomentsApiRegion.Custom).value)
    }

    @Test
    fun fromDataObject_Handles_ReferrerAsDataList() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.UsEast)
            put(MomentsApiConfiguration.KEY_REFERRER, DataList.EMPTY_LIST)
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(MomentsApiRegion.UsEast, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Handles_ReferrerAsDataObject() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.UsEast)
            put(MomentsApiConfiguration.KEY_REFERRER, DataObject.EMPTY_OBJECT)
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(MomentsApiRegion.UsEast, config?.region)
        assertNull(config?.referrer)
    }

    @Test
    fun fromDataObject_Preserves_ReferrerStringWithWhitespace() {
        val dataObject = DataObject.create {
            put(MomentsApiConfiguration.KEY_REGION, MomentsApiRegion.UsEast)
            put(MomentsApiConfiguration.KEY_REFERRER, " https://test.com ")
        }

        val config = MomentsApiConfiguration.fromDataObject(dataObject)

        assertNotNull(config)
        assertEquals(MomentsApiRegion.UsEast, config?.region)
        assertEquals(" https://test.com ", config?.referrer)
    }
}
