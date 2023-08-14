package com.tealium.core.api.data

import com.tealium.core.api.Deserializer
import com.tealium.core.internal.stringify
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONStringer
import org.json.JSONTokener
import java.lang.reflect.Array

/**
 * The [TealiumList] represents a list of restricted data types which are wrappable by
 * [TealiumValue], to ensure that all data passed to the SDK can be used correctly and without
 * unexpected behaviours when converting to Strings.
 *
 * Instances of [TealiumList] are immutable. When requiring updates, the [copy] method is
 * available to use, which is prepopulate a [Builder] with the existing set of [TealiumValue]s
 *
 * Indexing starts at 0 as with standard Java lists.
 *
 * This class will serialize to a JSON array - [[ ... ]] - when calling [toString].
 *
 * @see TealiumValue
 * @see TealiumBundle
 */
class TealiumList private constructor(
    collection: List<TealiumValue>? = null,
    string: String? = null
) : Iterable<TealiumValue>, TealiumSerializable {

    private var _toString: String? = string
    private lateinit var _collection: List<TealiumValue>
    private var isLazy = collection == null && string != null

    init {
        collection?.let {
            _collection = it
        }
    }

    private val collection: List<TealiumValue>
        get() {
            if (!this::_collection.isInitialized && isLazy) {
                parseCollection(_toString).also {

                    isLazy = false
                    _collection = it ?: let {
                        _toString = EMPTY_LIST_STRING
                        emptyList()
                    }
                }
            }

            return _collection
        }

    /**
     * Gets the [TealiumValue] at the given index.
     *
     * @param index The index of the list entry to retrieve; starts at 0
     * @return [TealiumValue] found at the given [index]; or null if index not found.
     */
    fun get(index: Int): TealiumValue? {
        return collection.getOrNull(index)
    }

    /**
     * Gets the [String] entry at the given [index]. If the underlying value is not a [String] then
     * it will be coerced.
     *
     * @param index The index in the list to retrieve
     */
    fun getString(index: Int): String? = get(index)?.getString()

    /**
     * Gets the [Int] entry at the given [index] if it exists and the value can be correctly coerced
     * to an [Int].
     *
     * @param index The index in the list to retrieve
     * @return The [Int] stored at the given [index]; else null
     */
    fun getInt(index: Int): Int? = get(index)?.getInt()

    /**
     * Gets the [Long] entry at the given [index] if it exists and the value can be correctly coerced
     * to an [Long].
     *
     * @param index The index in the list to retrieve
     * @return The [Long] stored at the given [index]; else null
     */
    fun getLong(index: Int): Long? = get(index)?.getLong()

    /**
     * Gets the [Double] entry at the given [index] if it exists and the value can be correctly coerced
     * to an [Double].
     *
     * @param index The index in the list to retrieve
     * @return The [Double] stored at the given [index]; else null
     */
    fun getDouble(index: Int): Double? = get(index)?.getDouble()

    /**
     * Gets the [Boolean] entry at the given [index] if it exists and the value can be correctly coerced
     * to an [Boolean].
     *
     * @param index The index in the list to retrieve
     * @return The [Boolean] stored at the given [index]; else null
     */
    fun getBoolean(index: Int): Boolean? = get(index)?.getBoolean()

    /**
     * Gets the [TealiumList] entry at the given [index] if it exists and the value can be correctly coerced
     * to an [TealiumList].
     *
     * @param index The index in the list to retrieve
     * @return The [TealiumList] stored at the given [index]; else null
     */
    fun getList(index: Int): TealiumList? = get(index)?.getList()

    /**
     * Gets the [TealiumBundle] entry at the given [index] if it exists and the value can be correctly coerced
     * to an [TealiumBundle].
     *
     * @param index The index in the list to retrieve
     * @return The [TealiumBundle] stored at the given [index]; else null
     */
    fun getBundle(index: Int): TealiumBundle? = get(index)?.getBundle()

    /**
     * Gets the [TealiumValue] at the given [index], and attempts to deserialize it into the type [T]
     * using the provided [Deserializer].
     *
     * @param key The key to use to lookup the item.
     * @param deserializer The deserializer to use to recreate the object of type [T]
     * @return The reconstructed instance of [T]; else null
     */
    fun <T> get(index: Int, deserializer: Deserializer<TealiumValue, T>): T? {
        return get(index)?.let { obj ->
            deserializer.deserialize(obj)
        }
    }

    /**
     * Returns the number of top level entries stored in this list.
     *
     * @return The number of entries in this list.
     */
    val size: Int
        get() = collection.size

    /**
     * Checks for the existence of the given [value] in the [TealiumList]
     *
     * @return true if the [value] exists; else false
     */
    fun contains(value: TealiumValue): Boolean = collection.contains(value)

    override fun iterator(): Iterator<TealiumValue> {
        return collection.iterator()
    }

    /**
     * Copies the existing list into a new [Builder] instance that can be used to add/remove entries
     * and create a new instance of the [TealiumList]
     *
     * @param block Builder scope with which to add or remove entries in the existing [TealiumList]
     */
    fun copy(block: Builder.() -> Unit = {}): TealiumList {
        val builder = Builder(this)
        block.invoke(builder)
        return builder.getList()
    }

    override fun toString(): String {
        return _toString ?: run {
            val stringer = JSONStringer()
            stringify(stringer)
            stringer.toString()
        }.also {
            _toString = it
        }
    }

    override fun asTealiumValue(): TealiumValue {
        return TealiumValue.convert(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TealiumList

        if (collection != other.collection) return false

        return true
    }

    override fun hashCode(): Int {
        return collection.hashCode()
    }


    companion object {
        private const val EMPTY_LIST_STRING = "[]"

        /**
         * Constant value representing an empty [TealiumList].
         *
         * It's preferable to use this instance if an empty list is required, to save on unnecessary
         * object creation.
         */
        @JvmField
        val EMPTY_LIST: TealiumList = TealiumList(collection = emptyList(), string = EMPTY_LIST_STRING)

        /**
         * The default index used for List insertion on the [Builder]
         * The value of -1 will insert the item at the next possible index.
         */
        private const val DEFAULT_INDEX = -1

        /**
         * Eagerly parses the given [string] to attempt to return a [TealiumList].
         *
         * The [string] should be well formatted as a JSON array; e.g. [[ ... ]], but should really
         * only be used with the output of calling [toString] on a given [TealiumList]
         *
         * @param string The JSON formatted string representation of a [TealiumList]
         */
        @JvmStatic
        fun fromString(string: String): TealiumList? {
            if (string.isBlank()) return null

            return try {
                val parser = JSONTokener(string)

                // TODO - Should try and lazy load this.
                // i.e. keep as a string, only parse if `map` property is called
                val value = TealiumValue.convert(parser.nextValue())
                return value.getList()
            } catch (ex: JSONException) {
                null
            }
        }

        /**
         * Eagerly parses the given [string] to attempt to return a [List] of [TealiumValue]
         * entries.
         */
        private fun parseCollection(string: String?): List<TealiumValue>? {
            if (string.isNullOrBlank()) return null

            return try {
                val parser = JSONTokener(string)

                val jsonArray = parser.nextValue()
                if (jsonArray !is JSONArray) return null

                return jsonArray.map { value ->
                    TealiumValue.convert(value)
                }
            } catch (ex: JSONException) {
                null
            }
        }

        /**
         * Converts an array to the supported [TealiumList] type.
         */
        @JvmStatic
         fun fromArray(array: Any): TealiumList? {
            if (!array.javaClass.isArray) return null

            val length = Array.getLength(array)
            val list = Builder()

            for (i in 0 until length) {
                val value = Array.get(array, i)
                if (value != null) {
                    list.add(TealiumValue.convert(value))
                }
            }
            return list.getList()
        }

        /**
         * Converts a JSONArray to the supported [TealiumList] type.
         */
        @JvmStatic
        fun fromJSONArray(jsonArray: JSONArray): TealiumList {
            val builder = Builder()

            jsonArray.forEach { value ->
                builder.add(TealiumValue.convert(value))
            }

            return builder.getList()
        }

        /**
         * Converts a Collection to the supported [TealiumList] type.
         */
        @JvmStatic
        fun fromCollection(collection: Collection<*>): TealiumList {
            val list = Builder()
            for (obj in collection) {
                if (obj != null) {
                    list.add(TealiumValue.convert(obj))
                }
            }
            return list.getList()
        }

        /**
         * Unsafe method allowing a [TealiumList] to be instantiated from a stringified version of
         * its [collection].
         *
         * The [collection] will remain uninitialized until its first access, either directly or
         * indirectly via any of the "get" methods. This can be a performant way to create instances
         * from stringified versions where the overhead of parsing the value is not necessarily
         * required.
         *
         * @param string The stringified representation of this value.
         * @return A TealiumValue with the [value] currently unparsed which could lead to errors
         */
        internal fun lazy(string: String): TealiumList {
            if (EMPTY_LIST_STRING == string) return EMPTY_LIST

            return TealiumList(string = string)
        }

        /**
         * Creates a new [TealiumList] providing the [Builder] in a block for easy population
         *
         * @param block A function used to populate the [Builder] with the required entries
         * @return A new [TealiumList] containing all entries added to the [Builder]
         */
        @JvmStatic
        fun create(block: Builder.() -> Unit): TealiumList {
            val builder = Builder()
            block.invoke(builder)
            return builder.getList()
        }
    }

    class Builder @JvmOverloads constructor(copy: TealiumList = EMPTY_LIST) {
        private val list: MutableList<TealiumValue> =
            mutableListOf(*copy.collection.toTypedArray())

        /**
         * Adds a [String] to the List.
         *
         * @param value The [String] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: String, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        /**
         * Adds an [Int] to the List.
         *
         * @param value The [Int] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: Int, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        /**
         * Adds a [Long] to the List.
         *
         * @param value The [Long] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: Long, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        /**
         * Adds a [Double] to the List.
         *
         * @param value The [Double] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: Double, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        /**
         * Adds a [Boolean] to the List.
         *
         * @param value The [Boolean] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: Boolean, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        /**
         * Unsafe shortcut to put [any] object into the bundle. The [any] will attempt to be
         * converted to a supported type. If no supported type is available, then
         * [TealiumValue.NULL] will be returned.
         *
         * @param any The object instance to convert and add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun addAny(any: Any, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(any), index)
        }

        /**
         * Adds a [TealiumValue] to the List.
         *
         * @param value The [TealiumValue] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: TealiumValue, index: Int = DEFAULT_INDEX) = apply {
            try {
                list.add(index, value)
            } catch (e: IndexOutOfBoundsException) {
                list.add(value)
            }
        }

        /**
         * Adds a [TealiumSerializable] to the list. The [serializable] is first converted
         * into a [TealiumValue] using its implementation of [asTealiumValue].
         *
         * @param value The [TealiumSerializable] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: TealiumSerializable, index: Int = DEFAULT_INDEX) = apply {
            add(value.asTealiumValue(), index)
        }

        /**
         * Adds all entries from the given [list] to this builder. If an index is provided then the
         * entries are inserted starting at that index.
         *
         * @param list The [TealiumList] of entries to add
         * @param index Optional list [index] to insert the items at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun addAll(list: TealiumList, index: Int = DEFAULT_INDEX) = apply {
            if (index >= 0)
                this.list.addAll(index, list.collection)
            else
                this.list.addAll(list.collection)
        }

        /**
         * Removes the item at the give [index].
         *
         * [IndexOutOfBoundsException]s are ignored.
         *
         * @return The current [Builder] instance.
         */
        fun remove(index: Int) = apply {
            try {
                list.removeAt(index)
            } catch (ignored: IndexOutOfBoundsException) { }
        }

        /**
         * Removes all entries from the current [Builder]
         *
         * @return The current [Builder] instance.
         */
        fun clear() = apply {
            list.clear()
        }

        /**
         * Creates the new [TealiumList] based on the entries provided to this [Builder]
         *
         * @return The newly created [TealiumList] containing all added values. If the [Builder] is
         * empty then the [EMPTY_LIST] is returned instead.
         */
        fun getList(): TealiumList {
            if (list.isEmpty()) return EMPTY_LIST

            return TealiumList(list.toList())
        }
    }
}