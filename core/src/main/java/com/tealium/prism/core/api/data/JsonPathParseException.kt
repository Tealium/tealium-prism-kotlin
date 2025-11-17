package com.tealium.prism.core.api.data

import com.tealium.prism.core.api.misc.TealiumException

/**
 * Base exception for all Exceptions thrown when parsing a [JsonPath] [String].
 */
abstract class JsonPathParseException(
    message: String,
    cause: Throwable? = null
) : TealiumException(message, cause)

/**
 * Indicates that the parser reached the end of the [JsonPath] string input, but was expecting additional
 * tokens.
 *
 * The [message] will contain additional information about what was expected
 */
class UnexpectedEndOfInputException(
    message: String,
    cause: Throwable? = null
): JsonPathParseException(message, cause)

/**
 * Exception to indicate that there a syntax error in the provided [JsonPath] string which could not
 * be parsed successfully.
 *
 * The [message] will contain additional information about what was expected
 */
class JsonPathSyntaxException(
    val position: Int,
    message: String,
    cause: Throwable? = null
): JsonPathParseException("$message; at position $position", cause)