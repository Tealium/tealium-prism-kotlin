package com.tealium.prism.core.api.command

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.data.JsonObjectPathConvertible
import com.tealium.prism.core.api.settings.Mappings

/**
 * Extends [Mappings] with type-safe command and destination overloads for
 * [CommandDispatcher]-based dispatcher modules.
 *
 * Subclasses supply concrete command type [C] and destination type [D], giving callers
 * enum-based safety for both. All base [Mappings] methods remain available via delegation.
 *
 * Example:
 * ```kotlin
 * class MyMappings : CommandMappingsBuilder<MyCommand, MyDestination>()
 * ```
 */
abstract class CommandMappingsBuilder<Command : CommandName, Destination : JsonObjectPathConvertible>(
    private val delegate: Mappings = Mappings.default()
) : Mappings by delegate {

    /** Maps a typed [command] to [com.tealium.prism.core.api.tracking.Dispatch.Keys.COMMAND_NAME]. */
    fun mapCommand(command: Command): Mappings.CommandOptions =
        delegate.mapCommand(command.commandName)

    /** Maps a payload [key] to a typed [destination] path. */
    fun mapFrom(key: String, destination: Destination): Mappings.VariableOptions =
        delegate.mapFrom(key, destination.asJsonObjectPath())

    /** Maps a nested [path] to a typed [destination] path. */
    fun mapFrom(path: JsonObjectPath, destination: Destination): Mappings.VariableOptions =
        delegate.mapFrom(path, destination.asJsonObjectPath())

    /** Maps a constant [value] to a typed [destination] path. */
    fun mapConstant(value: DataItem, destination: Destination): Mappings.ConstantOptions =
        delegate.mapConstant(value, destination.asJsonObjectPath())

    /** Keeps a typed [destination] path unchanged (source == destination). */
    fun keep(destination: Destination): Mappings.VariableOptions =
        delegate.keep(destination.asJsonObjectPath())


}
