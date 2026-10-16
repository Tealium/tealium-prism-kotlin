package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RetryPolicy

/**
 * Forwards the result of each request attempt to the [interceptors] and resolves the retry decision.
 *
 * Waiting for the chosen [RetryPolicy] is left to the caller.
 *
 * @param interceptors The interceptors, in insertion order.
 * @param maxRetryCount The maximum number of retries of a request, whichever interceptor proposes
 * them; `null` means no limit.
 */
internal class InterceptorManager(
    val interceptors: List<RequestInterceptor>,
    val maxRetryCount: Int? = null
) {

    /**
     * A retry proposed by one of the interceptors.
     *
     * @param policy The [RetryPolicy] to wait for before retrying; never [RetryPolicy.DoNotRetry].
     * @param context The [RetryContext] of the next attempt.
     */
    class Retry(
        val policy: RetryPolicy,
        val context: RetryContext
    )

    /**
     * Forwards the [result] of an attempt of [request] to all the interceptors:
     * [RequestInterceptor.onAttemptResult] is called on every interceptor, in list order.
     *
     * Called for every attempt, before [decide].
     *
     * @param request The request that was just completed.
     * @param retryCount The number of retries of [request] sent before this attempt (see
     * [RetryContext.retryCount]).
     * @param result The [NetworkResult] of the attempt.
     */
    fun notifyAttemptResult(request: HttpRequest, retryCount: Int, result: NetworkResult) {
        interceptors.forEach { interceptor ->
            interceptor.onAttemptResult(request, retryCount, result)
        }
    }

    /**
     * Resolves the retry decision for the [result] of an attempt of [request].
     *
     * Unless [maxRetryCount] has been reached (in which case no interceptor is asked and the
     * request is not retried), interceptors are asked in reverse order with their own consecutive
     * retry count (see [RetryContext.consecutiveRetryCount]); the first one whose policy retries
     * wins.
     *
     * @param request The request that was just completed.
     * @param context The [RetryContext] describing the retries already sent for [request].
     * @param result The [NetworkResult] of the attempt.
     * @return The winning policy along with the next attempt's context, or `null` when the request
     * must not be retried.
     */
    fun decide(
        request: HttpRequest,
        context: RetryContext,
        result: NetworkResult
    ): Retry? {
        if (maxRetryCount != null && context.retryCount >= maxRetryCount) {
            return null
        }

        interceptors.asReversed().forEach { interceptor ->
            val policy = interceptor.shouldRetry(
                request,
                context.consecutiveRetryCount(interceptor),
                result
            )
            if (policy !is RetryPolicy.DoNotRetry) {
                return Retry(policy, context.nextRetry(proposedBy = interceptor))
            }
        }
        return null
    }

    /**
     * Creates a new manager using this manager's interceptors followed by [additionalInterceptors].
     *
     * This manager is unaffected: it keeps using only its own interceptors.
     *
     * @param additionalInterceptors The interceptors to append to this manager's interceptors.
     * @param maxRetryCount The maximum number of retries of a request, whichever interceptor
     * proposes them; `null` means no limit. Not inherited from this manager.
     */
    fun newManager(
        additionalInterceptors: List<RequestInterceptor>,
        maxRetryCount: Int?
    ): InterceptorManager =
        InterceptorManager(interceptors + additionalInterceptors, maxRetryCount)
}
