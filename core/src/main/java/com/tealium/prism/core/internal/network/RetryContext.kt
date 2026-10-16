package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.RequestInterceptor

/**
 * Describes the retries already sent for a request.
 *
 * The [InterceptorManager] advances it with [nextRetry] for the [RequestInterceptor] that proposed
 * the retry. Interceptors never see the context: they receive only their consecutive retry count
 * and, in [RequestInterceptor.onAttemptResult], the [retryCount].
 *
 * @property retryCount The retries already sent for this request, whichever interceptor proposed them.
 * @property lastRetryInterceptor The interceptor that proposed the last retry; `null` before the first retry.
 * @property consecutiveRetryCount The retries proposed in a row by [lastRetryInterceptor], including the last one.
 */
internal class RetryContext private constructor(
    val retryCount: Int,
    val lastRetryInterceptor: RequestInterceptor?,
    val consecutiveRetryCount: Int
) {

    /**
     * The consecutive retry count seen by [interceptor]: its current run if it proposed the last
     * retry, 0 otherwise.
     *
     * Interceptors are compared by identity.
     */
    fun consecutiveRetryCount(interceptor: RequestInterceptor): Int =
        if (lastRetryInterceptor === interceptor) consecutiveRetryCount else 0

    /**
     * The next attempt's context, after [proposedBy] proposed a retry.
     */
    fun nextRetry(proposedBy: RequestInterceptor): RetryContext {
        val newConsecutiveRetryCount =
            if (lastRetryInterceptor === proposedBy) consecutiveRetryCount + 1 else 1
        return RetryContext(
            retryCount = retryCount + 1,
            lastRetryInterceptor = proposedBy,
            consecutiveRetryCount = newConsecutiveRetryCount
        )
    }

    companion object {
        /**
         * The context of a request that was never retried.
         */
        val INITIAL = RetryContext(
            retryCount = 0,
            lastRetryInterceptor = null,
            consecutiveRetryCount = 0
        )
    }
}
