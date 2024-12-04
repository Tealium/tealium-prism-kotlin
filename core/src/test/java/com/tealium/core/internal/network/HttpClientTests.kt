package com.tealium.core.internal.network

import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.Interceptor
import com.tealium.core.api.network.NetworkException.CancelledException
import com.tealium.core.api.network.NetworkException.NetworkIOException
import com.tealium.core.api.network.NetworkException.Non200Exception
import com.tealium.core.api.network.NetworkException.UnexpectedException
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.NetworkResult.Failure
import com.tealium.core.api.network.NetworkResult.Success
import com.tealium.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.core.api.network.RetryPolicy.RetryAfterDelay
import com.tealium.core.api.network.RetryPolicy.RetryAfterEvent
import com.tealium.core.api.pubsub.Observables
import com.tealium.tests.common.testNetworkScheduler
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HttpClientTests {

    lateinit var mockWebServer: MockWebServer

    private val mockInterceptor: Interceptor = mockk(relaxed = true)
    private val mockLogger: Logger = mockk(relaxed = true)

    lateinit var httpClient: HttpClient
    private val port = 8888

    var httpRequest: HttpRequest? = null
    var networkResult: NetworkResult? = null
    var status: Int? = null
    var response: String? = null
    var errorMessage: String? = null

    private val urlString = "http://localhost:$port"

    @Before
    fun setUp() {

        httpClient = HttpClient(mockLogger, testTealiumScheduler, testNetworkScheduler)
        httpClient.addInterceptor(mockInterceptor)

        val captureRequest = slot<HttpRequest>()
        val captureResult = slot<NetworkResult>()

        every {
            mockInterceptor.shouldRetry(
                capture(captureRequest),
                capture(captureResult),
                any()
            )
        } answers {
            httpRequest = captureRequest.captured
            networkResult = captureResult.captured
            DoNotRetry
        }
        every {
            mockInterceptor.didComplete(
                capture(captureRequest),
                capture(captureResult)
            )
        } answers {
            httpRequest = captureRequest.captured
            networkResult = captureResult.captured
            when (val result = networkResult) {
                is Success -> {
                    status = result.httpResponse.statusCode
                    response = result.httpResponse.body
                }

                is Failure -> {
                    when (val error = result.networkException) {
                        is Non200Exception -> {
                            status = error.statusCode
                        }

                        is NetworkIOException -> {
                            errorMessage = error.cause?.message
                        }

                        is UnexpectedException -> {
                            errorMessage = error.cause?.message
                        }

                        is CancelledException -> {
                            errorMessage = "Cancelled"
                        }
                    }
                }

                else -> {}
            }
        }
    }

    @After
    fun tearDown() {
        if (this::mockWebServer.isInitialized) {
            mockWebServer.shutdown()
        }
    }

    private fun startMockWebServer(vararg responses: MockResponse) {
        mockWebServer = MockWebServer()

        for (response in responses) {
            mockWebServer.enqueue(
                response
            )
        }
        mockWebServer.start(port)
        mockWebServer.url(urlString)
    }

    @Test
    fun sendSuccessfulGetRequest() {
        startMockWebServer(
            MockResponse()
                .setBody("Success")
                .setResponseCode(200)
        )
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        httpClient.sendRequest(
            HttpRequest.get(
                urlString
            ).build(),
            completion
        )

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)

        verify(timeout = 1000) {
            completion(match { result ->
                result is Success
                        && status == 200
                        && "Success" == response
            })
        }
    }

    @Test
    fun sendFailedGetRequest() {
        startMockWebServer(
            MockResponse()
                .setResponseCode(500)
        )
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        httpClient.sendRequest(
            HttpRequest.get(
                urlString
            ).build(),
            completion
        )

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)
        verify(timeout = 1000) {
            completion(match { result ->
                result is Failure
                        && result.networkException is Non200Exception
                        && 500 == status
            })
        }
    }

    @Test
    fun sendRequestSuccessReportsSameRequestInDidComplete() {
        startMockWebServer(
            MockResponse()
                .setBody("Success")
                .setResponseCode(200)
        )
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        val httpRequest = HttpRequest.get(
            urlString
        ).build()

        httpClient.sendRequest(httpRequest, completion)

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)

        verify(timeout = 1000) {
            completion(match { result ->
                result is Success
                        && httpRequest == this@HttpClientTests.httpRequest
                        && 200 == status
                        && "Success" == response
            })
        }
    }

    @Test
    fun sendRequestFailureReportsSameRequestInDidComplete() {
        startMockWebServer(
            MockResponse()
                .setResponseCode(500)
        )
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        val httpRequest = HttpRequest.get(
            urlString
        ).build()

        httpClient.sendRequest(httpRequest, completion)

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)

        verify(timeout = 1000) {
            completion(match { result ->
                result is Failure
                        && httpRequest == this@HttpClientTests.httpRequest
                        && result == networkResult
                        && 500 == status
                        && result.networkException is Non200Exception
            })
        }
    }

    @Test
    fun cancelledRequestReturnsCancelledFailure() {
        every { mockInterceptor.shouldRetry(any(), any(), any()) } returns RetryAfterDelay(100L)
        startMockWebServer(
            MockResponse()
                .setResponseCode(500),
            MockResponse()
                .setResponseCode(500)
        )
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        val httpRequest = HttpRequest.get(
            urlString
        ).build()

        val request = httpClient.sendRequest(httpRequest, completion)
        request.dispose()

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)

        verify(timeout = 1000) {
            completion(match { result ->
                result is Failure
                        && result.networkException is CancelledException
            })
        }
        confirmVerified(completion)
    }

    @Test
    fun redirectSuccessfulResponse() {
        startMockWebServer(
            MockResponse()
                .setResponseCode(301)
                .setHeader("Location", urlString),
            MockResponse()
                .setResponseCode(200)
        )
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        httpClient.sendRequest(
            HttpRequest.get(
                urlString
            ).build(),
            completion
        )

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)

        verify(timeout = 1000) {
            completion(match { result ->
                result is Success
                        && status == 200
            })
        }
    }

    @Test
    fun redirectFailedResponseWhenNoLocationHeader() {
        startMockWebServer(
            MockResponse()
                .setResponseCode(301),
            MockResponse()
                .setResponseCode(200)
        )
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        httpClient.sendRequest(
            HttpRequest.get(
                urlString
            ).build(),
            completion
        )

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)

        verify(timeout = 1000) {
            completion(match { result ->
                result is Failure
                        && result.networkException is UnexpectedException
            })
        }
    }

    @Test
    fun sendSuccessfulPostRequest() {
        startMockWebServer(
            MockResponse()
                .setBody("Successful POST")
                .setResponseCode(200)
        )
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        httpClient.sendRequest(
            HttpRequest.post(
                urlString, "Test Body"
            ).header("Content-Type", "text/plain")
                .build(),
            completion
        )

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("POST / HTTP/1.1", mockRequest.requestLine)

        verify(timeout = 1000) {
            completion(match { result ->
                result is Success
                        && "Successful POST" == result.httpResponse.body
            })
        }
    }

    @Test
    fun sendFailedPostRequest() {
        startMockWebServer(
            MockResponse()
                .setBody("Failed POST")
                .setResponseCode(404)
        )
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        httpClient.sendRequest(
            HttpRequest.post(
                urlString,
                "Test Body"
            )
                .header("Content-type", "text/plain")
                .build(),
            completion
        )

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("POST / HTTP/1.1", mockRequest.requestLine)

        verify(timeout = 1000) {
            completion(match { result ->
                result is Failure
            })
        }
    }

    @Test
    fun incrementRetryCount() {
        every { mockInterceptor.didComplete(any(), any()) } just Runs
        every {
            mockInterceptor.shouldRetry(any(), any(), any())
        } returns RetryAfterDelay(10) andThen RetryAfterDelay(10) andThen DoNotRetry
        val completion = mockk<(NetworkResult) -> Unit>(relaxed = true)

        startMockWebServer(
            MockResponse()
                .setResponseCode(500),
            MockResponse()
                .setResponseCode(500),
            MockResponse()
                .setResponseCode(200)
        )

        httpClient.sendRequest(
            HttpRequest.get(
                urlString
            ).build(), completion
        )

        mockWebServer.takeRequest()

        verify(timeout = 1000) {
            mockInterceptor.shouldRetry(any(), any(), 0)
            mockInterceptor.shouldRetry(any(), any(), 1)
            mockInterceptor.shouldRetry(any(), any(), 2)
        }
    }

    @Test
    fun delayRequestIfInterceptorReturnsAfterDelay() {
        val startTime = System.currentTimeMillis()
        val delayDuration = 500L // 0.5 second
        every { mockInterceptor.shouldRetry(any(), any(), any()) } returns RetryAfterDelay(
            delayDuration
        )

        val httpRequest = HttpRequest.get(
            urlString
        ).build()

        val assertion = mockk<(Boolean) -> Unit>()
        val completion: (Boolean) -> Unit = {
            assertTrue(System.currentTimeMillis() >= startTime + delayDuration)
            assertion(it)
        }
        httpClient.processInterceptorsForDelay(httpRequest, mockk(), 0, completion)

        verify { mockInterceptor.shouldRetry(httpRequest, any(), any()) }
        verify(timeout = 1000) {
            assertion(true)
        }
    }

    @Test
    fun delayRequestIfInterceptorReturnsAfterEvent() {
        val subject = Observables.publishSubject<Unit>()

        every { mockInterceptor.shouldRetry(any(), any(), any()) } returns RetryAfterEvent(
            subject
        )

        val httpRequest = HttpRequest.get(
            urlString
        ).build()

        val completion = mockk<(Boolean) -> Unit>(relaxed = true)
        httpClient.processInterceptorsForDelay(httpRequest, mockk(), 0, completion)

        verify { mockInterceptor.shouldRetry(httpRequest, any(), any()) }
        verify(inverse = true) {
            completion(true)
        }

        subject.onNext(Unit)
        verify {
            completion(true)
        }
    }

    @Test
    fun shouldRetryCalledInReverseOrder() {
        val completion = mockk<(Boolean) -> Unit>(relaxed = true)
        val mockInterceptor1 = mockk<Interceptor>()
        val mockInterceptor2 = mockk<Interceptor>()

        every { mockInterceptor1.shouldRetry(any(), any(), any()) } returns DoNotRetry
        every { mockInterceptor2.shouldRetry(any(), any(), any()) } returns DoNotRetry

        httpClient.addInterceptor(mockInterceptor1)
        httpClient.addInterceptor(mockInterceptor2)

        val httpRequest = HttpRequest.get(
            urlString
        ).build()

        httpClient.processInterceptorsForDelay(httpRequest, mockk(), 0, completion)

        verifyOrder {
            mockInterceptor2.shouldRetry(httpRequest, any(), 0)
            mockInterceptor1.shouldRetry(httpRequest, any(), 0)
            mockInterceptor.shouldRetry(httpRequest, any(), 0)
        }

        verify { completion(false) }

        confirmVerified(mockInterceptor1, mockInterceptor2)
    }

    @Test
    fun shouldRetryReturnsTrueForSingleInterceptorsAndRemainingIgnored() {
        val completion = mockk<(Boolean) -> Unit>(relaxed = true)
        val mockInterceptor1 = mockk<Interceptor>()
        val mockInterceptor2 = mockk<Interceptor>()
        val mockInterceptor3 = mockk<Interceptor>()

        every { mockInterceptor1.shouldRetry(any(), any(), any()) } returns DoNotRetry
        every { mockInterceptor2.shouldRetry(any(), any(), any()) } returns RetryAfterDelay(100)
        every { mockInterceptor3.shouldRetry(any(), any(), any()) } returns DoNotRetry

        httpClient.addInterceptor(mockInterceptor1)
        httpClient.addInterceptor(mockInterceptor2)
        httpClient.addInterceptor(mockInterceptor3)

        val httpRequest = HttpRequest.get(
            urlString
        ).build()

        httpClient.processInterceptorsForDelay(httpRequest, mockk(), 0, completion)

        verify { mockInterceptor3.shouldRetry(httpRequest, any(), any()) }
        verify { mockInterceptor2.shouldRetry(httpRequest, any(), any()) }
        verify(exactly = 0) { mockInterceptor1.shouldRetry(httpRequest, any(), any()) }
        verify(exactly = 0) { mockInterceptor.shouldRetry(httpRequest, any(), any()) }

        verify(timeout = 1000) { completion(true) }

        confirmVerified(mockInterceptor1, mockInterceptor2, mockInterceptor3)
    }
}
