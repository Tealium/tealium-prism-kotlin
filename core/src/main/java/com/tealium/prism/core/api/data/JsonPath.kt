package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItemUtils.asDataItem
import com.tealium.prism.core.internal.data.JsonPathParser

/**
 * A [JsonPath] that can be applied to a JSON object to represent the path to a potentially nested value.
 * Nested items can be in both JSON objects and JSON arrays.
 *
 * To create a basic [JsonObjectPath] you can call [JsonPath.root] with a `String`.
 * ```kotlin
 * JsonPath.root("container")
 *
 * // or for Kotlin users only
 * JsonPath["container"]
 * ```
 *
 * To create a path like `container.array[0].property` you can use either [JsonPath.key] or [JsonPath.index]
 * for each subsequent path component.
 * ```kotlin
 * JsonPath.root("container")
 *      .key("array")
 *      .index(0)
 *      .key("property")
 *
 * // or for Kotlin users only
 * JsonPath["container"]["array"][0]["property"]
 * ```
 */
typealias JsonObjectPath = JsonPath<JsonPath.Component.Key>

/**
 * A [JsonPath] that can be applied to a JSON array to represent the path to a potentially nested value.
 * Nested items can be in both JSON objects and JSON arrays.
 *
 * To create a basic [JsonListPath] you can call [JsonPath.root] with an `Int`.
 * ```kotlin
 * JsonPath.root(0)
 *
 * // or for Kotlin users only
 * JsonPath[0]
 * ```
 *
 * To create a path like `[0].container.array[0].property` you can use a subscript for each path component:
 * ```kotlin
 * JsonPath.root(0)
 *      .key("container")
 *      .key("array")
 *      .index(0)
 *      .key("property")
 *
 * // or for Kotlin users only
 * JsonPath[0]["container"]["array"][0]["property"]
 * ```
 */
typealias JsonListPath = JsonPath<JsonPath.Component.Index>

/**
 * A structure representing the location of an item in a JSON object or JSON array, potentially nested
 * in other JSON objects and JSON arrays.
 *
 *
 * To create a basic [JsonPath] you can call the [JsonPath] factory methods [JsonPath.key] and [JsonPath.index]
 * depending on where you want to start the path from: the first one would start from a JSON object,
 * the second would start from a JSON array.
 *
 * ```kotlin
 * val objectPath = JsonPath.key("container")
 * val arrayPath = JsonPath.index(0)
 * ```
 *
 * To create a path like `container.array[0].property` you can use a subscript for each path component:
 * ```kotlin
 * JsonPath.key("container")
 *      .key("array")
 *      .index(0)
 *      .key("property")
 *
 * // or for Kotlin users only
 * JsonPath["container"]["array"][0]["property"]
 * ```
 *
 * Kotlin users can make use of the more expressive syntax through the [get] operator functions for
 * easy readability
 * ```kotlin
 * val objectPath = JsonPath["container"]
 * val arrayPath = JsonPath[0]
 * ```
 */
class JsonPath<TRoot : JsonPath.Component> private constructor(
    val firstComponent: TRoot,
    val components: List<Component>
): DataItemConvertible {

    override fun asDataItem(): DataItem =
        toString().asDataItem()

    /**
     * Defines a [Component] of a [JsonPath] for navigating through complex JSON objects/arrays
     *
     * [Key] is used to select an item from a JSON object, and [Index] is used to select an item from
     * a JSON array. Multiple [Component]s, as in a [JsonPath], can be used to describe a route through
     * a JSON structure to find a child item.
     */
    sealed class Component {

        /**
         * [Component.Key] defines a key [key] used for extracting items from a JSON object structure.
         */
        data class Key(val key: String) : Component() {

            val isDotNotationAllowed: Boolean =
                JsonPathParser.isDotNotationAllowed(this)

            private fun quote() =
                if (!key.contains('"') || key.contains('\'')) '"' else '\''

            override fun toString(): String {
                if (isDotNotationAllowed)
                    return key

                val quote = quote()
                val escaped = key.replace("\\", "\\\\")
                    .replace("$quote", "\\$quote")
                return "[$quote$escaped$quote]"
            }
        }

        /**
         * [Component.Index] defines an [index] used for extracting items from a JSON array structure.
         */
        data class Index(val index: Int) : Component() {
            override fun toString(): String {
                return "[$index]"
            }
        }
    }

    /**
     * Returns a new [JsonPath] with an additional [Component.Key] used to access a key in the next
     * JSON object.
     *
     * Additional [JsonPath] components can be appended through the returned [JsonPath] via the
     * [JsonPath.key] and [JsonPath.index] methods.
     *
     * @param key The key name used to extract the item in the json object
     * @return A new [JsonPath] instance with additional [Component.Key] added
     */
    fun key(key: String): JsonPath<TRoot> =
        JsonPath(firstComponent, components + Component.Key(key))

    /**
     * Returns a new [JsonPath] with an additional [Component.Index] used to access an index in the next
     * JSON array.
     *
     * Additional [JsonPath] components can be appended through the returned [JsonPath] via the
     * [JsonPath.key] and [JsonPath.index] methods.
     *
     * @param index The index used to extract the item in the json array
     * @return A new [JsonPath] instance with additional [Component.Index] added
     */
    fun index(index: Int): JsonPath<TRoot> =
        JsonPath(firstComponent, components + Component.Index(index))

    override fun toString(): String {
        val builder = StringBuilder()
        var prev: Component? = null

        fun append(c: Component) {
            if (prev != null && c is Component.Key
                && c.isDotNotationAllowed
            ) {
                builder.append(".")
            }
            builder.append(c.toString())
            prev = c
        }

        append(firstComponent)
        components.forEach(::append)
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JsonPath<*>

        if (firstComponent != other.firstComponent) return false
        if (components != other.components) return false

        return true
    }

    override fun hashCode(): Int {
        var result = firstComponent.hashCode()
        result = 31 * result + components.hashCode()
        return result
    }

    companion object {

        /**
         * Parses a string into a [JsonPath].
         *
         * The string to pass needs to conform to a specific format.
         * - It can be a dot (`.`) separated list of alphanumeric characters and/or underscores.
         *      - Each component of a list built this way represents one level of a JSON object.
         *      - The last one can represent any type of JSON value.
         * - Square brackets (`[]`)  could be used instead of the dot notation, to separate one (or
         * each) of the components. Inside these brackets you can put:
         *      - An integer, to represent an element into a JSON array.
         *      - A single (`'`) or double  (`"`)  quoted string to represent an element into a JSON object.
         *      - Inside of the quoted string any character is valid, but the character used to quote the string (`'` or `"`)
         *     needs to be escaped with a backslash (`\`), and same for backslashes, which need to be escaped with an additional backslash.
         *
         * Examples of valid strings:
         * - `property`
         * - `container.property`
         * - `container["property"]`
         * - `container['property']`
         * - `container["john's party"]`
         * - `container['john\'s party']`
         * - `container["\"nested quote\""]`
         * - `container["escaped\\backslash"]`
         * - `array[123]`
         *      - which is different from `array["123"]`, although both are valid.
         *      Difference is that the quoted version treats the `array` property as an object and
         *      looks for a nested "123" by string instead of the item at index 123 in an array.
         * - `array[123].property`
         * - `some_property`
         * - `container.some_property`
         * - `container["some.property"]`
         *      - which is different from `container.some.property`, although both are valid
         * - `container["some@property"]`
         *      - which would be wrong without the quoted brackets: `container.some@property`)
         * - `["array"][123]["property"]`
         * - `[1].array[2]`
         * - `[1][2][3]`
         *
         * Examples of invalid strings:
         * - `"property"`: invalid character (`"`)
         * - `container-property`: invalid character (`-`)
         * - `container[property]`: missing quotes (`"`) in brackets
         * - `container.["property"]`: invalid character (`.`) before the brackets
         * - `container["property']`: closing quote (`'`) different from opening one (`"`) in brackets
         * - `container['john's party']`: unescaped quote (`'`)
         * - `container[""nested quote""]`: unescaped quotes (`"`)
         * - `container["unescaped\backslash"]`: unescaped backslash (`\`)
         * - `array[12 3]`: invalid number with whitespace ( ) inside index brackets
         * - `container@property`: invalid character (`@`)
         *
         * @param path: The [String] that will be parsed.
         * @return A [JsonPath], if the parsing succeeded.
         * @throws JsonPathParseException if the parsing failed.
         */
        @JvmStatic
        @Throws(JsonPathParseException::class)
        fun parse(path: String): JsonPath<*> {
            val components = JsonPathParser(path).parseComponents()
            val first = components.first()
            val remaining = components.drop(1)

            return when (first) {
                is Component.Key -> JsonPath(first, remaining)
                is Component.Index -> JsonPath(first, remaining)
            }
        }

        /**
         * Attempts to parse the given [path] as a [JsonPath], where the root component is applicable
         * to a JSON object and therefore extractable via a key.
         *
         * Calling this method with a [path] _not_ starting with an object key, e.g. `[1].key`, is
         * an error, and will throw [JsonPathParseException].
         *
         * See [parse] for explanation of accepted [path] format
         *
         * @param path The string representing the [JsonPath] to be parsed
         * @throws JsonPathParseException when the [path] is syntactically invalid, or the root component
         *      of the path is not an object key
         *
         * @see JsonPath
         * @see parse
         */
        @JvmStatic
        @Throws(JsonPathParseException::class)
        fun parseJsonObjectPath(path: String): JsonObjectPath =
            parse(path).requireObjectPath()

        /**
         * Attempts to parse the given [path] as a [JsonPath], where the root component is applicable
         * to a JSON array and therefore extractable via an index.
         *
         * Calling this method with a [path] _not_ starting with an array index, e.g. `object.key`, is
         * an error, and will throw [JsonPathParseException].
         *
         * See [parse] for explanation of accepted [path] format
         *
         * @param path The string representing the [JsonPath] to be parsed
         * @throws JsonPathParseException when the [path] is syntactically invalid, or the root component
         *      of the path is not an array index
         *
         * @see JsonPath
         * @see parse
         */
        @JvmStatic
        @Throws(JsonPathParseException::class)
        fun parseJsonListPath(path: String): JsonListPath =
            parse(path).requireListPath()

        /**
         * Starts configuring a [JsonPath] that should be applied to a JSON object.
         *
         * Additional [JsonPath] components can be appended through the returned [JsonPath] via the
         * [JsonPath.key] and [JsonPath.index] methods.
         *
         * @param key The key name used to extract the first item in the json object
         * @return The [JsonPath] used to continue configuring this [JsonPath]
         */
        @JvmStatic
        fun root(key: String): JsonPath<Component.Key> =
            JsonPath(Component.Key(key), emptyList())

        /**
         * Starts configuring a [JsonPath] that should be applied to a JSON array.
         *
         * Additional [JsonPath] components can be appended through the returned [JsonPath] via the
         * [JsonPath.key] and [JsonPath.index] methods.
         *
         * @param index The index used to extract the first item in the json array
         * @return The [JsonPath] used to continue configuring this [JsonPath]
         */
        @JvmStatic
        fun root(index: Int): JsonPath<Component.Index> =
            JsonPath(Component.Index(index), emptyList())

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
         * @param name The key name used to extract the first item in the json object
         * @return a new [JsonPath] with additional [Component.Key] added
         */
        @JvmSynthetic
        operator fun get(name: String): JsonPath<Component.Key> =
            root(name)

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
         * @param index The index used to extract the first item in the json array
         * @return a new [JsonPath] with additional [Component.Index] added
         */
        @JvmSynthetic
        operator fun get(index: Int): JsonPath<Component.Index> =
            root(index)
    }

    /**
     * [DataItemConverter] to create [JsonPath] instances from their [String] representations.
     *
     * In the event of any parse exceptions it will return `null`
     */
    object Converter: DataItemConverter<JsonPath<*>> {
        override fun convert(dataItem: DataItem): JsonPath<*>? {
            runCatching {
                val jsonPath = dataItem.getString()
                    ?: return null
                return parse(jsonPath)
            }

            return null
        }
    }
}