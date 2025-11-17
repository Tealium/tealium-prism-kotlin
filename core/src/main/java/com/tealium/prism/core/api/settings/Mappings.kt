package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.data.JsonObjectPath
import com.tealium.prism.core.api.data.JsonPath

/**
 * The [Mappings] interface is used to build up key/destination mappings used when optionally
 * translating the full [Dispatch] payload to just the relevant data for any given [Dispatcher]
 *
 * Use the multiple [from] methods to supply the required source "key" and "destination" key, as
 * well as any optional "path" entries required to access keys in nested object.
 *
 * Using the following payload DataObject as an example (shown as JSON)
 * ```json
 * {
 *      "source" : "value",
 *      "path": {
 *          "to" : {
 *              "source": "nested value"
 *          }
 *      }
 * }
 * ```
 *
 * Simple usage for keys in the top level [DataObject] would look like so:
 * ```kotlin
 * from("source", "destination")
 * ```
 *
 * More complex versions requiring accessing keys that exist in nested objects would look like so:
 * ```kotlin
 * from(JsonPath["path"]["to"]["source"], JsonPath["path"]["to"]["destination"])
 *
 * ```
 *
 * The [from] method returns a [VariableOptions] that allows for setting optional properties relevant to a mapping.
 *
 * @see VariableOptions
 * @see ConstantOptions
 */
interface Mappings {

    /**
     * The [VariableOptions] allows for configuring optional properties relevant only when mapping values
     * from the source payload.
     */
    interface VariableOptions {

        /**
         * Sets an optional basic condition that the value at the given mapping key needs to match
         * in order for this mapping to take place.
         *
         * @param value The target value that the source key should contain.
         */
        fun ifValueEquals(value: String): VariableOptions
    }

    /**
     * The [ConstantOptions] allows for configuring optional properties relevant only when mapping
     * constant values to the destination.
     */
    interface ConstantOptions {

        /**
         * Sets an optional basic condition that the value at the given mapping [key] needs to match
         * in order for this mapping to take place.
         *
         * @param key The [key] to take the value from when comparing against the expected [value]
         * @param value The target value that the source key should contain.
         */
        fun ifValueEquals(key: String, value: String): ConstantOptions

        /**
         * Sets an optional basic condition that the value at the given mapping [path] needs to match
         * in order for this mapping to take place, where the [path] may be found in a configured level
         * of nesting according to the [path]
         *
         * @param path The [path] to take the value from when comparing against the expected [value]
         * @param value The target value that the source key should contain.
         */
        fun ifValueEquals(path: JsonObjectPath, value: String): ConstantOptions
    }

    /**
     * Adds a mapping where the [key] and [destination] are in the top level of the payload.
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and place it in the mapped payload at the [destination] key
     * @param destination The [destination] key to store the mapped value
     */
    fun from(key: String, destination: String): VariableOptions

    /**
     * Adds a mapping where the [destination] is to be in the top level of the mapped payload, but
     * the source key is defined at some nested object/list as defined by [path].
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param path The [path] used to take the value from and place it in the mapped payload at the [destination] key
     * @param destination The [destination] key to store the mapped value
     */
    fun from(path: JsonObjectPath, destination: String): VariableOptions

    /**
     * Adds a mapping where the [key] is to be found in the top level of the input payload, but the
     * [destination] key is to be defined at some nested object/list as defined by [destination].
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and place it in the mapped payload at the [destination] key
     * @param destination The [destination] key path to store the mapped value
     */
    fun from(key: String, destination: JsonObjectPath): VariableOptions

    /**
     * Adds a mapping where both the [path] and [destination] can be located/stored at some
     * configured level of nesting as defined by the [JsonObjectPath] for each input.
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param path The [path] to take the value from and place it in the mapped payload at the [destination] key
     * @param destination The [destination] key path to store the mapped value
     */
    fun from(path: JsonObjectPath, destination: JsonObjectPath): VariableOptions

    /**
     * Adds a mapping where the [key] is both the source and destination of the mapping.
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and also the destination to place it in the mapped payload
     */
    fun keep(key: String): VariableOptions

    /**
     * Adds a mapping where the possibly nested [path] is both the source and destination of the mapping.
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param path The [path] to take the value from and also the destination to place it in the mapped payload
     */
    fun keep(path: JsonObjectPath): VariableOptions

    /**
     * Adds a mapping where the value to map is given by the constant [value] and will be mapped to
     * the given [destination]
     *
     * Returns a [ConstantOptions] with which to configure some options if required.
     *
     * @param value The constant value to map to the given [destination]
     * @param destination The [destination] key to store the mapped value
     */
    fun constant(value: String, destination: String): ConstantOptions

    /**
     * Adds a mapping where the value to map is given by the constant [value] and will be mapped to
     * the given [destination] located/stored at some configured level of nesting as defined by
     * the [JsonObjectPath].
     *
     * Returns a [ConstantOptions] with which to configure some options if required.
     *
     * @param value The constant value to map to the given [destination]
     * @param destination The [destination] key to store the mapped value
     */
    fun constant(value: String, destination: JsonObjectPath): ConstantOptions

    /**
     * Utility method to start creating [JsonObjectPath]s. This method is shorthand for [JsonPath.root]
     *
     * For example, using the following DataObject (as Json)
     * ```json
     * {
     *      "obj": {
     *          "list": [{
     *              "key": "some value"
     *          }]
     *      }
     * }
     * ```
     *
     * To reference the key "key" the [root] parameters would be:
     * ```kotlin
     * path("obj")
     *      .key("list")
     *      .index(0)
     *      .key("key")
     *
     * // or for Kotlin users only
     * path("obj")["list"][0]["key"]
     * ```
     *
     * @param root The root key required to reach a variable contained in nested [DataObject]s or [DataList]s
     */
    fun path(root: String): JsonObjectPath = JsonPath[root]
}
