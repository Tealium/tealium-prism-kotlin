package com.tealium.core.api.data

import com.tealium.core.internal.misc.stringify
import org.json.*

/**
 * The [TealiumBundle] represents a map of restricted data types which are wrappable by
 * [TealiumValue], to ensure that all data passed to the SDK can be used correctly and without
 * unexpected behaviours when converting to Strings.
 *
 * Instances of [TealiumBundle] are immutable. When requiring updates, the [copy] method is
 * available to use, which is prepopulate a [Builder] with the existing set of [TealiumValue]s
 *
 * This class will serialize to a JSON object - { ... } - when calling [toString].
 *
 * @see TealiumValue
 * @see TealiumList
 */
class TealiumBundle private constructor(
    data: Map<String, TealiumValue>? = null,
    string: String? = null
) : Iterable<Map.Entry<String, TealiumValue>>, TealiumSerializable {

    private lateinit var _data: Map<String, TealiumValue>
    private var _toString: String? = string
    private var isLazy = data == null && string != null

    init {
        data?.let {
            _data = it
        }
    }

    private val map: Map<String, TealiumValue>
        get() {
            if (!this::_data.isInitialized && isLazy) {
                parseData(_toString).also {
                    isLazy = false
                    _data = it ?: let {
                        // _toString was not parsable
                        _toString = EMPTY_BUNDLE_STRING
                        emptyMap()
                    }
                }
            }

            return _data
        }

    /**
     * Gets the [TealiumValue] stored at the given [key] if it exists regardless of it's underlying type.
     *
     * @param key The key to use to lookup the item.
     * @return The [TealiumValue] stored at the given [key]; else null
     */
    fun get(key: String): TealiumValue? {
        return map[key]
    }

    /**
     * Gets the [String] stored at the given [key] if it exists and can be correctly returned
     * as a [String].
     *
     * @param key The key to use to lookup the item.
     * @return The [String] stored at the given [key]; else null
     */
    fun getString(key: String): String? = map[key]?.getString()

    /**
     * Gets the [Int] stored at the given [key] if it exists and can be correctly returned
     * as a [Int].
     *
     * @param key The key to use to lookup the item.
     * @return The [Int] stored at the given [key]; else null
     */
    fun getInt(key: String): Int? = map[key]?.getInt()

    /**
     * Gets the [Long] stored at the given [key] if it exists and can be correctly returned
     * as a [Long].
     *
     * @param key The key to use to lookup the item.
     * @return The [Long] stored at the given [key]; else null
     */
    fun getLong(key: String): Long? = map[key]?.getLong()

    /**
     * Gets the [Double] stored at the given [key] if it exists and can be correctly returned
     * as a [Double].
     *
     * @param key The key to use to lookup the item.
     * @return The [Double] stored at the given [key]; else null
     */
    fun getDouble(key: String): Double? = map[key]?.getDouble()

    /**
     * Gets the [Boolean] stored at the given [key] if it exists and can be correctly returned
     * as a [Boolean].
     *
     * @param key The key to use to lookup the item.
     * @return The [Boolean] stored at the given [key]; else null
     */
    fun getBoolean(key: String): Boolean? = map[key]?.getBoolean()

    /**
     * Gets the [TealiumList] stored at the given [key] if it exists and can be correctly returned
     * as a [TealiumList].
     *
     * @param key The key to use to lookup the item.
     * @return The [TealiumList] stored at the given [key]; else null
     */
    fun getList(key: String): TealiumList? = map[key]?.getList()

    /**
     * Gets the [TealiumBundle] stored at the given [key] if it exists and can be correctly returned
     * as a [TealiumBundle].
     *
     * @param key The key to use to lookup the item.
     * @return The [TealiumBundle] stored at the given [key]; else null
     */
    fun getBundle(key: String): TealiumBundle? = map[key]?.getBundle()

    /**
     * Gets the [TealiumValue] at the given [key], and attempts to deserialize it into the type [T]
     * using the provided [Deserializer].
     *
     * @param key The key to use to lookup the item.
     * @param deserializer The deserializer to use to recreate the object of type [T]
     * @return The reconstructed instance of [T]; else null
     */
    fun <T> get(key: String, deserializer: Deserializer<TealiumValue, T>): T? {
        return map[key]?.let { obj ->
            deserializer.deserialize(obj)
        }
    }

    /**
     * Returns all entries stored in the [TealiumBundle]
     *
     * @return All entries in the bundle
     */
    fun getAll(): Map<String, TealiumValue> {
        return map.toMap()
    }

    /**
     * Returns the number of top level entries stored in this bundle.
     *
     * @return The number of entries in this bundle.
     */
    val size: Int
        get() = map.size

    /**
     * Returns the [Iterator] in order to iterate over the collection.
     *
     * @return The iterator which can be used to iterate over all entries in the collection
     */
    override fun iterator(): Iterator<Map.Entry<String, TealiumValue>> {
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

        other as TealiumBundle

        if (map != other.map) return false

        return true
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }

    // Possibly for KTX separate library
    fun copy(block: Builder.() -> Unit = {}): TealiumBundle {
        val builder = Builder(this)
        block.invoke(builder)
        return builder.getBundle()
    }

    /**
     * Convenience method to create a new [Builder] containing all the values in this [TealiumBundle]
     */
    fun buildUpon(): Builder {
        return Builder(this)
    }

    override fun asTealiumValue(): TealiumValue {
        return TealiumValue.convert(this)
    }

    companion object {
        private const val EMPTY_BUNDLE_STRING = "{}"

        /**
         * Constant value representing an empty [TealiumBundle].
         *
         * It's preferable to use this instance if an empty bundle is required, to save on
         * unnecessary object creation.
         */
        @JvmField
        val EMPTY_BUNDLE = TealiumBundle(data = emptyMap(), string = EMPTY_BUNDLE_STRING)

        /**
         * Converts a [String] representation of a bundle, into an actual [TealiumBundle] if
         * possible.
         * This method eagerly parses the [string] value.
         *
         * @return [TealiumBundle] of the given string; else null
         */
        @JvmStatic
        fun fromString(string: String): TealiumBundle? {
            if (string.isBlank()) return null

            return try {
                val parser = JSONTokener(string)

                // TODO - Should try and lazy load this.
                // i.e. keep as a string, only parse if `map` property is called
                val value = TealiumValue.convert(parser.nextValue())
                return value.getBundle()
            } catch (ex: JSONException) {
                null
            }
        }

        private fun parseData(string: String?): Map<String, TealiumValue>? {
            if (string.isNullOrBlank()) return null

            return try {
                val parser = JSONTokener(string)

                val jsonObject = parser.nextValue()
                if (jsonObject !is JSONObject) return null

                return jsonObject.mapValues { value ->
                    TealiumValue.convert(value)
                }
            } catch (ex: JSONException) {
                null
            }
        }

        /**
         * Converts a Map to the supported [TealiumBundle] type.
         *
         * Keys should be [String]s.
         * Unsupported values are replaced with [TealiumValue.NULL]
         *
         * @param map The map of key value pairs
         * @return [TealiumBundle] containing all key value pairs as wrapped by [TealiumValue]
         */
        @JvmStatic
        fun fromMap(map: Map<*, *>): TealiumBundle {
            val builder = Builder()
            for ((key, value) in map) {
                if (key is String && value != null) {
                    // tolerate invalid keys
                    builder.put(key, TealiumValue.convert(value))
                }
            }
            return builder.getBundle()
        }

        /**
         * Converts a [JSONObject] to the supported [TealiumBundle] type.
         *
         * @param jsonObject The [JSONObject] containing the key value pairs
         * @return [TealiumBundle] containing all key value pairs as wrapped by [TealiumValue]
         */
        @JvmStatic
        fun fromJSONObject(jsonObject: JSONObject): TealiumBundle {
            val builder = Builder()
            for (key in jsonObject.keys()) {
                jsonObject.opt(key)?.let { value ->
                    // tolerate invalid keys
                    builder.put(key, TealiumValue.convert(value))
                }
            }
            return builder.getBundle()
        }

        /**
         * Unsafe method allowing a [TealiumValue] to be instantiated from a stringified version of
         * its [value].
         *
         * The [value] will remain uninitialized until its first access, either directly or
         * indirectly via any of the "get" methods. This can be a performant way to create instances
         * from stringified versions where the overhead of parsing the value is not necessarily
         * required.
         *
         * @param string The stringified representation of this value.
         * @return A TealiumValue with the [value] currently unparsed which could lead to errors
         */
        internal fun lazy(string: String): TealiumBundle {
            if (EMPTY_BUNDLE_STRING == string) return EMPTY_BUNDLE

            return TealiumBundle(string = string)
        }


        /**
         * Creates a new [TealiumBundle] providing the [Builder] in a block for easy population
         *
         * @param block A function used to populate the [Builder] with the required entries
         * @return A new [TealiumBundle] containing all entries added to the [Builder]
         */
        @JvmStatic // TODO - could be moved to a separate KTX project possibly.
        fun create(block: Builder.() -> Unit): TealiumBundle {
            val builder = Builder()
            block.invoke(builder)
            return builder.getBundle()
        }
    }

    class Builder @JvmOverloads constructor(copy: TealiumBundle = EMPTY_BUNDLE) {
        private val data: MutableMap<String, TealiumValue> =
            mutableMapOf<String, TealiumValue>().apply {
                putAll(copy.getAll())
            }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: String) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Int) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Long) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Double) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Boolean) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Unsafe shortcut to put [any] object into the bundle. The [any] will attempt to be
         * converted to a supported type. If no supported type is available, then
         * [TealiumValue.NULL] will be returned.
         *
         * @return The current [Builder] being operated on
         */
        fun putAny(key: String, any: Any) = apply {
            put(key, TealiumValue.convert(any))
        }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: TealiumValue) = apply {
            data[key] = value
        }

        /**
         * Adds all entries from the provided [bundle] into this [TealiumBundle.Builder],
         * overwriting any keys that already exist.
         *
         * @return The current [Builder] being operated on
         */
        fun putAll(bundle: TealiumBundle) = apply {
            data.putAll(bundle.getAll())
        }

        /**
         * Adds a [TealiumSerializable] object to the bundle. The
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: TealiumSerializable) = apply {
            put(key, value.asTealiumValue())
        }

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
         * Creates an immutable [TealiumBundle] using the values added to the builder.
         *
         * @return A [TealiumBundle] containing the result of all add/remove operations performed on
         * the [Builder]
         */
        fun getBundle(): TealiumBundle {
            if (data.isEmpty()) return EMPTY_BUNDLE

            return TealiumBundle(data.toMap())
        }
    }

    object BundleDeserializer : TealiumDeserializable<TealiumBundle> {
        override fun deserialize(value: TealiumValue): TealiumBundle? =
            value.getBundle()
    }
}
