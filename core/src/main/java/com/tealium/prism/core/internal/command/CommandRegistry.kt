package com.tealium.prism.core.internal.command

import com.tealium.prism.core.api.command.Command
import com.tealium.prism.core.api.command.CommandException
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import java.util.Locale

/**
 * When two commands share the same normalized name, the last entry in [commands] wins —
 * consistent with Swift's `uniquingKeysWith: { _, new in new }` semantics.
 */
internal class CommandRegistry(commands: List<Command>) {

    private val commands: Map<String, Command> =
        commands.associate { normalize(it.name) to it }

    fun execute(
        commandName: String,
        payload: DataObject,
        callback: Callback<TealiumResult<Unit>>
    ): Disposable {
        val command = commands[normalize(commandName)]
            ?: run {
                callback.failure(CommandException.commandNotFound(commandName))
                return Disposables.disposed()
            }
        return command.execute(payload, callback)
    }

    private fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
}
