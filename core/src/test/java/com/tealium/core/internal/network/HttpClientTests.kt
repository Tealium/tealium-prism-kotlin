package com.tealium.core.internal.network

import com.tealium.core.api.logger.Logger
import com.tealium.core.api.network.AfterDelay
import com.tealium.core.api.network.Cancelled
import com.tealium.core.api.network.DoNotDelay
import com.tealium.core.api.network.DoNotRetry
import com.tealium.core.api.network.Failure
import com.tealium.core.api.network.HttpMethod
import com.tealium.core.api.network.HttpRequest
import com.tealium.core.api.network.IOError
import com.tealium.core.api.network.Interceptor
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.Non200Error
import com.tealium.core.api.network.RetryAfterDelay
import com.tealium.core.api.network.Success
import com.tealium.core.api.network.UnexpectedError
import com.tealium.core.internal.LoggerImpl
import com.tealium.core.internal.network.*
import io.mockk.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis

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
        httpClient = HttpClient(mockLogger)
        httpClient.addInterceptor(mockInterceptor)

        val captureRequest = slot<HttpRequest>()
        val captureResult = slot<NetworkResult>()

        every { mockInterceptor.shouldDelay(any()) } returns DoNotDelay
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
                    when (val error = result.networkError) {
                        is Non200Error -> {
                            status = error.statusCode
                        }
                        is IOError -> {
                            val cause = error.ex?.cause
                            errorMessage = error.ex?.message
                        }
                        is UnexpectedError -> {
                            val cause = error.ex?.cause
                            errorMessage = error.ex?.message
                        }
                        is Cancelled -> {
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
        mockWebServer.shutdown()
    }

    @Test
    fun addAndRemoveInterceptor() {
        mockWebServer = MockWebServer()
        assertEquals(1, httpClient.interceptors.size)
        val mockInterceptor = mockk<Interceptor>()
        httpClient.addInterceptor(mockInterceptor)

        assertEquals(2, httpClient.interceptors.size)

        httpClient.removeInterceptor(mockInterceptor)

        assertEquals(1, httpClient.interceptors.size)
    }

    @Test
    fun sendSuccessfulGetRequest() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setBody("Success")
                .setResponseCode(200)
        )
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val request = httpClient.sendRequestAsync(
            HttpRequest(
                urlString,
                HttpMethod.Get
            )
        )

        val result = request.await()

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)
        assertTrue(result is Success)
        assertEquals(200, status)
        assertEquals("Success", response)
    }

    @Test
    fun sendFailedGetRequest() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
        )
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val request = httpClient.sendRequestAsync(
            HttpRequest(
                urlString,
                HttpMethod.Get
            )
        )

        val result = request.await()
        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)
        assertTrue(result is Failure)
        assertTrue((result as Failure).networkError is Non200Error)
        assertEquals(500, status)
    }

    @Test
    fun sendRequestAsyncSuccessReportsSameRequestInDidComplete() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setBody("Success")
                .setResponseCode(200)
        )
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        val request = httpClient.sendRequestAsync(httpRequest)

        val result = request.await()

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)
        assertTrue(result is Success)
        assertEquals(httpRequest, this@HttpClientTests.httpRequest)
        assertEquals(result, networkResult)
        assertEquals(200, status)
        assertEquals("Success", response)
    }

    @Test
    fun sendRequestAsyncFailureReportsSameRequestInDidComplete() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
        )
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        val request = httpClient.sendRequestAsync(httpRequest)

        val result = request.await()

        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)
        assertTrue(result is Failure)
        assertEquals(httpRequest, this@HttpClientTests.httpRequest)
        assertEquals(result, networkResult)
        assertTrue((result as Failure).networkError is Non200Error)
        assertEquals(500, status)
    }

    @Test
    fun cancelledRequestReturnsCancelledFailure() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
        )
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val request = httpClient.sendRequestAsync(
            HttpRequest(
                urlString,
                HttpMethod.Get
            )
        )

        request.cancel()

        val result = request.await()
        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)
        assertTrue(result is Failure)
        assertTrue((result as Failure).networkError is Cancelled)
    }

    @Test
    fun redirectSuccessfulResponse() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(301)
                .setHeader("Location", urlString)
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
        )
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        val request = httpClient.sendRequestAsync(httpRequest)

        val result = request.await()
        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)
        assertTrue(result is Success)
    }

    @Test
    fun redirectFailedResponseWhenNoLocationHeader() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(301)
        )

        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        val request = httpClient.sendRequestAsync(httpRequest)

        val result = request.await()
        val mockRequest = mockWebServer.takeRequest()

        assertEquals("GET / HTTP/1.1", mockRequest.requestLine)
        assertTrue(result is Failure)
        assertTrue((result as Failure).networkError is UnexpectedError)
        assertTrue((result.networkError as UnexpectedError).ex?.message == "Received redirect response without a valid Location header")
    }

    @Test
    fun sendSuccessfulPostRequest() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse()
                .setBody("Successful POST")
                .setResponseCode(200)
        )

        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Post,
            body = "Test Body",
            headers = mapOf("Content-type" to "text/plain")
        )

        val request = httpClient.sendRequestAsync(httpRequest)

        val result = request.await()
        val mockRequest = mockWebServer.takeRequest()

        assertEquals("POST / HTTP/1.1", mockRequest.requestLine)
        assertTrue(result is Success)
        assertEquals( "Successful POST", (result as Success).httpResponse.body)
    }

    @Test
    fun sendFailedPostRequest() = runBlocking {
        mockWebServer = MockWebServer()
        mockWebServer.enqueue(MockResponse()
            .setBody("Successful POST")
            .setResponseCode(200)
        )

        val url = "http://localhost:1000"
        mockWebServer.start(port)
        mockWebServer.url(url)

        val httpRequest = HttpRequest(
            url,
            HttpMethod.Post,
            body = "Test Body",
            headers = mapOf("Content-type" to "text/plain")
        )

        val request = httpClient.sendRequestAsync(httpRequest)

        val result = request.await()

        assertTrue(result is Failure)
    }

    @Test
    fun incrementRetryCount() = runBlocking {
        every { mockInterceptor.didComplete(any(), any()) } just Runs
        every {
            mockInterceptor.shouldRetry(any(), any(), any())
        } returns RetryAfterDelay(10) andThen RetryAfterDelay(10) andThen DoNotRetry
        every { mockInterceptor.shouldDelay(any()) } returns DoNotDelay

        mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
        )
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        val request = httpClient.sendRequestAsync(httpRequest)

        request.await()
        mockWebServer.takeRequest()

        coVerify {
            mockInterceptor.shouldRetry(any(), any(), 0)
            mockInterceptor.shouldRetry(any(), any(), 1)
            mockInterceptor.shouldRetry(any(), any(), 2)
        }
    }

    @Test
    fun delayRequestIfInterceptorReturnsAfterDelay() = runBlocking {
        val delayDuration = 1000L // 1 second
        val delayPolicy = AfterDelay(delayDuration)
        every { mockInterceptor.shouldDelay(any()) } returns delayPolicy

        mockWebServer = MockWebServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
        )

        mockWebServer.start(port)
        mockWebServer.url(urlString)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        val delayDurationActual = measureTimeMillis {
            httpClient.sendRequestAsync(httpRequest).await()
        }

        mockWebServer.takeRequest()


        coVerify { mockInterceptor.shouldDelay(httpRequest) }
        assert(delayDurationActual >= delayDuration)

    }

    @Test
    fun interceptRequestCalledInReverseOrder() = runBlocking {
        val mockInterceptor1 = mockk<Interceptor>()
        val mockInterceptor2 = mockk<Interceptor>()

        every { mockInterceptor1.shouldDelay(any()) } returns DoNotDelay
        every { mockInterceptor2.shouldDelay(any()) } returns DoNotDelay

        mockWebServer = MockWebServer()
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        httpClient.addInterceptor(mockInterceptor1)
        httpClient.addInterceptor(mockInterceptor2)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        httpClient.processInterceptorsForDelay(httpRequest)

        verifyOrder {
            mockInterceptor2.shouldDelay(httpRequest)
            mockInterceptor1.shouldDelay(httpRequest)
            mockInterceptor.shouldDelay(httpRequest)
        }

        confirmVerified(mockInterceptor1, mockInterceptor2)
    }

    @Test
    fun shouldRetryCalledInReverseOrder() = runBlocking {
        val mockInterceptor1 = mockk<Interceptor>()
        val mockInterceptor2 = mockk<Interceptor>()

        every { mockInterceptor1.shouldRetry(any(), any(), any()) } returns DoNotRetry
        every { mockInterceptor2.shouldRetry(any(), any(), any()) } returns DoNotRetry

        mockWebServer = MockWebServer()
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        httpClient.addInterceptor(mockInterceptor1)
        httpClient.addInterceptor(mockInterceptor2)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        httpClient.processInterceptorsForRetry(httpRequest, mockk(), 0)

        verifyOrder {
            mockInterceptor2.shouldRetry(httpRequest, any(), 0)
            mockInterceptor1.shouldRetry(httpRequest, any(), 0)
            mockInterceptor.shouldRetry(httpRequest, any(), 0)
        }

        confirmVerified(mockInterceptor1, mockInterceptor2)
    }

    @Test
    fun shouldRetryReturnsTrueForSingleInterceptorsAndRemainingIgnored() = runBlocking {
        val mockInterceptor1 = mockk<Interceptor>()
        val mockInterceptor2 = mockk<Interceptor>()
        val mockInterceptor3 = mockk<Interceptor>()

        every { mockInterceptor1.shouldRetry(any(), any(), any()) } returns DoNotRetry
        every { mockInterceptor2.shouldRetry(any(), any(), any()) } returns RetryAfterDelay(1000)
        every { mockInterceptor3.shouldRetry(any(), any(), any()) } returns DoNotRetry

        mockWebServer = MockWebServer()
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        httpClient.addInterceptor(mockInterceptor1)
        httpClient.addInterceptor(mockInterceptor2)
        httpClient.addInterceptor(mockInterceptor3)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        httpClient.processInterceptorsForRetry(httpRequest, mockk(), 0)

        coVerify { mockInterceptor3.shouldRetry(httpRequest, any(), any()) }
        coVerify { mockInterceptor2.shouldRetry(httpRequest, any(), any()) }
        coVerify(exactly = 0) { mockInterceptor1.shouldRetry(httpRequest, any(), any()) }
        coVerify(exactly = 0) { mockInterceptor.shouldRetry(httpRequest, any(), any()) }

        confirmVerified(mockInterceptor1, mockInterceptor2, mockInterceptor3)
    }

    @Test
    fun interceptRequestReturnsTrueForSingleInterceptorsAndRemainingIgnored() = runBlocking {
        val mockInterceptor1 = mockk<Interceptor>()
        val mockInterceptor2 = mockk<Interceptor>()
        val mockInterceptor3 = mockk<Interceptor>()

        every { mockInterceptor1.shouldDelay(any()) } returns DoNotDelay
        every { mockInterceptor2.shouldDelay(any()) } returns AfterDelay(1000)
        every { mockInterceptor3.shouldDelay(any()) } returns DoNotDelay

        mockWebServer = MockWebServer()
        mockWebServer.start(port)
        mockWebServer.url(urlString)

        httpClient.addInterceptor(mockInterceptor1)
        httpClient.addInterceptor(mockInterceptor2)
        httpClient.addInterceptor(mockInterceptor3)

        val httpRequest = HttpRequest(
            urlString,
            HttpMethod.Get
        )

        httpClient.processInterceptorsForDelay(httpRequest)

        coVerify { mockInterceptor3.shouldDelay(httpRequest) }
        coVerify { mockInterceptor2.shouldDelay(httpRequest) }
        coVerify(exactly = 0) { mockInterceptor1.shouldDelay(httpRequest) }
        coVerify(exactly = 0) { mockInterceptor.shouldDelay(httpRequest) }

        confirmVerified(mockInterceptor1, mockInterceptor2, mockInterceptor3)
    }
}