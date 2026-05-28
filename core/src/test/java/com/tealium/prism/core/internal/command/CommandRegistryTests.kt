package com.tealium.prism.core.internal.command

import com.tealium.prism.core.api.command.CommandException
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.tests.common.TestCommand
import com.tealium.tests.common.TestSyncCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRegistryTests {

    private val payload = DataObject.EMPTY_OBJECT

    @Test
    fun registry_ExecutesCommand_WhenNameMatches() {
        val command = TestCommand("test_command")
        val registry = CommandRegistry(listOf(command))

        registry.execute("test_command", payload) { result ->
            assertTrue(result.isSuccess)
        }

        assertTrue(command.executeCalled)
    }

    @Test
    fun registry_NormalizesName_CaseInsensitive() {
        val command = TestCommand("My_Command")
        val registry = CommandRegistry(listOf(command))

        registry.execute("MY_COMMAND", payload) { }

        assertTrue(command.executeCalled)
    }

    @Test
    fun registry_NormalizesName_TrimsWhitespace() {
        val command = TestCommand("trimmed")
        val registry = CommandRegistry(listOf(command))

        registry.execute("  trimmed  ", payload) { }

        assertTrue(command.executeCalled)
    }

    @Test
    fun registry_ReturnsCommandNotFound_WhenNameUnknown() {
        val registry = CommandRegistry(emptyList())
        var result: TealiumResult<Unit>? = null

        registry.execute("unknown", payload) { result = it }

        assertTrue(result!!.isFailure)
        assertTrue(result!!.exceptionOrNull() is CommandException)
    }

    @Test
    fun registry_LastCommandWins_OnDuplicateNormalizedNames() {
        val first = TestCommand("cmd")
        val last = TestCommand("CMD")  // same normalized name
        val registry = CommandRegistry(listOf(first, last))

        registry.execute("cmd", payload) { }

        assertTrue(last.executeCalled)
        assertFalse(first.executeCalled)
    }

    @Test
    fun registry_ForwardsPayload_ToCommand() {
        val expected = DataObject.create { put("key", "value") }
        val command = TestCommand("cmd")
        val registry = CommandRegistry(listOf(command))

        registry.execute("cmd", expected) { }

        assertEquals(expected, command.lastPayload)
    }

    @Test
    fun registry_PropagatesFailure_FromCommand() {
        val error = CommandException.missingParameter("required_key")
        val command = TestSyncCommand("cmd", errorToThrow = error)
        val registry = CommandRegistry(listOf(command))
        var result: TealiumResult<Unit>? = null

        registry.execute("cmd", payload) { result = it }

        assertTrue(result!!.isFailure)
        assertSame(error, result!!.exceptionOrNull())
    }

    @Test
    fun registry_ReturnsDisposed_WhenCommandNotFound() {
        val registry = CommandRegistry(emptyList())

        val disposable = registry.execute("unknown", payload) { }

        assertTrue(disposable.isDisposed)
    }

    @Test
    fun registry_AcceptsMultipleCommands() {
        val a = TestCommand("alpha")
        val b = TestCommand("beta")
        val registry = CommandRegistry(listOf(a, b))

        registry.execute("alpha", payload) { }
        registry.execute("beta", payload) { }

        assertTrue(a.executeCalled)
        assertTrue(b.executeCalled)
    }

    @Test
    fun registry_ReturnsCommandDisposable_WhenCommandFound() {
        val subscription = Disposables.subscription()
        val command = TestCommand("cmd") { _, _ -> subscription }
        val registry = CommandRegistry(listOf(command))

        val result = registry.execute("cmd", payload) { }

        assertEquals(subscription, result)
    }
}
