package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.internal.network.NetworkHelperImpl

/**
 * Utility class for making asynchronous Http requests.
 *
 * Requests will be made on an IO thread, but any completions will be executed on the Tealium background
 * thread.
 *
 * Each client has a fixed list of [RequestInterceptor]s that decide whether a failed request is
 * retried. The shared client in [NetworkUtilities.networkClient] has none, so it makes a single
 * attempt per request; use [newClient] to derive a client that retries, e.g. with
 * [RequestInterceptors.retryWithBackoff] and [RequestInterceptors.retryWhenConnected].
 */
interface NetworkClient {
    /**
     * Sends an HTTP request asynchronously and returns the result to the provided [completion] block.
     * The request is retried, when necessary, as decided by this client's interceptors, based on the
     * provided [request] and the response [NetworkResult].
     *
     * @param request The [HttpRequest] object representing the request to be sent.
     * @param completion The block to receive the result of the network request. This will be called
     * on Tealium's background thread.
     * @return A [Disposable] that can be used to cancel the request.
     */
    fun sendRequest(request: HttpRequest, completion: Callback<NetworkResult>): Disposable

    /**
     * Builds the [request] and sends it asynchronously, returning the result to the provided
     * [completion] block.
     *
     * Unlike [sendRequest], this overload takes an unbuilt [HttpRequest.Builder] and so absorbs
     * the [MalformedURLException] that [HttpRequest.Builder.build] may throw for an invalid url.
     * In that case no request is made, [completion] is called with a [NetworkResult.Failure]
     * wrapping a [NetworkException.UnexpectedException], and a disposed [Disposable] is returned.
     *
     * @param request The [HttpRequest.Builder] representing the request to be built and sent.
     * @param completion The block to receive the result of the network request. This will be called
     * on Tealium's background thread.
     * @return A [Disposable] that can be used to cancel the request.
     */
    fun sendRequest(request: HttpRequest.Builder, completion: Callback<NetworkResult>): Disposable

    /**
     * Creates a new [NetworkClient] from this instance which will also use the given interceptors.
     *
     * Its interceptors are this instance's followed by [additionalInterceptors]:
     *  - The given interceptors come after this instance's, so they are asked first for retry
     *  decisions (see [RequestInterceptor.shouldRetry]).
     *  - [RequestInterceptor.onAttemptResult] is called on all the interceptors in list order.
     *
     * This instance is unaffected: its requests keep going only through its own interceptors.
     *
     * @param additionalInterceptors The interceptors to add after this instance's interceptors.
     * @param maxRetryCount The maximum number of retries of a request, whichever interceptor
     * proposes them; `null` means no limit. Not inherited from this instance.
     * @return A new [NetworkClient] instance.
     */
    fun newClient(additionalInterceptors: List<RequestInterceptor>, maxRetryCount: Int?): NetworkClient

    /**
     * Same as [newClient] with a `maxRetryCount`, with no limit on the number of retries.
     *
     * @param additionalInterceptors The interceptors to add after this instance's interceptors.
     * @return A new [NetworkClient] instance.
     */
    fun newClient(additionalInterceptors: List<RequestInterceptor>): NetworkClient =
        newClient(additionalInterceptors, null)

    /**
     * Creates a [NetworkHelper] that sends every request through this client.
     *
     * Typically used with a client derived with [newClient], to use the [NetworkHelper] API with
     * custom retry behavior.
     *
     * @return A new [NetworkHelper] that sends its requests through this client.
     */
    fun makeNetworkHelper(): NetworkHelper = NetworkHelperImpl(this)
}