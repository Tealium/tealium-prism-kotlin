package com.tealium.core.api.network

/**
 * Describes the available results of a network request.
 *
 * @see Success
 * @see Failure
 */
sealed class NetworkResult

/**
 * [Success] indicates a successful network request, returning the relevant HTTP response data.
 *
 * @param httpResponse The HTTP response data returned for this request
 * @see Failure
 */
class Success(val httpResponse: HttpResponse) : NetworkResult() {
    override fun toString(): String {
        return "Success(${httpResponse.statusCode})"
    }
}

/**
 * [Failure] indicates that the network request was unsuccessful for the reason given by the
 * returned [networkError]
 *
 * [Failure] does not always mean that the connection was not made, however, and the [networkError]
 * should therefore be inspected for the failure reasons and whether or not the request
 * can be retried.
 *
 * @param networkError The underlying cause of the failure
 * @see Success
 */
class Failure(val networkError: NetworkError) : NetworkResult() {
    override fun toString(): String {
        return "Failure(${networkError})"
    }
}