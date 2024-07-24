package com.tealium.core.internal.network

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.network.Failure
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.HttpResponse
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.Success
import com.tealium.tests.common.SystemLogger
import io.mockk.*
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NetworkHelperTests {

    private lateinit var networkClient: NetworkClient
    private lateinit var networkHelper: NetworkHelper

    @Before
    fun setup() {
        networkClient = mockk(relaxed = true)
        networkHelper = NetworkHelperImpl(networkClient, SystemLogger)
    }

    /**
     * Helper that sets up a mock on the [networkClient] to return the [response]
     * immediately for the given [request].
     * Optional [returns] Disposable if a mocked one is not sufficient.
     */
    private fun mockRequest(request: HttpRequest, response: NetworkResult, returns: Disposable = mockk()) {
        val responseCapture = slot<(NetworkResult) -> Unit>()
        every { networkClient.sendRequest(request, capture(responseCapture)) } answers {
            responseCapture.captured.invoke(response)
            returns
        }
    }

    @Test
    fun sendGetRequestReturnsSuccessfulResult() {
        val request = HttpRequest.get("http://localhost", null).build()
        val successResponse: NetworkResult = Success(mockk(relaxed = true))
        val onComplete: (NetworkResult) -> Unit = mockk(relaxed = true)
        mockRequest(request, successResponse)

        networkHelper.get(request.url.toString(), null, onComplete)

        verify {
            onComplete(match { result ->
                result is Success
            })
        }
        verify(exactly = 1) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun sendGetRequestReturnsFailedResult() {
        val request = HttpRequest.get("http://localhost", null).build()
        val failedResponse: NetworkResult = Failure(mockk())
        val onComplete: (NetworkResult) -> Unit = mockk(relaxed = true)
        mockRequest(request, failedResponse)

        networkHelper.get(request.url.toString(), null, onComplete)

        verify {
            onComplete(match { result ->
                result is Failure
            })
        }
        verify(exactly = 1) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun sendPostRequestReturnsSuccessfulResult() {
        val payload: TealiumBundle = TealiumBundle.EMPTY_BUNDLE
        val request = HttpRequest.post("http://localhost", payload.toString()).gzip(true).build()
        val successResponse: NetworkResult = Success(mockk(relaxed = true))
        val onComplete: (NetworkResult) -> Unit = mockk(relaxed = true)
        mockRequest(request, successResponse)

        networkHelper.post(request.url.toString(), payload, onComplete)

        verify {
            onComplete(match { result ->
                result is Success
            })
        }
        verify(exactly = 1) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun sendPostRequestReturnsFailedResult() {
        val payload: TealiumBundle = TealiumBundle.EMPTY_BUNDLE
        val request = HttpRequest.post("http://localhost", payload.toString()).gzip(true).build()
        val failedResponse: NetworkResult = Failure(mockk())
        val onComplete: (NetworkResult) -> Unit = mockk(relaxed = true)
        mockRequest(request, failedResponse)

        networkHelper.post(request.url.toString(), payload, onComplete)

        verify {
            onComplete(match { result ->
                result is Failure
            })
        }
        verify(exactly = 1) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun getJsonReturnsJSONObjectForValidJSON() {
        val request = HttpRequest.get("http://localhost", null).build()
        val successResponse: NetworkResult = Success(
            HttpResponse(
                mockk(),
                200,
                message = "",
                headers = emptyMap(),
                body = "{\"key\": \"value\"}"
            )
        )
        val onComplete: (JSONObject?) -> Unit = mockk(relaxed = true)
        mockRequest(request, successResponse)

        networkHelper.getJson(request.url.toString(), null, onComplete)

        verify {
            onComplete(isNull(inverse = true))
        }
        verify(exactly = 1) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun getJsonReturnsNullForInvalidJSON() {
        val request = HttpRequest.get("http://localhost", null).build()
        val successResponse: NetworkResult = Success(
            HttpResponse(
                mockk(),
                200,
                message = "",
                headers = emptyMap()
            )
        )
        val onComplete: (JSONObject?) -> Unit = mockk(relaxed = true)
        mockRequest(request, successResponse)

        networkHelper.getJson(request.url.toString(), null, onComplete)

        verify {
            onComplete(isNull())
        }
        verify(exactly = 1) {
            networkClient.sendRequest(request, any())
        }
    }

    @Test
    fun getTealiumBundleReturnsValidTealiumBundle() {
        val request = HttpRequest.get("http://localhost", null).build()
        val successResponse: NetworkResult = Success(
            HttpResponse(
                mockk(),
                200,
                message = "",
                headers = emptyMap(),
                body = "{\"key\": \"value\"}"
            )
        )
        val onComplete: (TealiumBundle?) -> Unit = mockk(relaxed = true)
        mockRequest(request, successResponse)

        networkHelper.getTealiumBundle(request.url.toString(), null, onComplete)

        verify {
            onComplete(match { bundle ->
                bundle.get("key")!!.value == "value"
            })
        }
        verify(exactly = 1) {
            networkClient.sendRequest(request, any())
        }
    }
}
