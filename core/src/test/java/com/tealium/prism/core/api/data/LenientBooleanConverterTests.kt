package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataItemUtils.asDataObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LenientBooleanConverterTests {

    private val converter = LenientConverters.BOOLEAN

    @Test
    fun convert_Converts_Boolean_Directly() {
        val trueItem = true.asDataItem()
        val falseItem = false.asDataItem()
        assertTrue(converter.convert(trueItem)!!)
        assertFalse(converter.convert(falseItem)!!)
    }

    @Test
    fun convert_Converts_String_True_Variations() {
        val trueStrings = listOf("true", "True", "TRUE", "  true  ", "yes", "YES", "Yes", "1")
        for (trueString in trueStrings) {
            val dataItem = trueString.asDataItem()
            val result = converter.convert(dataItem)!!
            assertTrue(result)
        }
    }

    @Test
    fun convert_Converts_String_False_Variations() {
        val falseStrings = listOf("false", "False", "FALSE", "  false  ", "no", "NO", "No", "0")
        for (falseString in falseStrings) {
            val dataItem = falseString.asDataItem()
            val result = converter.convert(dataItem)!!
            assertFalse(result)
        }
    }

    @Test
    fun convert_Converts_Int_Zero_To_False() {
        val dataItem = 0.asDataItem()
        val result = converter.convert(dataItem)
        assertEquals(false, result)
    }

    @Test
    fun convert_Converts_Int_One_To_True() {
        val dataItem = 1.asDataItem()
        assertTrue(converter.convert(dataItem)!!)
    }

    @Test
    fun convert_Returns_Null_For_NonZero_Integers() {
        val negativeItem = (-1).asDataItem()
        val largeItem = 100.asDataItem()
        assertNull(converter.convert(negativeItem))
        assertNull(converter.convert(largeItem))
    }

    @Test
    fun convert_Returns_Null_For_Invalid_String() {
        val dataItem = "maybe".asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_For_Double() {
        val dataItem = 1.5.asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_For_Double_Infinity() {
        val dataItem = Double.POSITIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_For_Double_Negative_Infinity() {
        val dataItem = Double.NEGATIVE_INFINITY.asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Returns_Null_For_Double_NaN() {
        val dataItem = Double.NaN.asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Handles_Large_Positive_Integer() {
        val dataItem = Int.MAX_VALUE.asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
    }

    @Test
    fun convert_Handles_Large_Negative_Integer() {
        val dataItem = Int.MIN_VALUE.asDataItem()
        val result = converter.convert(dataItem)
        assertNull(result)
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