package com.tealium.prism.core.api.data

/**
 * Implemented by types that can be converted to a [JsonObjectPath].
 *
 * A [JsonObjectPath] is itself a [JsonPathConvertible] of [JsonPath.Component.Key], so functions
 * that take one accept both implementers of this interface and raw object paths.
 */
interface JsonObjectPathConvertible : JsonPathConvertible<JsonPath.Component.Key> {

    /**
     * Returns the [JsonObjectPath] representation of this type.
     */
    fun asJsonObjectPath(): JsonObjectPath

    /**
     * Returns [asJsonObjectPath].
     */
    override fun asJsonPath(): JsonObjectPath = asJsonObjectPath()
}
