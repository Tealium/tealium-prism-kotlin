package com.tealium.prism.core.internal.command

import com.tealium.prism.core.api.command.Command
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.misc.success
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables

/**
 * [Command] implementation for synchronous work. Wraps a [block] that runs on the calling thread
 * and completes before returning. Failures are caught and forwarded as [TealiumResult.failure].
 *
 * Not intended for direct use — obtain instances via [Command.synchronous].
 */
internal class SyncCommand(
    override val name: String,
    private val block: (DataObject) -> Unit
) : Command {

    override fun execute(payload: DataObject, callback: Callback<TealiumResult<Unit>>): Disposable {
        try {
            block(payload)
            callback.success(Unit)
        } catch (e: Throwable) {
            callback.failure(e)
        }
        return Disposables.disposed()
    }
}
