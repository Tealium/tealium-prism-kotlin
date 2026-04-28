package com.tealium.prism.core.api.data

/**
 * Implemented by types that can be converted to a [JsonObjectPath].
 */
interface JsonObjectPathConvertible {

    /**
     * Returns the [JsonObjectPath] representation of this type.
     */
    fun asJsonObjectPath(): JsonObjectPath
}
