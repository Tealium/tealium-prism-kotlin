package com.tealium.core.api.data.bundle

import com.tealium.core.api.Deserializer

class TealiumBundle private constructor(
    initialData: Map<String, TealiumValue> = emptyMap()
) : Iterable<Map.Entry<String, TealiumValue>> {

    private val map: Map<String, TealiumValue> =
        mutableMapOf<String, TealiumValue>().apply {
            putAll(initialData)
        }

    fun get(key: String): TealiumValue? {
        return map[key]
    }

    fun getString(key: String): String? {
        return get(key)?.value as? String
    }

    fun <T> get(key: String, deserializer: Deserializer<T, TealiumValue>): T? {
        return map[key]?.let { obj ->
            deserializer.deserialize(obj)
        }
    }

    fun getAll(): Map<String, TealiumValue> {
        return map.toMap()
    }

    override fun iterator(): Iterator<Map.Entry<String, TealiumValue>> {
        return map.iterator()
    }

    // Possibly for KTX separate library
    fun copy(block: Builder.() -> Unit) : TealiumBundle {
        val builder = Builder(this)
        block.invoke(builder)
        return builder.getBundle()
    }

    companion object {
        @JvmStatic
        val EMPTY_BUNDLE = TealiumBundle()

        @JvmStatic
        fun create(block: Builder.() -> Unit): TealiumBundle {
            val builder = Builder()
            block.invoke(builder)
            return builder.getBundle()
        }
    }

    class Builder @JvmOverloads constructor(copy: TealiumBundle = EMPTY_BUNDLE) {
        private val data: MutableMap<String, TealiumValue> = mutableMapOf<String, TealiumValue>().apply {
            putAll(copy.getAll())
        }

//        fun string(key: String, value: String) = apply { bundle.put(key, value) }
//        fun int(key: String, value: Int) = apply { bundle.put(key, value) }
        // TODO rest of the helpers

        fun put(key: String, value: String) = apply {
            put(key, TealiumValue.convert(value))
        }

        fun put(key: String, value: Int) = apply {
            put(key, TealiumValue.convert(value))
        }

        fun put(key: String, value: Long) = apply {
            put(key, TealiumValue.convert(value))
        }

        fun put(key: String, value: Boolean) = apply {
            put(key, TealiumValue.convert(value))
        }

        fun put(key: String, value: kotlin.Array<String>) = apply {
            put(key, TealiumValue.convert(value))
        }

        fun put(key: String, value: kotlin.Array<Int>) = apply {
            put(key, TealiumValue.convert(value))
        }

        fun put(key: String, value: kotlin.Array<Long>) = apply {
            put(key, TealiumValue.convert(value))
        }

        fun put(key: String, value: kotlin.Array<Boolean>) = apply {
            put(key, TealiumValue.convert(value))
        }

        /**
         * Unsafe shortcut to put [any] object into the bundle. The [any] will attempt to be
         * converted to a supported type. If no supported type is available, then
         * [TealiumValue.NULL] will be returned.
         */
        fun putAny(key: String, any: Any) = apply {
            put(key, TealiumValue.convert(any))
        }

        fun put(key: String, value: TealiumValue) = apply {
            data[key] = value
        }

        fun putAll(bundle: TealiumBundle) = apply {
            data.putAll(bundle.getAll())
        }

        /**
         * Adds a [TealiumSerializable] object to the bundle. The
         */
        fun putSerializable(key: String, serializable: TealiumSerializable) = apply {
            put(key, serializable.serialize())
        }

        fun remove(key: String) {
            data.remove(key)
        }

        fun clear() {
            data.clear()
        }

        fun getBundle(): TealiumBundle {
            return TealiumBundle(data)
        }
    }
}


