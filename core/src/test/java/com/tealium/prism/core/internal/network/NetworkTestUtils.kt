package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.network.DeserializedNetworkCallback
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.network.Connectivity
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.network.NetworkClient
import com.tealium.prism.core.api.network.NetworkHelper
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.pubsub.Subscription
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.slot
import java.net.URL
import java.util.Collections

val localhost = URL("https://localhost/")

/**
 * A [Connectivity] backed by a real [StateSubject]; connected while its [status] is
 * [Connectivity.Status.Connected].
 */
class FakeConnectivity(
    initialStatus: Connectivity.Status = Connectivity.Status.Connected(Connectivity.ConnectivityType.WIFI)
) : Connectivity {
    val status: StateSubject<Connectivity.Status> = Observables.stateSubject(initialStatus)

    override val connectionStatus: ObservableState<Connectivity.Status>
        get() = status.asObservableState()

    override fun isConnected(): Boolean = status.value is Connectivity.Status.Connected

    override fun connectionType(): Connectivity.ConnectivityType =
        (status.value as? Connectivity.Status.Connected)?.connectivityType
            ?: Connectivity.ConnectivityType.UNKNOWN

    fun connect() = status.onNext(Connectivity.Status.Connected(Connectivity.ConnectivityType.WIFI))

    fun disconnect() = status.onNext(Connectivity.Status.NotConnected)
}

/**
 * A [NetworkClient] that records the arguments of every [newClient] call and returns [derived]
 * from it, or itself when there is none. It fails any request sent through it directly, unless
 * [acceptRequests] is `true`.
 *
 * @param derived The client returned by [newClient], or `null` to return this client.
 * @param acceptRequests Whether requests sent through this client are recorded in [requests] and
 * left pending, instead of failing the test.
 */
class RecordingNetworkClient(
    private val derived: NetworkClient? = null,
    private val acceptRequests: Boolean = false
) : NetworkClient {
    val additionalInterceptors = mutableListOf<List<RequestInterceptor>>()
    val maxRetryCounts = mutableListOf<Int?>()
    val requests: MutableList<HttpRequest> = Collections.synchronizedList(mutableListOf())

    override fun sendRequest(request: HttpRequest, completion: Callback<NetworkResult>): Disposable {
        if (!acceptRequests) throw AssertionError("Requests should be sent through a derived client")

        requests.add(request)
        return Disposables.subscription {}
    }

    override fun sendRequest(request: HttpRequest.Builder, completion: Callback<NetworkResult>): Disposable =
        sendRequest(request.build(), completion)

    override fun newClient(additionalInterceptors: List<RequestInterceptor>, maxRetryCount: Int?): NetworkClient {
        this.additionalInterceptors.add(additionalInterceptors)
        maxRetryCounts.add(maxRetryCount)
        return derived ?: this
    }
}

fun <T : DataItemConvertible> NetworkHelper.mockGetDataItemConvertibleSuccess(
    value: T,
    converter: DataItemConverter<T>,
    headers: Map<String, List<String>> = mapOf(),
    statusCode: Int = 200,
    url: URL = localhost
) {
    mockGetDataItemConvertibleResponse(
        TealiumResult.success(
            NetworkHelper.HttpValue(
                value,
                HttpResponse(
                    url,
                    statusCode,
                    "",
                    headers,
                    value.asDataItem().toString().toByteArray(Charsets.UTF_8)
                )
            )
        ),
        converter
    )
}

fun <T> NetworkHelper.mockGetDataItemConvertibleFailure(
    converter: DataItemConverter<T>,
    cause: NetworkException = NetworkException.UnexpectedException(null)
) {
    mockGetDataItemConvertibleResponse(
        TealiumResult.failure(cause),
        converter
    )
}

fun <T> NetworkHelper.mockGetDataItemConvertibleResponse(
    response: TealiumResult<NetworkHelper.HttpValue<T>>,
    converter: DataItemConverter<T>,
    completionCapture: CapturingSlot<DeserializedNetworkCallback<T>> = slot()
) {
    every {
        getDataItemConvertible(
            any<URL>(),
            any(),
            any(),
            converter,
            capture(completionCapture)
        )
    } answers {
        completionCapture.captured.onComplete(response)
        Subscription()
    }
}