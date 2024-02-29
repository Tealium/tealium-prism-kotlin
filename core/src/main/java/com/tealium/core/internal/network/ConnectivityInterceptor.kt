package com.tealium.core.internal.network

import android.content.Context
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.DoNotRetry
import com.tealium.core.api.network.Failure
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.Interceptor
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.RetryAfterEvent
import com.tealium.core.api.network.RetryPolicy
import com.tealium.core.internal.Singleton

/**
 * The [ConnectivityInterceptor] is an [Interceptor] implementation that uses [Connectivity] to
 * determine whether or not any given network request is safe to execute.
 *
 * For failures resulting from loss of connectivity, it will await the return of connectivity before
 * retrying.
 *
 * Note. The internal constructor is for testing purposes only, and access to the [ConnectivityInterceptor]
 * should typically be via its [Companion] singleton instead as it is safe to share this between
 * Tealium instances
 */
class ConnectivityInterceptor internal constructor(
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

    companion object : Singleton<ConnectivityInterceptor, Context>({ context ->
        ConnectivityInterceptor(ConnectivityRetriever.getInstance(context))
    })
}