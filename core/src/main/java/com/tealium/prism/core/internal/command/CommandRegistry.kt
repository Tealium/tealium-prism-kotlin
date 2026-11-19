package com.tealium.prism.core.internal.command

import com.tealium.prism.core.api.command.Command
import com.tealium.prism.core.api.command.CommandException
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.ExtractionException
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.failure
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables

/**
 * When two commands share the same normalized name, the last entry in [commands] wins —
 * consistent with Swift's `uniquingKeysWith: { _, new in new }` semantics.
 */
internal class CommandRegistry(commands: List<Command>) {

    companion object {
        /**
         * The form of a command name used to match command names: trimmed of whitespace and lowercased.
         * Applied to [Command.name] at registration and by `Dispatch.getCommands()`.
         */
        fun normalize(name: String): String = name.trim().lowercase()
    }

    private val commands: Map<String, Command> =
        commands.associate { normalize(it.name) to it }

    /**
     * Executes the command registered under [commandName], which must already be normalized with [normalize],
     * as `Dispatch.getCommands()` returns it.
     *
     * A failure passed to the callback is reported as a [CommandException]: a [CommandException] as is,
     * an [ExtractionException] as [CommandException.invalidPayload], and anything else as
     * [CommandException.unknown].
     */
    fun execute(
        commandName: String,
        payload: DataObject,
        callback: Callback<TealiumResult<Unit>>
    ): Disposable {
        val command = commands[commandName]
            ?: run {
                callback.failure(CommandException.commandNotFound(commandName))
                return Disposables.disposed()
            }
        return command.execute(payload) { result ->
            val error = result.exceptionOrNull()
            callback.onComplete(if (error == null) result else TealiumResult.failure(error.toCommandException()))
        }
    }
}

private fun Throwable.toCommandException(): CommandException = when (this) {
    is CommandException -> this
    is ExtractionException -> CommandException.invalidPayload(this)
    else -> CommandException.unknown(this)
}
