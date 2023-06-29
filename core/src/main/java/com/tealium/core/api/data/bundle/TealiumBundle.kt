package com.tealium.core.api.data.bundle

import com.tealium.core.api.Deserializer
import com.tealium.core.internal.stringify
import org.json.JSONException
import org.json.JSONStringer
import org.json.JSONTokener

class TealiumBundle private constructor(
    initialData: Map<String, TealiumValue> = emptyMap()
) : Iterable<Map.Entry<String, TealiumValue>> {

    private var _toString: String? = null

    private val map: Map<String, TealiumValue> =
        mutableMapOf<String, TealiumValue>().apply {
            putAll(initialData)
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
     * Gets the [TealiumValue] at the given key, and attempts to deserialize it into the type [T]
     * using the provided [Deserializer].
     * If conversion fails, then
     */
    fun <T> get(key: String, deserializer: Deserializer<T, TealiumValue>): T? {
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
     * Returns the [Iterator] in order to iterate over the collection.
     *
     * @return The iterator which can be used to iterate over all entries in the collection
     */
    override fun iterator(): Iterator<Map.Entry<String, TealiumValue>> {
        return map.iterator()
    }

    // Possibly for KTX separate library
    fun copy(block: Builder.() -> Unit) : TealiumBundle {
        val builder = Builder(this)
        block.invoke(builder)
        return builder.getBundle()
    }

    fun asTealiumValue() : TealiumValue {
        return TealiumValue.convert(this)
    }

    companion object {
        @JvmField
        val EMPTY_BUNDLE = TealiumBundle()

        /**
         * Converts a [String] representation of a bundle, into an actual [TealiumBundle] if
         * possible
         *
         * @return [TealiumBundle] of the given string; else null
         */
        @JvmStatic
        fun fromString(string: String) : TealiumBundle? {
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

    override fun toString(): String {
        return _toString ?: run {
            val stringer = JSONStringer()
            stringify(stringer)
            stringer.toString()
        }.also { _toString = it }
    }

    class Builder @JvmOverloads constructor(copy: TealiumBundle = EMPTY_BUNDLE) {
        private val data: MutableMap<String, TealiumValue> = mutableMapOf<String, TealiumValue>().apply {
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
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Array<String>) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Array<Int>) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Array<Long>) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Array<Double>) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Adds the provided [value] into this [TealiumBundle.Builder], overwriting the existing
         * item at that key if it already exists.
         *
         * @return The current [Builder] being operated on
         */
        fun put(key: String, value: Array<Boolean>) = apply {
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
        fun putSerializable(key: String, serializable: TealiumSerializable) = apply {
            put(key, serializable.serialize())
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
            return TealiumBundle(data)
        }
    }
}


