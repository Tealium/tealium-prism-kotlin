package com.tealium.core.api.data.bundle

import com.tealium.core.api.Deserializer
import com.tealium.core.internal.stringify
import org.json.JSONStringer


class TealiumList(private val collection: List<TealiumValue>) : Iterable<TealiumValue> {

    private var _toString: String? = null

    fun get(index: Int): TealiumValue? {
        return collection.getOrNull(index)
    }

    fun getString(index: Int): String? {
        return get(index)?.value as? String
    }

    fun getInt(index: Int): Int? {
        return get(index)?.value as? Int
    }

    fun <T> get(index: Int, deserializer: Deserializer<T, TealiumValue>): T? {
        return collection.getOrNull(index)?.let { obj ->
            deserializer.deserialize(obj)
        }
    }

    fun size() : Int = collection.size

    fun contains(value: TealiumValue): Boolean = collection.contains(value)

    override fun iterator(): Iterator<TealiumValue> {
        return collection.iterator()
    }

    // Possibly for KTX separate library
    fun copy(block: Builder.() -> Unit) : TealiumList {
        val builder = Builder(this)
        block.invoke(builder)
        return builder.getList()
    }

    override fun toString(): String {
        return _toString ?: run {
            val stringer = JSONStringer()
            stringer.array()
            stringify(stringer)
            stringer.endArray()
            ""
        }.also { _toString = it }
    }

    companion object {
        val EMPTY_LIST: TealiumList = TealiumList(emptyList())
        private val DEFAULT_INDEX = -1

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
        fun add(value: Boolean, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        @JvmOverloads
        fun add(value: kotlin.Array<String>, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        @JvmOverloads
        fun add(value: kotlin.Array<Int>, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        @JvmOverloads
        fun add(value: kotlin.Array<Long>, index: Int = DEFAULT_INDEX) = apply {
            add(TealiumValue.convert(value), index)
        }

        @JvmOverloads
        fun add(value: kotlin.Array<Boolean>, index: Int = DEFAULT_INDEX) = apply {
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
        fun putAll(list: TealiumList, index: Int = DEFAULT_INDEX) = apply {
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
            add(serializable.serialize(), index)
        }

        fun remove(index: Int) {
            list.removeAt(index)
        }

        fun clear() {
            list.clear()
        }

        fun getList(): TealiumList {
            return TealiumList(list.toList())
        }
    }
}