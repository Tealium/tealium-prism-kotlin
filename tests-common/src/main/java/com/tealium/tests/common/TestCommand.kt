package com.tealium.tests.common

import com.tealium.prism.core.api.command.Command
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.success
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables

/**
 * Test implementation of [Command] that records invocations and allows configuring
 * simulated async behaviour via a [handler] lambda.
 *
 * Default handler completes immediately with success, mimicking a successful sync command.
 * Override [handler] to defer completion or simulate failures:
 * ```kotlin
 * var delayedComplete: (() -> Unit)? = null
 * val command = TestCommand("my_command") { _, callback ->
 *     delayedComplete = { callback.success(Unit) }
 *     Disposables.subscription { delayedComplete = null }
 * }
 * ```
 */
class TestCommand(
    override val name: String,
    private val handler: (DataObject, Callback<TealiumResult<Unit>>) -> Disposable = { _, callback ->
        callback.success(Unit)
        Disposables.disposed()
    }
) : Command {

    var executeCallCount = 0
        private set
    var lastPayload: DataObject? = null
        private set

    val executeCalled: Boolean get() = executeCallCount > 0

    override fun execute(
        payload: DataObject,
        callback: Callback<TealiumResult<Unit>>
    ): Disposable {
        executeCallCount++
        lastPayload = payload
        return handler(payload, callback)
    }
}
