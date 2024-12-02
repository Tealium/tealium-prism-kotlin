package com.tealium.core.api.data

import com.tealium.core.api.data.DataObject.Builder
import com.tealium.core.internal.misc.stringify
import org.json.*

/**
 * The [DataObject] represents a map of restricted data types which are wrappable by
 * [DataItem], to ensure that all data passed to the SDK can be used correctly and without
 * unexpected behaviours when converting to Strings.
 *
 * Instances of [DataObject] are immutable. When requiring updates, the [copy] method is
 * available to use, which is prepopulate a [Builder] with the existing set of [DataItem]s
 *
 * This class will serialize to a JSON object - { ... } - when calling [toString].
 *
 * @see DataItem
 * @see DataList
 */
class DataObject private constructor(
    data: Map<String, DataItem>? = null,
    string: String? = null
) : Iterable<Map.Entry<String, DataItem>>, DataItemConvertible, DataItemExtractor {

    private lateinit var _data: Map<String, DataItem>
    private var _toString: String? = string
    private var isLazy = data == null && string != null

    init {
        data?.let {
            _data = it
        }
    }

    private val map: Map<String, DataItem>
        get() {
            if (!this::_data.isInitialized && isLazy) {
                parseData(_toString).also {
                    isLazy = false
                    _data = it ?: let {
                        // _toString was not parsable
                        _toString = EMPTY_OBJECT_STRING
                        emptyMap()
                    }
                }
            }

            return _data
        }

    /**
     * Gets the [DataItem] stored at the given [key] if it exists regardless of it's underlying type.
     *
     * @param key The key to use to lookup the item.
     * @return The [DataItem] stored at the given [key]; else null
     */
    override fun get(key: String): DataItem? {
        return map[key]
    }
    
    /**
     * Returns all entries stored in the [DataObject]
     *
     * @return All entries in the [DataObject]
     */
    fun getAll(): Map<String, DataItem> {
        return map.toMap()
    }

    /**
     * Returns the number of top level entries stored in this [DataObject].
     *
     * @return The number of entries in this [DataObject].
     */
    val size: Int
        get() = map.size

    /**
     * Returns the [Iterator] in order to iterate over the collection.
     *
     * @return The iterator which can be used to iterate over all entries in the collection
     */
    override fun iterator(): Iterator<Map.Entry<String, DataItem>> {
        return map.iterator()
    }

    override fun toString(): String {
        return _toString ?: run {
            val stringer = JSONStringer()
            stringify(stringer)
            stringer.toString()
        }.also { _toString = it }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataObject

        if (map != other.map) return false

        return true
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }

    // Possibly for KTX separate library
    inline fun copy(block: Builder.() -> Unit = {}): DataObject {
        val builder = buildUpon()
        block.invoke(builder)
        return builder.build()
    }

    /**
     * Convenience method to create a new [Builder] containing all the values in this [DataObject]
     */
    fun buildUpon(): Builder {
        return Builder(this)
    }

    override fun asDataItem(): DataItem {
        return DataItem.convert(this)
    }

    companion object {
        private const val EMPTY_OBJECT_STRING = "{}"

        /**
         * Constant value representing an empty [DataObject].
         *
         * It's preferable to use this instance if an empty [DataObject] is required, to save on
         * unnecessary object creation.
         */
        @JvmField
        val EMPTY_OBJECT = DataObject(data = emptyMap(), string = EMPTY_OBJECT_STRING)

        /**
         * Converts a [String] representation of a [DataObject], into an actual [DataObject] if
         * possible.
         * This method eagerly parses the [string] value.
         *
         * @return [DataObject] of the given string; else null
         */
        @JvmStatic
        fun fromString(string: String): DataObject? {
            if (string.isBlank()) return null

            return try {
                val parser = JSONTokener(string)

                // TODO - Should try and lazy load this.
                // i.e. keep as a string, only parse if `map` property is called
                val value = DataItem.convert(parser.nextValue())
                return value.getDataObject()
            } catch (ex: JSONException) {
                null
            }
        }

        private fun parseData(string: String?): Map<String, DataItem>? {
            if (string.isNullOrBlank()) return null

            return try {
                val parser = JSONTokener(string)

                val jsonObject = parser.nextValue()
                if (jsonObject !is JSONObject) return null

                return jsonObject.mapValues { value ->
                    DataItem.convert(value)
                }
            } catch (ex: JSONException) {
                null
            }
        }

        /**
         * Converts a Map to the supported [DataObject] type.
         *
         * Keys should be [String]s.
         * Unsupported values are replaced with [DataItem.NULL]
         *
         * @param map The map of key value pairs
         * @return [DataObject] containing all key value pairs as wrapped by [DataItem]
         */
        @JvmStatic
        @Throws(UnsupportedDataItemException::class)
        fun fromMap(map: Map<*, *>): DataObject {
            val builder = Builder()
            for ((key, value) in map) {
                if (key !is String) throw UnsupportedDataItemException("Only String keys are supported.")

                builder.put(key, DataItem.convert(value))
            }
            return builder.build()
        }

        /**
         * Converts a [Map] with [String] keys and values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfStrings(map: Map<String, String?>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [Int] values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfInts(map: Map<String, Int?>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [Long] values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfLongs(map: Map<String, Long?>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [Double] values into a [DataObject]
         *
         * [Double.NaN] or Infinity values will be mapped to their toString counterpart
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfDoubles(map: Map<String, Double?>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [Boolean] values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfBooleans(map: Map<String, Boolean?>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [DataItem] values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfDataItems(map: Map<String, DataItem?>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [DataList] values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfDataLists(map: Map<String, DataList?>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [DataObject] values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfDataObjects(map: Map<String, DataObject?>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [String] collection values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfStringCollections(map: Map<String, Collection<String?>>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [Int] collection values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfIntCollections(map: Map<String, Collection<Int?>>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [Number] values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfLongCollections(map: Map<String, Collection<Long?>>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [Number] values into a [DataObject]
         *
         * [Double.NaN] or Infinity values will be mapped to their toString counterpart
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfDoubleCollections(map: Map<String, Collection<Double?>>): DataObject =
            fromMap(map)

        /**
         * Converts a [Map] with [String] keys and [Number] values into a [DataObject]
         *
         * @param map The key-value data to put into the [DataObject]
         */
        @JvmStatic
        fun fromMapOfBooleanCollections(map: Map<String, Collection<Boolean?>>): DataObject =
            fromMap(map)

        /**
         * Converts a [JSONObject] to the supported [DataObject] type.
         *
         * @param jsonObject The [JSONObject] containing the key value pairs
         * @return [DataObject] containing all key value pairs as wrapped by [DataItem]
         */
        @JvmStatic
        fun fromJSONObject(jsonObject: JSONObject): DataObject {
            val builder = Builder()
            for (key in jsonObject.keys()) {
                jsonObject.opt(key)?.let { value ->
                    builder.put(key, DataItem.convert(value))
                }
            }
            return builder.build()
        }

        /**
         * Unsafe method allowing a [DataItem] to be instantiated from a stringified version of
         * its [value].
         *
         * The [value] will remain uninitialized until its first access, either directly or
         * indirectly via any of the "get" methods. This can be a performant way to create instances
         * from stringified versions where the overhead of parsing the value is not necessarily
         * required.
         *
         * @param string The stringified representation of this value.
         * @return A [DataItem] with the [value] currently unparsed which could lead to errors
         */
        internal fun lazy(string: String): DataObject {
            if (EMPTY_OBJECT_STRING == string) return EMPTY_OBJECT

            return DataObject(string = string)
        }


        /**
         * Creates a new [DataObject] providing the [Builder] in a block for easy population
         *
         * @param block A function used to populate the [Builder] with the required entries
         * @return A new [DataObject] containing all entries added to the [Builder]
         */
        @JvmStatic // TODO - could be moved to a separate KTX project possibly.
        inline fun create(block: Builder.() -> Unit): DataObject {
            val builder = Builder()
            block.invoke(builder)
            return builder.build()
        }
    }

    class Builder @JvmOverloads constructor(copy: DataObject = EMPTY_OBJECT) {
        private val data: MutableMap<String, DataItem> =
            mutableMapOf<String, DataItem>().apply {
                putAll(copy.getAll())
            }

        /**
         * Adds the provided [value] into this [DataObject.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: String) =
            put(key, DataItem.string(value))

        /**
         * Adds the provided [value] into this [DataObject.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Int) =
            put(key, DataItem.int(value))

        /**
         * Adds the provided [value] into this [DataObject.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Long) =
            put(key, DataItem.long(value))

        /**
         * Adds the provided [value] into this [DataObject.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Double) =
            put(key, DataItem.double(value))

        /**
         * Adds the provided [value] into this [DataObject.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Boolean) =
            put(key, DataItem.boolean(value))

        /**
         * Unsafe shortcut to put [any] object into the [DataObject]. The [any] will attempt to be
         * converted to a supported type. If no supported type is available, then an [UnsupportedDataItemException]
         * will be thrown.
         *
         * @return The current [Builder] being operated on
         */
        @Throws(UnsupportedDataItemException::class)
        fun putAny(key: String, any: Any?) =
            put(key, DataItem.convert(any))

        /**
         * Attempts to put [any] object into the [DataObject]. The [any] will attempt to be
         * converted to a supported type. If no supported type is available, then
         * [DataItem.NULL] will be instead.
         *
         * @return The current [Builder] being operated on
         */
        fun putAnyOrNull(key: String, any: Any?) =
            put(key, DataItem.convert(any, DataItem.NULL))

        /**
         * Adds the provided [value] into this [DataObject.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: DataItem) = apply {
            data[key] = value
        }

        /**
         * Adds all entries from the provided [dataObject] into this [DataObject.Builder],
         * overwriting any keys that already exist.
         *
         * @return The current [Builder] being operated on
         */
        fun putAll(dataObject: DataObject) = apply {
            data.putAll(dataObject.getAll())
        }

        /**
         * Adds a [DataItemConvertible] object to the [DataObject]. The
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: DataItemConvertible) =
            put(key, value.asDataItem())

        /**
         * Adds a [DataItem.NULL] object to the [DataObject].
         *
         * @return The current [Builder] being operated on
         */
        fun putNull(key: String) =
            put(key, DataItem.NULL)

        /**
         * Removes the entry currently stored at the current [key]. If the [key] does not exist,
         * then this is a no-operation.
         *
         * @return The current [Builder] being operated on
         */
        fun remove(key: String) = apply {
            data.remove(key)
        }

        /**
         * Clears all entries in the [Builder].
         *
         * @return The current [Builder] being operated on
         */
        fun clear() = apply {
            data.clear()
        }

        /**
         * Creates an immutable [DataObject] using the values added to the builder.
         *
         * @return A [DataObject] containing the result of all add/remove operations performed on
         * the [Builder]
         */
        fun build(): DataObject {
            if (data.isEmpty()) return EMPTY_OBJECT

            return DataObject(data.toMap())
        }
    }

    object Converter : DataItemConverter<DataObject> {
        override fun convert(dataItem: DataItem): DataObject? =
            dataItem.getDataObject()
    }
}
