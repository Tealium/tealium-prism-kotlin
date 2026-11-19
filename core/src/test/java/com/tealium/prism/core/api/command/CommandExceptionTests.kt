package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.data.ExtractionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CommandExceptionTests {

    @Test
    fun invalidPayload_Message_Is_Cause_Message_And_Keeps_Cause() {
        val cause = ExtractionException.InvalidValue("gender", "expected one of m, f")

        val exception = CommandException.invalidPayload(cause)

        assertEquals("Invalid value for 'gender' - expected one of m, f", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    @Suppress("DEPRECATION")
    fun missingParameter_Keeps_Legacy_Message() {
        val exception = CommandException.missingParameter("key")

        assertEquals("Required parameter missing: key", exception.message)
        assertNull(exception.cause)
    }

    @Test
    @Suppress("DEPRECATION")
    fun invalidParameterType_Keeps_Legacy_Message() {
        val exception = CommandException.invalidParameterType("key", "String")

        assertEquals("Parameter 'key' expected type String", exception.message)
        assertNull(exception.cause)
    }

    @Test
    @Suppress("DEPRECATION")
    fun emptyParameter_Keeps_Legacy_Message() {
        val exception = CommandException.emptyParameter("key")

        assertEquals("Parameter 'key' is empty", exception.message)
        assertNull(exception.cause)
    }

    @Test
    @Suppress("DEPRECATION")
    fun emptyArray_Keeps_Legacy_Message() {
        val exception = CommandException.emptyArray("key")

        assertEquals("Array 'key' is empty", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun noValidParameters_Message_Lists_Expected_Parameters() {
        val exception = CommandException.noValidParameters(listOf("a", "b"))

        assertEquals("No valid parameter found. Expected one of: 'a', 'b'", exception.message)
    }

    @Test
    fun noValidParameters_Message_Omits_Expected_When_None_Are_Given() {
        assertEquals("No valid parameter found.", CommandException.noValidParameters().message)
    }

    @Test
    fun unknown_Message_Includes_Cause_Message_And_Keeps_Cause() {
        val cause = IllegalStateException("boom")

        val exception = CommandException.unknown(cause)

        assertEquals("Command failed due to error: boom", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun unknown_Message_Falls_Back_To_Cause_Class_Name_When_Cause_Has_No_Message() {
        val exception = CommandException.unknown(IllegalStateException())

        assertEquals("Command failed due to error: java.lang.IllegalStateException", exception.message)
    }
}
