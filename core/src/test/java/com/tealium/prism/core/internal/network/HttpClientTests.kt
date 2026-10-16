package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkClient
import com.tealium.prism.core.api.network.NetworkException.NetworkIOException
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkException.UnexpectedException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.misc.ThreadPoolScheduler
import com.tealium.tests.common.Recorder
import com.tealium.tests.common.RecordingInterceptor
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.assertCancelled
import com.tealium.tests.common.awaitQueuedTasks
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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.Collections.synchronizedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
class HttpClientTests {

    lateinit var mockWebServer: MockWebServer

    private val mockInterceptor: RequestInterceptor = mockk(relaxed = true)
    private val mockLogger: Logger = mockk(relaxed = true)

    lateinit var httpClient: HttpClient
    private val port = 8888

    private val urlString = "http://localhost:$port"

    @Before
    fun setUp() {

        httpClient = HttpClient(mockLogger, testTealiumScheduler, testNetworkScheduler, listOf(mockInterceptor))

        // a relaxed mock reports every level as disabled; trace is the level under test for the
        // detailed response log, so opt in to it by default
        every { mockLogger.shouldLog(LogLevel.TRACE) } returns true

        every { mockInterceptor.shouldRetry(any(), any(), any()) } returns DoNotRetry
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
    fun sendRequest_Reports_Same_Request_To_OnAttemptResult_When_Successful() {
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

        val result = slot<NetworkResult>()
        verify(timeout = 1000) { completion(capture(result)) }

        val success = result.captured as Success
        assertEquals(200, success.httpResponse.statusCode)
        assertEquals("Success", success.httpResponse.bodyText())
        verify { mockInterceptor.onAttemptResult(httpRequest, any(), success) }
    }

    @Test
    fun sendRequest_Reports_Same_Request_To_OnAttemptResult_When_Failed() {
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

        val result = slot<NetworkResult>()
        verify(timeout = 1000) { completion(capture(result)) }

        val failure = result.captured as Failure
        assertEquals(500, (failure.networkException as Non200Exception).statusCode)
        verify { mockInterceptor.onAttemptResult(httpRequest, any(), failure) }
    }

    @Test
    fun sendRequest_Completes_With_Cancelled_Immediately_When_Disposed_While_Waiting_To_Retry() {
        startMockWebServer(MockResponse().setResponseCode(500))
        val decided = CountDownLatch(1)
        // long enough that only an immediate cancellation completes within the await below
        val retrying = RecordingInterceptor("retrying", synchronizedList(mutableListOf())) {
            decided.countDown()
            RetryPolicy.AfterDelay(30_000.milliseconds)
        }
        val client = httpClient.newClient(listOf(retrying))
        val completion = Recorder<NetworkResult>()

        val request = client.sendRequest(HttpRequest.get(urlString).build(), completion::record)
        assertTrue(decided.await(5, TimeUnit.SECONDS))
        request.dispose()

        assertCancelled(completion.await())
        assertTrue(request.isDisposed)
        testTealiumScheduler.awaitQueuedTasks()
        assertTrue(completion.isEmpty())
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun sendRequest_Is_Disposed_When_Completed() {
        startMockWebServer(MockResponse().setResponseCode(200))
        val completion = Recorder<NetworkResult>()

        val request = httpClient.sendRequest(HttpRequest.get(urlString).build(), completion::record)

        assertTrue(completion.await() is Success)
        assertTrue(request.isDisposed)
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
                        && result.httpResponse.statusCode == 200
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
        every { mockInterceptor.onAttemptResult(any(), any(), any()) } just Runs
        every {
            mockInterceptor.shouldRetry(any(), any(), any())
        } returns RetryPolicy.AfterDelay(10.milliseconds) andThen RetryPolicy.AfterDelay(10.milliseconds) andThen DoNotRetry
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
            mockInterceptor.shouldRetry(any(), 0, any())
            mockInterceptor.shouldRetry(any(), 1, any())
            mockInterceptor.shouldRetry(any(), 2, any())
        }
    }

    @Test
    fun sendRequest_Retries_After_Delay_When_Interceptor_Returns_AfterDelay() {
        val delayDuration = 500L
        every { mockInterceptor.shouldRetry(any(), any(), any()) } returns
                RetryPolicy.AfterDelay(TimeFrame(delayDuration, TimeUnit.MILLISECONDS)) andThen DoNotRetry
        startMockWebServer(
            MockResponse().setResponseCode(500),
            MockResponse().setResponseCode(200)
        )
        val httpRequest = HttpRequest.get(urlString).build()
        val completion = Recorder<NetworkResult>()

        val startTime = System.currentTimeMillis()
        httpClient.sendRequest(httpRequest, completion::record)

        assertTrue(completion.await() is Success)
        assertTrue(System.currentTimeMillis() >= startTime + delayDuration)
        assertEquals(2, mockWebServer.requestCount)
        verify(exactly = 2) { mockInterceptor.shouldRetry(httpRequest, any(), any()) }
    }

    @Test
    fun sendRequest_Retries_When_AfterEvent_Emits() {
        val subject = Observables.publishSubject<Unit>()
        every { mockInterceptor.shouldRetry(any(), any(), any()) } returns
                RetryPolicy.AfterEvent(subject) andThen DoNotRetry
        startMockWebServer(
            MockResponse().setResponseCode(500),
            MockResponse().setResponseCode(200)
        )
        val httpRequest = HttpRequest.get(urlString).build()
        val completion = Recorder<NetworkResult>()

        httpClient.sendRequest(httpRequest, completion::record)

        verify(timeout = 5000) { mockInterceptor.shouldRetry(httpRequest, any(), any()) }
        // the wait is subscribed in the same tealium task as the decision
        testTealiumScheduler.awaitQueuedTasks()
        assertTrue(completion.isEmpty())
        assertEquals(1, mockWebServer.requestCount)

        testTealiumScheduler.execute { subject.onNext(Unit) }
        assertTrue(completion.await() is Success)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun sendRequest_Asks_Interceptors_In_Reverse_Order_When_Every_One_Declines() {
        startMockWebServer(MockResponse().setResponseCode(503))
        val mockInterceptor1 = mockk<RequestInterceptor>()
        val mockInterceptor2 = mockk<RequestInterceptor>()

        every { mockInterceptor1.onAttemptResult(any(), any(), any()) } just Runs
        every { mockInterceptor1.shouldRetry(any(), any(), any()) } returns DoNotRetry
        every { mockInterceptor2.onAttemptResult(any(), any(), any()) } just Runs
        every { mockInterceptor2.shouldRetry(any(), any(), any()) } returns DoNotRetry

        val client = httpClient.newClient(listOf(mockInterceptor1, mockInterceptor2), maxRetryCount = null)

        val httpRequest = HttpRequest.get(urlString).build()
        val completion = Recorder<NetworkResult>()

        client.sendRequest(httpRequest, completion::record)

        // every interceptor declines, so the request is not retried, and is only reported once
        assertEquals(503, statusCodeOf(completion.await()))
        testTealiumScheduler.awaitQueuedTasks()
        assertTrue(completion.isEmpty())
        assertEquals(1, mockWebServer.requestCount)

        verifyOrder {
            mockInterceptor2.shouldRetry(httpRequest, 0, any())
            mockInterceptor1.shouldRetry(httpRequest, 0, any())
            mockInterceptor.shouldRetry(httpRequest, 0, any())
        }

        verify { mockInterceptor1.onAttemptResult(httpRequest, any(), any()) }
        verify { mockInterceptor2.onAttemptResult(httpRequest, any(), any()) }

        confirmVerified(mockInterceptor1, mockInterceptor2)
    }

    @Test
    fun sendRequest_Retries_Through_First_Interceptor_That_Retries_When_Asked_In_Reverse_Order() {
        startMockWebServer(
            MockResponse().setResponseCode(503),
            MockResponse().setResponseCode(200)
        )
        val mockInterceptor1 = mockk<RequestInterceptor>()
        val mockInterceptor2 = mockk<RequestInterceptor>()
        val mockInterceptor3 = mockk<RequestInterceptor>()

        every { mockInterceptor1.onAttemptResult(any(), any(), any()) } just Runs
        every { mockInterceptor1.shouldRetry(any(), any(), any()) } returns DoNotRetry
        every { mockInterceptor2.onAttemptResult(any(), any(), any()) } just Runs
        every { mockInterceptor2.shouldRetry(any(), any(), any()) } returns RetryPolicy.AfterDelay(100.milliseconds)
        every { mockInterceptor3.onAttemptResult(any(), any(), any()) } just Runs
        every { mockInterceptor3.shouldRetry(any(), any(), any()) } returns DoNotRetry

        // a single retry: the second attempt completes the request without asking the interceptors
        val client = httpClient.newClient(listOf(mockInterceptor1, mockInterceptor2, mockInterceptor3), maxRetryCount = 1)

        val httpRequest = HttpRequest.get(urlString).build()
        val completion = Recorder<NetworkResult>()

        client.sendRequest(httpRequest, completion::record)

        assertEquals(200, statusCodeOf(completion.await()))
        assertEquals(2, mockWebServer.requestCount)

        verify(exactly = 1) { mockInterceptor3.shouldRetry(httpRequest, any(), any()) }
        verify(exactly = 1) { mockInterceptor2.shouldRetry(httpRequest, any(), any()) }
        verify(exactly = 0) { mockInterceptor1.shouldRetry(httpRequest, any(), any()) }
        verify(exactly = 0) { mockInterceptor.shouldRetry(httpRequest, any(), any()) }

        verify(exactly = 2) { mockInterceptor1.onAttemptResult(httpRequest, any(), any()) }
        verify(exactly = 2) { mockInterceptor2.onAttemptResult(httpRequest, any(), any()) }
        verify(exactly = 2) { mockInterceptor3.onAttemptResult(httpRequest, any(), any()) }

        confirmVerified(mockInterceptor1, mockInterceptor2, mockInterceptor3)
    }

    @Test
    fun newClient_Retries_Through_Additional_Interceptor_While_Parent_Does_Not() {
        startMockWebServer(
            MockResponse().setResponseCode(503),
            MockResponse().setResponseCode(503),
            MockResponse().setResponseCode(200)
        )
        val calls = synchronizedList(mutableListOf<String>())
        val retrying = RecordingInterceptor("retrying", calls) { consecutiveRetryCount ->
            if (consecutiveRetryCount == 0) RetryPolicy.AfterDelay(0.milliseconds) else DoNotRetry
        }
        val derived = httpClient.newClient(listOf(retrying))

        assertEquals(503, statusCodeOf(sendAndAwait(httpClient)))
        assertEquals(1, mockWebServer.requestCount)
        assertEquals(emptyList<String>(), calls)

        assertEquals(200, statusCodeOf(sendAndAwait(derived)))
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun newClient_Appends_Interceptors_After_Parents() {
        startMockWebServer(MockResponse().setResponseCode(503))
        val calls = synchronizedList(mutableListOf<String>())
        val parentInterceptor = RecordingInterceptor("parent", calls)
        val additionalInterceptor = RecordingInterceptor("additional", calls)
        val parent = HttpClient(mockLogger, testTealiumScheduler, testNetworkScheduler, listOf(parentInterceptor))

        val derived = parent.newClient(listOf(additionalInterceptor), maxRetryCount = null)
        sendAndAwait(derived)

        assertEquals(listOf(parentInterceptor, additionalInterceptor), derived.interceptorManager.interceptors)
        assertEquals(
            listOf(
                "parent.onAttemptResult(0)",
                "additional.onAttemptResult(0)",
                "additional.shouldRetry(0)",
                "parent.shouldRetry(0)"
            ),
            calls
        )
    }

    @Test
    fun newClient_Does_Not_Change_Parent() {
        startMockWebServer(MockResponse().setResponseCode(503))
        val calls = synchronizedList(mutableListOf<String>())
        val parentInterceptor = RecordingInterceptor("parent", calls)
        val parent = HttpClient(mockLogger, testTealiumScheduler, testNetworkScheduler, listOf(parentInterceptor))

        parent.newClient(listOf(RecordingInterceptor("additional", calls)), maxRetryCount = 1)
        sendAndAwait(parent)

        assertEquals(listOf(parentInterceptor), parent.interceptorManager.interceptors)
        assertNull(parent.interceptorManager.maxRetryCount)
        assertEquals(listOf("parent.onAttemptResult(0)", "parent.shouldRetry(0)"), calls)
    }

    @Test
    fun newClient_Stops_Retrying_When_MaxRetryCount_Is_Reached() {
        startMockWebServer(*Array(5) { MockResponse().setResponseCode(503) })
        val calls = synchronizedList(mutableListOf<String>())
        val alwaysRetrying = RecordingInterceptor("retrying", calls) { RetryPolicy.AfterDelay(0.milliseconds) }

        val derived = httpClient.newClient(listOf(alwaysRetrying), maxRetryCount = 2)

        assertEquals(503, statusCodeOf(sendAndAwait(derived)))
        assertEquals(3, mockWebServer.requestCount)
        assertEquals(
            listOf("retrying.shouldRetry(0)", "retrying.shouldRetry(1)"),
            calls.filter { it.contains("shouldRetry") }
        )
        // the last result still reaches the interceptors, with a count equal to maxRetryCount
        assertEquals(
            listOf("retrying.onAttemptResult(0)", "retrying.onAttemptResult(1)", "retrying.onAttemptResult(2)"),
            calls.filter { it.contains("onAttemptResult") }
        )
    }

    @Test
    fun newClient_Does_Not_Inherit_MaxRetryCount() {
        startMockWebServer(*Array(8) { MockResponse().setResponseCode(503) })
        val calls = synchronizedList(mutableListOf<String>())
        val retrying = RecordingInterceptor("retrying", calls) { consecutiveRetryCount ->
            if (consecutiveRetryCount < 4) RetryPolicy.AfterDelay(0.milliseconds) else DoNotRetry
        }
        val limited = httpClient.newClient(listOf(retrying), maxRetryCount = 2)

        val unlimited = limited.newClient(emptyList(), maxRetryCount = null)

        assertNull(unlimited.interceptorManager.maxRetryCount)
        sendAndAwait(unlimited)
        assertEquals(5, mockWebServer.requestCount)
        sendAndAwait(limited)
        assertEquals(8, mockWebServer.requestCount)
    }

    @Test
    fun newClient_Shares_Schedulers_And_Logger_With_Parent() {
        startMockWebServer(MockResponse().setResponseCode(200))
        val tealiumScheduler = CountingScheduler(testTealiumScheduler)
        val networkScheduler = CountingScheduler(testNetworkScheduler)
        val logger: Logger = mockk(relaxed = true)
        val parent = HttpClient(logger, tealiumScheduler, networkScheduler)

        val derived = parent.newClient(listOf(RecordingInterceptor("additional", synchronizedList(mutableListOf()))))
        assertTrue(sendAndAwait(derived) is Success)

        assertTrue(tealiumScheduler.count.get() > 0)
        assertTrue(networkScheduler.count.get() > 0)
        verify { logger.log(LogLevel.DEBUG, LogCategory.HTTP_CLIENT, any<() -> String>()) }
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

    @Test
    fun logResult_Logs_Concise_Summary_At_Debug_For_Cancelled_Request() {
        val request = HttpRequest.get(urlString).build()
        val completion = Recorder<NetworkResult>()

        httpClient.sendRequest(request, completion::record).dispose()

        assertCancelled(completion.await())
        assertEquals(
            "Completed request ${request.url} with Failure(Cancelled)",
            captureLogMessage(LogLevel.DEBUG)
        )
        verify(exactly = 0) {
            mockLogger.log(LogLevel.ERROR, LogCategory.HTTP_CLIENT, any<() -> String>())
        }
    }

    @Test
    fun sendRequest_Reports_Cancelled_To_OnAttemptResult_When_Disposed_Before_Result_Is_Processed() {
        startMockWebServer(MockResponse().setResponseCode(200))
        val tealiumScheduler = DisposingBeforeResumeScheduler(testTealiumScheduler)
        val networkScheduler = ThreadPoolScheduler(0)
        val ioGate = CountDownLatch(1)
        networkScheduler.execute { ioGate.await() }
        val attemptResults = Recorder<NetworkResult>()
        val client = HttpClient(
            SystemLogger,
            tealiumScheduler,
            networkScheduler,
            listOf(RecordingInterceptor("interceptor", synchronizedList(mutableListOf()), attemptResults::record))
        )
        val completion = Recorder<NetworkResult>()

        // the attempt can't complete before the scheduler is armed with its request
        tealiumScheduler.disposeBeforeResume =
            client.sendRequest(HttpRequest.get(urlString).build(), completion::record)
        ioGate.countDown()

        assertCancelled(completion.await())
        // the request succeeded, but was disposed before its result was processed
        assertCancelled(attemptResults.await())
        assertEquals(1, mockWebServer.requestCount)
        testTealiumScheduler.awaitQueuedTasks()
        assertTrue(completion.isEmpty())
    }

    @Test
    fun dispose_Aborts_Attempt_When_Response_Never_Arrives() {
        startMockWebServer(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val abortable = AbortableClient()
        val completion = Recorder<NetworkResult>()
        val startTime = System.nanoTime()

        val request = abortable.client.sendRequest(HttpRequest.get(urlString).build(), completion::record)
        assertNotNull(mockWebServer.takeRequest(5, TimeUnit.SECONDS))
        request.dispose()

        assertCancelled(completion.await())
        // the attempt ends, well before its 30 seconds read timeout, and is reported as cancelled
        assertCancelled(abortable.attemptResults.await())
        assertTrue(System.nanoTime() - startTime < TimeUnit.SECONDS.toNanos(10))
    }

    @Test
    fun dispose_Never_Sends_Attempt_When_Disposed_While_Queued_Behind_Blocked_Attempt() {
        startMockWebServer()
        mockWebServer.dispatcher = blockingDispatcher()
        val abortable = AbortableClient()
        val blockedCompletion = Recorder<NetworkResult>()
        val queuedCompletion = Recorder<NetworkResult>()

        val blocked = abortable.client.sendRequest(
            HttpRequest.get("$urlString/blocked").build(), blockedCompletion::record
        )
        assertEquals("/blocked", mockWebServer.takeRequest(5, TimeUnit.SECONDS)?.path)
        val queued = abortable.client.sendRequest(
            HttpRequest.get("$urlString/queued").build(), queuedCompletion::record
        )
        // the attempt is now queued on the network scheduler, behind the blocked one
        testTealiumScheduler.awaitQueuedTasks()
        queued.dispose()
        assertCancelled(queuedCompletion.await())
        blocked.dispose()
        assertCancelled(blockedCompletion.await())

        assertTrue(sendAndAwait(abortable.client, "$urlString/after") is Success)
        assertEquals("/after", mockWebServer.takeRequest(5, TimeUnit.SECONDS)?.path)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun dispose_Lets_Queued_Attempt_Proceed_When_Blocked_Attempt_Is_Aborted() {
        startMockWebServer()
        mockWebServer.dispatcher = blockingDispatcher()
        val abortable = AbortableClient()
        val blockedCompletion = Recorder<NetworkResult>()
        val queuedCompletion = Recorder<NetworkResult>()
        val startTime = System.nanoTime()

        val blocked = abortable.client.sendRequest(
            HttpRequest.get("$urlString/blocked").build(), blockedCompletion::record
        )
        assertEquals("/blocked", mockWebServer.takeRequest(5, TimeUnit.SECONDS)?.path)
        abortable.client.sendRequest(HttpRequest.get("$urlString/queued").build(), queuedCompletion::record)
        blocked.dispose()

        assertCancelled(blockedCompletion.await())
        assertTrue(queuedCompletion.await() is Success)
        assertEquals("/queued", mockWebServer.takeRequest(5, TimeUnit.SECONDS)?.path)
        assertTrue(System.nanoTime() - startTime < TimeUnit.SECONDS.toNanos(10))
    }

    @Test
    fun dispose_Completes_With_Cancelled_When_Aborted_While_5xx_Body_Is_Throttled() {
        // the headers are sent at once, but the body takes about a second
        startMockWebServer(
            MockResponse()
                .setResponseCode(500)
                .setBody("a".repeat(256))
                .throttleBody(128, 500, TimeUnit.MILLISECONDS)
        )
        val abortable = AbortableClient()
        val completion = Recorder<NetworkResult>()

        val request = abortable.client.sendRequest(HttpRequest.get(urlString).build(), completion::record)
        assertNotNull(mockWebServer.takeRequest(5, TimeUnit.SECONDS))
        request.dispose()

        // the JDK's disconnect() can block until a body read in progress returns, and doesn't
        // interrupt it; the attempt is still reported as cancelled, whatever error body was read.
        // HttpClientAbortTests checks that Android aborts it promptly.
        assertCancelled(completion.await())
        assertCancelled(abortable.attemptResults.await())
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

    /**
     * Sends a GET request to [urlString] through [client], and waits for its result.
     */
    private fun sendAndAwait(client: NetworkClient, url: String = urlString): NetworkResult {
        val completion = Recorder<NetworkResult>()
        client.sendRequest(HttpRequest.get(url).build(), completion::record)
        return completion.await()
    }

    /**
     * Never responds to requests for `/blocked`, and responds `200` to any other.
     */
    private fun blockingDispatcher() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            if (request.path == "/blocked") {
                MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
            } else {
                MockResponse().setResponseCode(200)
            }
    }

    /**
     * A client with its own network scheduler, so that an attempt left blocked can't hold up
     * other tests; [attemptResults] records every attempt's result.
     */
    private class AbortableClient {
        val attemptResults = Recorder<NetworkResult>()
        val client = HttpClient(
            SystemLogger,
            testTealiumScheduler,
            ThreadPoolScheduler(0),
            listOf(RecordingInterceptor("abortable", synchronizedList(mutableListOf()), attemptResults::record))
        )
    }

    private fun statusCodeOf(result: NetworkResult): Int = when (result) {
        is Success -> result.httpResponse.statusCode
        is Failure -> (result.networkException as Non200Exception).statusCode
    }

    /**
     * A [Scheduler] that runs its tasks on [delegate], first disposing [disposeBeforeResume] when
     * armed.
     */
    private class DisposingBeforeResumeScheduler(private val delegate: Scheduler) : Scheduler {
        /**
         * Once set, disposed on the [delegate] just before the first task submitted from another
         * thread than the [testTealiumScheduler]'s, i.e. the network scheduler handing back a result.
         */
        @Volatile
        var disposeBeforeResume: Disposable? = null

        override fun execute(runnable: Runnable) {
            val toDispose = disposeBeforeResume
            if (toDispose != null && Thread.currentThread().name != TEALIUM_TEST_THREAD_NAME) {
                disposeBeforeResume = null
                delegate.execute { toDispose.dispose() }
            }
            delegate.execute(runnable)
        }

        override fun schedule(runnable: Runnable): Disposable = delegate.schedule(runnable)

        override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable =
            delegate.schedule(delay, runnable)
    }

    /**
     * A [Scheduler] that runs its tasks on [delegate], counting how many it was given.
     */
    private class CountingScheduler(private val delegate: Scheduler) : Scheduler {
        val count = AtomicInteger()

        override fun execute(runnable: Runnable) {
            count.incrementAndGet()
            delegate.execute(runnable)
        }

        override fun schedule(runnable: Runnable): Disposable {
            count.incrementAndGet()
            return delegate.schedule(runnable)
        }

        override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable {
            count.incrementAndGet()
            return delegate.schedule(delay, runnable)
        }
    }

    private companion object {
        /**
         * The name of the [testTealiumScheduler]'s thread.
         */
        const val TEALIUM_TEST_THREAD_NAME = "tealium-test"
    }
}
