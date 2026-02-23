package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataItemUtils.asDataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LenientDoubleConverterTests {

    private val converter = LenientConverters.DOUBLE

    @Test
    fun convert_Converts_Double_Directly() {
        val dataItem = 42.5.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42.5, result)
    }

    @Test
    fun convert_Converts_Int_To_Double() {
        val dataItem = 42.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42.0, result)
    }

    @Test
    fun convert_Converts_Long_To_Double() {
        val dataItem = 42L.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42.0, result)
    }

    @Test
    fun convert_Converts_String_To_Double() {
        val dataItem = "42.5".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42.5, result)
    }

    @Test
    fun convert_Converts_String_Integer_To_Double() {
        val dataItem = "42".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42.0, result)
    }

    @Test
    fun convert_Returns_Null_For_Invalid_String() {
        val dataItem = "not a number".asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_For_Boolean() {
        val dataItem = true.asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Converts_Negative_Double() {
        val dataItem = "-123.456".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(-123.456, result)
    }

    @Test
    fun convert_Converts_Scientific_Notation() {
        val dataItem = "1.23e2".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(123.0, result)
    }

    @Test
    fun convert_Rounds_Long_Max_Value() {
        val dataItem = Long.MAX_VALUE.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals( 9.223372036854776E18, result)
    }

    @Test
    fun convert_Rounds_Long_Min_Value() {
        val dataItem = Long.MIN_VALUE.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals( -9.223372036854776E18, result)
    }

    @Test
    fun convert_Handles_Infinity() {
        val dataItem = Double.POSITIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals( Double.POSITIVE_INFINITY, result)
    }

    @Test
    fun convert_Handles_Negative_Infinity() {
        val dataItem = Double.NEGATIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Double.NEGATIVE_INFINITY, result)
    }

    @Test
    fun convert_Handles_NaN() {
        val dataItem = Double.NaN.asDataItem()
        val result = converter.convert(dataItem)!!
        assertTrue(result.isNaN())
    }

    @Test
    fun convert_Returns_Null_For_DataList() {
        val dataItem = listOf(1, 2, 3).asDataList().asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_For_DataObject() {
        val dataItem = mapOf("key" to "value").asDataObject().asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_For_DataItem_Null() {
        val dataItem = DataItem.NULL
        val result = converter.convert(dataItem)
        assertNull(result)
    }
}