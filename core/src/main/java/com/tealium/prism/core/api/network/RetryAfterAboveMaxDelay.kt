package com.tealium.prism.core.api.network

/**
 * What an interceptor does when a retryable failure has a `Retry-After` header longer than the
 * interceptor's maximum delay.
 */
enum class RetryAfterAboveMaxDelay {
    /**
     * Stop proposing a retry for that failure: the interceptor returns [RetryPolicy.DoNotRetry]
     * (no opinion), so the request completes with that failure unless another interceptor
     * retries it.
     */
    GIVE_UP,

    /**
     * Wait for the full `Retry-After`, with no upper bound.
     */
    WAIT
}
