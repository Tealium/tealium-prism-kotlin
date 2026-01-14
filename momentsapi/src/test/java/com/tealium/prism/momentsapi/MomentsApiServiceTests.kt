package com.tealium.prism.momentsapi

import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.network.DeserializedNetworkCallback
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkException
import com.tealium.prism.core.api.network.NetworkHelper
import com.tealium.prism.core.internal.pubsub.Subscription
import com.tealium.prism.momentsapi.internal.Converters
import com.tealium.prism.momentsapi.internal.MomentsApiConfiguration
import com.tealium.prism.momentsapi.internal.MomentsApiServiceImpl
import io.mockk.CapturingSlot
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class MomentsApiServiceTests {

    @RelaxedMockK
    private lateinit var mockNetworkHelper: NetworkHelper

    private lateinit var service: MomentsApiServiceImpl

    private val accountName = "test-account"
    private val profileName = "test-profile"
    private val environment = "prod"

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        val configuration = MomentsApiConfiguration(region = MomentsApiRegion.UsEast)
        service = MomentsApiServiceImpl(
            networkHelper = mockNetworkHelper,
            account = accountName,
            profile = profileName,
            environment = environment,
            configuration = configuration
        )
    }

    private fun mockSuccessResponse(
        engineResponse: EngineResponse = EngineResponse(flags = mapOf(), metrics = mapOf(), properties = mapOf()),
        url: URL? = null,
        headersCapture: CapturingSlot<Map<String, String>>? = null
    ) {
        val completionCapture = slot<DeserializedNetworkCallback<EngineResponse>>()
        val defaultURL = URL("https://personalization-api.us-east-1.prod.tealiumapis.com/personalization/accounts/$accountName/profiles/$profileName/engines/test-engine/visitors/test-visitor?ignoreTapid=true")
        val httpResponse = HttpResponse(
            url ?: defaultURL,
            200,
            "",
            mapOf(),
            """{"flags": {}, "metrics": {}, "properties": {}}""".toByteArray(Charsets.UTF_8)
        )

        every {
            mockNetworkHelper.getDataItemConvertible<EngineResponse>(
                url = if (url != null) url else any(),
                etag = any(),
                additionalHeaders = if (headersCapture != null) capture(headersCapture) else any(),
                converter = Converters.EngineResponseConverter,
                completion = capture(completionCapture)
            )
        } answers {
            completionCapture.captured.onComplete(
                TealiumResult.success(NetworkHelper.HttpValue(engineResponse, httpResponse))
            )
            Subscription()
        }
    }

    private fun mockFailureResponse(cause: NetworkException) {
        val completionCapture = slot<DeserializedNetworkCallback<EngineResponse>>()
        every {
            mockNetworkHelper.getDataItemConvertible(
                url = any<URL>(),
                etag = any(),
                additionalHeaders = any(),
                converter = Converters.EngineResponseConverter,
                completion = capture(completionCapture)
            )
        } answers {
            completionCapture.captured.onComplete(TealiumResult.failure(cause))
            Subscription()
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsError_WhenEngineIDIsEmpty() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        val disposable = service.fetchEngineResponse("", "visitor-id", callback)

        verify {
            callback.onComplete(match { result ->
                result.isFailure &&
                result.exceptionOrNull() is IllegalArgumentException &&
                (result.exceptionOrNull() as IllegalArgumentException).message?.contains("engine ID") == true
            })
        }
        verify(exactly = 0) {
            mockNetworkHelper.getDataItemConvertible<EngineResponse>(
                any<URL>(),
                any(),
                any(),
                any<com.tealium.prism.core.api.data.DataItemConverter<EngineResponse>>(),
                any<DeserializedNetworkCallback<EngineResponse>>()
            )
        }
        assertNotNull(disposable)
    }

    @Test
    fun fetchEngineResponse_BuildsCorrectURL() {
        val engineId = "test-engine-id"
        val visitorId = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        val urlCapture = slot<URL>()

        every {
            mockNetworkHelper.getDataItemConvertible<EngineResponse>(
                url = capture(urlCapture),
                etag = any(),
                additionalHeaders = any(),
                converter = Converters.EngineResponseConverter,
                completion = any()
            )
        } answers {
            val completion = arg<DeserializedNetworkCallback<EngineResponse>>(4)
            val httpResponse = HttpResponse(
                urlCapture.captured,
                200,
                "",
                mapOf(),
                """{"flags": {}, "metrics": {}, "properties": {}}""".toByteArray(Charsets.UTF_8)
            )
            completion.onComplete(
                TealiumResult.success(
                    NetworkHelper.HttpValue(
                        EngineResponse(flags = mapOf(), metrics = mapOf(), properties = mapOf()),
                        httpResponse
                    )
                )
            )
            Subscription()
        }

        service.fetchEngineResponse(engineId, visitorId, callback)

        val expectedURL = "https://personalization-api.us-east-1.prod.tealiumapis.com/personalization/accounts/$accountName/profiles/$profileName/engines/$engineId/visitors/$visitorId?ignoreTapid=true"
        assertEquals(expectedURL, urlCapture.captured.toString())
    }

    @Test
    fun fetchEngineResponse_SendsRequestWithCorrectHeaders() {
        val engineId = "test-engine-id"
        val visitorId = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        val headersCapture = slot<Map<String, String>>()

        mockSuccessResponse(headersCapture = headersCapture)

        service.fetchEngineResponse(engineId, visitorId, callback)

        val headers = headersCapture.captured
        assertEquals("application/json", headers["Accept"])
        assertEquals("https://tags.tiqcdn.com/utag/$accountName/$profileName/$environment/mobile.html", headers["Referer"])
    }

    @Test
    fun fetchEngineResponse_UsesCustomReferrer_WhenProvided() {
        val customReferrer = "https://custom-referrer.com"
        val configuration = MomentsApiConfiguration(
            region = MomentsApiRegion.UsEast,
            referrer = customReferrer
        )
        val service = MomentsApiServiceImpl(
            networkHelper = mockNetworkHelper,
            account = accountName,
            profile = profileName,
            environment = environment,
            configuration = configuration
        )
        val engineId = "test-engine-id"
        val visitorId = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        val headersCapture = slot<Map<String, String>>()

        mockSuccessResponse(headersCapture = headersCapture)

        service.fetchEngineResponse(engineId, visitorId, callback)

        val headers = headersCapture.captured
        assertEquals(customReferrer, headers["Referer"])
    }

    @Test
    fun fetchEngineResponse_UsesEmptyStringReferrer_WhenProvided() {
        val configuration = MomentsApiConfiguration(
            region = MomentsApiRegion.UsEast,
            referrer = ""
        )
        val service = MomentsApiServiceImpl(
            networkHelper = mockNetworkHelper,
            account = accountName,
            profile = profileName,
            environment = environment,
            configuration = configuration
        )
        val engineId = "test-engine-id"
        val visitorId = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        val headersCapture = slot<Map<String, String>>()

        mockSuccessResponse(headersCapture = headersCapture)

        service.fetchEngineResponse(engineId, visitorId, callback)

        val headers = headersCapture.captured
        assertEquals("", headers["Referer"])
    }

    @Test
    fun fetchEngineResponse_ReturnsSuccess_WhenHTTP200() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        val expectedResponse = EngineResponse(
            flags = mapOf("flag1" to true),
            metrics = mapOf("metric1" to 1.5),
            properties = mapOf("prop1" to "value1")
        )

        mockSuccessResponse(engineResponse = expectedResponse)

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isSuccess &&
                result.getOrNull()?.flags?.get("flag1") == true &&
                result.getOrNull()?.metrics?.get("metric1") == 1.5 &&
                result.getOrNull()?.properties?.get("prop1") == "value1"
            })
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsSuccess_WithAllFields_WhenHTTP200() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        val expectedResponse = EngineResponse(
            audiences = listOf("audience1", "audience2"),
            badges = listOf("badge1", "badge2"),
            flags = mapOf("flag1" to true, "flag2" to false),
            dates = mapOf("date1" to 1234567890L, "date2" to 9876543210L),
            metrics = mapOf("metric1" to 1.5, "metric2" to 2.0),
            properties = mapOf("prop1" to "value1", "prop2" to "value2")
        )

        mockSuccessResponse(engineResponse = expectedResponse)

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isSuccess &&
                result.getOrNull()?.audiences?.contains("audience1") == true &&
                result.getOrNull()?.audiences?.contains("audience2") == true &&
                result.getOrNull()?.badges?.contains("badge1") == true &&
                result.getOrNull()?.badges?.contains("badge2") == true &&
                result.getOrNull()?.flags?.get("flag1") == true &&
                result.getOrNull()?.flags?.get("flag2") == false &&
                result.getOrNull()?.dates?.get("date1") == 1234567890L &&
                result.getOrNull()?.dates?.get("date2") == 9876543210L &&
                result.getOrNull()?.metrics?.get("metric1") == 1.5 &&
                result.getOrNull()?.metrics?.get("metric2") == 2.0 &&
                result.getOrNull()?.properties?.get("prop1") == "value1" &&
                result.getOrNull()?.properties?.get("prop2") == "value2"
            })
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsError_WhenHTTP400() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        mockFailureResponse(NetworkException.Non200Exception(400))

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isFailure &&
                result.exceptionOrNull() is NetworkException.Non200Exception &&
                (result.exceptionOrNull() as NetworkException.Non200Exception).statusCode == 400
            })
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsError_WhenHTTP403() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        mockFailureResponse(NetworkException.Non200Exception(403))

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isFailure &&
                result.exceptionOrNull() is NetworkException.Non200Exception &&
                (result.exceptionOrNull() as NetworkException.Non200Exception).statusCode == 403
            })
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsError_WhenHTTP404() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        mockFailureResponse(NetworkException.Non200Exception(404))

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isFailure &&
                result.exceptionOrNull() is NetworkException.Non200Exception &&
                (result.exceptionOrNull() as NetworkException.Non200Exception).statusCode == 404
            })
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsError_WhenHTTP500() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        mockFailureResponse(NetworkException.Non200Exception(500))

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isFailure &&
                result.exceptionOrNull() is NetworkException.Non200Exception &&
                (result.exceptionOrNull() as NetworkException.Non200Exception).statusCode == 500
            })
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsError_WhenJSONParsingFails() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        mockFailureResponse(NetworkException.UnexpectedException(Exception("JSON parsing failed")))

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isFailure &&
                result.exceptionOrNull() is NetworkException.UnexpectedException
            })
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsError_WhenNetworkFailure() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        mockFailureResponse(NetworkException.UnexpectedException(Exception("Network error")))

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isFailure &&
                result.exceptionOrNull() is NetworkException.UnexpectedException
            })
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsError_WhenNetworkIOException() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        mockFailureResponse(NetworkException.NetworkIOException(java.io.IOException("Connection failed")))

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isFailure &&
                result.exceptionOrNull() is NetworkException.NetworkIOException
            })
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsError_WhenCancelledException() {
        val engineID = "test-engine-id"
        val visitorID = "test-visitor-id"
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        mockFailureResponse(NetworkException.CancelledException)

        service.fetchEngineResponse(engineID, visitorID, callback)

        verify {
            callback.onComplete(match { result ->
                result.isFailure &&
                result.exceptionOrNull() is NetworkException.CancelledException
            })
        }
    }

    @Test
    fun fetchEngineResponse_BuildsCorrectURL_ForAllRegions() {
        val regions = mapOf(
            MomentsApiRegion.Germany to "eu-central-1",
            MomentsApiRegion.UsEast to "us-east-1",
            MomentsApiRegion.Sydney to "ap-southeast-2",
            MomentsApiRegion.Oregon to "us-west-2",
            MomentsApiRegion.Tokyo to "ap-northeast-1",
            MomentsApiRegion.HongKong to "ap-east-1"
        )

        regions.forEach { (region, expectedRegionString) ->
            val configuration = MomentsApiConfiguration(region = region)
            val service = MomentsApiServiceImpl(
                networkHelper = mockNetworkHelper,
                account = accountName,
                profile = profileName,
                environment = environment,
                configuration = configuration
            )
            val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
            val urlCapture = slot<URL>()

            every {
                mockNetworkHelper.getDataItemConvertible<EngineResponse>(
                    url = capture(urlCapture),
                    etag = any(),
                    additionalHeaders = any(),
                    converter = Converters.EngineResponseConverter,
                    completion = any()
                )
            } answers {
                val completion = args[4] as DeserializedNetworkCallback<EngineResponse>
                val httpResponse = HttpResponse(
                    urlCapture.captured,
                    200,
                    "",
                    mapOf(),
                    """{"flags": {}}""".toByteArray(Charsets.UTF_8)
                )
                completion.onComplete(
                    TealiumResult.success(
                        NetworkHelper.HttpValue(
                            EngineResponse(flags = mapOf()),
                            httpResponse
                        )
                    )
                )
                Subscription()
            }

            service.fetchEngineResponse("engine-id", "visitor-id", callback)

            val url = urlCapture.captured.toString()
            assertTrue("URL should contain region $expectedRegionString for $region", url.contains(expectedRegionString))
        }
    }

    @Test
    fun fetchEngineResponse_ReturnsDisposable() {
        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)

        mockSuccessResponse()

        val disposable = service.fetchEngineResponse("engine-id", "visitor-id", callback)

        assertNotNull(disposable)
    }

    @Test
    fun updateConfiguration_UpdatesConfiguration() {
        val newConfiguration = MomentsApiConfiguration(
            region = MomentsApiRegion.Germany,
            referrer = "https://new-referrer.com"
        )

        service.updateConfiguration(newConfiguration)

        val callback = mockk<Callback<TealiumResult<EngineResponse>>>(relaxed = true)
        val headersCapture = slot<Map<String, String>>()

        mockSuccessResponse(headersCapture = headersCapture)

        service.fetchEngineResponse("engine-id", "visitor-id", callback)

        val headers = headersCapture.captured
        assertEquals("https://new-referrer.com", headers["Referer"])
    }
}
