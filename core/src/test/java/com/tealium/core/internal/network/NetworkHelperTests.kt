package com.tealium.core.internal.network

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.network.Failure
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.HttpResponse
import com.tealium.core.api.network.NetworkClient
import com.tealium.core.api.network.NetworkHelper
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.Success
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        networkHelper = NetworkHelperImpl(networkClient)
    }

    @Test
    fun sendGetRequestReturnsSuccessfulResult() = runBlocking {
        val request = HttpRequest.get("url", null)
        val successResponse: NetworkResult = Success(mockk())
        coEvery { networkClient.sendRequestAsync(request).await() } returns successResponse

        val result = networkHelper.get(request.url, null)

        assertTrue(result is Success)
        coVerify(exactly = 1) { networkClient.sendRequestAsync(request).await() }
    }

    @Test
    fun sendGetRequestReturnsFailedResult() = runBlocking {
        val request = HttpRequest.get("url", null)
        val failedResponse: NetworkResult = Failure(mockk())
        coEvery { networkClient.sendRequestAsync(request).await() } returns failedResponse

        val result = networkHelper.get(request.url, null)

        assertTrue(result is Failure)
        coVerify(exactly = 1) { networkClient.sendRequestAsync(request).await() }
    }

    @Test
    fun sendPostRequestReturnsSuccessfulResult() = runBlocking {
        val payload: TealiumBundle = TealiumBundle.EMPTY_BUNDLE
        val request = HttpRequest.post("url", payload, true)
        val successResponse: NetworkResult = Success(mockk())
        coEvery { networkClient.sendRequestAsync(request).await() } returns successResponse

        val result = networkHelper.post(request.url, payload)

        assertTrue(result is Success)
        coVerify(exactly = 1) { networkClient.sendRequestAsync(request).await() }
    }

    @Test
    fun sendPostRequestReturnsFailedResult() = runBlocking {
        val payload: TealiumBundle = TealiumBundle.EMPTY_BUNDLE
        val request = HttpRequest.post("url", payload, true)
        val failedResponse: NetworkResult = Failure(mockk())
        coEvery { networkClient.sendRequestAsync(request).await() } returns failedResponse

        val result = networkHelper.post(request.url, payload)

        assertTrue(result is Failure)
        coVerify(exactly = 1) { networkClient.sendRequestAsync(request).await() }
    }

    @Test
    fun getJsonReturnsJSONObjectForValidJSON() = runBlocking {
        val request = HttpRequest.get("url", null)
        val successResponse: NetworkResult = Success(
            HttpResponse(
                mockk(),
                200,
                message = "",
                headers = emptyMap(),
                body = "{\"key\": \"value\"}"
            )
        )
        coEvery { networkClient.sendRequestAsync(request).await() } returns successResponse

        val jsonObject = networkHelper.getJson(request.url, null)

        assertNotNull(jsonObject)
        coVerify(exactly = 1) { networkClient.sendRequestAsync(request).await() }
    }

    @Test
    fun getJsonReturnsNullForInvalidJSON() = runBlocking {
        val request = HttpRequest.get("url", null)
        val successResponse: NetworkResult = Success(
            HttpResponse(
                mockk(),
                200,
                message = "",
                headers = emptyMap()
            )
        )
        coEvery { networkClient.sendRequestAsync(request).await() } returns successResponse

        val jsonObject = networkHelper.getJson(request.url, null)

        assertNull(jsonObject)
        coVerify(exactly = 1) { networkClient.sendRequestAsync(request).await() }
    }

    @Test
    fun getTealiumBundleReturnsValidTealiumBundle() = runBlocking {
        val request = HttpRequest.get("url", null)
        val successResponse: NetworkResult = Success(
            HttpResponse(
                mockk(),
                200,
                message = "",
                headers = emptyMap(),
                body = "{\"key\": \"value\"}"
            )
        )
        coEvery { networkClient.sendRequestAsync(request).await() } returns successResponse

        val bundle = networkHelper.getTealiumBundle(request.url, null)

        assertNotNull(bundle)
        val bundleValue = bundle?.get("key")
        assertEquals("value", bundleValue?.value )
        coVerify(exactly = 1) { networkClient.sendRequestAsync(request).await() }
    }
}
