package com.tealium.core.api.data.bundle

import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Array

class TealiumValue private constructor(
    val value: Any?
) {
    // TODO other getters / coercions

    fun getBundle(): TealiumBundle? {
        return value as? TealiumBundle
    }

    fun isBundle(): Boolean {
        return value is TealiumBundle
    }

    fun getString(): String? {
        return value as? String
    }

    fun getList(): TealiumList? {
        return value as? TealiumList
    }

    override fun toString(): String {
        if (value == NULL) {
            return "null";
        }

        try {
            if (value is String) {
                return JSONObject.quote(value)
            }

            if (value is Number) {
                return JSONObject.numberToString(value)
            }

            if (value is TealiumBundle || value is TealiumList) {
                return value.toString()
            }

            return value.toString()
        } catch (e: JSONException) {
            return ""
        }
    }

    companion object {
        @JvmStatic
        val NULL = TealiumValue(null)

        @JvmStatic
        fun string(string: String): TealiumValue {
            return convert(string)
        }

        @JvmStatic
        fun int(int: Int): TealiumValue {
            return convert(int)
        }

        @JvmStatic
        fun double(double: Double): TealiumValue {
            return convert(double)
        }

        @JvmStatic
        fun long(long: Long): TealiumValue {
            return convert(long)
        }

        @JvmStatic
        fun boolean(boolean: Boolean): TealiumValue {
            return convert(boolean)
        }

        /**
         * Converts an object of unknown type. This method is not guaranteed to succeed.
         * For unsupported types, [NULL] will be returned.
         */
        @JvmStatic
        fun convertOpt(any: Any?): TealiumValue {
            return convert(any, NULL)
        }

        @JvmStatic
        fun convert(
            any: Any?,
            default: TealiumValue
        ): TealiumValue {
            val tValue =
                convert(any)
            return if (tValue != NULL) tValue else default
        }

        @JvmStatic
        fun convert(any: Any?): TealiumValue {
            if (any == null || any === JSONObject.NULL) {
                return NULL
            }
            if (any is TealiumValue) {
                return any
            }
            if (any is TealiumBundle ||
                any is TealiumList ||
                any is Boolean ||
                any is Int ||
                any is Long ||
                any is String
            ) {
                return TealiumValue(any)
            }
            if (any is TealiumSerializable) {
                return any.serialize()
            }
            if (any is Byte || any is Short) {
                return TealiumValue((any as Number).toInt())
            }
            if (any is Char) {
                return TealiumValue(any.toString())
            }
            if (any is Float) {
                return TealiumValue((any as Number).toDouble())
            }
            if (any is Double) {
                if (any.isInfinite() || any.isNaN()) {
                    return NULL
                }
                return TealiumValue(any)
            }
            try {
//                if (any is JSONArray) {
//                   // TODO
//                }
//                if (any is JSONObject) {
//                   // TODO
//                }
                if (any is Collection<*>) {
                    return convertCollection(any)
                }
                if (any.javaClass.isArray) {
                    return convertArray(any)
                }
                if (any is Map<*, *>) {
                    return convertMap(any)
                }
            } catch (exception: Exception) {
                // TODO, log this
            }
            return NULL
        }

        private fun convertArray(array: Any): TealiumValue {
            val length = Array.getLength(array)
            val list: MutableList<TealiumValue> = ArrayList(length)
            for (i in 0 until length) {
                val value = Array.get(array, i)
                if (value != null) {
                    list.add(convert(value))
                }
            }
            return TealiumValue(TealiumList(list))
        }

        private fun convertCollection(collection: Collection<*>): TealiumValue {
            val list: MutableList<TealiumValue> = arrayListOf()
            for (obj in collection) {
                if (obj != null) {
                    list.add(convert(obj))
                }
            }
            return TealiumValue(TealiumList(list))
        }

        private fun convertMap(map: Map<*, *>): TealiumValue {
            val builder = TealiumBundle.Builder()
            for ((key, value) in map) {
                if (key is String && value != null) {
                    // tolerate invalid keys
                    builder.put(key, convert(value))
                }
            }
            return TealiumValue(builder.getBundle())
        }
    }
}