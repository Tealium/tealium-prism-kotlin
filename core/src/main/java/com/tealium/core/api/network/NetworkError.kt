package com.tealium.core.api.network

import java.io.IOException

/**
 * Return type to signify that an error has occurred. The type returned indicates what type of
 * error has occurred, and the [isRetryable] implementation will indicate if it is safe to retry
 * the request.
 *
 * @see Non200Error
 * @see IOError
 * @see UnexpectedError
 * @see Cancelled
 */
sealed class NetworkError {
    abstract fun isRetryable() : Boolean
}

/**
 * Indicates that the response was a non-2XX HTTP status code.
 * Whether the request can be retried is determined by the [statusCode].
 *
 * @param statusCode The HTTP status code of the network response
 */
class Non200Error(
    val statusCode: Int
): NetworkError() {
    override fun isRetryable(): Boolean {
        // inclusive range?? might need updating
        return statusCode == 429 || (500.. 600).contains(statusCode)
    }

    override fun toString(): String {
        return "Non200Error($statusCode)"
    }
}

/**
 * Indicates that a connection was not able to be made - possibly due to loss of connectivity prior
 * to the connection being opened.
 * This type of error can always be retried as no connection to the destination was ever made.
 *
 * @param ex The underlying cause of the connection failure, if available
 */
class IOError(
    val ex: IOException?
): NetworkError() {
    override fun isRetryable(): Boolean {
        return true
    }

    override fun toString(): String {
        return "IOError(${ex?.message})"
    }
}

/**
 * Indicates that a network request failed for an unknown reason. It is therefore unknown whether it
 * is safe to retry the request, so it is deemed not safe to retry.
 *
 * @param ex The underlying cause of the failure, if available
 */
class UnexpectedError(
    val ex: Throwable?
): NetworkError() {
    override fun isRetryable(): Boolean {
        return false
    }

    override fun toString(): String {
        return "UnexpectedError(${ex?.message})"
    }
}

/**
 * Indicates that the request was cancelled but the requester.
 * It is therefore unknown whether it is safe to retry the request, so it is deemed not safe to
 * retry.
 */
object Cancelled: NetworkError() {
    override fun isRetryable(): Boolean {
        return false
    }

    override fun toString(): String {
        return "Cancelled"
    }
}