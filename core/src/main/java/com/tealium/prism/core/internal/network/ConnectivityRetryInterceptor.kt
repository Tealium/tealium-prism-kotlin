package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.Connectivity
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RequestInterceptors
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry

/**
 * The [RequestInterceptor] returned by [RequestInterceptors.retryWhenConnected], which documents
 * its behavior: it makes a retryable failure wait for connectivity to return while the device is
 * not connected.
 *
 * @param connectivity The [Connectivity] used to know when the device is connected again.
 */
internal class ConnectivityRetryInterceptor(
    val connectivity: Connectivity
) : RequestInterceptor {

    override fun shouldRetry(
        request: HttpRequest,
        consecutiveRetryCount: Int,
        result: NetworkResult
    ): RetryPolicy {
        return when (result) {
            is Failure -> {
                if (!connectivity.connectionStatus.value.isAssumedConnected
                    && result.networkException.isRetryable()
                ) {
                    RetryPolicy.AfterEvent(
                        connectivity.connectionStatus.filter { status -> status.isAssumedConnected }
                    )
                } else {
                    DoNotRetry
                }
            }

            else -> DoNotRetry
        }
    }
}
