package com.tealium.prism.contextapi

import com.tealium.prism.contextapi.internal.ContextApiConfiguration
import com.tealium.prism.contextapi.internal.ContextApiModule
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.Environment
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.modules.VisitorIdProvider
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkClient
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkUtilities
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.modules.ModuleManagerImpl
import com.tealium.prism.core.internal.network.HttpClient
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.assertAfterDelay
import com.tealium.tests.common.getDefaultConfig
import com.tealium.tests.common.testNetworkScheduler
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The Context API engine URL has a fixed host, so the requests of the derived client are
 * redirected to a [MockWebServer] by [RedirectingRootClient]; everything else - the [HttpClient],
 * its interceptors, the network helper and the service - is the production code.
 */
@RunWith(RobolectricTestRunner::class)
class ContextApiModuleRetryTests {

    private lateinit var server: MockWebServer
    private val configuration = ContextApiConfiguration(region = ContextApiRegion.UsEast, referrer = null)
    private val request = HttpRequest.get("http://localhost").build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun constructor_Derives_Client_Once_With_One_Interceptor_And_No_Connectivity_Retries() {
        val root = RedirectingRootClient(server)

        ContextApiModule(context(root), configuration)

        assertEquals("The client should be derived exactly once", 1, root.additionalInterceptors.size)
        val interceptors = root.additionalInterceptors.single()
        assertEquals("Context API should not wait for connectivity", 1, interceptors.size)
        assertEquals("Context API should not limit the total retries", listOf<Int?>(null), root.maxRetryCounts)
    }

    @Test
    fun fetchEngineResponse_Fails_After_One_Request_When_Throttled_With_RetryAfter_Above_Max_Delay() {
        repeat(3) {
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        }

        val result = fetchEngineResponse()

        assertTrue(result.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun fetchEngineResponse_Fails_After_Three_Requests_When_Every_Attempt_Fails() {
        repeat(4) { server.enqueue(MockResponse().setResponseCode(503)) }

        val result = fetchEngineResponse()

        assertTrue(result.isFailure)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun fetchEngineResponse_Retries_Transient_Failure_Once_And_Then_Succeeds() {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"audiences":["audience"]}"""))

        val result = fetchEngineResponse()

        assertTrue(result.isSuccess)
        assertEquals(listOf("audience"), result.getOrNull()?.audiences)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun networkInterceptors_Retries_After_RetryAfter_When_It_Equals_Max_Delay() {
        val policy = ContextApiModule.networkInterceptors().single()
            .shouldRetry(request, 0, failure(retryAfter = "20"))

        assertEquals(20_000L, assertAfterDelay(policy))
    }

    @Test
    fun networkInterceptors_Gives_Up_When_RetryAfter_Exceeds_Max_Delay() {
        val policy = ContextApiModule.networkInterceptors().single()
            .shouldRetry(request, 0, failure(retryAfter = "21"))

        assertSame(RetryPolicy.DoNotRetry, policy)
    }

    /**
     * Fetches an engine response through a [ContextApiModule] built from a context whose shared
     * client is a real [HttpClient], with the constructor that derives its retrying client.
     */
    private fun fetchEngineResponse(): TealiumResult<EngineResponse> {
        val module = ContextApiModule(context(RedirectingRootClient(server)), configuration)
        val results = LinkedBlockingQueue<TealiumResult<EngineResponse>>()

        module.fetchEngineResponse("test-engine") { results.add(it) }

        val result = results.poll(10, TimeUnit.SECONDS)
        assertNotNull("The fetch should complete", result)
        return result!!
    }

    private fun context(root: NetworkClient): TealiumContext {
        val network = NetworkUtilities(
            connectivity = mockk(relaxed = true),
            networkClient = root,
            networkHelper = root.makeNetworkHelper(),
            logger = SystemLogger
        )
        val visitorIdProvider = mockk<VisitorIdProvider>(relaxed = true)
        every { visitorIdProvider.visitorId } returns Observables.stateSubject("visitor")
        val context = mockk<TealiumContext>(relaxed = true)
        every { context.moduleManager } returns
                ModuleManagerImpl(Scheduler.SYNCHRONOUS, Observables.stateSubject(listOf(visitorIdProvider)))
        every { context.logger } returns SystemLogger
        every { context.config } returns getDefaultConfig(mockk(), "test-account", "test-profile", Environment.PROD)
        every { context.network } returns network
        return context
    }

    private fun failure(retryAfter: String): NetworkResult {
        val response = HttpResponse(request.url, 503, "", mapOf("Retry-After" to listOf(retryAfter)))
        return Failure(Non200Exception(503, response))
    }

    /**
     * A shared [NetworkClient] that records its derivations. A derived client is a real
     * [HttpClient] with the given interceptors, whose requests are redirected to [server], keeping
     * their path and query.
     */
    private class RedirectingRootClient(private val server: MockWebServer) : NetworkClient {
        val additionalInterceptors = mutableListOf<List<RequestInterceptor>>()
        val maxRetryCounts = mutableListOf<Int?>()

        override fun sendRequest(request: HttpRequest, completion: Callback<NetworkResult>): Disposable =
            throw AssertionError("Requests should be sent through the derived client")

        override fun sendRequest(request: HttpRequest.Builder, completion: Callback<NetworkResult>): Disposable =
            throw AssertionError("Requests should be sent through the derived client")

        override fun newClient(additionalInterceptors: List<RequestInterceptor>, maxRetryCount: Int?): NetworkClient {
            this.additionalInterceptors.add(additionalInterceptors)
            maxRetryCounts.add(maxRetryCount)
            // the public constructor has no retry limit; Context API sets none
            val client = HttpClient(SystemLogger, testTealiumScheduler, testNetworkScheduler, additionalInterceptors)
            return RedirectingClient(client, server)
        }
    }

    private class RedirectingClient(
        private val delegate: HttpClient,
        private val server: MockWebServer
    ) : NetworkClient {
        override fun sendRequest(request: HttpRequest, completion: Callback<NetworkResult>): Disposable =
            delegate.sendRequest(redirect(request), completion)

        override fun sendRequest(request: HttpRequest.Builder, completion: Callback<NetworkResult>): Disposable =
            sendRequest(request.build(), completion)

        override fun newClient(additionalInterceptors: List<RequestInterceptor>, maxRetryCount: Int?): NetworkClient =
            throw AssertionError("Not expected to be derived again")

        private fun redirect(request: HttpRequest): HttpRequest {
            // URL.file is the path followed by the query
            val redirected = server.url(request.url.file).toUrl()
            return HttpRequest.Builder(redirected, request.method)
                .additionalHeaders(request.headers)
                .body(request.body)
                .gzip(request.isGzip)
                .build()
        }
    }
}
