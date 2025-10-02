package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.data.DataItemConverter
import com.tealium.prism.core.api.data.DataItemConvertible
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.network.DeserializedNetworkCallback
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.network.NetworkHelper
import com.tealium.prism.core.internal.pubsub.Subscription
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.slot
import java.net.URL

val localhost = URL("https://localhost/")

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
            converter,
            capture(completionCapture)
        )
    } answers {
        completionCapture.captured.onComplete(response)
        Subscription()
    }
}