package com.tealium.prism.core.api.network

import com.tealium.prism.core.api.misc.TealiumCallback
import com.tealium.prism.core.api.pubsub.Disposable

interface NetworkClient {
    /**
     * Sends an HTTP request asynchronously and returns the result to the provided [completion] block.
     * The request is retried if necessary based on the provided [request] and the response [NetworkResult].
     *
     * @param request The [HttpRequest] object representing the request to be sent.
     * @param completion The block to receive the result of the network request. This will be called
     * on Tealium's background thread.
     * @return A [Disposable] that can be used to cancel the request.
     */
    fun sendRequest(request: HttpRequest, completion: TealiumCallback<NetworkResult>): Disposable

    /**
     * Adds an interceptor to the client's list of interceptors.
     *
     * @param interceptor The [Interceptor] to be added.
     */
    fun addInterceptor(interceptor: Interceptor)

    /**
     * Removes an interceptor from the client's list of interceptors.
     *
     * @param interceptor The [Interceptor] to be removed.
     */
    fun removeInterceptor(interceptor: Interceptor)
}