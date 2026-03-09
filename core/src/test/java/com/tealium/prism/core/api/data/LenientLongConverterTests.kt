package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataItemUtils.asDataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LenientLongConverterTests {

    private val converter = LenientConverters.LONG

    @Test
    fun convert_Converts_Long_Directly() {
        val dataItem = 42L.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42L, result)
    }

    @Test
    fun convert_Converts_Int_Directly() {
        val dataItem = 42.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42L, result)
    }

    @Test
    fun convert_Converts_String_To_Long() {
        val dataItem = "42".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42L, result)
    }

    @Test
    fun convert_Converts_Double_String_To_Long() {
        val dataItem = "42.5".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42L, result)
    }

    @Test
    fun convert_Converts_Double_To_Long_Truncating() {
        val dataItem = 42.9.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42L, result)
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
    fun convert_Converts_Negative_Int() {
        val dataItem = "-123".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(-123L, result)
    }

    @Test
    fun convert_Converts_Zero() {
        val dataItem = "0".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(0L, result)
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

    @Test
    fun convert_Returns_Long_Max_For_Double_Infinity() {
        val dataItem = Double.POSITIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Long.MAX_VALUE, result)
    }

    @Test
    fun convert_Returns_Long_Min_For_Double_Negative_Infinity() {
        val dataItem = Double.NEGATIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Long.MIN_VALUE, result)
    }

    @Test
    fun convert_Returns_Long_Max_For_OutOfBounds_Number_String() {
        val dataItem = "100000000000000000000000000000".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Long.MAX_VALUE, result)
    }

    @Test
    fun convert_Returns_Long_Min_For_OutOfBounds_Number_String() {
        val dataItem = "-100000000000000000000000000000".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Long.MIN_VALUE, result)
    }

    @Test
    fun convert_Returns_Null_For_Double_NaN() {
        val dataItem = Double.NaN.asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_For_NaN_String() {
        val dataItem = "NaN".asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }
}