package com.tealium.core.internal.network

/**
 * Interface for intercepting network requests and responses.
 */
interface Interceptor {

    /**
     * Called when a network request is completed along with the corresponding result.
     *
     * @param request The [HttpRequestData] object representing the completed request.
     * @param result The [NetworkResult] object representing the result of the request.
     */
    fun didComplete(request: HttpRequestData, result: NetworkResult) // unsure of this naming. rename - onRequestResult?

    /**
     * Determines if a network request should be retried based on the provided parameters.
     *
     * @param request The [HttpRequestData] object representing the request.
     * @param result The [NetworkResult] object representing the result of the previous request attempt.
     * @param retryCount The number of retry attempts made so far.
     * @return A [RetryPolicy] indicating whether the request should be retried and the retry behavior.
     */
    fun shouldRetry(request: HttpRequestData, result: NetworkResult, retryCount: Int) : RetryPolicy

    /**
     * Determines if a network request should be delayed before being sent.
     *
     * @param request The [HttpRequestData] object representing the request.
     * @return A [DelayPolicy] indicating whether the request should be delayed and the delay behavior.
     */
    fun shouldDelay(request: HttpRequestData): DelayPolicy
}