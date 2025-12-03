package com.tealium.prism.core.internal.utils

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataItemFormatterTests {

    @Test
    fun format_Returns_Null_When_DataItem_Is_Null() {
        val dataItem: DataItem? = null
        
        val result = dataItem.format()
        
        assertNull(result)
    }

    @Test
    fun format_Returns_Null_When_DataItem_Is_Null_Constant() {
        val dataItem = DataItem.NULL
        
        val result = dataItem.format()
        
        assertNull(result)
    }

    @Test
    fun format_Returns_String_Value_When_DataItem_Contains_String() {
        val dataItem = DataItem.string("test string")
        
        val result = dataItem.format()
        
        assertEquals("test string", result)
    }

    @Test
    fun format_Returns_Integer_As_String_When_DataItem_Contains_Integer() {
        val dataItem = DataItem.int(42)
        
        val result = dataItem.format()
        
        assertEquals("42", result)
    }

    @Test
    fun format_Returns_Long_As_String_When_DataItem_Contains_Long() {
        val dataItem = DataItem.long(123456789L)
        
        val result = dataItem.format()
        
        assertEquals("123456789", result)
    }

    @Test
    fun format_Returns_Boolean_As_String_When_DataItem_Contains_Boolean() {
        val dataItem = DataItem.boolean(true)
        
        val result = dataItem.format()
        
        assertEquals("true", result)
    }

    @Test
    fun format_Returns_Human_Readable_Double_When_DataItem_Contains_Double() {
        val dataItem = DataItem.double(123.456)
        
        val result = dataItem.format()
        
        assertEquals("123.456", result)
    }

    @Test
    fun format_Returns_Human_Readable_Double_When_DataItem_Contains_Double_With_Trailing_Zeros() {
        val dataItem = DataItem.double(123.000)
        
        val result = dataItem.format()
        
        assertEquals("123", result)
    }

    @Test
    fun format_Returns_Human_Readable_Double_When_DataItem_Contains_Very_Large_Double() {
        val dataItem = DataItem.double(1.23456789E10)
        
        val result = dataItem.format()
        
        assertEquals("12345678900", result)
    }

    @Test
    fun format_Returns_Human_Readable_Double_When_DataItem_Contains_Very_Small_Double() {
        val dataItem = DataItem.double(1.23456789E-5)
        
        val result = dataItem.format()
        
        assertEquals("0.0000123456789", result)
    }

    @Test
    fun format_Returns_Json_String_When_DataItem_Contains_DataObject() {
        val dataItem = DataObject.create {
            put("string", "value")
            put("int", 42)
            put("double", 1.5E25)
            put("null", DataItem.NULL)
        }.asDataItem()

        val result = dataItem.format()
        
        assertEquals("""{"string":"value","int":42,"double":1.5E25,"null":null}""", result)
    }

    @Test
    fun format_Returns_Json_String_When_DataItem_Contains_DataList() {
        val dataItem = DataList.create {
            add("item1")
            add(42)
            add(true)
        }.asDataItem()

        val result = dataItem.format()
        
        assertEquals("[\"item1\",42,true]", result)
    }

    @Test
    fun format_Returns_Json_String_When_DataItem_Contains_Empty_DataObject() {
        val dataItem = DataObject.EMPTY_OBJECT.asDataItem()
        
        val result = dataItem.format()
        
        assertEquals("{}", result)
    }

    @Test
    fun format_Returns_Json_String_When_DataItem_Contains_Empty_DataList() {
        val dataItem = DataList.EMPTY_LIST.asDataItem()
        
        val result = dataItem.format()
        
        assertEquals("[]", result)
    }

    @Test
    fun humanReadable_Returns_Plain_String_When_Double_Is_Integer() {
        val double = 42.0
        
        val result = double.humanReadable()
        
        assertEquals("42", result)
    }

    @Test
    fun humanReadable_Returns_Plain_String_When_Double_Has_Decimals() {
        val double = 42.123
        
        val result = double.humanReadable()
        
        assertEquals("42.123", result)
    }

    @Test
    fun humanReadable_Returns_Plain_String_When_Double_Has_Trailing_Zeros() {
        val double = 42.1000
        
        val result = double.humanReadable()
        
        assertEquals("42.1", result)
    }

    @Test
    fun humanReadable_Returns_Plain_String_When_Double_Is_Zero() {
        val double = 0.0
        
        val result = double.humanReadable()
        
        assertEquals("0", result)
    }

    @Test
    fun humanReadable_Returns_Plain_String_When_Double_Is_Negative() {
        val double = -42.123
        
        val result = double.humanReadable()
        
        assertEquals("-42.123", result)
    }

    @Test
    fun humanReadable_Returns_Plain_String_When_Double_Is_Very_Large() {
        val double = 1.23456789E10
        
        val result = double.humanReadable()
        
        assertEquals("12345678900", result)
    }

    @Test
    fun humanReadable_Returns_Plain_String_When_Double_Is_Very_Small() {
        val double = 1.23456789E-5
        
        val result = double.humanReadable()
        
        assertEquals("0.0000123456789", result)
    }
}
