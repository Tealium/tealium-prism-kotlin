package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.data.DataItem.Companion.convert
import com.tealium.prism.core.api.data.DataItem.Companion.string
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
 *  - DataList data will be formatted as a JSON Array; i.e. [["value", 10, true]]
 *  - DataObject data will be formatted as a JSON Object; i.e. { "key":"value", "number":10 }
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
class DataItem private constructor(
    any: Any? = null,
    string: String? = null,
): DataItemConvertible {
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
     * @see getDataList
     * @see getDataObject
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
     * Indicates whether the contained value is a [DataObject]
     *
     * @return true if [value] is a [DataObject]; else false
     */
    fun isDataObject(): Boolean {
        return value is DataObject
    }

    /**
     * Indicates whether the contained value is a [DataList]
     *
     * @return true if [value] is a [DataList]; else false
     */
    fun isDataList(): Boolean {
        return value is DataList
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
     *
     * If [value] is a [Double] or [Long] then the returned value will possibly lose accuracy as a
     * result.
     *
     * @return [value] as an [Int]; else converted to an [Int] if possible; else null
     */
    fun getInt(): Int? {
        return if (isInt()) value as Int else asNumber()?.toInt()
    }

    /**
     * Returns the contained value as an [Long] if the contained value is a [Long], or a [Number]
     * that can be coerced to an [Long].
     *
     * If [value] is a [Double] then the returned value will possibly lose accuracy as a result.
     *
     * @return [value] as a [Long]; else converted to an [Long] if possible; else null
     */
    fun getLong(): Long? {
        return if (isLong()) value as Long
        else asNumber()?.toLong()
    }

    /**
     * Returns the contained value as an [Double] if the contained value is a [Double], or a [Number]
     * that can be coerced to an [Double].
     *
     * @return [value] as a [Double]; else converted to an [Double] if possible; else null
     */
    fun getDouble(): Double? {
        return if (isDouble()) value as Double else asNumber()?.toDouble()
    }

    /**
     * Returns the contained value as an [Boolean] if the contained value is a [Boolean].
     *
     * No type coercion is attempted.
     *
     * @return [value] as a [Boolean]; else null
     */
    fun getBoolean(): Boolean? {
        return if (isBoolean()) value as Boolean else null
    }

    /**
     * Returns the contained value as an [DataList] if the contained value is a [DataList].
     *
     * No type coercion is attempted.
     *
     * @return [value] as a [DataList]; else null
     */
    fun getDataList(): DataList? {
        return if (isDataList()) value as DataList else null
    }

    /**
     * Returns the contained value as an [DataObject] if the contained value is a [DataObject].
     *
     * No type coercion is attempted.
     *
     * @return [value] as a [DataObject]; else null
     */
    fun getDataObject(): DataObject? {
        return if (isDataObject()) value as DataObject else null
    }

    /**
     * Returns the contained value as a [Number] if the contained value is a [Number].
     * No type coercion is attempted.
     *
     * @return [value] as a [Number]; else null
     */
    private fun asNumber(): Number? {
        return value as? Number
    }

    /**
     * Returns the String representation of the [value] in a JSON compliant format.
     *
     * @return [value] as a String
     * @see DataItem
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

                    is DataObject, is DataList -> {
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
        val otherDataItem = other as? DataItem ?: return false

        if (isNull() && otherDataItem.isNull()) return true

        if (isNumber() && otherDataItem.isNumber()) {
            if (isDouble() && otherDataItem.isDouble()) {
                return getDouble() == otherDataItem.getDouble()
            }

            return getLong() == otherDataItem.getLong()
        }

        return value == otherDataItem.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    override fun asDataItem(): DataItem = this

    companion object {
        private const val NULL_STRING = "null"

        /**
         * Constant value representing null.
         *
         * It's preferable to use this instance if null is required, to save on unnecessary object
         * creation.
         */
        @JvmField
        val NULL = DataItem(any = null)

        /**
         * Creates a [DataItem] that contains a [string] as its [value]
         *
         * @param string [String] value to wrap as a [DataItem]
         */
        @JvmStatic
        fun string(string: String?): DataItem {
            return convertOrNull(string)
        }

        /**
         * Creates a [DataItem] that contains a [int] as its [value]
         *
         * @param int [Int] value to wrap as a [DataItem]
         */
        @JvmStatic
        fun int(int: Int?): DataItem {
            return convertOrNull(int)
        }

        /**
         * Creates a [DataItem] that contains a [double] as its [value]
         *
         * NaN and Infinity are not supported, and will be replaced with [NULL]
         *
         * @param double [Double] value to wrap as a [DataItem]
         */
        @JvmStatic
        fun double(double: Double?): DataItem {
            return convertOrNull(double)
        }

        /**
         * Creates a [DataItem] that contains a [long] as its [value]
         *
         * @param long [Long] value to wrap as a [DataItem]
         */
        @JvmStatic
        fun long(long: Long?): DataItem {
            return convertOrNull(long)
        }

        /**
         * Creates a [DataItem] that contains a [boolean] as its [value]
         *
         * @param boolean [Boolean] value to wrap as a [DataItem]
         */
        @JvmStatic
        fun boolean(boolean: Boolean?): DataItem {
            return convertOrNull(boolean)
        }

        /**
         * Converts an object of unknown type. This method is not guaranteed to succeed.
         * For unsupported types, [NULL] will be returned.
         */
        @JvmStatic
        fun convertOrNull(any: Any?): DataItem {
            return convert(any, NULL)
        }

        /**
         * Attempts to create a [DataItem] that contains [any] as its [value].
         * If the conversion to a [DataItem] fails for any reason, then the [default] value is
         * returned instead.
         *
         * @param any Value to wrap as a [DataItem]
         * @param default Default value to use if conversion to [DataItem] fails
         */
        @JvmStatic
        fun convert(
            any: Any?,
            default: DataItem
        ): DataItem {
            return try {
                convert(any)
            } catch (e: Exception) {
                default
            }
        }

        /**
         * Attempts to create a [DataItem] that contains [any] as its [value].
         * If the conversion to a [DataItem] fails for any reason, then the [NULL] value is
         * returned instead.
         *
         * Types that require no conversion are as follows:
         * [String], [Int], [Long], [Double], [Boolean], [DataList], [DataObject]
         *
         * If [any] is a [DataItemConvertible], then it will first be converted using
         * [DataItemConvertible.asDataItem]
         *
         * [Float] and [Short] are coerced to [Double] and [Int] respectively, whilst [Char] is also
         * coerced to a [String]
         *
         * [Collection], [JSONArray] and [Array] types will be converted to a [DataList]. Any
         * contained objects that cannot be converted to a [DataItem] will be omitted from the
         * resulting [DataList]
         *
         * [Map] and [JSONObject] types will be converted to a [DataObject]. Any contained
         * objects that cannot be converted to a [DataItem] will be omitted from the resulting
         * [DataObject]
         *
         * @param any Value to wrap as a [DataItem]
         * @return [any] as a [DataItem] if possible, else [NULL]
         */
        @JvmStatic
        @Throws(UnsupportedDataItemException::class)
        fun convert(any: Any?): DataItem {
            val supportedType = convertToSupported(any)

            return if (supportedType is DataItem) {
                supportedType
            } else {
                DataItem(supportedType)
            }
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
        internal fun lazy(string: String): DataItem {
            if (NULL_STRING == string) return NULL

            return DataItem(string = string)
        }

        /**
         * Creates a [DataItem] from it's string representation, parsing the contents of the
         * string as if it were JSON
         * i.e. string values should be quoted "value"
         */
        fun parse(string: String): DataItem {
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
                if (any is DataItem) {
                    any.value
                } else any
            } catch (ex: Exception) {
                null
            }
        }

        /**
         * Takes an object of any type and converts it into one of the supported data types if
         * possible.
         *
         * Returned values may already be instances of [DataItem] if the value provided as [any]
         * was either:
         * a) already a [DataItem], including [NULL]
         * b) implements [DataItemConvertible]
         *
         * Returned types are limited to: [String], [Int], [Long], [Double], [Boolean],
         * [DataList], [DataObject] and [DataItem]
         *
         * @param any The object to be converted to a supported type
         *
         * @return An instance of a supported value.
         */
        @Throws(UnsupportedDataItemException::class)
        private fun convertToSupported(any: Any?): Any {
            if (any == null || any === JSONObject.NULL || any === NULL) {
                return NULL
            }
            if (any is DataItem) {
                return any
            }
            if (any is DataObject ||
                any is DataList ||
                any is Boolean ||
                any is Int ||
                any is Long ||
                any is String
            ) {
                return any
            }
            if (any is DataItemConvertible) {
                return any.asDataItem()
            }
            if (any is Byte || any is Short) {
                return (any as Number).toInt()
            }
            if (any is Char) {
                return any.toString()
            }
            if (any is Double || any is Float) {
                val double = (any as Number).toDouble()
                if (!double.isFinite()) {
                    return double.toString()
                }

                return double
            }
            try {
                if (any is JSONArray) {
                    return DataList.fromJSONArray(any)
                }
                if (any is JSONObject) {
                    return DataObject.fromJSONObject(any)
                }
                if (any is Collection<*>) {
                    return DataList.fromCollection(any)
                }
                if (any.javaClass.isArray) {
                    return DataList.fromArray(any)
                }
                if (any is Map<*, *>) {
                    return DataObject.fromMap(any)
                }
            } catch (exception: UnsupportedDataItemException) {
                throw exception
            } catch (exception: Exception) {
                throw UnsupportedDataItemException(
                    "DataItem conversion failed.",
                    cause = exception
                )
            }
            throw UnsupportedDataItemException("Unsupported type for DataItem.")
        }
    }
}
