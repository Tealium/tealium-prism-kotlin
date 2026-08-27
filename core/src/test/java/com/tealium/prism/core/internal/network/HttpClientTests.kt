package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.Interceptor
import com.tealium.prism.core.api.network.NetworkException.CancelledException
import com.tealium.prism.core.api.network.NetworkException.NetworkIOException
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkException.UnexpectedException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.prism.core.api.network.RetryPolicy.RetryAfterDelay
import com.tealium.prism.core.api.network.RetryPolicy.RetryAfterEvent
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.tests.common.testNetworkScheduler
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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

        // a relaxed mock reports every level as disabled; trace is the level under test for the
        // detailed response log, so opt in to it by default
        every { mockLogger.shouldLog(LogLevel.TRACE) } returns true

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
                    response = result.httpResponse.bodyText()
                }

                is Failure -> {
                    when (val error = result.networkException) {
                        is Non200Exception -> {
                            status = error.statusCode
                            // bodyText() throws on an unreadable body, e.g. a mismatched
                            // content-encoding; an interceptor must not fail the request
                            response = error.httpResponse?.let {
                                runCatching { it.bodyText() }.getOrNull()
                            }
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
                        && "Successful POST" == result.httpResponse.bodyText()
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

        // every interceptor declines, so the request is not retried, and is only reported once
        verify(exactly = 1) { completion(false) }
        verify(exactly = 0) { completion(true) }

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

    @Test
    fun httpResponse_BodyText_Decompresses_GZipped_Bytes() {
        val charset = Charsets.UTF_8
        val text = "Some text"
        val gzipped = gzipBytes(text, charset)

        val response = MockResponse()
            .setResponseCode(200)
            .setHeader(HttpRequest.Headers.CONTENT_TYPE, "application/json; charset=${charset.name()}")
            .setHeader(HttpRequest.Headers.CONTENT_ENCODING, "gzip")
            .setBody(Buffer().write(gzipped))
        startMockWebServer(response)

        val callback = mockk<Callback<NetworkResult>>()
        val request = HttpRequest.get(urlString).build()
        httpClient.sendRequest(request, callback)

        verify(timeout = 1000) {
            callback.onComplete(match {
                it is Success
                        && it.httpResponse.bodyText() == text
            })
        }
    }

    @Test
    fun httpResponse_BodyText_Reads_Body_Using_ContentType_Charset() {
        val charset = Charsets.ISO_8859_1
        val text = "Some text"
        val bytes = text.toByteArray(charset)

        val response = MockResponse()
            .setResponseCode(200)
            .setHeader(HttpRequest.Headers.CONTENT_TYPE, "application/json; charset=${charset.name()}")
            .setBody(Buffer().write(bytes))
        startMockWebServer(response)

        val callback = mockk<Callback<NetworkResult>>()
        val request = HttpRequest.get(urlString).build()
        httpClient.sendRequest(request, callback)

        verify(timeout = 1000) {
            callback.onComplete(match {
                it is Success
                        && it.httpResponse.body.contentEquals(bytes)
                        && it.httpResponse.bodyText() == text
            })
        }
    }

    @Test
    fun httpResponse_BodyText_Reads_Body_Using_UTF8_When_No_Charset_Specified() {
        val charset = Charsets.UTF_8
        val text = "Some text"
        val bytes = text.toByteArray(charset)

        val response = MockResponse()
            .setResponseCode(200)
            .setBody(Buffer().write(bytes))
        startMockWebServer(response)

        val callback = mockk<Callback<NetworkResult>>()
        val request = HttpRequest.get(urlString).build()
        httpClient.sendRequest(request, callback)

        verify(timeout = 1000) {
            callback.onComplete(match {
                it is Success
                        && it.httpResponse.body.contentEquals(bytes)
                        && it.httpResponse.bodyText() == text
            })
        }
    }

    @Test
    fun non200Exception_Includes_HttpResponse_With_Status_Headers_And_Body() {
        startMockWebServer(
            MockResponse()
                .setResponseCode(400)
                .setHeader(HttpRequest.Headers.CONTENT_TYPE, "application/json")
                .setBody("""{"error":"bad request"}""")
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) {
            callback.onComplete(match { result ->
                val response = (result as? Failure)
                    ?.let { it.networkException as? Non200Exception }
                    ?.httpResponse

                response != null
                        && response.statusCode == 400
                        && response.bodyText() == """{"error":"bad request"}"""
                        && response.headers[HttpRequest.Headers.CONTENT_TYPE]
                    ?.firstOrNull() == "application/json"
            })
        }
    }

    @Test
    fun non200Exception_Includes_HttpResponse_When_Error_Body_Is_Empty() {
        startMockWebServer(
            MockResponse()
                .setResponseCode(500)
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) {
            callback.onComplete(match { result ->
                val exception = (result as? Failure)?.networkException as? Non200Exception

                exception?.statusCode == 500
                        && exception.httpResponse?.statusCode == 500
                        && exception.httpResponse?.bodyText().isNullOrEmpty()
            })
        }
    }

    @Test
    fun non200Exception_HttpResponse_BodyText_Decompresses_GZipped_Error_Body() {
        val text = """{"error":"server error"}"""
        startMockWebServer(
            MockResponse()
                .setResponseCode(503)
                .setHeader(HttpRequest.Headers.CONTENT_ENCODING, "gzip")
                .setBody(Buffer().write(gzipBytes(text)))
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) {
            callback.onComplete(match { result ->
                val exception = (result as? Failure)?.networkException as? Non200Exception

                exception?.httpResponse?.bodyText() == text
            })
        }
    }

    @Test
    fun non200Exception_Reports_StatusCode_When_Error_Body_Is_Unreadable() {
        // claims gzip but the body is not gzipped, so reading it throws
        startMockWebServer(
            MockResponse()
                .setResponseCode(400)
                .setHeader(HttpRequest.Headers.CONTENT_ENCODING, "gzip")
                .setBody("not actually gzipped")
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) {
            callback.onComplete(match { result ->
                // the status code is still reported, whatever happened to the body
                (result as? Failure)?.networkException
                    ?.let { it as? Non200Exception }
                    ?.statusCode == 400
            })
        }
    }

    @Test
    fun non200Exception_Includes_Headers_When_Error_Body_Cannot_Be_Read() {
        // the connection drops part way through the body, so reading it throws
        startMockWebServer(
            MockResponse()
                .setResponseCode(500)
                .setHeader(HttpRequest.Headers.CONTENT_TYPE, "text/plain")
                .setChunkedBody("a".repeat(1024), 256)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) {
            callback.onComplete(match { result ->
                val response = (result as? Failure)?.networkException?.httpResponse

                // the body is lost, but the rest of the response is still reported
                response?.statusCode == 500
                        && response.headers[HttpRequest.Headers.CONTENT_TYPE]
                    ?.firstOrNull() == "text/plain"
                        && response.body == null
            })
        }
    }

    @Test
    fun unexpectedException_Includes_HttpResponse_For_Redirect_Without_Location_Header() {
        startMockWebServer(
            MockResponse()
                .setResponseCode(301)
                .setHeader(HttpRequest.Headers.CONTENT_TYPE, "text/plain")
                .setBody("Moved")
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) {
            callback.onComplete(match { result ->
                val exception = (result as? Failure)?.networkException as? UnexpectedException

                exception?.httpResponse?.statusCode == 301
                        && exception.httpResponse?.headers
                    ?.get(HttpRequest.Headers.CONTENT_TYPE)?.firstOrNull() == "text/plain"
                        // a 3XX has no error stream, so the body arrives on the input stream
                        && exception.httpResponse?.bodyText() == "Moved"
            })
        }
    }

    @Test
    fun networkIOException_Includes_HttpResponse_When_Successful_Body_Cannot_Be_Read() {
        // the connection drops part way through the body, so reading it throws
        startMockWebServer(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpRequest.Headers.CONTENT_TYPE, "text/plain")
                .setChunkedBody("a".repeat(1024), 256)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) {
            callback.onComplete(match { result ->
                val exception = (result as? Failure)?.networkException as? NetworkIOException

                // the body is lost, but the rest of the response is still reported
                exception?.httpResponse?.statusCode == 200
                        && exception.httpResponse?.headers
                    ?.get(HttpRequest.Headers.CONTENT_TYPE)?.firstOrNull() == "text/plain"
                        && exception.httpResponse?.body == null
            })
        }
    }

    @Test
    fun networkException_HttpResponse_Is_Null_When_No_Response_Was_Received() {
        // nothing listening on the port, so the connection is never made
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) {
            callback.onComplete(match { result ->
                val exception = (result as? Failure)?.networkException

                exception is NetworkIOException && exception.httpResponse == null
            })
        }
    }

    @Test
    fun logResult_Logs_Concise_Summary_At_Error_For_Non200_Response() {
        val request = HttpRequest.get(urlString).build()
        startMockWebServer(
            MockResponse()
                .setResponseCode(404)
                .setHeader(HttpRequest.Headers.CONTENT_TYPE, "text/plain")
                .setBody("Not Found")
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(request, callback)

        verify(timeout = 1000) { callback.onComplete(any()) }

        val message = captureLogMessage(LogLevel.ERROR)
        assertEquals(
            "Completed request ${request.url} with Failure(Non200Exception(404))",
            message
        )
    }

    @Test
    fun logResult_Logs_Concise_Summary_At_Debug_For_Not_Modified_Response() {
        val request = HttpRequest.get(urlString).build()
        startMockWebServer(MockResponse().setResponseCode(304))
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(request, callback)

        verify(timeout = 1000) { callback.onComplete(any()) }

        // an unchanged resource is the expected outcome of a conditional request, not an error
        val message = captureLogMessage(LogLevel.DEBUG)
        assertEquals(
            "Completed request ${request.url} with Failure(Non200Exception(304))",
            message
        )
        verify(exactly = 0) {
            mockLogger.log(LogLevel.ERROR, LogCategory.HTTP_CLIENT, any<() -> String>())
        }
    }

    @Test
    fun logResult_Logs_Concise_Summary_At_Debug_For_Successful_Response() {
        val request = HttpRequest.get(urlString).build()
        startMockWebServer(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpRequest.Headers.CONTENT_TYPE, "text/plain")
                .setBody("Success")
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(request, callback)

        verify(timeout = 1000) { callback.onComplete(any()) }

        val message = captureLogMessage(LogLevel.DEBUG)
        assertTrue(message.startsWith("Completed request ${request.url} with Success("))
        assertFalse(message.contains("Body: "))
        assertFalse(message.contains("Headers: "))
    }

    @Test
    fun logResult_Logs_Headers_And_Body_At_Trace_For_Non200_Response() {
        startMockWebServer(
            MockResponse()
                .setResponseCode(401)
                .setHeader("set-cookie", "session=super-secret")
                .setBody("Unauthorized")
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) { callback.onComplete(any()) }

        val message = captureTraceLogMessage()
        assertTrue(message.contains("Headers: "))
        assertTrue(message.contains("session=super-secret"))
        assertTrue(message.contains("Body: Unauthorized"))
    }

    @Test
    fun logResult_Logs_Headers_And_Body_At_Trace_For_Successful_Response() {
        startMockWebServer(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpRequest.Headers.CONTENT_TYPE, "text/plain")
                .setBody("Success")
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) { callback.onComplete(any()) }

        val message = captureTraceLogMessage()
        assertTrue(message.contains("Headers: "))
        assertTrue(message.contains("text/plain"))
        assertTrue(message.contains("Body: Success"))
    }

    @Test
    fun logResult_Logs_Full_Body_At_Trace_When_Response_Is_Large() {
        val body = "a".repeat(10_000)
        startMockWebServer(
            MockResponse()
                .setResponseCode(500)
                .setBody(body)
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) { callback.onComplete(any()) }

        assertTrue(captureTraceLogMessage().contains("Body: $body"))
    }

    @Test
    fun logResult_Does_Not_Log_Response_Details_When_Trace_Logging_Disabled() {
        every { mockLogger.shouldLog(LogLevel.TRACE) } returns false
        startMockWebServer(
            MockResponse()
                .setResponseCode(401)
                .setHeader("set-cookie", "session=super-secret")
                .setBody("Unauthorized")
        )
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) { callback.onComplete(any()) }

        // the failure is still summarised at ERROR, but no response details are written
        assertTrue(captureLogMessage(LogLevel.ERROR).contains("Non200Exception(401)"))
        verify(exactly = 0) {
            mockLogger.trace(LogCategory.HTTP_CLIENT, any<String>())
        }
    }

    @Test
    fun logResult_Reports_No_Response_At_Trace_When_None_Is_Available() {
        val request = HttpRequest.get(urlString).build()

        // nothing listening on the port, so no response is ever received
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)
        httpClient.sendRequest(request, callback)

        verify(timeout = 1000) {
            callback.onComplete(match { it is Failure && it.networkException is NetworkIOException })
        }

        val message = captureTraceLogMessage()
        assertTrue(message.contains("No response was received"))
        assertFalse(message.contains("Headers: "))
        assertFalse(message.contains("Body: "))
    }

    @Test
    fun sendRequest_Sends_Default_Accept_Encoding_When_Request_Has_None() {
        startMockWebServer(MockResponse().setResponseCode(200))
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get(urlString).build(), callback)

        verify(timeout = 1000) { callback.onComplete(any()) }

        val mockRequest = mockWebServer.takeRequest()
        assertEquals("gzip", mockRequest.getHeader(HttpRequest.Headers.ACCEPT_ENCODING))
    }

    @Test
    fun sendRequest_Sends_Request_Accept_Encoding_When_One_Is_Provided() {
        startMockWebServer(MockResponse().setResponseCode(200))
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(
            HttpRequest.get(urlString)
                .header(HttpRequest.Headers.ACCEPT_ENCODING, "identity")
                .build(),
            callback
        )

        verify(timeout = 1000) { callback.onComplete(any()) }

        val mockRequest = mockWebServer.takeRequest()
        assertEquals("identity", mockRequest.getHeader(HttpRequest.Headers.ACCEPT_ENCODING))
    }

    @Test
    fun sendRequest_Sends_Default_Content_Type_When_Request_Body_Has_None() {
        startMockWebServer(MockResponse().setResponseCode(200))
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.post(urlString, "Test Body").build(), callback)

        verify(timeout = 1000) { callback.onComplete(any()) }

        val mockRequest = mockWebServer.takeRequest()
        assertEquals("application/json", mockRequest.getHeader(HttpRequest.Headers.CONTENT_TYPE))
        assertEquals("Test Body", mockRequest.body.readUtf8())
    }

    @Test
    fun sendRequest_Sends_GZipped_Body_When_Request_Is_Gzip() {
        startMockWebServer(MockResponse().setResponseCode(200))
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(
            HttpRequest.post(urlString, "Test Body").gzip(true).build(),
            callback
        )

        verify(timeout = 1000) { callback.onComplete(any()) }

        val mockRequest = mockWebServer.takeRequest()
        assertEquals("gzip", mockRequest.getHeader(HttpRequest.Headers.CONTENT_ENCODING))
        assertEquals(
            "Test Body",
            GZIPInputStream(mockRequest.body.inputStream()).use { it.readBytes() }
                .toString(Charsets.UTF_8)
        )
    }

    @Test
    fun sendRequest_Builds_And_Sends_Request_When_Url_Is_Valid() {
        startMockWebServer(MockResponse().setResponseCode(200).setBody("result"))

        val callback = mockk<Callback<NetworkResult>>(relaxed = true)
        httpClient.sendRequest(HttpRequest.get(urlString), callback)

        verify(timeout = 1000) {
            callback.onComplete(match {
                it is Success && it.httpResponse.bodyText() == "result"
            })
        }
    }

    @Test
    fun sendRequest_Builder_Completes_With_Failure_When_Url_Is_Malformed() {
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        val disposable = httpClient.sendRequest(HttpRequest.get("not a url"), callback)

        assertTrue(disposable.isDisposed)
        verify {
            callback.onComplete(match {
                it is Failure && it.networkException is UnexpectedException
            })
        }
    }

    @Test
    fun sendRequest_Builder_Logs_Error_When_Url_Is_Malformed() {
        every { mockLogger.shouldLog(LogLevel.ERROR) } returns true
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get("not a url"), callback)

        verify {
            mockLogger.error(LogCategory.HTTP_CLIENT, any<String>())
        }
    }

    @Test
    fun sendRequest_Builder_Does_Not_Log_When_Error_Logging_Disabled() {
        every { mockLogger.shouldLog(LogLevel.ERROR) } returns false
        val callback = mockk<Callback<NetworkResult>>(relaxed = true)

        httpClient.sendRequest(HttpRequest.get("not a url"), callback)

        verify(exactly = 0) {
            mockLogger.error(any<String>(), any<String>())
        }
        // the caller is still notified regardless of log level
        verify {
            callback.onComplete(match { it is Failure })
        }
    }

    /**
     * Captures the message produced by the completion summary log statement written at the given
     * [level].
     */
    private fun captureLogMessage(level: LogLevel): String {
        val messages = mutableListOf<() -> String>()
        verify {
            mockLogger.log(level, LogCategory.HTTP_CLIENT, capture(messages))
        }

        return messages.last().invoke()
    }

    /**
     * Captures the message produced by the detailed response log statement, which is written at
     * [LogLevel.TRACE] alongside the trace statements for sending the request.
     */
    private fun captureTraceLogMessage(): String {
        val messages = mutableListOf<String>()
        verify {
            mockLogger.trace(LogCategory.HTTP_CLIENT, capture(messages))
        }

        return messages.last { it.startsWith("Response for request") }
    }

    private fun gzipBytes(text: String, charset: Charset = Charsets.UTF_8): ByteArray {
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { gzip ->
            gzip.write(text.toByteArray(charset))
        }

        return byteStream.toByteArray()
    }
}
