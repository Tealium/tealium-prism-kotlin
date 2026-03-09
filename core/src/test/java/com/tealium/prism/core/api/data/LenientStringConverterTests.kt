package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataItemUtils.asDataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LenientStringConverterTests {

    private val converter = LenientConverters.STRING

    @Test
    fun convert_Converts_String_Directly() {
        val dataItem = "hello".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("hello", result)
    }

    @Test
    fun convert_Converts_Int_To_String() {
        val dataItem = 42.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("42", result)
    }

    @Test
    fun convert_Converts_Double_To_String() {
        val dataItem = 42.5.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("42.5", result)
    }

    @Test
    fun convert_Converts_Boolean_True_To_String() {
        val dataItem = true.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("true", result)
    }

    @Test
    fun convert_Converts_Boolean_False_To_String() {
        val dataItem = false.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("false", result)
    }

    @Test
    fun convert_Converts_Negative_Integer() {
        val dataItem = (-123).asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("-123", result)
    }

    @Test
    fun convert_Converts_Zero() {
        val dataItem = 0.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("0", result)
    }

    @Test
    fun convert_Converts_Empty_String() {
        val dataItem = "".asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("", result)
    }

    @Test
    fun convert_Converts_Infinity_To_String() {
        val dataItem = Double.POSITIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("Infinity", result)
    }

    @Test
    fun convert_Converts_Negative_Infinity_To_String() {
        val dataItem = Double.NEGATIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("-Infinity", result)
    }

    @Test
    fun convert_Converts_NaN_To_String() {
        val dataItem = Double.NaN.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("NaN", result)
    }

    @Test
    fun convert_Formats_Whole_Numbers_Without_Decimal() {
        val dataItem = 1.0.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals("1", result)
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