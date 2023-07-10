package com.tealium.core.api.data.bundle

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.Array

/**
 * Data class for restricting the supported types that can be passed into the system.
 *
 * The toString() method will provide a JSON friendly representation of the data enclosed. That is,
 *  - String data will be quoted; i.e.  "my string"
 *  - Numeric data will not be quoted; i.e. 10 or 3.141..
 *  - Boolean data will not be quoted; i.e. true/false
 *  - TealiumList data will be formatted as a JSON Array; i.e. [["value", 10, true]]
 *  - TealiumBundle data will be formatted as a JSON Object; i.e. { "key":"value", "number":10 }
 *
 * This class is currently broadly similar to the [JSONObject] and makes use of several methods
 * provided by the [org.json] package on Android.
 */
class TealiumValue private constructor(
    val value: Any,
) {

    /**
     * Indicates whether the contained value is a [TealiumBundle]
     *
     * @return true if [value] is a [TealiumBundle]; else false
     */
    fun isBundle(): Boolean {
        return value is TealiumBundle
    }

    /**
     * Indicates whether the contained value is a [TealiumList]
     *
     * @return true if [value] is a [TealiumList]; else false
     */
    fun isList(): Boolean {
        return value is TealiumList
    }

    /**
     * Indicates whether the contained value is a [String]
     *
     * @return true if [value] is a [String]; else false
     */
    fun isString(): Boolean {
        return value is String
    }

    /**
     * Indicates whether the contained value is an [Int]
     *
     * @return true if [value] is an [Int]; else false
     */
    fun isInt(): Boolean {
        return value is Int
    }

    /**
     * Indicates whether the contained value is a [Long]
     *
     * @return true if [value] is a [Long]; else false
     */
    fun isLong(): Boolean {
        return value is Long
    }

    /**
     * Indicates whether the contained value is a [Double]
     *
     * @return true if [value] is a [Double]; else false
     */
    fun isDouble(): Boolean {
        return value is Double
    }

    /**
     * Indicates whether the contained value is a [Number]
     *
     * @return true if [value] is a [Number]; else false
     */
    fun isNumber(): Boolean {
        return value is Number
    }

    /**
     * Indicates whether the contained value is a [Boolean]
     *
     * @return true if [value] is a [Boolean]; else false
     */
    fun isBoolean(): Boolean {
        return value is Boolean
    }

    /**
     * Returns the contained value as a [String] if the contained value is a [String]
     * No type coercion is attempted.
     *
     * @return [value] as a [String]; else null
     */
    fun getString(): String? {
        return if (isString()) value as String else null
    }

    /**
     * Returns the contained value as an [Int] if the contained value is an [Int], or a [Number]
     * that can be coerced to an [Int].
     * If [value] is a [Double] or [Long] then the returned value will possibly lose accuracy as a
     * result.
     *
     * @return [value] as an [Int]; else converted to an [Int] if possible; else null
     */
    fun getInt(): Int? {
        return if (isInt()) value as Int else asNumber()?.toInt()
    }

    /**
     * Returns the contained value as an [Long] if the contained value is an [Long], or a [Number]
     * that can be coerced to an [Long].
     * If [value] is a [Double] then the returned value will possibly lose accuracy as a result.
     *
     * @return [value] as an [Long]; else converted to an [Long] if possible; else null
     */
    fun getLong(): Long? {
        return if (isLong()) value as Long
        else asNumber()?.toLong()
    }

    /**
     * Returns the contained value as an [Double] if the contained value is an [Double], or a [Number]
     * that can be coerced to an [Double].
     *
     * @return [value] as an [Double]; else converted to an [Double] if possible; else null
     */
    fun getDouble(): Double? {
        return if (isDouble()) value as Double else asNumber()?.toDouble()
    }

    /**
     * Returns the contained value as an [Boolean] if the contained value is a [Boolean].
     * No type coercion is attempted.
     *
     * @return [value] as an [Boolean]; else null
     */
    fun getBoolean(): Boolean? {
        return if (isBoolean()) value as Boolean else null
    }

    /**
     * Returns the contained value as an [TealiumList] if the contained value is a [TealiumList].
     * No type coercion is attempted.
     *
     * @return [value] as an [TealiumList]; else null
     */
    fun getList(): TealiumList? {
        return if (isList()) value as TealiumList else null
    }

    /**
     * Returns the contained value as an [TealiumBundle] if the contained value is a [TealiumBundle].
     * No type coercion is attempted.
     *
     * @return [value] as an [TealiumBundle]; else null
     */
    fun getBundle(): TealiumBundle? {
        return if (isBundle()) value as TealiumBundle else null
    }

    /**
     * Returns the contained value as a [Number] if the contained value is a [Number].
     * No type coercion is attempted.
     *
     * @return [value] as an [Number]; else null
     */
    private fun asNumber(): Number? {
        return value as? Number
    }

    /**
     * Returns the String representation of the [value] in a JSON compliant format.
     *
     * @return [value] as a String
     * @see TealiumValue
     */
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

            return getLong() == otherValue.getLong()
        }

        return value == otherValue.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    companion object {
        @JvmField
        val NULL = TealiumValue(Any())

        /**
         * Creates a [TealiumValue] that contains a [string] as its [value]
         *
         * @param string [String] value to wrap as a [TealiumValue]
         */
        @JvmStatic
        fun string(string: String): TealiumValue {
            return convert(string)
        }

        /**
         * Creates a [TealiumValue] that contains a [int] as its [value]
         *
         * @param int [Int] value to wrap as a [TealiumValue]
         */
        @JvmStatic
        fun int(int: Int): TealiumValue {
            return convert(int)
        }

        /**
         * Creates a [TealiumValue] that contains a [double] as its [value]
         *
         * @param double [Double] value to wrap as a [TealiumValue]
         */
        @JvmStatic
        fun double(double: Double): TealiumValue {
            return convert(double)
        }

        /**
         * Creates a [TealiumValue] that contains a [long] as its [value]
         *
         * @param long [Long] value to wrap as a [TealiumValue]
         */
        @JvmStatic
        fun long(long: Long): TealiumValue {
            return convert(long)
        }

        /**
         * Creates a [TealiumValue] that contains a [boolean] as its [value]
         *
         * @param boolean [Boolean] value to wrap as a [TealiumValue]
         */
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

        /**
         * Attempts to create a [TealiumValue] that contains [any] as its [value].
         * If the conversion to a [TealiumValue] fails for any reason, then the [default] value is
         * returned instead.
         *
         * @param any Value to wrap as a [TealiumValue]
         * @param default Default value to use if conversion to [TealiumValue] fails
         */
        @JvmStatic
        fun convert(
            any: Any?,
            default: TealiumValue
        ): TealiumValue {
            val tValue =
                convert(any)
            return if (tValue != NULL) tValue else default
        }

        /**
         * Attempts to create a [TealiumValue] that contains [any] as its [value].
         * If the conversion to a [TealiumValue] fails for any reason, then the [NULL] value is
         * returned instead.
         *
         * Types that require no conversion are as follows:
         * [String], [Int], [Long], [Double], [Boolean], [TealiumList], [TealiumBundle]
         *
         * If [any] is a [TealiumSerializable], then it will first be converted using
         * [TealiumSerializable.serialize]
         *
         * [Float] and [Short] are coerced to [Double] and [Int] respectively, whilst [Char] is also
         * coerced to a [String]
         *
         * [Collection], [JSONArray] and [Array] types will be converted to a [TealiumList]. Any
         * contained objects that cannot be converted to a [TealiumValue] will be omitted from the
         * resulting [TealiumList]
         *
         * [Map] and [JSONObject] types will be converted to a [TealiumBundle]. Any contained
         * objects that cannot be converted to a [TealiumValue] will be omitted from the resulting
         * [TealiumBundle]
         *
         * @param any Value to wrap as a [TealiumValue]
         * @return [any] as a [TealiumValue] if possible, else [NULL]
         */
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