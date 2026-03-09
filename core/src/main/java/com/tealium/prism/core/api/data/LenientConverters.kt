package com.tealium.prism.core.api.data

import com.tealium.prism.core.internal.data.LenientBooleanConverter
import com.tealium.prism.core.internal.data.LenientDoubleConverter
import com.tealium.prism.core.internal.data.LenientIntConverter
import com.tealium.prism.core.internal.data.LenientLongConverter
import com.tealium.prism.core.internal.data.LenientStringConverter


/**
 * A collection of lenient converters that can handle type mismatches in JSON data.
 *
 * These converters attempt multiple conversion strategies when the actual type in JSON differs from
 * the expected type. They follow a fail-safe pattern: returning `null` for invalid or non-representable
 * values rather than crashing or producing corrupted data.
 *
 * Edge cases handled:
 * - [Int]/[Long] overflow: NaN values return `null`; values outside [Int.MIN_VALUE]..[Int.MAX_VALUE]
 * or [Long.MIN_VALUE]..[Long.MAX_VALUE] (including infinities) are clamped to their respective max
 * and min values
 * - Special float values: [Double.NaN] is treated as invalid for [Int]/[Long] converters;
 * [Double.POSITIVE_INFINITY] and [Double.NEGATIVE_INFINITY] are clamped in integer max and min values
 * - String parsing: Invalid formats return `null` rather than crashing
 * - Type mismatches: Missing or incompatible types return `null`
 *
 * Example usage:
 * ```kotlin
 * val payload: DataObject = ...
 * val timeout = payload.get("timeout", LenientConverters.DOUBLE)
 * val retryCount = payload.get("retryCount", LenientConverters.INT)
 * val isEnabled = payload.get("isEnabled", LenientConverters.BOOLEAN)
 * ```
 */
object LenientConverters {

    /**
     * A lenient converter to [Double] values.
     *
     * This converter attempts the following conversions in order:
     * 1. Direct extraction as [Double]
     * 2. Extraction as [String] and parsing via [toDoubleOrNull]
     *
     */
    val DOUBLE: DataItemConverter<Double> =
        LenientDoubleConverter

    /**
     * A lenient converter to [Int] values.
     *
     * This converter attempts the following conversions in order:
     * 1. Direct extraction as [Int]
     * 2. Extraction as [String] and parsing
     *
     * Returns `null` for values that cannot be parsed as numbers. Decimals are truncated and out of
     * bounds numbers will be coerced to between [Int.MIN_VALUE] and [Int.MAX_VALUE]
     *
     */
    val INT: DataItemConverter<Int> =
        LenientIntConverter

    /**
     * A lenient converter to [Long] values.
     *
     * This converter attempts the following conversions in order:
     * 1. Direct extraction as [Long]
     * 2. Extraction as [String] and parsing
     *
     * Returns `null` for values that cannot be parsed as numbers. Decimals are truncated and out of
     * bounds numbers will be coerced to between [Long.MIN_VALUE] and [Long.MAX_VALUE]
     *
     */
    val LONG: DataItemConverter<Long> =
        LenientLongConverter

    /**
     * A lenient converter to [Boolean] values.
     *
     * This converter attempts the following conversions in order:
     * 1. Direct extraction as [Boolean]
     * 2. Extraction as [String] and a case-insensitive parsing of common boolean representations:
     *    - "true", "yes", "1" -> `true`
     *    - "false", "no", "0" -> `false`
     * 3. Extraction as whole number where 0 is `false` and 1 is `true`
     *
     * Returns `null` for other strings non-conforming to the rule above and any numbers other than
     * 0 or 1
     *
     */
    val BOOLEAN: DataItemConverter<Boolean> =
        LenientBooleanConverter

    /**
     * A lenient converter to [String] values.
     *
     * This converter attempts the following conversions in order:
     * 1. Direct extraction as [String]
     * 2. Conversion of [Boolean] to "true" or "false"
     * 3. Conversion of numeric types to [String]
     *    - Special values: [Double.NaN] -> `"NaN"`, [Double.POSITIVE_INFINITY] -> `"Infinity"`
     *    - Whole numbers: 1.0 → `"1"` (removes unnecessary ".0" suffix)
     *    - Disables scientific notation for better readability
     */
    val STRING: DataItemConverter<String> =
        LenientStringConverter

}