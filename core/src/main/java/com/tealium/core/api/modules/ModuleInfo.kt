package com.tealium.core.api.modules

/**
 * Data class containing information about a specific [Module] implementation.
 *
 * @param id The string that uniquely identifies a [Module]
 * @param version The string describing the version of the [Module]
 *
 * @see Module.id
 * @see Module.version
 */
data class ModuleInfo(
    val id: String,
    val version: String
)