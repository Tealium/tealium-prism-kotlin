package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.internal.misc.SingleThreadedScheduler
import com.tealium.prism.core.internal.misc.ThreadPoolScheduler
import com.tealium.tests.common.Recorder
import com.tealium.tests.common.RecordingInterceptor
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.assertCancelled
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections.synchronizedList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Checks that disposing a request aborts its attempt in flight on Android's [java.net.HttpURLConnection]
 * implementation, which differs from the JDK's used by the unit tests.
 */
class HttpClientAbortTests {

    private lateinit var server: MockWebServer
    private lateinit var tealiumExecutor: ScheduledExecutorService
    private lateinit var networkExecutor: ScheduledExecutorService
    private lateinit var attemptResults: Recorder<NetworkResult>
    private lateinit var client: HttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        // fresh schedulers per test, so that an attempt left blocked can't hold up the others
        val tealiumThreadFactory = SingleThreadedScheduler.SingleThreadFactory("tealium-abort-test")
        tealiumExecutor = Executors.newSingleThreadScheduledExecutor(tealiumThreadFactory)
        networkExecutor = Executors.newScheduledThreadPool(0)
        attemptResults = Recorder()

        client = HttpClient(
            SystemLogger,
            SingleThreadedScheduler(tealiumThreadFactory, tealiumExecutor),
            ThreadPoolScheduler(networkExecutor),
            listOf(RecordingInterceptor("abort", synchronizedList(mutableListOf()), attemptResults::record))
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        networkExecutor.shutdownNow()
        tealiumExecutor.shutdownNow()
    }

    @Test
    fun dispose_Aborts_Attempt_Promptly_When_Response_Never_Arrives() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        // the attempt would otherwise end at its 30 seconds read timeout
        assertAbortedPromptly()
    }

    @Test
    fun dispose_Aborts_Attempt_Promptly_When_5xx_Body_Is_Throttled() {
        // the headers are sent at once, but the body would take over a minute
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("a".repeat(1024))
                .throttleBody(16, 1, TimeUnit.SECONDS)
        )

        assertAbortedPromptly()
    }

    /**
     * Sends a request, disposes it once the server has received it, and checks that both the
     * request and its attempt complete as cancelled within 10 seconds.
     */
    private fun assertAbortedPromptly() {
        val completion = Recorder<NetworkResult>()
        val startTime = System.nanoTime()

        val request = client.sendRequest(HttpRequest.get(server.url("/").toString()).build(), completion::record)
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        request.dispose()

        assertCancelled(completion.await(10, TimeUnit.SECONDS))
        assertCancelled(attemptResults.await(10, TimeUnit.SECONDS))
        assertTrue(System.nanoTime() - startTime < TimeUnit.SECONDS.toNanos(10))
    }
}
