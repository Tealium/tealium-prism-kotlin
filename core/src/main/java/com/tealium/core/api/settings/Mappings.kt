package com.tealium.core.api.settings

import com.tealium.core.api.settings.Mappings.ConstantOptions
import com.tealium.core.api.settings.Mappings.VariableOptions

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
 * from("source", listOf("path", "to"), "destination", listOf("path", "to"))
 *
 * ```
 *
 * If preferred, there is `vararg` utility method [variable] to supply the path in the logical order.
 * ```kotlin
 * from(variable("path", "to", "source"), variable("path", "to", "destination"))
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
        fun ifValueEquals(key: String, value: String): ConstantOptions =
            ifValueEquals(VariableAccessor(key), value)

        /**
         * Sets an optional basic condition that the value at the given mapping [key] needs to match
         * in order for this mapping to take place, where the [key] may be found in a configured level
         * of nesting according to the [keyPath]
         *
         * @param key The [key] to take the value from when comparing against the expected [value]
         * @param keyPath The ordered set of keys required to access the [key] in a nested object
         * @param value The target value that the source key should contain.
         */
        fun ifValueEquals(key: String, keyPath: List<String>, value: String): ConstantOptions =
            ifValueEquals(VariableAccessor(key, keyPath), value)

        /**
         * Sets an optional basic condition that the value at the given mapping [key] needs to match
         * in order for this mapping to take place, where the [key] may be found in a configured level
         * of nesting according to the [VariableAccessor.path]
         *
         * @param key The [key] to take the value from when comparing against the expected [value]
         * @param value The target value that the source key should contain.
         */
        fun ifValueEquals(key: VariableAccessor, value: String): ConstantOptions
    }

    /**
     * Adds a mapping where the [key] and [destination] are in the top level of the payload.
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and place it in the mapped payload at the [destination] key
     * @param destination The [destination] key to store the mapped value
     */
    fun from(key: String, destination: String): VariableOptions =
        from(VariableAccessor(key), VariableAccessor(destination))

    /**
     * Adds a mapping where the [destination] is to be in the top level of the
     * mapped payload, but the source [key] is defined at some nested object as defined by [keyPath].
     *
     * The [keyPath] can be used to specify that the destination value will be taken from an object
     * that is nested, where the [keyPath] specifies the path to get to the [key]
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and place it in the mapped payload at the [destination] key
     * @param keyPath The ordered set of keys required to access the [key] in a nested object
     * @param destination The [destination] key to store the mapped value
     */
    fun from(key: String, keyPath: List<String>, destination: String): VariableOptions =
        from(VariableAccessor(key, keyPath), VariableAccessor(destination))

    /**
     * Adds a mapping where the [key] is to be found in the top level of the input
     * payload, but the [destination] key is to be defined at some nested object as defined by [destinationPath].
     *
     * The [destinationPath] can be used to specify that the destination value will be stored in an object
     * that is nested, where the [destinationPath] specifies the ordered path of keys
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and place it in the mapped payload at the [destination] key
     * @param destination The [destination] key to store the mapped value
     * @param destinationPath The ordered set of keys required to place the [destination] value in a nested object
     */
    fun from(key: String, destination: String, destinationPath: List<String>): VariableOptions =
        from(VariableAccessor(key), VariableAccessor(destination, destinationPath))

    /**
     * Adds a mapping where both the [key] and [destination] can be located/stored at some
     * configured level of nesting as defined by [keyPath] and [destinationPath] respectively.
     *
     * The [keyPath] can be used to specify that the destination value will be taken from an object
     * that is nested, where the [keyPath] specifies the path to get to the [key]
     *
     * The [destinationPath] can be used to specify that the destination value will be stored in an object
     * that is nested, where the [destinationPath] specifies the ordered path of keys
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and place it in the mapped payload at the [destination] key
     * @param keyPath The ordered set of keys required to access the [key] in a nested object
     * @param destination The [destination] key to store the mapped value
     * @param destinationPath The ordered set of keys required to place the [destination] value in a nested object
     */
    fun from(
        key: String,
        keyPath: List<String>,
        destination: String,
        destinationPath: List<String>
    ): VariableOptions = from(VariableAccessor(key, keyPath), VariableAccessor(destination, destinationPath))

    /**
     * Adds a mapping where both the [key] and [destination] can be located/stored at some
     * configured level of nesting as defined by [VariableAccessor.path] for each input.
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and place it in the mapped payload at the [destination] key
     * @param destination The [destination] key to store the mapped value
     */
    fun from(key: VariableAccessor, destination: VariableAccessor): VariableOptions

    /**
     * Adds a mapping where the [key] is both the source and destination of the mapping.
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and also the destination to place it in the mapped payload
     */
    fun keep(key: String): VariableOptions =
        from(key, key)

    /**
     * Adds a mapping where the [key]/[keyPath] is both the source and destination of the mapping.
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and also the destination to place it in the mapped payload
     * @param keyPath The ordered set of keys required to access the [key] in a nested object
     */
    fun keep(key: String, keyPath: List<String>): VariableOptions =
        keep(VariableAccessor(key, keyPath))

    /**
     * Adds a mapping where the possibly nested [key] is both the source and destination of the mapping.
     *
     * Returns a [VariableOptions] with which to configure some options if required.
     *
     * @param key The [key] to take the value from and also the destination to place it in the mapped payload
     */
    fun keep(key: VariableAccessor): VariableOptions =
        from(key, key)

    /**
     * Adds a mapping where the value to map is given by the constant [value] and will be mapped to
     * the given [destination]
     *
     * Returns a [ConstantOptions] with which to configure some options if required.
     *
     * @param value The constant value to map to the given [destination]
     * @param destination The [destination] key to store the mapped value
     */
    fun constant(value: String, destination: String): ConstantOptions =
        constant(value, variable(destination))

    /**
     * Adds a mapping where the value to map is given by the constant [value] and will be mapped to
     * the given [destination] located/stored at some configured level of nesting as defined [destinationPath].
     *
     * Returns a [ConstantOptions] with which to configure some options if required.
     *
     * @param value The constant value to map to the given [destination]
     * @param destination The [destination] key to store the mapped value
     */
    fun constant(value: String, destination: String, destinationPath: List<String>): ConstantOptions =
        constant(value, VariableAccessor(destination, destinationPath))

    /**
     * Adds a mapping where the value to map is given by the constant [value] and will be mapped to
     * the given [destination] located/stored at some configured level of nesting as defined by
     * the [VariableAccessor.path].
     *
     * Returns a [ConstantOptions] with which to configure some options if required.
     *
     * @param value The constant value to map to the given [destination]
     * @param destination The [destination] key to store the mapped value
     */
    fun constant(value: String, destination: VariableAccessor): ConstantOptions

    /**
     * Utility method to create [VariableAccessor] from path components. The order of the components
     * is important, and should start with the initial key, ending in the key from the sub-object of interest.
     *
     * For example, using the following DataObject (as Json)
     * ```json
     * {
     *      "obj": {
     *          "sub": {
     *              "key": "some value"
     *          }
     *      }
     * }
     * ```
     *
     * To reference the key "key" the [path] parameters would be:
     * ```kotlin
     * variable("obj", "sub", "key")
     * ```
     *
     * The [path] must be at least one entry, assumed to be the key in the main [DataObject]. It is
     * not allowed to be empty, and will throw if it is.
     *
     * @param path The ordered path of keys required to reach a variable contained in nested [DataObject]s
     */
    @Throws(IllegalArgumentException::class)
    fun variable(vararg path: String): VariableAccessor {
        if (path.isEmpty()) throw IllegalArgumentException("[path] variable cannot be empty")

        val variable = path[path.size - 1]
        val variablePath = path.dropLast(1)
            .ifEmpty { null }

        return VariableAccessor(variable, variablePath)
    }
}
