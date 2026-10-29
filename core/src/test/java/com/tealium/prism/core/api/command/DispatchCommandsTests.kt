package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.tracking.Dispatch
import org.junit.Assert.assertEquals
import org.junit.Test

class DispatchCommandsTests {

    private fun dispatchWithCommands(vararg commands: String): Dispatch =
        Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.COMMAND_NAME, commands.toList().asDataList())
        })

    @Test
    fun getCommands_Returns_Empty_List_When_No_Command_Name() {
        assertEquals(emptyList<String>(), Dispatch.create("event").getCommands())
    }

    @Test
    fun getCommands_Returns_Single_String_Command() {
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.COMMAND_NAME, "cmd")
        })

        assertEquals(listOf("cmd"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Ignores_Blank_Commands() {
        val dispatch = dispatchWithCommands("a", "  ", "", "\t\n", "b")

        assertEquals(listOf("a", "b"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Removes_Exact_Duplicates() {
        val dispatch = dispatchWithCommands("a", "b", "a")

        assertEquals(listOf("a", "b"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Removes_Duplicates_Differing_By_Case() {
        val dispatch = dispatchWithCommands("Log", "LOG", "log")

        assertEquals(listOf("log"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Removes_Duplicates_Differing_By_Whitespace() {
        val dispatch = dispatchWithCommands("  log  ", "log", "log\n")

        assertEquals(listOf("log"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Returns_Lowercased_Names() {
        val dispatch = dispatchWithCommands("Log", "MY_Command")

        assertEquals(listOf("log", "my_command"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Returns_Names_Trimmed_Of_Spaces_And_Tabs() {
        val dispatch = dispatchWithCommands("  log  ", "\tview\t")

        assertEquals(listOf("log", "view"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Returns_Names_Trimmed_Of_Newlines() {
        val dispatch = dispatchWithCommands("log\n", "\r\nview")

        assertEquals(listOf("log", "view"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Returns_Normalized_Names_When_Case_And_Whitespace_Are_Mixed() {
        val dispatch = dispatchWithCommands(" \tLog\n", "VIEW ")

        assertEquals(listOf("log", "view"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Keeps_First_Occurrence_And_Order() {
        val dispatch = dispatchWithCommands("b", "a", "B", "c", "A")

        assertEquals(listOf("b", "a", "c"), dispatch.getCommands())
    }

    @Test
    fun getCommands_Does_Not_Modify_Payload() {
        val dispatch = dispatchWithCommands("a", "A", "a")
        val original = dispatch.payload()

        dispatch.getCommands()

        assertEquals(original, dispatch.payload())
        assertEquals(
            listOf("a", "A", "a").asDataList(),
            dispatch.payload().getDataList(Dispatch.Keys.COMMAND_NAME)
        )
    }
}
