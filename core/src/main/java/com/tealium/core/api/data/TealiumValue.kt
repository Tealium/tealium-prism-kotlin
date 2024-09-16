package com.tealium.core.api.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.lang.reflect.Array

/**
 * Immutable data class for restricting the supported types that can be passed into the system.
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
 *
 *
 * The [any] value should be restricted to only JSON supportable types - see [convert].
 *
 * The [string] parameter should only be used in the case where this value is being lazily
 * instantiated from an already stringified representation of the [value]. If provided, then the
 * [string] parameter will be used as the pre-computed return value from [toString] as well
 *
 * @param any The value to be wrapped; at least this or the [string] value need to be provided
 * @param string The string value representing the value of [any]; at least this or the [string]
 *      value need to be provided
 */
class TealiumValue private constructor(
    any: Any? = null,
    string: String? = null,
): TealiumSerializable {
    private var _value: Any? = any
    private var _toString: String? = string
    private var isLazy: Boolean = (any == null && string != null)


    /**
     * Contains the underlying data value for this instance.
     *
     * It is likely more convenient to use one of the many get methods to receive the [value] as the
     * appropriate type.
     *
     * @see getString
     * @see getInt
     * @see getLong
     * @see getDouble
     * @see getBoolean
     * @see getList
     * @see getBundle
     */
    val value: Any?
        get() {
            if (_value == null && isLazy) {
                parseSupported(_toString).also {
                    isLazy = false
                    _value = it
                }
            }

            return _value
        }


    /**
     * Indicates whether the contained value is null
     *
     * @return true if [value] is a null; else false
     */
    fun isNull(): Boolean {
        return value == null
    }

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
     * Returns the contained value as a [String] if the contained value is a [String].
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
        return (_toString ?: if (isNull()) {
            NULL_STRING;
        } else {
            try {
                when (value) {
                    is String -> {
                        JSONObject.quote(value as String)
                    }
                    is Number -> {
                        JSONObject.numberToString(value as Number)
                    }
                    is TealiumBundle, is TealiumList -> {
                        value.toString()
                    }
                    else -> {
                        value.toString()
                    }
                }
            } catch (e: JSONException) {
                // TODO - log error, but this shouldn't happen
                return ""
            }
        }).also { _toString = it }
    }

    override fun equals(other: Any?): Boolean {
        val otherValue = other as? TealiumValue ?: return false

        if (isNull() && otherValue.isNull()) return true

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

    override fun asTealiumValue(): TealiumValue = this

    companion object {
        private const val NULL_STRING = "null"

        /**
         * Constant value representing null.
         *
         * It's preferable to use this instance if null is required, to save on unnecessary object
         * creation.
         */
        @JvmField
        val NULL = TealiumValue(any = null)

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
        fun convertOrNull(any: Any?): TealiumValue {
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
         * [TealiumSerializable.asTealiumValue]
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
            val supportedType = convertToSupported(any)

            return if (supportedType is TealiumValue) {
                supportedType
            } else {
                TealiumValue(supportedType)
            }
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
        internal fun lazy(string: String): TealiumValue {
            if (NULL_STRING == string) return NULL

            return TealiumValue(string = string)
        }

        /**
         * Creates a [TealiumValue] from it's string representation, parsing the contents of the
         * string as if it were JSON
         * i.e. string values should be quoted "value"
         */
        fun parse(string: String): TealiumValue {
            return convert(parseSupported(string))
        }

        /**
         * Parses the contents of the string as if it were JSON
         * i.e. string values should be quoted "value"
         *
         * The resultant value is converted to a supported type and subsequently returned.
         * If conversion fails, then null will be returned
         *
         * @param string The JSON string representation of the value
         * @return The Tealium supported type
         */
        private fun parseSupported(string: String?): Any? {
            if (string == null) return null

            return try {
                val tokener = JSONTokener(string)

                val any = convertToSupported(tokener.nextValue())
                if (any is TealiumValue) {
                    any.value
                } else any
            } catch (ex: JSONException) {
                null
            }
        }

        /**
         * Takes an object of any type and converts it into one of the supported data types if
         * possible.
         *
         * Returned values may already be instances of [TealiumValue] if the value provided as [any]
         * was either:
         * a) already a [TealiumValue], including [NULL]
         * b) implements [TealiumSerializable]
         *
         * Returned types are limited to: [String], [Int], [Long], [Double], [Boolean],
         * [TealiumList], [TealiumBundle] and [TealiumValue]
         *
         * @param any The object to be converted to a supported type
         *
         * @return An instance of a supported value.
         */
        private fun convertToSupported(any: Any?): Any {
            if (any == null || any === JSONObject.NULL || any === NULL) {
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
                return any
            }
            if (any is TealiumSerializable) {
                return any.asTealiumValue()
            }
            if (any is Byte || any is Short) {
                return (any as Number).toInt()
            }
            if (any is Char) {
                return any.toString()
            }
            if (any is Float) {
                return (any as Number).toDouble()
            }
            if (any is Double) {
                if (any.isInfinite() || any.isNaN()) {
                    return NULL
                }
                return any
            }
            try {
                if (any is JSONArray) {
                    return TealiumList.fromJSONArray(any)
                }
                if (any is JSONObject) {
                    return TealiumBundle.fromJSONObject(any)
                }
                if (any is Collection<*>) {
                    return TealiumList.fromCollection(any)
                }
                if (any.javaClass.isArray) {
                    return TealiumList.fromArray(any) ?: NULL
                }
                if (any is Map<*, *>) {
                    return TealiumBundle.fromMap(any)
                }
            } catch (exception: Exception) {
                // TODO, log this
            }
            return NULL
        }
    }
}