package com.tealium.tests.common

import com.tealium.prism.core.api.command.Command
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.misc.success
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables

/**
 * Test implementation of [Command] that executes synchronously, records invocations,
 * and optionally reports a configured [errorToThrow] via [Callback.failure] on execution.
 */
class TestSyncCommand(
    override val name: String,
    val errorToThrow: Throwable? = null
) : Command {

    var executeCallCount = 0
        private set
    var lastPayload: DataObject? = null
        private set

    val executeCalled: Boolean get() = executeCallCount > 0

    override fun execute(payload: DataObject, callback: Callback<TealiumResult<Unit>>): Disposable {
        executeCallCount++
        lastPayload = payload
        val error = errorToThrow
        if (error != null) {
            callback.failure(error)
        } else {
            callback.success(Unit)
        }
        return Disposables.disposed()
    }
}
