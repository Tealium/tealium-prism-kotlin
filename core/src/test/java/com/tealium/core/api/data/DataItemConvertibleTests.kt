package com.tealium.core.api.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataItemConvertibleTests {

    private val dataListConvertible = TestDataListConvertible("value", 10)
    private val dataObjectConvertible = TestDataObjectConvertible("value", 10)

    @Test
    fun convert_ListConvertible_CreatesDataList() {
        val dataItem = dataListConvertible.asDataItem()

        val converted = TestDataListConvertible.Converter.convert(dataItem)

        assertEquals("[\"value\",10]", dataItem.toString())
        assertEquals(dataListConvertible, converted)
    }

    @Test
    fun convert_DataObjectConvertible_CreatesDataObject() {
        val dataItem = dataObjectConvertible.asDataItem()

        val converted = TestDataObjectConvertible.Converter.convert(dataItem)

        assertEquals("{\"string\":\"value\",\"int\":10}", dataItem.toString())
        assertEquals(dataObjectConvertible, converted)
    }

    @Test
    fun convert_ReturnsNull_WhenInvalidType() {

        assertNull(TestDataListConvertible.Converter.convert(DataObject.create { }
            .asDataItem()))

        assertNull(TestDataObjectConvertible.Converter.convert(DataList.create { }
            .asDataItem()))
    }
}

