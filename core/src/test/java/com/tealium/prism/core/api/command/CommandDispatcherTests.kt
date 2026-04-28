package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.data.DataItemUtils.asDataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.misc.success
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.TestCommand
import io.mockk.MockKAnnotations
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CommandDispatcherTests {

    private lateinit var dispatcher: TestCommandDispatcher

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    private fun dispatchWithCommands(vararg commandNames: String): Dispatch =
        Dispatch.create(
            "test_event",
            dataObject = DataObject.create {
                put(Dispatch.Keys.COMMAND_NAME, commandNames.toList().asDataList())
            }
        )

    // Concrete subclass for testing. Uses [Scheduler.SYNCHRONOUS] so that completion
    // callbacks run on the calling thread, keeping these tests deterministic.
    class TestCommandDispatcher(
        commands: List<Command>
    ) : CommandDispatcher(
        id = "test_dispatcher",
        version = "1.0",
        commands = commands,
        logger = SystemLogger,
        scheduler = Scheduler.SYNCHRONOUS,
    )

    // --- Single command ---

    @Test
    fun dispatch_ExecutesMatchingCommand_ForSingleDispatch() {
        val command = TestCommand("my_command")
        dispatcher = TestCommandDispatcher(listOf(command))
        val dispatch = dispatchWithCommands("my_command")
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch), callback)

        assertTrue(command.executeCalled)
        verify { callback.onComplete(listOf(dispatch)) }
        confirmVerified(callback)
    }

    @Test
    fun dispatch_ForwardsPayload_ToCommand() {
        val command = TestCommand("cmd")
        dispatcher = TestCommandDispatcher(listOf(command))
        val payload = DataObject.create {
            put("extra_key", "extra_value")
            put(Dispatch.Keys.COMMAND_NAME, listOf("cmd").asDataList())
        }
        val dispatch = Dispatch.create("event", dataObject = payload)
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch), callback)

        assertEquals(dispatch.payload(), command.lastPayload)
    }

    // --- Multiple commands ---

    @Test
    fun dispatch_ExecutesAllCommands_WhenMultipleNamesInPayload() {
        val alpha = TestCommand("alpha")
        val beta = TestCommand("beta")
        dispatcher = TestCommandDispatcher(listOf(alpha, beta))
        val dispatch = dispatchWithCommands("alpha", "beta")
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch), callback)

        assertTrue(alpha.executeCalled)
        assertTrue(beta.executeCalled)
        verify(exactly = 1) { callback.onComplete(listOf(dispatch)) }
    }

    @Test
    fun dispatch_CallsCallback_Once_EvenWhenOneCommandFails() {
        val failing = TestCommand("fail_cmd") { _, callback ->
            callback.failure(CommandException.noValidParameters())
            Disposables.disposed()
        }
        val passing = TestCommand("pass_cmd")
        dispatcher = TestCommandDispatcher(listOf(failing, passing))
        val dispatch = dispatchWithCommands("fail_cmd", "pass_cmd")
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch), callback)

        verify(exactly = 1) { callback.onComplete(listOf(dispatch)) }
        confirmVerified(callback)
    }

    // --- No commands ---

    @Test
    fun dispatch_CallsCallback_Immediately_WhenNoCommandsInPayload() {
        dispatcher = TestCommandDispatcher(emptyList())
        val dispatch = Dispatch.create("event")
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch), callback)

        verify { callback.onComplete(listOf(dispatch)) }
        confirmVerified(callback)
    }

    @Test
    fun dispatch_CallsCallback_Immediately_WhenCommandNamesAreBlank() {
        dispatcher = TestCommandDispatcher(listOf(TestCommand("cmd")))
        val dispatch = Dispatch.create("event", dataObject = DataObject.create {
            put(Dispatch.Keys.COMMAND_NAME, listOf("  ").asDataList())
        })
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch), callback)

        verify { callback.onComplete(listOf(dispatch)) }
    }

    // --- Multiple dispatches ---

    @Test
    fun dispatch_CallsCallback_OncePerDispatch_ForMultipleDispatches() {
        val command = TestCommand("cmd")
        dispatcher = TestCommandDispatcher(listOf(command))
        val dispatch1 = dispatchWithCommands("cmd")
        val dispatch2 = dispatchWithCommands("cmd")
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch1, dispatch2), callback)

        // Matches Swift: completion([dispatch]) fires once per individual dispatch.
        verify(exactly = 1) { callback.onComplete(listOf(dispatch1)) }
        verify(exactly = 1) { callback.onComplete(listOf(dispatch2)) }
        confirmVerified(callback)
    }

    // --- Async command with deferred completion ---

    @Test
    fun dispatch_CallsCallback_OnlyAfterAsyncCommandCompletes() {
        var deferred: (() -> Unit)? = null
        val command = TestCommand("async_cmd") { _, callback ->
            val subscription = Disposables.subscription()
            deferred = { if (!subscription.isDisposed) callback.success(Unit) }
            subscription
        }
        dispatcher = TestCommandDispatcher(listOf(command))
        val dispatch = dispatchWithCommands("async_cmd")
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch), callback)

        // Callback must NOT fire before async work completes.
        verify(exactly = 0) { callback.onComplete(any()) }

        deferred?.invoke()

        verify(exactly = 1) { callback.onComplete(listOf(dispatch)) }
        confirmVerified(callback)
    }

    @Test
    fun dispatch_DoesNotCallCallback_WhenDisposedBeforeAsyncCompletion() {
        var deferred: (() -> Unit)? = null
        val command = TestCommand("async_cmd") { _, callback ->
            val subscription = Disposables.subscription()
            deferred = { if (!subscription.isDisposed) callback.success(Unit) }
            subscription
        }
        dispatcher = TestCommandDispatcher(listOf(command))
        val dispatch = dispatchWithCommands("async_cmd")
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        val disposable = dispatcher.dispatch(listOf(dispatch), callback)
        disposable.dispose()

        // The subscription was disposed, so the deferred block never calls onComplete.
        deferred?.invoke()

        verify(exactly = 0) { callback.onComplete(any()) }
        confirmVerified(callback)
    }

    // --- Single string command name ---

    @Test
    fun dispatch_ExecutesCommand_WhenCommandNameIsPlainString() {
        val command = TestCommand("my_command")
        dispatcher = TestCommandDispatcher(listOf(command))
        val dispatch = Dispatch.create("test_event", dataObject = DataObject.create {
            put(Dispatch.Keys.COMMAND_NAME, "my_command")
        })
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch), callback)

        assertTrue(command.executeCalled)
        verify { callback.onComplete(listOf(dispatch)) }
    }

    // --- Unknown command ---

    @Test
    fun dispatch_CallsCallback_WhenCommandNameIsNotRegistered() {
        dispatcher = TestCommandDispatcher(emptyList())
        val dispatch = dispatchWithCommands("unknown_cmd")
        val callback = mockk<Callback<List<Dispatch>>>(relaxed = true)

        dispatcher.dispatch(listOf(dispatch), callback)

        verify(exactly = 1) { callback.onComplete(listOf(dispatch)) }
        confirmVerified(callback)
    }

    // --- dispatchLimit ---

    @Test
    fun dispatch_HasDefaultDispatchLimitOfTen() {
        dispatcher = TestCommandDispatcher(emptyList())
        assertTrue(dispatcher.dispatchLimit == 10)
    }

    // --- Returns disposable ---

    @Test
    fun dispatch_ReturnsActiveDisposable_WhenCommandsPresent() {
        val command = TestCommand("cmd") { _, _ -> Disposables.subscription() }
        dispatcher = TestCommandDispatcher(listOf(command))
        val dispatch = dispatchWithCommands("cmd")

        val result = dispatcher.dispatch(listOf(dispatch)) { }

        assertFalse(result.isDisposed)
    }
}
