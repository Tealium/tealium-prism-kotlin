package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.data.DataItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MomentsApiRegionTests {

    @Test
    fun asDataItem_Returns_CorrectValue_ForAllRegions() {
        val expectedValues = mapOf(
            MomentsApiRegion.Germany to "eu-central-1",
            MomentsApiRegion.UsEast to "us-east-1",
            MomentsApiRegion.Sydney to "ap-southeast-2",
            MomentsApiRegion.Oregon to "us-west-2",
            MomentsApiRegion.Tokyo to "ap-northeast-1",
            MomentsApiRegion.HongKong to "ap-east-1"
        )

        expectedValues.forEach { (region, expectedValue) ->
            val dataItem = region.asDataItem()
            assertNotNull(dataItem)
            assertTrue(dataItem.isString())
            assertEquals(expectedValue, dataItem.getString())
        }
    }

    @Test
    fun value_Property_Returns_CorrectValue_ForAllRegions() {
        val expectedValues = mapOf(
            MomentsApiRegion.Germany to "eu-central-1",
            MomentsApiRegion.UsEast to "us-east-1",
            MomentsApiRegion.Sydney to "ap-southeast-2",
            MomentsApiRegion.Oregon to "us-west-2",
            MomentsApiRegion.Tokyo to "ap-northeast-1",
            MomentsApiRegion.HongKong to "ap-east-1"
        )

        expectedValues.forEach { (region, expectedValue) ->
            assertEquals(expectedValue, region.value)
        }
    }

    @Test
    fun asDataItem_Returns_DataItemString_WithValueProperty() {
        val regions = listOf(
            MomentsApiRegion.Germany,
            MomentsApiRegion.UsEast,
            MomentsApiRegion.Sydney,
            MomentsApiRegion.Oregon,
            MomentsApiRegion.Tokyo,
            MomentsApiRegion.HongKong
        )

        regions.forEach { region ->
            val dataItem = region.asDataItem()
            val expectedDataItem = DataItem.string(region.value)

            assertEquals(expectedDataItem, dataItem)
            assertEquals(region.value, dataItem.getString())
        }
    }

    @Test
    fun custom_Region_Works() {
        val customRegion = MomentsApiRegion.Custom("custom-region-1")

        assertEquals("custom-region-1", customRegion.value)
        val dataItem = customRegion.asDataItem()
        assertNotNull(dataItem)
        assertTrue(dataItem.isString())
        assertEquals("custom-region-1", dataItem.getString())
    }

    @Test
    fun custom_Region_WithDifferentValues_AreNotEqual() {
        val custom1 = MomentsApiRegion.Custom("region-1")
        val custom2 = MomentsApiRegion.Custom("region-2")

        assertTrue(custom1 != custom2)
        assertEquals("region-1", custom1.value)
        assertEquals("region-2", custom2.value)
    }
}
