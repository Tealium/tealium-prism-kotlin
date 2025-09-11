package com.tealium.core.api.rules

import com.tealium.core.api.rules.Condition.Operator
import com.tealium.core.api.data.DataItem

/**
 * Base exception for exceptions thrown by [Operator] implementations. This exception is used to
 * denote that an [Operator] has completed exceptionally, and is therefore unable to return a valid
 * true/false result.
 */
abstract class OperatorFailedException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Indicates that the [Condition.filter] was required, but was unavailable.
 *
 * This is a configuration error.
 */
class MissingFilterException(message: String = "Filter cannot be null.") :
    OperatorFailedException(message)

/**
 * Indicates that the [DataItem] was not found whilst evaluating the [Condition.operator]
 */
class MissingDataItemException(message: String = "DataItem not found.") :
    OperatorFailedException(message)

/**
 * Indicates that a numeric type was expected, but was not found or was not parseable to a numeric type
 */
class NumberParseException(message: String, cause: Throwable? = null) :
    OperatorFailedException(message, cause) {

    constructor(
        numberString: String,
        source: String,
        cause: Throwable? = null
    ) : this("Failed to parse number from $source: \"$numberString\"", cause)
}

/**
 * Indicates that an operator was applied to an unsupported input type. Not all [Condition.Operator]
 * implementations are supported for all input types.
 */
class UnsupportedOperatorException(message: String, cause: Throwable?) :
    OperatorFailedException(message, cause) {

    constructor(
        operatorId: String,
        dataItem: DataItem,
        cause: Throwable? = null
    ) : this(
        "Failed to apply operator \"$operatorId\" to type ${dataItem.value?.javaClass?.simpleName}",
        cause
    )

    constructor(
        operatorId: String,
        containerItem: DataItem,
        dataItem: DataItem,
        cause: Throwable? = null
    ) : this(
        "Failed to apply operator \"$operatorId\" to type ${containerItem.value?.javaClass?.simpleName} containing ${dataItem.value?.javaClass?.simpleName}>",
        cause
    )
}