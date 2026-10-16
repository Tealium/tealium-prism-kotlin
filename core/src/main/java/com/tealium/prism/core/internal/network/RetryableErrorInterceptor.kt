package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.inMilliseconds
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RequestInterceptors
import com.tealium.prism.core.api.network.RetryAfterAboveMaxDelay
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import java.util.concurrent.TimeUnit

/**
 * The [RequestInterceptor] returned by [RequestInterceptors.retryWithBackoff], which documents its
 * behavior: it retries retryable failures with a full-jitter exponential backoff, and decides when
 * to give up.
 */
internal class RetryableErrorInterceptor(
    val maxAttempts: Int?,
    val maxDelay: Long,
    private val transientBackoff: Backoff,
    private val throttlingBackoff: Backoff,
    val retryAfterAboveMaxDelay: RetryAfterAboveMaxDelay,
    private val now: () -> Long = System::currentTimeMillis
) : RequestInterceptor {

    /**
     * Creates a [RetryableErrorInterceptor] with a jittered exponential backoff for each of
     * [transientBaseDelay] and [throttlingBaseDelay], both capped at [maxDelay]; see
     * [RequestInterceptors.retryWithBackoff] for the parameters.
     */
    constructor(
        maxAttempts: Int?,
        transientBaseDelay: TimeFrame,
        throttlingBaseDelay: TimeFrame,
        maxDelay: TimeFrame,
        retryAfterAboveMaxDelay: RetryAfterAboveMaxDelay
    ) : this(
        maxAttempts = maxAttempts,
        maxDelay = maxDelay.inMilliseconds(),
        transientBackoff = Backoff.jitteredExponential(
            initialDelay = transientBaseDelay.inMilliseconds(),
            maximum = maxDelay.inMilliseconds()
        ),
        throttlingBackoff = Backoff.jitteredExponential(
            initialDelay = throttlingBaseDelay.inMilliseconds(),
            maximum = maxDelay.inMilliseconds()
        ),
        retryAfterAboveMaxDelay = retryAfterAboveMaxDelay
    )

    override fun shouldRetry(
        request: HttpRequest,
        consecutiveRetryCount: Int,
        result: NetworkResult
    ): RetryPolicy {
        val exception = (result as? Failure)?.networkException
        if (exception == null || !exception.isRetryable()) {
            return DoNotRetry
        }
        // widened to Long, so that the largest consecutiveRetryCount cannot overflow
        if (maxAttempts != null && consecutiveRetryCount.toLong() + 1 >= maxAttempts) {
            return DoNotRetry
        }

        val backoff = if (exception is Non200Exception && exception.statusCode == HTTP_TOO_MANY_REQUESTS) {
            throttlingBackoff
        } else {
            transientBackoff
        }
        val backoffDelay = backoff.delay(consecutiveRetryCount)
        val retryAfter = exception.httpResponse?.let { response -> retryAfterMillis(response, now) }
            ?: return RetryPolicy.AfterDelay(TimeFrame(backoffDelay, TimeUnit.MILLISECONDS))

        if (retryAfter > maxDelay && retryAfterAboveMaxDelay == RetryAfterAboveMaxDelay.GIVE_UP) {
            return DoNotRetry
        }
        return RetryPolicy.AfterDelay(
            TimeFrame(maxOf(retryAfter, backoffDelay), TimeUnit.MILLISECONDS)
        )
    }

    private companion object {
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
