package com.tealium.core.api.data.bundle

import com.tealium.core.api.Deserializer
import com.tealium.core.api.data.bundle.TealiumBundle.Builder
import com.tealium.core.internal.stringify
import org.json.JSONException
import org.json.JSONStringer
import org.json.JSONTokener

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
class TealiumList private constructor(private val collection: List<TealiumValue>) :
    Iterable<TealiumValue>, TealiumSerializable {

    private var _toString: String? = null

    /**
     * Gets the [TealiumValue] at the given index.
     *
     * @param index The index of the list entry to retrieve; starts at 0
     * @return [TealiumValue] found at the given [index]; or null if index not found.
     */
    fun get(index: Int): TealiumValue? {
        return collection.getOrNull(index)
    }

    fun getString(index: Int): String? = get(index)?.getString()

    fun getInt(index: Int): Int? = get(index)?.getInt()

    fun getLong(index: Int): Long? = get(index)?.getLong()

    fun getDouble(index: Int): Double? = get(index)?.getDouble()

    fun getBoolean(index: Int): Boolean? = get(index)?.getBoolean()

    fun getList(index: Int): TealiumList? = get(index)?.getList()

    fun getBundle(index: Int): TealiumBundle? = get(index)?.getBundle()

    fun <T> get(index: Int, deserializer: Deserializer<TealiumValue, T>): T? {
        return get(index)?.let { obj ->
            deserializer.deserialize(obj)
        }
    }

    fun size(): Int = collection.size

    fun contains(value: TealiumValue): Boolean = collection.contains(value)

    override fun iterator(): Iterator<TealiumValue> {
        return collection.iterator()
    }

    // Possibly for KTX separate library
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
        @JvmField
        val EMPTY_LIST: TealiumList = TealiumList(emptyList())
        private val DEFAULT_INDEX = -1

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

        @JvmOverloads
        fun add(value: String, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        @JvmOverloads
        fun add(value: Int, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        @JvmOverloads
        fun add(value: Long, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        @JvmOverloads
        fun add(value: Double, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        @JvmOverloads
        fun add(value: Boolean, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        /**
         * Unsafe shortcut to put [any] object into the bundle. The [any] will attempt to be
         * converted to a supported type. If no supported type is available, then
         * [TealiumValue.NULL] will be returned.
         */
        @JvmOverloads
        fun addAny(any: Any, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(any), index)
        }

        @JvmOverloads
        fun add(value: TealiumValue, index: Int = DEFAULT_INDEX) = apply {
            if (index >= 0)
                list.add(index, value)
            else
                list.add(value)
        }

        @JvmOverloads
        fun add(value: TealiumSerializable, index: Int = DEFAULT_INDEX) = apply {
            add(value.asTealiumValue(), index)
        }

        @JvmOverloads
        fun addAll(list: TealiumList, index: Int = DEFAULT_INDEX) = apply {
            if (index >= 0)
                this.list.addAll(index, list.collection)
            else
                this.list.addAll(list.collection)
        }

        /**
         * Adds a [TealiumSerializable] object to the bundle. The
         */
        @JvmOverloads
        fun addSerializable(serializable: TealiumSerializable, index: Int = DEFAULT_INDEX) = apply {
            add(serializable.asTealiumValue(), index)
        }

        fun remove(index: Int) {
            list.removeAt(index)
        }

        fun clear() {
            list.clear()
        }

        fun getList(): TealiumList {
            if (list.isEmpty()) return EMPTY_LIST

            return TealiumList(list.toList())
        }
    }
}