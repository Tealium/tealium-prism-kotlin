package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.tests.common.TestSyncCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCommandTests {

    private val payload = DataObject.EMPTY_OBJECT

    @Test
    fun synchronous_InvokesBlock_OnExecute() {
        var called = false
        val command = Command.synchronous("cmd") { called = true }

        command.execute(payload) { }

        assertTrue(called)
    }

    @Test
    fun synchronous_CompletesWithSuccess_WhenBlockSucceeds() {
        val command = Command.synchronous("cmd") { }
        var result: TealiumResult<Unit>? = null

        command.execute(payload) { result = it }

        assertTrue(result!!.isSuccess)
    }

    @Test
    fun synchronous_CompletesWithFailure_WhenBlockThrows() {
        val cause = CommandException.missingParameter("key")
        val command = Command.synchronous("cmd") { throw cause }
        var result: TealiumResult<Unit>? = null

        command.execute(payload) { result = it }

        assertTrue(result!!.isFailure)
        assertSame(cause, result!!.exceptionOrNull())
    }

    @Test
    fun synchronous_CompletesWithFailure_WhenNonCommandExceptionThrown() {
        val cause = RuntimeException("unexpected")
        val command = Command.synchronous("cmd") { throw cause }
        var result: TealiumResult<Unit>? = null

        command.execute(payload) { result = it }

        assertTrue(result!!.isFailure)
        assertSame(cause, result!!.exceptionOrNull())
    }

    @Test
    fun synchronous_ReturnsDisposed_Always() {
        val command = Command.synchronous("cmd") { }

        val disposable = command.execute(payload) { }

        assertTrue(disposable.isDisposed)
    }

    @Test
    fun synchronous_ForwardsPayload_ToBlock() {
        val expected = DataObject.create { put("key", "value") }
        var received: DataObject? = null
        val command = Command.synchronous("cmd") { received = it }

        command.execute(expected) { }

        assertEquals(expected, received)
    }

    @Test
    fun synchronous_HasCorrectName() {
        val command = Command.synchronous("my_command") { }

        assertEquals("my_command", command.name)
    }

    // TestSyncCommand is a test-only helper — verify it behaves consistently with the factory.

    @Test
    fun testSyncCommand_InvokesSyncHook_OnExecute() {
        val command = TestSyncCommand("cmd")

        command.execute(payload) { }

        assertTrue(command.executeCalled)
    }

    @Test
    fun testSyncCommand_CompletesWithSuccess_OnSuccess() {
        val command = TestSyncCommand("cmd")
        var result: TealiumResult<Unit>? = null

        command.execute(payload) { result = it }

        assertTrue(result!!.isSuccess)
    }

    @Test
    fun testSyncCommand_CompletesWithFailure_WhenErrorToThrowSet() {
        val expected = CommandException.missingParameter("key")
        val command = TestSyncCommand("cmd", errorToThrow = expected)
        var result: TealiumResult<Unit>? = null

        command.execute(payload) { result = it }

        assertTrue(result!!.isFailure)
        assertSame(expected, result!!.exceptionOrNull())
    }

    @Test
    fun testSyncCommand_ReturnsDisposed_Always() {
        val command = TestSyncCommand("cmd")

        val disposable = command.execute(payload) { }

        assertTrue(disposable.isDisposed)
    }

    @Test
    fun testSyncCommand_ForwardsPayload_ToSyncHook() {
        val expected = DataObject.create { put("key", "value") }
        val command = TestSyncCommand("cmd")

        command.execute(expected) { }

        assertEquals(expected, command.lastPayload)
    }

    @Test
    fun testSyncCommand_TracksExecuteCallCount() {
        val command = TestSyncCommand("cmd")

        command.execute(payload) { }
        command.execute(payload) { }

        assertEquals(2, command.executeCallCount)
    }
}
