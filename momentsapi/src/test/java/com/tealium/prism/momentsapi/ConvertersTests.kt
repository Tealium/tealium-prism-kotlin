package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.momentsapi.internal.Converters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ConvertersTests {

    @Test
    fun momentsApiRegionConverter_Converts_Germany() {
        val dataItem = DataItem.string("eu-central-1")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertEquals(MomentsApiRegion.Germany, result)
    }

    @Test
    fun momentsApiRegionConverter_Converts_UsEast() {
        val dataItem = DataItem.string("us-east-1")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertEquals(MomentsApiRegion.UsEast, result)
    }

    @Test
    fun momentsApiRegionConverter_Converts_Sydney() {
        val dataItem = DataItem.string("ap-southeast-2")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertEquals(MomentsApiRegion.Sydney, result)
    }

    @Test
    fun momentsApiRegionConverter_Converts_Oregon() {
        val dataItem = DataItem.string("us-west-2")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertEquals(MomentsApiRegion.Oregon, result)
    }

    @Test
    fun momentsApiRegionConverter_Converts_Tokyo() {
        val dataItem = DataItem.string("ap-northeast-1")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertEquals(MomentsApiRegion.Tokyo, result)
    }

    @Test
    fun momentsApiRegionConverter_Converts_HongKong() {
        val dataItem = DataItem.string("ap-east-1")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertEquals(MomentsApiRegion.HongKong, result)
    }

    @Test
    fun momentsApiRegionConverter_IsCaseInsensitive_UpperCase() {
        val dataItem = DataItem.string("US-EAST-1")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertEquals(MomentsApiRegion.UsEast, result)
    }

    @Test
    fun momentsApiRegionConverter_IsCaseInsensitive_MixedCase() {
        val dataItem = DataItem.string("Eu-CeNtRaL-1")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertEquals(MomentsApiRegion.Germany, result)
    }

    @Test
    fun momentsApiRegionConverter_ReturnsCustom_ForUnknownRegion() {
        val dataItem = DataItem.string("unknown-region")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNotNull(result)
        assertTrue(result is MomentsApiRegion.Custom)
        assertEquals("unknown-region", (result as MomentsApiRegion.Custom).value)
    }

    @Test
    fun momentsApiRegionConverter_ReturnsNull_WhenDataItemIsNull() {
        val dataItem = DataItem.convert(null)
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun momentsApiRegionConverter_ReturnsNull_WhenDataItemIsNotString_Int() {
        val dataItem = DataItem.int(123)
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun momentsApiRegionConverter_ReturnsNull_WhenDataItemIsNotString_Double() {
        val dataItem = DataItem.double(123.45)
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun momentsApiRegionConverter_ReturnsNull_WhenDataItemIsNotString_Boolean() {
        val dataItem = DataItem.boolean(true)
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun momentsApiRegionConverter_ReturnsNull_WhenDataItemIsNotString_Long() {
        val dataItem = DataItem.long(123L)
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun momentsApiRegionConverter_ReturnsCustom_ForEmptyString() {
        val dataItem = DataItem.string("")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNotNull(result)
        assertTrue(result is MomentsApiRegion.Custom)
        assertEquals("", (result as MomentsApiRegion.Custom).value)
    }

    @Test
    fun momentsApiRegionConverter_PreservesOriginalCase_ForCustomRegion() {
        val dataItem = DataItem.string("MY-CUSTOM-REGION")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNotNull(result)
        assertTrue(result is MomentsApiRegion.Custom)
        assertEquals("MY-CUSTOM-REGION", (result as MomentsApiRegion.Custom).value)
    }

    @Test
    fun momentsApiRegionConverter_PreservesOriginalCase_ForMixedCaseCustomRegion() {
        val dataItem = DataItem.string("My-CuStOm-ReGiOn")
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNotNull(result)
        assertTrue(result is MomentsApiRegion.Custom)
        assertEquals("My-CuStOm-ReGiOn", (result as MomentsApiRegion.Custom).value)
    }

    @Test
    fun momentsApiRegionConverter_ReturnsNull_WhenDataItemIsNotString_DataList() {
        val dataItem = DataList.EMPTY_LIST.asDataItem()
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun momentsApiRegionConverter_ReturnsNull_WhenDataItemIsNotString_DataObject() {
        val dataItem = DataObject.EMPTY_OBJECT.asDataItem()
        val result = Converters.MomentsApiRegionConverter.convert(dataItem)
        assertNull(result)
    }

    // EngineResponseConverter tests
    @Test
    fun engineResponseConverter_Converts_CompleteResponse() {
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_AUDIENCES, DataItem.convert(listOf("audience1", "audience2")))
            put(Converters.EngineResponseConverter.KEY_BADGES, DataItem.convert(listOf("badge1", "badge2")))
            put(Converters.EngineResponseConverter.KEY_FLAGS, DataItem.convert(mapOf("flag1" to true, "flag2" to false)))
            put(Converters.EngineResponseConverter.KEY_DATES, DataItem.convert(mapOf("date1" to 1234567890L, "date2" to 9876543210L)))
            put(Converters.EngineResponseConverter.KEY_METRICS, DataItem.convert(mapOf("metric1" to 1.5, "metric2" to 2.0)))
            put(Converters.EngineResponseConverter.KEY_PROPERTIES, DataItem.convert(mapOf("prop1" to "value1", "prop2" to "value2")))
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertEquals(listOf("audience1", "audience2"), result?.audiences)
        assertEquals(listOf("badge1", "badge2"), result?.badges)
        assertEquals(mapOf("flag1" to true, "flag2" to false), result?.flags)
        assertEquals(mapOf("date1" to 1234567890L, "date2" to 9876543210L), result?.dates)
        assertEquals(mapOf("metric1" to 1.5, "metric2" to 2.0), result?.metrics)
        assertEquals(mapOf("prop1" to "value1", "prop2" to "value2"), result?.properties)
    }

    @Test
    fun engineResponseConverter_Converts_EmptyResponse() {
        val dataObject = DataObject.EMPTY_OBJECT
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertNull(result?.audiences)
        assertNull(result?.badges)
        assertNull(result?.flags)
        assertNull(result?.dates)
        assertNull(result?.metrics)
        assertNull(result?.properties)
    }

    @Test
    fun engineResponseConverter_Converts_PartialResponse() {
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_FLAGS, DataItem.convert(mapOf("flag1" to true)))
            put(Converters.EngineResponseConverter.KEY_PROPERTIES, DataItem.convert(mapOf("prop1" to "value1")))
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertNull(result?.audiences)
        assertNull(result?.badges)
        assertEquals(mapOf("flag1" to true), result?.flags)
        assertNull(result?.dates)
        assertNull(result?.metrics)
        assertEquals(mapOf("prop1" to "value1"), result?.properties)
    }

    @Test
    fun engineResponseConverter_Converts_Audiences() {
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_AUDIENCES, DataItem.convert(listOf("audience1", "audience2", "audience3")))
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertEquals(listOf("audience1", "audience2", "audience3"), result?.audiences)
    }

    @Test
    fun engineResponseConverter_Converts_Badges() {
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_BADGES, DataItem.convert(listOf("badge1", "badge2")))
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertEquals(listOf("badge1", "badge2"), result?.badges)
    }

    @Test
    fun engineResponseConverter_Converts_Dates() {
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_DATES, DataItem.convert(mapOf("timestamp1" to 1234567890L, "timestamp2" to 9876543210L)))
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertEquals(mapOf("timestamp1" to 1234567890L, "timestamp2" to 9876543210L), result?.dates)
    }

    @Test
    fun engineResponseConverter_ReturnsNull_WhenDataItemIsNotDataObject() {
        val dataItem = DataItem.string("not an object")
        val result = Converters.EngineResponseConverter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun engineResponseConverter_ReturnsNull_WhenDataItemIsNull() {
        val dataItem = DataItem.convert(null)
        val result = Converters.EngineResponseConverter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun engineResponseConverter_FiltersInvalidValues_InAudiences() {
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_AUDIENCES, DataItem.convert(listOf("valid1", 123, "valid2", null, "valid3")))
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertEquals(listOf("valid1", "valid2", "valid3"), result?.audiences)
    }

    @Test
    fun engineResponseConverter_FiltersInvalidValues_InBadges() {
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_BADGES, DataItem.convert(listOf("badge1", 456, "badge2", null)))
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertEquals(listOf("badge1", "badge2"), result?.badges)
    }

    @Test
    fun engineResponseConverter_FiltersInvalidValues_InFlags() {
        val flagsObj = DataObject.create {
            put("flag1", true)
            put("flag2", "not a boolean")
            put("flag3", false)
            put("flag4", 123)
        }
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_FLAGS, flagsObj)
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertEquals(mapOf("flag1" to true, "flag3" to false), result?.flags)
    }

    @Test
    fun engineResponseConverter_FiltersInvalidValues_InDates() {
        val datesObj = DataObject.create {
            put("date1", 1234567890L)
            put("date2", "not a long")
            put("date3", 9876543210L)
            put("date4", 123.45)
            put("date5", 100)
        }
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_DATES, datesObj)
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        // date4 (123.45) is converted to 123L, date5 (100) is converted to 100L
        assertEquals(mapOf("date1" to 1234567890L, "date3" to 9876543210L, "date4" to 123L, "date5" to 100L), result?.dates)
    }

    @Test
    fun engineResponseConverter_FiltersInvalidValues_InMetrics() {
        val metricsObj = DataObject.create {
            put("metric1", 1.5)
            put("metric2", "not a number")
            put("metric3", 2.0)
            put("metric4", true)
        }
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_METRICS, metricsObj)
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertEquals(mapOf("metric1" to 1.5, "metric3" to 2.0), result?.metrics)
    }

    @Test
    fun engineResponseConverter_FiltersInvalidValues_InProperties() {
        val propertiesObj = DataObject.create {
            put("prop1", "value1")
            put("prop2", 123)
            put("prop3", "value3")
            put("prop4", true)
        }
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_PROPERTIES, propertiesObj)
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        assertEquals(mapOf("prop1" to "value1", "prop3" to "value3"), result?.properties)
    }

    @Test
    fun engineResponseConverter_HandlesEmptyLists() {
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_AUDIENCES, DataItem.convert(emptyList<String>()))
            put(Converters.EngineResponseConverter.KEY_BADGES, DataItem.convert(emptyList<String>()))
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        // Empty lists will return empty lists (mapNotNull on empty list returns empty list)
        assertEquals(emptyList<String>(), result?.audiences)
        assertEquals(emptyList<String>(), result?.badges)
    }

    @Test
    fun engineResponseConverter_HandlesEmptyObjects() {
        val dataObject = DataObject.create {
            put(Converters.EngineResponseConverter.KEY_FLAGS, DataObject.EMPTY_OBJECT)
            put(Converters.EngineResponseConverter.KEY_DATES, DataObject.EMPTY_OBJECT)
            put(Converters.EngineResponseConverter.KEY_METRICS, DataObject.EMPTY_OBJECT)
            put(Converters.EngineResponseConverter.KEY_PROPERTIES, DataObject.EMPTY_OBJECT)
        }
        val dataItem = dataObject.asDataItem()
        val result = Converters.EngineResponseConverter.convert(dataItem)

        assertNotNull(result)
        // Empty objects will return empty maps (mapNotNull on empty map returns empty map)
        assertEquals(emptyMap<String, Boolean>(), result?.flags)
        assertEquals(emptyMap<String, Long>(), result?.dates)
        assertEquals(emptyMap<String, Double>(), result?.metrics)
        assertEquals(emptyMap<String, String>(), result?.properties)
    }
}
