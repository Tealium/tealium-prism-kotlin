package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataList.Builder
import com.tealium.prism.core.internal.misc.stringify
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONStringer
import org.json.JSONTokener
import java.lang.reflect.Array

/**
 * The [DataList] represents a list of restricted data types which are wrappable by
 * [DataItem], to ensure that all data passed to the SDK can be used correctly and without
 * unexpected behaviours when converting to Strings.
 *
 * Instances of [DataList] are immutable. When requiring updates, the [copy] method is
 * available to use, which is prepopulate a [Builder] with the existing set of [DataItem]s
 *
 * Indexing starts at 0 as with standard Java lists.
 *
 * This class will serialize to a JSON array - [[ ... ]] - when calling [toString].
 *
 * @see DataItem
 * @see DataObject
 */
class DataList private constructor(
    collection: List<DataItem>? = null,
    string: String? = null
) : Iterable<DataItem>, DataItemConvertible {

    private var _toString: String? = string
    private lateinit var _collection: List<DataItem>
    private var isLazy = collection == null && string != null

    init {
        collection?.let {
            _collection = it
        }
    }

    private val collection: List<DataItem>
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
     * Gets the [DataItem] at the given index.
     *
     * @param index The index of the list entry to retrieve; starts at 0
     * @return [DataItem] found at the given [index]; or null if index not found.
     */
    fun get(index: Int): DataItem? {
        return collection.getOrNull(index)
    }

    /**
     * Gets the [String] entry at the given [index] if the underlying value is a [String].
     *
     * No type coercion is attempted.
     *
     * @param index The index in the list to retrieve; else null
     */
    fun getString(index: Int): String? = get(index)?.getString()

    /**
     * Gets the [Int] entry at the given [index] if it exists and the value is an [Int], or a [Number]
     * that can be coerced to an [Int].
     *
     * If it is a [Double] or [Long] then the returned value will possibly lose accuracy as a
     * result.
     *
     * @param index The index in the list to retrieve
     * @return The [Int] stored at the given [index]; else null
     */
    fun getInt(index: Int): Int? = get(index)?.getInt()

    /**
     * Gets the [Long] entry at the given [index] if it exists and the value is a [Long], or a [Number]
     * that can be coerced to an [Long].
     *
     * If it is a [Double] then the returned value will possibly lose accuracy as a result.
     *
     * @param index The index in the list to retrieve
     * @return The [Long] stored at the given [index]; else null
     */
    fun getLong(index: Int): Long? = get(index)?.getLong()

    /**
     * Gets the [Double] entry at the given [index] if it exists and the value is a [Double], or a [Number]
     * that can be coerced to an [Double].
     *
     * @param index The index in the list to retrieve
     * @return The [Double] stored at the given [index]; else null
     */
    fun getDouble(index: Int): Double? = get(index)?.getDouble()

    /**
     * Gets the [Boolean] entry at the given [index] if it exists and the value can be correctly coerced
     * to an [Boolean].
     *
     * No type coercion is attempted.
     *
     * @param index The index in the list to retrieve
     * @return The [Boolean] stored at the given [index]; else null
     */
    fun getBoolean(index: Int): Boolean? = get(index)?.getBoolean()

    /**
     * Gets the [DataList] entry at the given [index] if it exists and the value is a [DataList].
     *
     * @param index The index in the list to retrieve
     * @return The [DataList] stored at the given [index]; else null
     */
    fun getDataList(index: Int): DataList? = get(index)?.getDataList()

    /**
     * Gets the [DataObject] entry at the given [index] if it exists and the value is a [DataObject].
     *
     * @param index The index in the list to retrieve
     * @return The [DataObject] stored at the given [index]; else null
     */
    fun getDataObject(index: Int): DataObject? = get(index)?.getDataObject()

    /**
     * Gets the [DataItem] at the given [index], and attempts to convert it into the type [T]
     * using the provided [DataItemConverter].
     *
     * @param index The key to use to lookup the item.
     * @param converter The [DataItemConverter] to use to recreate the object of type [T]
     * @return The reconstructed instance of [T]; else null
     */
    fun <T> get(index: Int, converter: DataItemConverter<T>): T? {
        return get(index)?.let { obj ->
            converter.convert(obj)
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
     * Checks for the existence of the given [value] in the [DataList]
     *
     * @return true if the [value] exists; else false
     */
    fun contains(value: DataItem): Boolean = collection.contains(value)

    override fun iterator(): Iterator<DataItem> {
        return collection.iterator()
    }

    /**
     * Copies the existing list into a new [Builder] instance that can be used to add/remove entries
     * and create a new instance of the [DataList]
     *
     * @param block Builder scope with which to add or remove entries in the existing [DataList]
     */
    inline fun copy(block: Builder.() -> Unit = {}): DataList {
        val builder = buildUpon()
        block.invoke(builder)
        return builder.build()
    }

    /**
     * Convenience method to create a new [Builder] containing all the values in this [DataList]
     */
    fun buildUpon(): Builder {
        return Builder(this)
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

    override fun asDataItem(): DataItem {
        return DataItem.convert(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataList

        if (collection != other.collection) return false

        return true
    }

    override fun hashCode(): Int {
        return collection.hashCode()
    }


    companion object {
        private const val EMPTY_LIST_STRING = "[]"

        /**
         * Constant value representing an empty [DataList].
         *
         * It's preferable to use this instance if an empty list is required, to save on unnecessary
         * object creation.
         */
        @JvmField
        val EMPTY_LIST: DataList =
            DataList(collection = emptyList(), string = EMPTY_LIST_STRING)

        /**
         * The default index used for List insertion on the [Builder]
         * The value of -1 will insert the item at the next possible index.
         */
        private const val DEFAULT_INDEX = -1

        /**
         * Eagerly parses the given [string] to attempt to return a [DataList].
         *
         * The [string] should be well formatted as a JSON array; e.g. [[ ... ]], but should really
         * only be used with the output of calling [toString] on a given [DataList]
         *
         * @param string The JSON formatted string representation of a [DataList]
         */
        @JvmStatic
        fun fromString(string: String): DataList? {
            if (string.isBlank()) return null

            return try {
                val parser = JSONTokener(string)

                // TODO - Should try and lazy load this.
                // i.e. keep as a string, only parse if `map` property is called
                val value = DataItem.convert(parser.nextValue())
                return value.getDataList()
            } catch (ex: JSONException) {
                null
            }
        }

        /**
         * Eagerly parses the given [string] to attempt to return a [List] of [DataItem]
         * entries.
         */
        private fun parseCollection(string: String?): List<DataItem>? {
            if (string.isNullOrBlank()) return null

            return try {
                val parser = JSONTokener(string)

                val jsonArray = parser.nextValue()
                if (jsonArray !is JSONArray) return null

                return jsonArray.map { value ->
                    DataItem.convert(value)
                }
            } catch (ex: JSONException) {
                null
            }
        }

        /**
         * Converts an array to the supported [DataList] type.
         */
        @JvmStatic
        @Throws(UnsupportedDataItemException::class)
        fun fromArray(array: Any): DataList {
            if (!array.javaClass.isArray) throw UnsupportedDataItemException("[array] was not an Array")

            val length = Array.getLength(array)
            val list = Builder()

            for (i in 0 until length) {
                val value = Array.get(array, i)
                list.add(DataItem.convert(value))
            }
            return list.build()
        }

        /**
         * Converts a JSONArray to the supported [DataList] type.
         */
        @JvmStatic
        fun fromJSONArray(jsonArray: JSONArray): DataList {
            val builder = Builder()

            jsonArray.forEach { value ->
                builder.add(DataItem.convert(value))
            }

            return builder.build()
        }

        /**
         * Converts a Collection to the supported [DataList] type.
         *
         * Any unsupported types contained in the provided [collection] will throw.
         */
        @JvmStatic
        @Throws(UnsupportedDataItemException::class)
        fun fromCollection(collection: Collection<*>): DataList {
            val list = Builder()
            for (obj in collection) {
                list.add(DataItem.convert(obj))
            }
            return list.build()
        }

        /**
         * Converts a [Collection] with [String] values into a [DataList]
         *
         * @param collection The collection data to put into the [DataList]
         */
        @JvmStatic
        fun fromStringCollection(collection: Collection<String?>): DataList =
            fromCollection(collection)

        /**
         * Converts a [Collection] with [Int] values into a [DataList]
         *
         * @param collection The collection data to put into the [DataList]
         */
        @JvmStatic
        fun fromIntCollection(collection: Collection<Int?>): DataList =
            fromCollection(collection)

        /**
         * Converts a [Collection] with [Long] values into a [DataList]
         *
         * @param collection The collection data to put into the [DataList]
         */
        @JvmStatic
        fun fromLongCollection(collection: Collection<Long?>): DataList =
            fromCollection(collection)

        /**
         * Converts a [Collection] with [Double] values into a [DataList]
         *
         * [Double.NaN] or Infinity values will be mapped to their toString counterpart
         *
         * @param collection The collection data to put into the [DataList]
         */
        @JvmStatic
        fun fromDoubleCollection(collection: Collection<Double?>): DataList =
            fromCollection(collection)

        /**
         * Converts a [Collection] with [Boolean] values into a [DataList]
         *
         * @param collection The collection data to put into the [DataList]
         */
        @JvmStatic
        fun fromBooleanCollection(collection: Collection<Boolean?>): DataList =
            fromCollection(collection)

        /**
         * Converts a [Collection] with [DataItem] values into a [DataList]
         *
         * @param collection The collection data to put into the [DataList]
         */
        @JvmStatic
        fun fromDataItemCollection(collection: Collection<DataItem?>): DataList =
            fromCollection(collection)

        /**
         * Converts a [Collection] with [DataList] values into a [DataList]
         *
         * @param collection The collection data to put into the [DataList]
         */
        @JvmStatic
        fun fromDataListCollection(collection: Collection<DataList?>): DataList =
            fromCollection(collection)

        /**
         * Converts a [Collection] with [DataObject] values into a [DataList]
         *
         * @param collection The collection data to put into the [DataList]
         */
        @JvmStatic
        fun fromDataObjectCollection(collection: Collection<DataObject?>): DataList =
            fromCollection(collection)

        /**
         * Unsafe method allowing a [DataList] to be instantiated from a stringified version of
         * its [collection].
         *
         * The [collection] will remain uninitialized until its first access, either directly or
         * indirectly via any of the "get" methods. This can be a performant way to create instances
         * from stringified versions where the overhead of parsing the value is not necessarily
         * required.
         *
         * @param string The stringified representation of this value.
         * @return A [DataItem] with the [value] currently unparsed which could lead to errors
         */
        internal fun lazy(string: String): DataList {
            if (EMPTY_LIST_STRING == string) return EMPTY_LIST

            return DataList(string = string)
        }

        /**
         * Creates a new [DataList] providing the [Builder] in a block for easy population
         *
         * @param block A function used to populate the [Builder] with the required entries
         * @return A new [DataList] containing all entries added to the [Builder]
         */
        @JvmStatic
        inline fun create(block: Builder.() -> Unit): DataList {
            val builder = Builder()
            block.invoke(builder)
            return builder.build()
        }
    }

    class Builder @JvmOverloads constructor(copy: DataList = EMPTY_LIST) {
        private val list: MutableList<DataItem> =
            mutableListOf(*copy.collection.toTypedArray())

        /**
         * Adds a [String] to the List.
         *
         * @param value The [String] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: String, index: Int = DEFAULT_INDEX) =
            add(DataItem.string(value), index)

        /**
         * Adds an [Int] to the List.
         *
         * @param value The [Int] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: Int, index: Int = DEFAULT_INDEX) =
            add(DataItem.int(value), index)

        /**
         * Adds a [Long] to the List.
         *
         * @param value The [Long] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: Long, index: Int = DEFAULT_INDEX) =
            add(DataItem.long(value), index)

        /**
         * Adds a [Double] to the List.
         *
         * @param value The [Double] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: Double, index: Int = DEFAULT_INDEX) =
            add(DataItem.double(value), index)

        /**
         * Adds a [Boolean] to the List.
         *
         * @param value The [Boolean] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: Boolean, index: Int = DEFAULT_INDEX) =
            add(DataItem.boolean(value), index)

        /**
         * Unsafe shortcut to put [any] object into the [DataList.Builder]. The [any] will attempt to be
         * converted to a supported type. If an unsupported type is found, then this method with throw.
         *
         * @param any The object instance to convert and add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        @Throws(UnsupportedDataItemException::class)
        fun addAny(any: Any?, index: Int = DEFAULT_INDEX) =
            add(DataItem.convert(any), index)

        /**
         * Unsafe shortcut to put [any] object into the [DataObject]. The [any] will attempt to be
         * converted to a supported type. If no supported type is available, then
         * [DataItem.NULL] will be returned.
         *
         * @param any The object instance to convert and add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun addAnyOrNull(any: Any?, index: Int = DEFAULT_INDEX) =
            add(DataItem.convert(any, DataItem.NULL), index)

        /**
         * Adds a [DataItem] to the List.
         *
         * @param value The [DataItem] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: DataItem, index: Int = DEFAULT_INDEX) = apply {
            try {
                list.add(index, value)
            } catch (e: IndexOutOfBoundsException) {
                list.add(value)
            }
        }

        /**
         * Adds a [DataItemConvertible] to the list. The [value] is first converted
         * into a [DataItem] using its implementation of [asDataItem].
         *
         * @param value The [DataItemConvertible] instance to add
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun add(value: DataItemConvertible, index: Int = DEFAULT_INDEX) =
            add(value.asDataItem(), index)

        /**
         * Adds a [DataItem.NULL] to the list.
         *
         * @param index Optional list [index] to insert the item at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun addNull(index: Int = DEFAULT_INDEX) =
            add(DataItem.NULL, index)

        /**
         * Adds all entries from the given [list] to this builder. If an index is provided then the
         * entries are inserted starting at that index.
         *
         * @param list The [DataList] of entries to add
         * @param index Optional list [index] to insert the items at
         * @return The current [Builder] instance.
         */
        @JvmOverloads
        fun addAll(list: DataList, index: Int = DEFAULT_INDEX) = apply {
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
            } catch (ignored: IndexOutOfBoundsException) {
            }
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
         * Creates the new [DataList] based on the entries provided to this [Builder]
         *
         * @return The newly created [DataList] containing all added values. If the [Builder] is
         * empty then the [EMPTY_LIST] is returned instead.
         */
        fun build(): DataList {
            if (list.isEmpty()) return EMPTY_LIST

            return DataList(list.toList())
        }
    }
}
