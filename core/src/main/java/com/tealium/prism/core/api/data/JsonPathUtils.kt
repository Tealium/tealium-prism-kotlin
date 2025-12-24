@file:JvmName("JsonPathUtils")

package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.JsonPath.Component


/**
 * Utility method to cast a [JsonPath] of unknown type, e.g. from a [JsonPath.parse] to the required
 * type.
 *
 * i.e. The first component of the returned [JsonPath] should be applicable to JSON objects.
 */
@Throws(JsonPathParseException::class)
fun JsonPath<*>.requireObjectPath(): JsonPath<Component.Key> =
    asObjectPathOrNull()
        ?: throw JsonPathSyntaxException(0, "JsonPath must start with a key for object paths")

/**
 * Utility method to cast a [JsonPath] of unknown type, e.g. from a [JsonPath.parse] to the required
 * type.
 *
 * i.e. The first component of the returned [JsonPath] should be applicable to JSON objects.
 */
@Throws(IllegalArgumentException::class)
fun JsonPath<*>.asObjectPathOrNull(): JsonPath<Component.Key>? {
    if (firstComponent !is Component.Key)
        return null

    @Suppress("UNCHECKED_CAST")
    return this as JsonPath<Component.Key>
}

/**
 * Utility method to cast a [JsonPath] of unknown type, e.g. from a [JsonPath.parse] to the required
 * type.
 *
 * i.e. The first component of the returned [JsonPath] should be applicable to JSON arrays.
 */
@Throws(JsonPathParseException::class)
fun JsonPath<*>.requireListPath(): JsonPath<Component.Index> =
    asListPathOrNull()
        ?: throw JsonPathSyntaxException(0, "JsonPath must start with an index for list paths")

/**
 * Utility method to cast a [JsonPath] of unknown type, e.g. from a [JsonPath.parse] to the required
 * type.
 *
 * i.e. The first component of the returned [JsonPath] should be applicable to JSON arrays.
 */
@Throws(IllegalArgumentException::class)
fun JsonPath<*>.asListPathOrNull(): JsonPath<Component.Index>? {
    if (firstComponent !is Component.Index)
        return null

    @Suppress("UNCHECKED_CAST")
    return this as JsonPath<Component.Index>
}

/**
 * Kotlin convenience method to allow expressing [JsonPath] items in a more succinct way:
 * ```kotlin
 * val verbosePath = JsonPath.key("key")
 *      .index(0)
 *      .key("sub-key")
 *
 * val shortPath = JsonPath["key"][0]["sub-key"]
 * verbosePath == shortPath // true
 * ```
 *
 * @return a new [JsonPath] with additional [Component.Key] added
 */
@JvmSynthetic
operator fun <TRoot: Component> JsonPath<TRoot>.get(name: String): JsonPath<TRoot> =
    key(name)

/**
 * Kotlin convenience method to allow expressing [JsonPath] items in a more succinct way:
 * ```kotlin
 * val verbosePath = JsonPath.index(1)
 *      .index(0)
 *      .key("sub-key")
 *
 * val shortPath = JsonPath[1][0]["sub-key"]
 * verbosePath == shortPath // true
 * ```
 *
 * @return a new [JsonPath] with additional [Component.Index] added
 */
@JvmSynthetic
operator fun <TRoot: Component> JsonPath<TRoot>.get(index: Int): JsonPath<TRoot> =
    index(index)