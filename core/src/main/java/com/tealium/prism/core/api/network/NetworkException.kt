package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.misc.TealiumIOException
import java.io.IOException

/**
 * Return type to signify that an error has occurred. The type returned indicates what type of
 * error has occurred, and the [isRetryable] implementation will indicate if it is safe to retry
 * the request.
 *
 * Where a response was received before the request failed, it is available on [httpResponse] for
 * further inspection of the failure.
 *
 * @param httpResponse The response received before the failure - e.g. the response headers, or an
 * error body returned by the server. It is `null` where the request failed before any response was
 * received, and may be missing its [HttpResponse.body], as reading the body is best-effort only.
 *
 * @see Non200Exception
 * @see NetworkIOException
 * @see UnexpectedException
 * @see CancelledException
 */
sealed class NetworkException(
    message: String? = null,
    cause: Throwable? = null,
    val httpResponse: HttpResponse? = null
): TealiumIOException(message, cause) {
    abstract fun isRetryable() : Boolean

    /**
     * Indicates that the response was a non-2XX HTTP status code.
     * Whether the request can be retried is determined by the [statusCode].
     *
     * @param statusCode The HTTP status code of the network response
     * @param httpResponse The response that was received, whose [HttpResponse.body] carries any
     * error body returned by the server, if it could be read
     */
    class Non200Exception @JvmOverloads constructor(
        val statusCode: Int,
        httpResponse: HttpResponse? = null
    ): NetworkException(httpResponse = httpResponse) {
        override fun isRetryable(): Boolean {
            // inclusive range?? might need updating
            return statusCode == 429 || (500.. 600).contains(statusCode)
        }

        override fun toString(): String {
            return "Non200Exception($statusCode)"
        }
    }

    /**
     * Indicates that the request failed with an [IOException] - possibly due to loss of
     * connectivity before the connection was opened, or while reading the response.
     * This type of error can always be retried, as the request never completed.
     *
     * @param cause The underlying cause of the failure, if available
     * @param httpResponse The response received before the failure, if any.
     */
    class NetworkIOException @JvmOverloads constructor(
        cause: IOException?,
        httpResponse: HttpResponse? = null
    ): NetworkException(cause?.message, cause, httpResponse) {
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
     * @param httpResponse The response, if one was received before the failure
     */
    class UnexpectedException @JvmOverloads constructor(
        cause: Throwable?,
        httpResponse: HttpResponse? = null
    ): NetworkException(cause?.message, cause, httpResponse) {
        override fun isRetryable(): Boolean {
            return false
        }

        override fun toString(): String {
            return "UnexpectedException(${cause?.message})"
        }
    }

    /**
     * Indicates that the request was canceled by the requester.
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
