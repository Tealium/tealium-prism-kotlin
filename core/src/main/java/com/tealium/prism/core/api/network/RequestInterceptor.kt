package com.tealium.prism.core.api.network

/**
 * Interface for intercepting network responses and deciding whether a request is retried.
 *
 * A [NetworkClient] notifies its interceptors of the result of every attempt and asks them whether
 * to retry it; add interceptors by deriving a client with [NetworkClient.newClient]. The built-in
 * ones are created with [RequestInterceptors.retryWithBackoff] and
 * [RequestInterceptors.retryWhenConnected]. Interceptors are asked to [shouldRetry] in reverse
 * order, so add [RequestInterceptors.retryWhenConnected] last: it is then asked first and, while
 * the device is offline, waits for connectivity instead of letting
 * [RequestInterceptors.retryWithBackoff] use up its attempts:
 * ```kotlin
 * val client = networkClient.newClient(
 *     listOf(
 *         RequestInterceptors.retryWithBackoff(maxAttempts = 3),
 *         RequestInterceptors.retryWhenConnected(connectivity)
 *     ),
 *     maxRetryCount = 10
 * )
 * ```
 *
 * [onAttemptResult] and [shouldRetry] are called on the Tealium background thread.
 */
interface RequestInterceptor {

    /**
     * Called with the result of every attempt of a request.
     *
     * It is called on every interceptor, in insertion order, for every attempt, before any
     * interceptor is asked to [shouldRetry]. The [result] may therefore still be retried: never
     * assume that it is the final result of the request.
     *
     * The default implementation does nothing.
     *
     * @param request The [HttpRequest] of the attempt.
     * @param retryCount The number of retries of this request sent before this attempt, 0 for the
     * initial attempt. It counts the retries proposed by all the interceptors, unlike the
     * `consecutiveRetryCount` of [shouldRetry], and is not reset by a wait for connectivity. Once
     * the client's `maxRetryCount` is reached, the last result still arrives here, with a
     * [retryCount] equal to `maxRetryCount`. A result arriving after the request was disposed, or
     * a queued attempt cancelled before it was sent, carries the count of its own attempt.
     * @param result The [NetworkResult] of the attempt. Once a dispose of the request has taken
     * effect on the Tealium background thread, the attempt in flight arrives as a
     * [NetworkResult.Failure] of [NetworkException.CancelledException], even if the server had
     * already answered; a result processed before that arrives unchanged.
     */
    fun onAttemptResult(request: HttpRequest, retryCount: Int, result: NetworkResult) {
    }

    /**
     * Determines if a network request should be retried based on the provided parameters. An
     * interceptor that only observes results returns [RetryPolicy.DoNotRetry].
     *
     * Interceptors are asked in reverse order of insertion, and the first one returning a
     * [RetryPolicy] that retries decides the retry; [RetryPolicy.DoNotRetry] means this
     * interceptor has no opinion, so the next one is asked. No interceptor is asked once the
     * client's `maxRetryCount`, if any, has been reached (see [NetworkClient.newClient]).
     *
     * @param request The [HttpRequest] object representing the request.
     * @param consecutiveRetryCount The number of consecutive retries this interceptor proposed for
     * this request. It is 0 on this interceptor's first proposal, and is reset to 0 after another
     * interceptor proposed a retry.
     * @param result The [NetworkResult] object representing the result of the previous request attempt.
     * @return A [RetryPolicy] indicating whether the request should be retried and the retry behavior.
     */
    fun shouldRetry(request: HttpRequest, consecutiveRetryCount: Int, result: NetworkResult) : RetryPolicy

}
