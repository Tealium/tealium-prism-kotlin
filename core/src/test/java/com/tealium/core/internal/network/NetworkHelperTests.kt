package com.tealium.core.internal.network

import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.TestDataObjectConvertible
import com.tealium.core.api.misc.TealiumCallback
import com.tealium.core.api.network.DeserializedNetworkCallback
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.HttpResponse
import com.tealium.core.api.network.NetworkCallback
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkException
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.NetworkResult.Failure
import com.tealium.core.api.network.NetworkResult.Success
import com.tealium.core.api.pubsub.Disposable
import com.tealium.tests.common.SystemLogger
import io.mockk.*
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class NetworkHelperTests {

    private lateinit var networkClient: NetworkClient
    private lateinit var networkHelper: NetworkHelper

    @Before
    fun setup() {
        networkClient = mockk(relaxed = true)
        networkHelper = NetworkHelperImpl(networkClient, SystemLogger)
    }

    @Test
    fun get_Makes_Network_Request() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockCallback<NetworkResult>()
        mockRequest(request, Success(mockk(relaxed = true)))

        networkHelper.get(request.url, null, callback)
        networkHelper.get(request.url.toString(), null, callback)

        verify(exactly = 2) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun get_Completes_With_Success() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockCallback<NetworkResult>()
        mockRequest(request, success(body = "result"))

        networkHelper.get(request.url, null, callback)
        networkHelper.get(request.url.toString(), null, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result is Success
                        && result.httpResponse.body == "result"
            })
        }
    }

    @Test
    fun get_Completes_With_Failure() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockCallback<NetworkResult>()
        mockRequest(request, failure(NetworkException.UnexpectedException(null)))

        networkHelper.get(request.url, null, callback)
        networkHelper.get(request.url.toString(), null, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result is Failure
                        && result.networkException is NetworkException.UnexpectedException
            })
        }
    }

    @Test
    fun post_Makes_Network_Request() {
        val payload: DataObject = DataObject.EMPTY_OBJECT
        val request = HttpRequest.post("http://localhost", payload.toString()).gzip(true).build()
        val callback = mockCallback<NetworkResult>()
        mockRequest(request, success())

        networkHelper.post(request.url, payload, callback)
        networkHelper.post(request.url.toString(), payload, callback)

        verify(exactly = 2) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun post_Completes_With_Success() {
        val payload: DataObject = DataObject.EMPTY_OBJECT
        val request = HttpRequest.post("http://localhost", payload.toString()).gzip(true).build()
        val callback = mockCallback<NetworkResult>()
        mockRequest(request, success(body = "result"))

        networkHelper.post(request.url, payload, callback)
        networkHelper.post(request.url.toString(), payload, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result is Success
                        && result.httpResponse.body == "result"
            })
        }
    }

    @Test
    fun post_Completes_With_Failure() {
        val payload: DataObject = DataObject.EMPTY_OBJECT
        val request = HttpRequest.post("http://localhost", payload.toString()).gzip(true).build()
        val callback = mockCallback<NetworkResult>()
        mockRequest(request, failure())

        networkHelper.post(request.url, payload, callback)
        networkHelper.post(request.url.toString(), payload, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result is Failure
            })
        }
    }

    @Test
    fun getJson_Makes_Network_Request() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<JSONObject>()
        mockRequest(request, success(body = "{\"key\": \"value\"}"))

        networkHelper.getJson(request.url, null, callback)
        networkHelper.getJson(request.url.toString(), null, callback)

        verify(exactly = 2) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun getJson_Completes_With_Success() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<JSONObject>()
        mockRequest(request, success(body = "{\"key\": \"value\"}"))

        networkHelper.getJson(request.url, null, callback)
        networkHelper.getJson(request.url.toString(), null, callback)

        verify(exactly = 2) {
            callback.onComplete(match {
                it.getOrThrow().value["key"] == "value"
            })
        }
    }

    @Test
    fun getJson_Completes_With_Failure_When_Invalid_Json() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<JSONObject>()
        mockRequest(request, success(body = "{...."))

        networkHelper.getJson(request.url, null, callback)
        networkHelper.getJson(request.url.toString(), null, callback)

        verify(exactly = 2) {
            callback.onComplete(match {
                it.exceptionOrNull() != null
            })
        }
    }

    @Test
    fun getJson_Completes_With_Failure_When_Network_Failure() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<JSONObject>()
        mockRequest(request, failure(NetworkException.NetworkIOException(null)))

        networkHelper.getJson(request.url, null, callback)
        networkHelper.getJson(request.url.toString(), null, callback)

        verify(exactly = 2) {
            callback.onComplete(match {
                it.exceptionOrNull() != null
            })
        }
    }

    @Test
    fun getDataObject_Makes_Network_Request() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<DataObject>()
        mockRequest(request, success(body = "{\"key\": \"value\"}"))

        networkHelper.getDataObject(request.url, null, callback)
        networkHelper.getDataObject(request.url.toString(), null, callback)

        verify(exactly = 2) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun getDataObject_Completes_With_Success() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<DataObject>()
        mockRequest(request, success(body = "{\"key\": \"value\"}"))

        networkHelper.getDataObject(request.url, null, callback)
        networkHelper.getDataObject(request.url.toString(), null, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result.getOrThrow().value.get("key")!!.value == "value"
            })
        }
    }

    @Test
    fun getDataObject_Completes_With_Failure_When_Invalid_Json() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<DataObject>()
        mockRequest(request, success(body = "{..."))

        networkHelper.getDataObject(request.url, null, callback)
        networkHelper.getDataObject(request.url.toString(), null, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result.exceptionOrNull() != null
            })
        }
    }

    @Test
    fun getDataItemConvertible_Makes_Network_Request() {
        val testConvertible = TestDataObjectConvertible("value", 10)
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<TestDataObjectConvertible>()
        mockRequest(request, success(body = testConvertible.asDataItem().toString()))

        val converter = TestDataObjectConvertible.Converter
        networkHelper.getDataItemConvertible(request.url, null, converter, callback)
        networkHelper.getDataItemConvertible(request.url.toString(), null, converter, callback)

        verify(exactly = 2) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun getDataItemConvertible_Completes_With_Success() {
        val testConvertible = TestDataObjectConvertible("value", 10)
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<TestDataObjectConvertible>()
        mockRequest(request, success(body = testConvertible.asDataItem().toString()))

        val converter = TestDataObjectConvertible.Converter
        networkHelper.getDataItemConvertible(request.url, null, converter, callback)
        networkHelper.getDataItemConvertible(request.url.toString(), null, converter, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result.getOrThrow().value.string == "value"
                        && result.getOrThrow().value.int == 10
            })
        }
    }

    @Test
    fun getDataItemConvertible_Completes_With_UnexpectedFailure_When_Not_Convertible() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<TestDataObjectConvertible>()
        mockRequest(request, success(body = "{..."))

        val converter = TestDataObjectConvertible.Converter
        networkHelper.getDataItemConvertible(request.url, null, converter, callback)
        networkHelper.getDataItemConvertible(request.url.toString(), null, converter, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result.exceptionOrNull() != null
                        && result.exceptionOrNull() is NetworkException.UnexpectedException
            })
        }
    }

    @Test
    fun getDataItemConvertible_Completes_With_Failure_When_NetworkException() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<TestDataObjectConvertible>()
        mockRequest(request, failure(NetworkException.NetworkIOException(null)))

        val converter = TestDataObjectConvertible.Converter
        networkHelper.getDataItemConvertible(request.url, null, converter, callback)
        networkHelper.getDataItemConvertible(request.url.toString(), null, converter, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result.exceptionOrNull() != null
                        && result.exceptionOrNull() is NetworkException.NetworkIOException
            })
        }
    }

    @Test
    fun getDeserializable_Makes_Network_Request() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<Int>()
        mockRequest(request, success())

        networkHelper.getDeserializable(request.url, null, String::toInt, callback)
        networkHelper.getDeserializable(request.url.toString(), null, String::toInt, callback)

        verify(exactly = 2) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun getDeserializable_Completes_With_Success() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<Int>()
        mockRequest(request, success(body = "10"))

        networkHelper.getDeserializable(request.url, null, String::toInt, callback)
        networkHelper.getDeserializable(request.url.toString(), null, String::toInt, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result.getOrThrow().value == 10
            })
        }
    }

    @Test
    fun getDeserializable_Completes_With_UnexpectedFailure_When_Not_Deserializable() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<Int>()
        mockRequest(request, success(body = "{..."))

        networkHelper.getDeserializable(request.url, null, String::toInt, callback)
        networkHelper.getDeserializable(request.url.toString(), null, String::toInt, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result.exceptionOrNull() != null
                        && result.exceptionOrNull() is NetworkException.UnexpectedException
            })
        }
    }

    @Test
    fun getDeserializable_Completes_With_Failure_When_Not_Deserializable() {
        val request = HttpRequest.get("http://localhost", null).build()
        val callback = mockDeserializedCallback<Int>()
        mockRequest(request, failure(NetworkException.NetworkIOException(null)))

        networkHelper.getDeserializable(request.url, null, String::toInt, callback)
        networkHelper.getDeserializable(request.url.toString(), null, String::toInt, callback)

        verify(exactly = 2) {
            callback.onComplete(match { result ->
                result.exceptionOrNull() != null
                        && result.exceptionOrNull() is NetworkException.NetworkIOException
            })
        }
    }

    /**
     * Helper that sets up a mock on the [networkClient] to return the [response]
     * immediately for the given [request].
     * Optional [returns] Disposable if a mocked one is not sufficient.
     */
    private fun mockRequest(
        request: HttpRequest,
        response: NetworkResult,
        returns: Disposable = mockk()
    ) {
        val responseCapture = slot<TealiumCallback<NetworkResult>>()
        every { networkClient.sendRequest(request, capture(responseCapture)) } answers {
            responseCapture.captured.onComplete(response)
            returns
        }
    }

    private fun success(
        url: URL = URL("http://localhost/"),
        status: Int = 200,
        msg: String = "",
        headers: Map<String, List<String>> = mapOf(),
        body: String? = null
    ) = Success(
        HttpResponse(
            url = url,
            statusCode = status,
            message = msg,
            headers = headers,
            body = body
        )
    )

    private fun failure(
        cause: NetworkException = NetworkException.UnexpectedException(null)
    ) = Failure(
        cause
    )

    private fun <T> mockCallback(): NetworkCallback<T> {
        return mockk(relaxed = true)
    }

    private fun <T> mockDeserializedCallback(): DeserializedNetworkCallback<T> {
        return mockk(relaxed = true)
    }
}