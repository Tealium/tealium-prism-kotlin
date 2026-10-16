package com.tealium.tests.common

import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry

/**
 * Records its [RequestInterceptor.onAttemptResult] and [shouldRetry] calls into [calls], prefixed
 * with [name] and with the count it is given, e.g. `"name.onAttemptResult(1)"`, and returns the
 * policy from [policy] for the consecutive retry count it is given.
 *
 * [onResult] is called with the result of every attempt, after recording it.
 *
 * Pass a synchronized list where the client calls it from another thread.
 */
class RecordingInterceptor(
    private val name: String,
    private val calls: MutableList<String>,
    private val onResult: (NetworkResult) -> Unit = {},
    private val policy: (consecutiveRetryCount: Int) -> RetryPolicy = { DoNotRetry }
) : RequestInterceptor {
    override fun onAttemptResult(request: HttpRequest, retryCount: Int, result: NetworkResult) {
        calls.add("$name.onAttemptResult($retryCount)")
        onResult(result)
    }

    override fun shouldRetry(
        request: HttpRequest,
        consecutiveRetryCount: Int,
        result: NetworkResult
    ): RetryPolicy {
        calls.add("$name.shouldRetry($consecutiveRetryCount)")
        return policy(consecutiveRetryCount)
    }
}
