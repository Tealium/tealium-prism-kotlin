package com.tealium.prism.core.api.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LenientConvertersTests {

    @Test
    fun get_Returns_Correct_Values_With_Lenient_Converters() {
        val dataObject = DataObject.create {
            put("doubleAsString", "123.45")
            put("intAsString", "42")
            put("boolAsString", "true")
            put("intAsDouble", 42.9)
            put("boolAsInt", 1)
            put("intAsStringForString", "123")
        }

        val doubleValue = dataObject.get("doubleAsString", LenientConverters.DOUBLE)
        assertEquals(123.45, doubleValue)

        val intValue = dataObject.get("intAsString", LenientConverters.INT)
        assertEquals(42, intValue)

        val boolValue = dataObject.get("boolAsString", LenientConverters.BOOLEAN)
        assertTrue(boolValue!!)

        val intFromDouble = dataObject.get("intAsDouble", LenientConverters.INT)
        assertEquals(42, intFromDouble)

        val boolFromInt = dataObject.get("boolAsInt", LenientConverters.BOOLEAN)
        assertTrue(boolFromInt!!)

        val stringValue = dataObject.get("intAsStringForString", LenientConverters.STRING)
        assertEquals("123", stringValue)
    }

    @Test
    fun get_Returns_Null_For_Missing_Keys() {
        val dataObject = DataObject.EMPTY_OBJECT

        assertNull(dataObject.get("missing", LenientConverters.DOUBLE))
        assertNull(dataObject.get("missing", LenientConverters.INT))
        assertNull(dataObject.get("missing", LenientConverters.LONG))
        assertNull(dataObject.get("missing", LenientConverters.BOOLEAN))
        assertNull(dataObject.get("missing", LenientConverters.STRING))
    }

    private val edgeCases = DataObject.create {
        put("largeDouble", Long.MAX_VALUE.toDouble() + 1000)
        put("infinity", Double.POSITIVE_INFINITY)
        put("nan", Double.NaN)
        put("negativeInfinity", Double.NEGATIVE_INFINITY)
        put("infinityString", "Infinity")
        put("nanString", "NaN")
        put("negativeInfinityString", "-Infinity")
    }

    @Test
    fun get_Handles_Int_Edge_Cases_From_DataObject() {
        // These should all return clamped values, and null for NaNs
        assertEquals(Int.MAX_VALUE, edgeCases.get("largeDouble", LenientConverters.INT))
        assertEquals(Int.MAX_VALUE, edgeCases.get("infinity", LenientConverters.INT))
        assertEquals(Int.MAX_VALUE, edgeCases.get("infinityString", LenientConverters.INT))
        assertEquals(Int.MIN_VALUE, edgeCases.get("negativeInfinity", LenientConverters.INT))
        assertEquals(Int.MIN_VALUE, edgeCases.get("negativeInfinityString", LenientConverters.INT))
        assertNull(edgeCases.get("nan", LenientConverters.INT))
        assertNull(edgeCases.get("nanString", LenientConverters.INT))
    }

    @Test
    fun get_Handles_Long_Edge_Cases_From_DataObject() {
        // These should all return clamped values, and null for NaNs
        assertEquals(Long.MAX_VALUE, edgeCases.get("largeDouble", LenientConverters.LONG))
        assertEquals(Long.MAX_VALUE, edgeCases.get("infinity", LenientConverters.LONG))
        assertEquals(Long.MAX_VALUE, edgeCases.get("infinityString", LenientConverters.LONG))
        assertEquals(Long.MIN_VALUE, edgeCases.get("negativeInfinity", LenientConverters.LONG))
        assertEquals(Long.MIN_VALUE, edgeCases.get("negativeInfinityString", LenientConverters.LONG))
        assertNull(edgeCases.get("nan", LenientConverters.LONG))
        assertNull(edgeCases.get("nanString", LenientConverters.LONG))
    }

    @Test
    fun get_Handles_Double_Edge_Cases_From_DataObject() {
        // These should all return clamped values, and NaN for NaNs
        assertEquals(Long.MAX_VALUE.toDouble() + 1000, edgeCases.get("largeDouble", LenientConverters.DOUBLE))
        assertEquals(Double.POSITIVE_INFINITY, edgeCases.get("infinity", LenientConverters.DOUBLE))
        assertEquals(Double.POSITIVE_INFINITY, edgeCases.get("infinityString", LenientConverters.DOUBLE))
        assertEquals(Double.NEGATIVE_INFINITY, edgeCases.get("negativeInfinity", LenientConverters.DOUBLE))
        assertEquals(Double.NEGATIVE_INFINITY, edgeCases.get("negativeInfinityString", LenientConverters.DOUBLE))
        assertTrue(edgeCases.get("nan", LenientConverters.DOUBLE)!!.isNaN())
        assertTrue(edgeCases.get("nanString", LenientConverters.DOUBLE)!!.isNaN())
    }

    @Test
    fun get_Handles_Boolean_Edge_Cases_From_DataObject() {
        // These should all return nulls
        edgeCases.forEach { (key, _) ->
            val boolean = edgeCases.get(key, LenientConverters.BOOLEAN)
            assertNull(boolean)
        }
    }

    @Test
    fun get_Handles_String_Edge_Cases_From_DataObject() {
        // String conversion should handle these gracefully
        // `largeDouble` has precision loss here
        assertEquals("9223372036854776000", edgeCases.get("largeDouble", LenientConverters.STRING))
        assertEquals("Infinity", edgeCases.get("infinity", LenientConverters.STRING))
        assertEquals("Infinity", edgeCases.get("infinityString", LenientConverters.STRING))
        assertEquals("-Infinity", edgeCases.get("negativeInfinity", LenientConverters.STRING))
        assertEquals("-Infinity", edgeCases.get("negativeInfinityString", LenientConverters.STRING))
        assertEquals("NaN", edgeCases.get("nan", LenientConverters.STRING))
        assertEquals("NaN", edgeCases.get("nanString", LenientConverters.STRING))
    }
}