package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.tests.common.ManualScheduler
import com.tealium.tests.common.RecordingInterceptor
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.assertCancelled
import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.URLStreamHandler

/**
 * Tests that [HttpClient] disconnects each connection once, and that the attempt calls back when
 * `disconnect()` throws.
 *
 * No test starts a thread: the tealium scheduler is synchronous and the network scheduler manual,
 * so the order of the calls is deterministic.
 */
class HttpClientDisconnectTests {

    private val attemptResults = mutableListOf<NetworkResult>()
    private val completions = mutableListOf<NetworkResult>()

    private val network = ManualScheduler()
    private val client = HttpClient(
        SystemLogger,
        Scheduler.SYNCHRONOUS,
        network,
        listOf(RecordingInterceptor("interceptor", mutableListOf(), attemptResults::add))
    )

    private val connection = mockk<HttpURLConnection>(relaxed = true)

    private val request = HttpRequest.get(
        URL("http", "fake.invalid", 80, "/", object : URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection = connection
        })
    ).build()

    init {
        every { connection.url } returns request.url
        every { connection.headerFields } returns emptyMap()
        every { connection.inputStream } returns ByteArrayInputStream(ByteArray(0))
        every { connection.responseMessage } returns "OK"
    }

    @Test
    fun sendRequest_Succeeds_When_Disconnect_Throws() {
        every { connection.responseCode } returns 200
        every { connection.disconnect() } throws RuntimeException("disconnect failed")

        client.sendRequest(request, completions::add)
        network.runAll()

        assertTrue(attemptResults.single() is Success)
        assertTrue(completions.single() is Success)
        verify(exactly = 1) { connection.disconnect() }
    }

    @Test
    fun dispose_Disconnects_Once_When_Abort_Disconnect_Throws() {
        every { connection.disconnect() } throws RuntimeException("disconnect failed") andThenJust Runs
        val disposable = client.sendRequest(request, completions::add)
        // the abort lands while the attempt waits for the response; the tealium scheduler is
        // synchronous, so the dispose disconnects inline
        every { connection.responseCode } answers {
            disposable.dispose()
            throw IOException("Socket closed")
        }

        network.runAll()

        assertCancelled(attemptResults.single())
        assertCancelled(completions.single())
        verify(exactly = 1) { connection.disconnect() }
    }
}
