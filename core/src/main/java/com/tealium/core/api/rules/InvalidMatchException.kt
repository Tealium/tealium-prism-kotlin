package com.tealium.core.api.rules

/**
 * Exception class to signify that the [Matchable] has failed in an exceptional way, as opposed
 * to having not matched the input.
 */
abstract class InvalidMatchException(message: String? = null, cause: Throwable? = null) :
    RuntimeException(message, cause)