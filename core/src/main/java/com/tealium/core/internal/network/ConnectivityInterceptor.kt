package com.tealium.core.internal.network

import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.DoNotRetry
import com.tealium.core.api.network.Failure
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.Interceptor
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.RetryAfterEvent
import com.tealium.core.api.network.RetryPolicy

/**
 * The [ConnectivityInterceptor] is an [Interceptor] implementation that uses [Connectivity] to
 * determine whether or not any given network request is safe to execute.
 *
 * For failures resulting from loss of connectivity, it will await the return of connectivity before
 * retrying.
 *
 */
class ConnectivityInterceptor(
    private val connectivity: Connectivity
) : Interceptor {

    override fun shouldRetry(
        request: HttpRequest,
        result: NetworkResult,
        retryCount: Int
    ): RetryPolicy {
        return when (result) {
            is Failure -> {
                if (!connectivity.isConnected() && result.networkError.isRetryable()) {
                    RetryAfterEvent(connectivity.onConnectionStatusUpdated.filter { status -> status == Connectivity.Status.Connected })
                } else {
                    DoNotRetry
                }
            }

            else -> DoNotRetry
        }
    }


    override fun didComplete(request: HttpRequest, result: NetworkResult) {
        // do nothing
    }
}