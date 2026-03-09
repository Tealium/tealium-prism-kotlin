package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataItemUtils.asDataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LenientIntConverterTests {

    private val converter = LenientConverters.INT

    @Test
    fun convert_Converts_Int_Directly() {
        val dataItem = 42.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42, result)
    }

    @Test
    fun convert_Converts_Long_Directly() {
        val dataItem = 42L.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42, result)
    }

    @Test
    fun convert_Converts_String_To_Int() {
        val dataItem = "42".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42, result)
    }

    @Test
    fun convert_Converts_Double_String_To_Int() {
        val dataItem = "42.5".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42, result)
    }

    @Test
    fun convert_Converts_Double_To_Int_Truncating() {
        val dataItem = 42.9.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(42, result)
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
        assertEquals(-123, result)
    }

    @Test
    fun convert_Converts_Zero() {
        val dataItem = "0".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(0, result)
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
    fun convert_Returns_Int_Max_For_Long_Max() {
        val dataItem = Long.MAX_VALUE.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Int.MAX_VALUE, result)
    }

    @Test
    fun convert_Returns_Int_Min_For_Long_Min() {
        val dataItem = Long.MIN_VALUE.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Int.MIN_VALUE, result)
    }

    @Test
    fun convert_Returns_Int_Max_For_Double_Infinity() {
        val dataItem = Double.POSITIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Int.MAX_VALUE, result)
    }

    @Test
    fun convert_Returns_Int_Min_For_Double_Negative_Infinity() {
        val dataItem = Double.NEGATIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Int.MIN_VALUE, result)
    }

    @Test
    fun convert_Returns_Int_Max_For_OutOfBounds_Number_String() {
        val dataItem = "100000000000000000000000000000".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Int.MAX_VALUE, result)
    }

    @Test
    fun convert_Returns_Int_Min_For_OutOfBounds_Number_String() {
        val dataItem = "-100000000000000000000000000000".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(Int.MIN_VALUE, result)
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