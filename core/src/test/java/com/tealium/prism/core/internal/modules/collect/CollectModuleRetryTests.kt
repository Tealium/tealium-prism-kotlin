package com.tealium.prism.core.internal.modules.collect

import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.TealiumConfig
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkClient
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkUtilities
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RetryAfterAboveMaxDelay
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.internal.network.ConnectivityRetryInterceptor
import com.tealium.prism.core.internal.network.FakeConnectivity
import com.tealium.prism.core.internal.network.HttpClient
import com.tealium.prism.core.internal.network.RecordingNetworkClient
import com.tealium.prism.core.internal.network.RetryableErrorInterceptor
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.assertAfterDelay
import com.tealium.tests.common.testNetworkScheduler
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class CollectModuleRetryTests {

    private val connectivity = FakeConnectivity()

    @Test
    fun constructor_Derives_Client_Once_With_Uncapped_And_Connectivity_Retries_When_Created_From_Context() {
        val client = RecordingNetworkClient()
        val network = networkUtilities(client)

        CollectModule(Modules.Types.COLLECT, context(network), CollectModuleConfiguration())

        assertEquals("The client should be derived exactly once", 1, client.additionalInterceptors.size)
        val interceptors = client.additionalInterceptors.single()
        assertEquals(2, interceptors.size)

        val retryInterceptor = interceptors.first() as RetryableErrorInterceptor
        assertNull(retryInterceptor.maxAttempts)
        assertEquals(150_000L, retryInterceptor.maxDelay)
        assertSame(RetryAfterAboveMaxDelay.WAIT, retryInterceptor.retryAfterAboveMaxDelay)
        assertEquals("Collect should not limit the total retries", listOf<Int?>(null), client.maxRetryCounts)

        val connectivityInterceptor = interceptors.last() as ConnectivityRetryInterceptor
        assertSame(network, connectivityInterceptor.connectivity)
    }

    @Test
    fun networkInterceptors_Honors_RetryAfter_Within_Max_Delay() {
        val policy = collectRetryInterceptor().shouldRetry(request, 0, throttledFailure(retryAfter = "1"))

        assertEquals(1_000L, assertAfterDelay(policy))
    }

    @Test
    fun networkInterceptors_Waits_For_RetryAfter_Above_Max_Delay() {
        val policy = collectRetryInterceptor().shouldRetry(request, 0, throttledFailure(retryAfter = "500"))

        assertEquals(500_000L, assertAfterDelay(policy))
    }

    @Test
    fun dispatch_Retries_Transient_Failure_Once_And_Completes_When_Created_From_Context() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val root = HttpClient(SystemLogger, testTealiumScheduler, testNetworkScheduler)
            val configuration = CollectModuleConfiguration(url = server.url("/event").toUrl())
            val collect = CollectModule(Modules.Types.COLLECT, context(networkUtilities(root)), configuration)
            val dispatch = Dispatch.create("test", dataObject = DataObject.create {
                put(Dispatch.Keys.TEALIUM_VISITOR_ID, "visitor")
            })
            val completed = LinkedBlockingQueue<List<Dispatch>>()

            collect.dispatch(listOf(dispatch)) { completed.add(it) }

            val dispatches = checkNotNull(completed.poll(10, TimeUnit.SECONDS)) {
                "Collect should complete the dispatch"
            }
            assertEquals(listOf(dispatch.id), dispatches.map { it.id })
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun dispatch_Sends_Through_Derived_Client() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200))
        server.start()
        try {
            val derived = HttpClient(SystemLogger, testTealiumScheduler, testNetworkScheduler)
            val root = RecordingNetworkClient(derived = derived)
            val configuration = CollectModuleConfiguration(url = server.url("/event").toUrl())
            val collect = CollectModule(Modules.Types.COLLECT, context(networkUtilities(root)), configuration)
            val completed = LinkedBlockingQueue<List<Dispatch>>()

            collect.dispatch(listOf(Dispatch.create("test", dataObject = DataObject.create {
                put(Dispatch.Keys.TEALIUM_VISITOR_ID, "visitor")
            }))) { completed.add(it) }

            assertNotNull(completed.poll(10, TimeUnit.SECONDS))
            assertEquals(1, server.requestCount)
            assertEquals(1, root.additionalInterceptors.size)
        } finally {
            server.shutdown()
        }
    }

    private fun collectRetryInterceptor(): RequestInterceptor =
        CollectModule.networkInterceptors(connectivity).first()

    private fun throttledFailure(retryAfter: String): NetworkResult {
        val response = HttpResponse(localhost, 429, "", mapOf("Retry-After" to listOf(retryAfter)))
        return Failure(Non200Exception(429, response))
    }

    private fun networkUtilities(client: NetworkClient) = NetworkUtilities(
        connectivity = connectivity,
        networkClient = client,
        networkHelper = client.makeNetworkHelper(),
        logger = SystemLogger
    )

    private fun context(network: NetworkUtilities): TealiumContext {
        val config = mockk<TealiumConfig>()
        every { config.accountName } returns "tealium_account"
        every { config.profileName } returns "tealium_profile"
        val context = mockk<TealiumContext>()
        every { context.config } returns config
        every { context.logger } returns SystemLogger
        every { context.network } returns network
        return context
    }

    private companion object {
        val localhost = URL("https://localhost/")
        val request: HttpRequest = HttpRequest.get("https://localhost/").build()
    }
}
