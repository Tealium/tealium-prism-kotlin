package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.internal.command.SyncCommand

/**
 * Defines a named unit of work that can be executed against a [DataObject] payload.
 *
 * The [name] is stored as-is but normalized for lookup (trimmed, lowercased) by the
 * [com.tealium.prism.core.internal.command.CommandRegistry]. Implementors should not
 * pre-normalize the name themselves.
 */
interface Command {

    val name: String

    /**
     * Executes this command against the given [payload].
     *
     * [callback] must be invoked exactly once — with a successful [TealiumResult] or a failure.
     *
     * @return A [Disposable] that can be used to abort in-flight work.
     */
    fun execute(payload: DataObject, callback: Callback<TealiumResult<Unit>>): Disposable

    companion object {

        /**
         * Creates a [Command] that executes [block] synchronously on the calling thread.
         *
         * [block] should throw to signal failure; the exception is forwarded as a
         * [TealiumResult.failure] to the caller.
         */
        @JvmStatic
        fun synchronous(name: String, block: (DataObject) -> Unit): Command =
            SyncCommand(name, block)
    }
}
