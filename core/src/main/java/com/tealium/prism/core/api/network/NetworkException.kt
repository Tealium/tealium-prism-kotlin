package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.misc.TealiumIOException
import com.tealium.prism.core.api.network.NetworkException.CancelledException
import com.tealium.prism.core.api.network.NetworkException.NetworkIOException
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkException.UnexpectedException
import java.io.IOException

/**
 * Return type to signify that an error has occurred. The type returned indicates what type of
 * error has occurred, and the [isRetryable] implementation will indicate if it is safe to retry
 * the request.
 *
 * @see Non200Exception
 * @see NetworkIOException
 * @see UnexpectedException
 * @see CancelledException
 */
sealed class NetworkException(
    message: String? = null,
    cause: Throwable? = null
): TealiumIOException(message, cause) {
    abstract fun isRetryable() : Boolean

    /**
     * Indicates that the response was a non-2XX HTTP status code.
     * Whether the request can be retried is determined by the [statusCode].
     *
     * @param statusCode The HTTP status code of the network response
     */
    class Non200Exception(
        val statusCode: Int
    ): NetworkException() {
        override fun isRetryable(): Boolean {
            // inclusive range?? might need updating
            return statusCode == 429 || (500.. 600).contains(statusCode)
        }

        override fun toString(): String {
            return "Non200Exception($statusCode)"
        }
    }

    /**
     * Indicates that a connection was not able to be made - possibly due to loss of connectivity prior
     * to the connection being opened.
     * This type of error can always be retried as no connection to the destination was ever made.
     *
     * @param cause The underlying cause of the connection failure, if available
     */
    class NetworkIOException(
        cause: IOException?
    ): NetworkException(cause?.message, cause) {
        override fun isRetryable(): Boolean {
            return true
        }

        override fun toString(): String {
            return "NetworkIOException(${cause?.message})"
        }
    }

    /**
     * Indicates that a network request failed for an unknown reason. It is therefore unknown whether it
     * is safe to retry the request, so it is deemed not safe to retry.
     *
     * @param cause The underlying cause of the failure, if available
     */
    class UnexpectedException(
        cause: Throwable?
    ): NetworkException(cause?.message, cause) {
        override fun isRetryable(): Boolean {
            return false
        }

        override fun toString(): String {
            return "UnexpectedException(${cause?.message})"
        }
    }

    /**
     * Indicates that the request was cancelled by the requester.
     * It is therefore unknown whether it is safe to retry the request, so it is deemed not safe to
     * retry.
     */
    object CancelledException: NetworkException() {
        override fun isRetryable(): Boolean {
            return false
        }

        override fun toString(): String {
            return "Cancelled"
        }
    }
}
