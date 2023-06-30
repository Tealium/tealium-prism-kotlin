package com.tealium.core.api.data.bundle

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Array

/**
 * Data class for restricting the supported types that can be passed into the system.
 * The toString() method will provide a JSON friendly representation of the data enclosed. That is,
 *  - String data will be quoted; i.e.  "my string"
 *  - Numeric data will not be quoted; i.e. 10 or 3.141..
 *  - Boolean data will not be quoted; i.e. true/false
 *  - TealiumList data will be formatted as a JSON Array; i.e. ["value", 10, true]
 *  - TealiumBundle data will be formatted as a JSON Object; i.e. { "key":"value", "number":10 }
 *
 * This class is currently broadly similar to the [JSONObject] and makes use of several methods
 * provided by the [org.json] package on Android.
 */
class TealiumValue private constructor(
    val value: Any,
) {

    fun isBundle(): Boolean {
        return value is TealiumBundle
    }

    fun isList(): Boolean {
        return value is TealiumList
    }

    fun isString(): Boolean {
        return value is String
    }

    fun isInt(): Boolean {
        return value is Int
    }

    fun isLong(): Boolean {
        return value is Long
    }

    fun isDouble(): Boolean {
        return value is Double
    }

    fun isNumber(): Boolean {
        return value is Number
    }

    fun isBoolean(): Boolean {
        return value is Boolean
    }

    fun getString(): String? {
        return if (isString()) value as String else null
    }

    fun getInt(): Int? {
        return if (isInt()) value as Int else asNumber()?.toInt()
    }

    fun getLong(): Long? {
        return if (isLong()) value as Long
        else asNumber()?.toLong()
    }

    fun getDouble(): Double? {
        return if (isDouble()) value as Double else asNumber()?.toDouble()
    }

    fun getBoolean(): Boolean? {
        return if (isBoolean()) value as Boolean else null
    }

    fun getList(): TealiumList? {
        return if (isList()) value as TealiumList else null
    }

    fun getBundle(): TealiumBundle? {
        return if (isBundle()) value as TealiumBundle else null
    }

    private fun asNumber(): Number? {
        return value as? Number
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

    override fun equals(other: Any?): Boolean {
        val otherValue = other as? TealiumValue ?: return false

        if (isNumber() && otherValue.isNumber()) {
            if (isDouble() && otherValue.isDouble()) {
                return getDouble() == otherValue.getDouble()
            }
        }

        return value == otherValue.value
    }

    override fun hashCode(): Int {
        var result = 17
        result = 31 * result + value.hashCode()
        return result
    }

    companion object {
        @JvmField
        val NULL = TealiumValue(Any())

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
                if (any is JSONArray) {
                    return convertJsonArray(any)
                }
                if (any is JSONObject) {
                    return convertJsonObject(any)
                }
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

        private fun convertJsonArray(jsonArray: JSONArray): TealiumValue {
            val builder = TealiumList.Builder()
            val size = jsonArray.length()
            for (idx in 0 until size) {
                jsonArray.opt(idx)?.let { value ->
                    builder.add(convert(value))
                }
            }
            return TealiumValue(builder.getList())
        }

        private fun convertJsonObject(jsonObject: JSONObject): TealiumValue {
            val builder = TealiumBundle.Builder()
            for (key in jsonObject.keys()) {
                jsonObject.opt(key)?.let { value ->
                    // tolerate invalid keys
                    builder.put(key, convert(value))
                }
            }
            return TealiumValue(builder.getBundle())
        }
    }
}