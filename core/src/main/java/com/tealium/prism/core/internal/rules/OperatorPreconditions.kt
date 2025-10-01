package com.tealium.prism.core.internal.rules

import com.tealium.prism.core.api.data.DataItem
import com.tealium.prism.core.api.rules.Condition.Operator
import com.tealium.prism.core.api.rules.MissingDataItemException
import com.tealium.prism.core.api.rules.MissingFilterException
import com.tealium.prism.core.api.rules.NumberParseException

/**
 * Utility functions for throwing standard exceptions used by [Operator] implementations
 */
object OperatorPreconditions {

    /**
     * @return [filter] when not null
     * @throws MissingFilterException when [filter] is null
     */
    @Throws(MissingFilterException::class)
    fun requireFilter(filter: String?): String =
        filter ?: throw MissingFilterException()

    /**
     * @return [dataItem] when not null
     * @throws MissingDataItemException when [dataItem] is null
     */
    @Throws(MissingDataItemException::class)
    fun requireDataItem(dataItem: DataItem?): DataItem =
        dataItem ?: throw MissingDataItemException()

    /**
     * @return a parsed [Double] value from the given [filter]
     * @throws NumberParseException when the [filter] cannot be successfully parsed to a [Double]
     */
    @Throws(NumberParseException::class)
    fun parseDouble(filter: String?): Double =
        parseDouble(filter, "Filter")

    /**
     * @return a parsed [Double] value from the given [dataItem]
     * @throws NumberParseException when the [dataItem] cannot be successfully parsed to a [Double]
     */
    @Throws(NumberParseException::class)
    fun parseDouble(dataItem: DataItem): Double =
        parseDouble(dataItem.getString(), "DataItem")

    /**
     * Internal method that attempts to parse a [Double] from the input [numberString]
     *
     * If the parsing fails, then a [NumberParseException] is thrown.
     */
    @Throws(NumberParseException::class)
    private fun parseDouble(numberString: String?, source: String): Double {
        if (numberString == null)
            throw NumberParseException("null", source)

        return try {
            numberString.toDouble()
        } catch (ex: NumberFormatException) {
            throw NumberParseException(numberString, source, ex)
        }
    }
}