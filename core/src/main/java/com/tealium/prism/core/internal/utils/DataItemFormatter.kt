package com.tealium.prism.core.internal.utils

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataList
import com.tealium.prism.core.api.data.DataObject
import java.math.BigDecimal

/**
 * Utility function to format a [DataItem]'s contained value as a [String].
 * - [Double]s will be in a [humanReadable] format
 * - [DataObject] and [DataList] will be formatted as regular JSON.
 * - `null` or [DataItem.NULL] will return `null`
 */
fun DataItem?.format() : String? {
    val value = this?.value
    if (value == null) {
        return null
    }

    if (value is Double) {
        return value.humanReadable()
    }

    return value.toString()
}

/**
 * Formats a [Double] as human-readable number, i.e.
 *  - not Scientific notation
 *  - trimmed decimal placings and trailing zeros
 *  - non-finite values are stringified; "NaN", "Infinity", "-Infinity"
 */
fun Double.humanReadable() : String =
    if (!isFinite()) {
        toString()
    } else {
        BigDecimal.valueOf(this)
            .stripTrailingZeros()
            .toPlainString()
    }