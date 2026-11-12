package com.tealium.prism.core.api.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class CommandExceptionTests {

    @Test
    fun invalidParameterValue_Message_Includes_Parameter_And_Reason() {
        val exception = CommandException.invalidParameterValue("gender", "expected one of m, f")

        assertEquals("Invalid value for 'gender' - expected one of m, f", exception.message)
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
