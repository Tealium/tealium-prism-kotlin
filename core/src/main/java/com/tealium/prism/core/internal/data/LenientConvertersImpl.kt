package com.tealium.prism.core.internal.data

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.internal.utils.format
import java.math.BigInteger


object LenientIntConverter: DataItemConverter<Int> {
    override fun convert(dataItem: DataItem): Int? {
        // coerce between Int limits because `toInt` returns -1 and 0 for Long.MAX_VALUE and
        // Long.MIN_VALUE respectively
        return LenientLongConverter.convert(dataItem)
            ?.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            ?.toInt()
    }
}

object LenientDoubleConverter: DataItemConverter<Double> {
    override fun convert(dataItem: DataItem): Double? {
        // Try direct conversion
        val doubleValue = dataItem.getDouble()
        if (doubleValue != null)
            return doubleValue

        // Try string conversion
        val stringValue = dataItem.getString()
        if (stringValue == null)
            return null

        return stringValue.toDoubleOrNull()
    }
}

object LenientLongConverter: DataItemConverter<Long> {
    private val longMinValue = BigInteger.valueOf(Long.MIN_VALUE)
    private val longMaxValue = BigInteger.valueOf(Long.MAX_VALUE)

    override fun convert(dataItem: DataItem): Long? {
        // get as long
        val longValue = dataItem.getLong()
        if (longValue != null)
            return longValue

        val stringValue = dataItem.getString()
        if (stringValue == null)
            return null

        // read as a long first, since it can exceed a double string
        val parsedLongValue = stringValue.toClampedLongOrNull()
        if (parsedLongValue != null)
            return parsedLongValue

        val parsedDouble = stringValue.toDoubleOrNull()
        if (parsedDouble == null || parsedDouble.isNaN())
            return null

        return parsedDouble.toLong()
    }

    private fun String.toClampedLongOrNull(): Long? {
        val bigInteger = try {
            BigInteger(this.trim())
        } catch (_: NumberFormatException) {
            return null
        }

        val clamped = when {
            bigInteger < longMinValue -> longMinValue
            bigInteger > longMaxValue -> longMaxValue
            else -> bigInteger
        }

        return clamped.toLong()
    }
}

object LenientBooleanConverter: DataItemConverter<Boolean> {
    override fun convert(dataItem: DataItem): Boolean? {
        // Try direct conversion
        if (dataItem.isBoolean())
            return dataItem.getBoolean()

        // Try string conversion with common boolean representations
        val stringValue = dataItem.getString()
        if (stringValue != null) {
            val lowercased = stringValue.lowercase().trim()
            return when (lowercased) {
                "true", "yes", "1" -> true
                "false", "no", "0" -> false
                else -> null
            }
        }

        // Try numeric conversion, but only for finite whole numbers
        return when (dataItem.getDouble()) {
            0.0 -> false
            1.0 -> true
            else -> null
        }
    }
}

object LenientStringConverter: DataItemConverter<String> {
    override fun convert(dataItem: DataItem): String? {
        // no support for Lists or Objects
        if (dataItem.isDataList() || dataItem.isDataObject())
            return null

        return dataItem.format()
    }
}