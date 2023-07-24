package com.tealium.core.internal.network

import kotlinx.coroutines.Deferred

interface NetworkClient {
    /**
     * Sends an HTTP request asynchronously and returns a [Deferred] that represents the ongoing request.
     * The request is retried if necessary based on the provided [request] and the response [NetworkResult].
     *
     * @param request The [HttpRequestData] object representing the request to be sent.
     * @return A [Deferred] that resolves to a [NetworkResult] representing the result of the request.
     */
    fun sendRequestAsync(request: HttpRequestData): Deferred<NetworkResult>

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