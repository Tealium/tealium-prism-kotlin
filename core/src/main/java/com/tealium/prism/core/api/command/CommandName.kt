package com.tealium.prism.core.api.command

/**
 * Implemented by types that represent a named command, enabling type-safe command name
 * references in [CommandMappingsBuilder.mapCommand].
 *
 * Typically implemented by enum classes:
 * ```kotlin
 * enum class MyCommand : CommandName {
 *     LAUNCH, PURCHASE;
 *     override val commandName = name.lowercase(java.util.Locale.ROOT)
 * }
 * ```
 */
interface CommandName {
    val commandName: String
}
