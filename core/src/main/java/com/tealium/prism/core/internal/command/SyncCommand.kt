package com.tealium.prism.core.internal.command

import com.tealium.prism.core.api.command.Command
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Consumer
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.internal.misc.completing

/**
 * [Command] implementation for synchronous work. Wraps a [block] that runs on the calling thread
 * and completes before returning. Failures are caught and forwarded as [TealiumResult.failure].
 * The completion callback is invoked exactly once, after [block] has run and outside of the
 * exception handling, so anything it throws propagates to the caller.
 *
 * Not intended for direct use — obtain instances via [Command.synchronous].
 */
internal class SyncCommand(
    override val name: String,
    private val block: Consumer<DataObject>
) : Command {

    override fun execute(payload: DataObject, callback: Callback<TealiumResult<Unit>>): Disposable {
        callback.completing { block.accept(payload) }
        return Disposables.disposed()
    }
}
