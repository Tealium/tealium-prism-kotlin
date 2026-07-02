package com.tealium.prism.core.internal.logger

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.api.logger.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LogLevelTests {

    private val converter = LogLevel.Converter

    // convert

    @Test
    fun convert_Will_Round_Trip_Serialization() {
        LogLevel.entries.forEach { level ->
            assertSame(level, converter.convert(level.asDataItem()))
        }
    }

    @Test
    fun convert_Returns_Trace_When_DataItem_Is_Trace_String() {
        val level = converter.convert("trace".asDataItem())
        assertEquals(LogLevel.TRACE, level)
    }

    @Test
    fun convert_Returns_Debug_When_DataItem_Is_Debug_String() {
        val level = converter.convert("debug".asDataItem())
        assertEquals(LogLevel.DEBUG, level)
    }

    @Test
    fun convert_Returns_Info_When_DataItem_Is_Info_String() {
        val level = converter.convert("info".asDataItem())
        assertEquals(LogLevel.INFO, level)
    }

    @Test
    fun convert_Returns_Warn_When_DataItem_Is_Warn_String() {
        val level = converter.convert("warn".asDataItem())
        assertEquals(LogLevel.WARN, level)
    }

    @Test
    fun convert_Returns_Error_When_DataItem_Is_Error_String() {
        val level = converter.convert("error".asDataItem())
        assertEquals(LogLevel.ERROR, level)
    }

    @Test
    fun convert_Returns_Silent_When_DataItem_Is_Silent_String() {
        val level = converter.convert("silent".asDataItem())
        assertEquals(LogLevel.SILENT, level)
    }

    @Test
    fun convert_Returns_Silent_When_DataItem_Is_None_String() {
        val level = converter.convert("none".asDataItem())
        assertEquals(LogLevel.SILENT, level)
    }

    @Test
    fun convert_Returns_Null_When_DataItem_Is_Not_A_String() {
        val level = converter.convert(123.asDataItem())
        assertNull(level)
    }

    @Test
    fun convert_Returns_Null_When_DataItem_Is_Not_A_Valid_String() {
        val level = converter.convert("invalid".asDataItem())
        assertNull(level)
    }

    @Test
    fun convert_Is_Case_Insensitive() {
        val level = converter.convert("ERroR".asDataItem())
        assertEquals(LogLevel.ERROR, level)
    }

    // asDataItem

    @Test
    fun asDataItem_Returns_Trace_String_When_LogLevel_Is_Trace() {
        assertEquals("trace", LogLevel.TRACE.asDataItem().getString())
    }

    @Test
    fun asDataItem_Returns_Debug_String_When_LogLevel_Is_Debug() {
        assertEquals("debug", LogLevel.DEBUG.asDataItem().getString())
    }

    @Test
    fun asDataItem_Returns_Info_String_When_LogLevel_Is_Info() {
        assertEquals("info", LogLevel.INFO.asDataItem().getString())
    }

    @Test
    fun asDataItem_Returns_Warn_String_When_LogLevel_Is_Warn() {
        assertEquals("warn", LogLevel.WARN.asDataItem().getString())
    }

    @Test
    fun asDataItem_Returns_Error_String_When_LogLevel_Is_Error() {
        assertEquals("error", LogLevel.ERROR.asDataItem().getString())
    }

    @Test
    fun asDataItem_Returns_Silent_String_When_LogLevel_Is_Silent() {
        assertEquals("silent", LogLevel.SILENT.asDataItem().getString())
    }
}