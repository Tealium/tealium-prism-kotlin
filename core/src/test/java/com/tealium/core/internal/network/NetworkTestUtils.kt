package com.tealium.core.internal.network

import com.tealium.core.api.data.TealiumDeserializable
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.network.DeserializedNetworkCallback
import com.tealium.core.api.network.HttpResponse
import com.tealium.core.api.network.NetworkException
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.internal.pubsub.Subscription
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.slot
import java.net.URL

val localhost = URL("https://localhost/")

fun <T: TealiumSerializable> NetworkHelper.mockGetTealiumDeserializableSuccess(
    value: T,
    deserializer: TealiumDeserializable<T>,
    headers: Map<String, List<String>> = mapOf(),
    statusCode: Int = 200,
    url: URL = localhost
) {
    mockGetTealiumDeserializableResponse(
        TealiumResult.success(
            NetworkHelper.HttpValue(
                value,
                HttpResponse(url, statusCode, "", headers, value.asTealiumValue().toString())
            )
        ),
        deserializer
    )
}

fun <T> NetworkHelper.mockGetTealiumDeserializableFailure(
    deserializer: TealiumDeserializable<T>,
    cause: NetworkException = NetworkException.UnexpectedException(null)
) {
    mockGetTealiumDeserializableResponse(
        TealiumResult.failure(cause),
        deserializer
    )
}

fun <T> NetworkHelper.mockGetTealiumDeserializableResponse(
    response: TealiumResult<NetworkHelper.HttpValue<T>>,
    deserializer: TealiumDeserializable<T>,
    completionCapture: CapturingSlot<DeserializedNetworkCallback<T>> = slot()
) {
    every {
        getTealiumDeserializable(
            any<URL>(),
            any(),
            deserializer,
            capture(completionCapture)
        )
    } answers {
        completionCapture.captured.onComplete(response)
        Subscription()
    }
}