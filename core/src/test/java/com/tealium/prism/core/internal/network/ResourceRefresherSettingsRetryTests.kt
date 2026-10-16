package com.tealium.prism.core.internal.network

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.data.TestDataObjectConvertible
import com.tealium.prism.core.api.misc.TealiumIOException
import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.api.misc.TimeFrameUtils.minutes
import com.tealium.prism.core.api.misc.TimeFrameUtils.seconds
import com.tealium.prism.core.api.network.CooldownHelper
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.ResourceRefresher
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.prism.core.internal.persistence.stores.getSharedDataStore
import com.tealium.prism.core.internal.settings.SettingsManager
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.awaitQueuedTasks
import com.tealium.tests.common.testNetworkScheduler
import com.tealium.tests.common.testTealiumScheduler
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Sends settings refreshes through the production settings client
 * ([SettingsManager.makeNetworkHelper]) derived from a real [HttpClient], against a
 * [MockWebServer].
 *
 * The client calls [onAttemptResultObserved] on the Tealium scheduler right before it makes the retry
 * decision, in the same task.
 *
 * A failure "while offline" is a `503` received while [connectivity] is not connected: it is
 * retryable, so the [ConnectivityRetryInterceptor] retries it
 * once connectivity returns.
 */
@RunWith(RobolectricTestRunner::class)
class ResourceRefresherSettingsRetryTests {

    private lateinit var server: MockWebServer
    private val connectivity = FakeConnectivity()

    /** Same cooldown the [SettingsManager] builds for the settings refresher. */
    private val cooldown = CooldownHelper.create(15.minutes, 20.seconds)!!

    @Volatile
    private var onAttemptResultObserved: ((NetworkResult) -> Unit)? = null

    private val errors = LinkedBlockingQueue<TealiumIOException>()
    private val resources = LinkedBlockingQueue<TestDataObjectConvertible>()

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
    fun refresh_Completes_After_Three_Requests_And_Enters_Error_Cooldown_When_Every_Attempt_Fails() {
        enqueue(*Array(5) { status(503) })
        val refresher = createSettingsRefresher()

        refresher.requestRefresh()

        assertNotNull(errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        testTealiumScheduler.awaitQueuedTasks(TIMEOUT_SECONDS)
        assertEquals(3, server.requestCount)
        assertFalse("A new refresh should not start during the error cooldown", refresher.shouldRefresh)
        cooldown.updateStatus(CooldownHelper.CooldownStatus.Success)
        assertTrue("Without the cooldown a new refresh can start", refresher.shouldRefresh)
    }

    @Test
    fun refresh_Retries_At_Most_Ten_Times_While_Offline() {
        // 11 failures while offline, then a success that only a 12th request would receive
        enqueue(*Array(11) { status(503) }, resourceResponse())
        val refresher = createSettingsRefresher()
        // Every request fails while offline, so the ConnectivityRetryInterceptor proposes each
        // retry and waits for connectivity, which is restored once it started waiting. Only the
        // SETTINGS_MAX_RETRY_COUNT stops the retries.
        onAttemptResultObserved = {
            connectivity.disconnect()
            // runs after the current task, in which the connectivity interceptor starts waiting;
            // `execute` would run it inline, as this is called on the Tealium scheduler
            testTealiumScheduler.schedule(0.milliseconds) { connectivity.connect() }
        }

        refresher.requestRefresh()

        assertNotNull(errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(1 + SettingsManager.SETTINGS_MAX_RETRY_COUNT, server.requestCount)
    }

    @Test
    fun refresh_Fails_After_One_Request_When_Throttled_With_RetryAfter_Above_Max_Delay() {
        enqueue(*Array(3) { status(429).setHeader("Retry-After", "30") })
        val refresher = createSettingsRefresher()

        refresher.requestRefresh()

        assertNotNull(errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun refresh_Waits_For_Connectivity_And_Then_Retries_Once_When_Failing_While_Offline() {
        enqueue(status(503), resourceResponse())
        val refresher = createSettingsRefresher()
        val retryDecided = expectFirstRetryDecision()
        connectivity.disconnect()

        refresher.requestRefresh()

        awaitRetryWaitingForConnectivity(retryDecided)
        assertNotNull(server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        // without the connectivity wait, the settings backoff would retry within 100ms
        assertNull(
            "No request should be sent while offline",
            server.takeRequest(BEYOND_FIRST_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
        )
        connectivity.connect()
        assertEquals(RESOURCE, resources.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(2, server.requestCount)
    }

    /**
     * Offline first attempt, then 3 attempts in a row while online: the RetryableErrorInterceptor
     * restarts its budget once the ConnectivityRetryInterceptor proposed the retry that waited for
     * connectivity.
     */
    @Test
    fun refresh_Completes_After_Four_Requests_When_Failing_Offline_Then_On_Every_Online_Attempt() {
        enqueue(*Array(6) { status(503) })
        val refresher = createSettingsRefresher()
        val retryDecided = expectFirstRetryDecision()
        connectivity.disconnect()

        refresher.requestRefresh()

        awaitRetryWaitingForConnectivity(retryDecided)
        assertEquals(1, server.requestCount)
        connectivity.connect()
        assertNotNull(errors.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertEquals(4, server.requestCount)
    }

    private fun createSettingsRefresher(): ResourceRefresher<TestDataObjectConvertible> {
        val observer = object : RequestInterceptor {
            override fun onAttemptResult(request: HttpRequest, retryCount: Int, result: NetworkResult) {
                onAttemptResultObserved?.invoke(result)
            }

            override fun shouldRetry(
                request: HttpRequest,
                consecutiveRetryCount: Int,
                result: NetworkResult
            ): RetryPolicy = DoNotRetry
        }
        val client = HttpClient(SystemLogger, testTealiumScheduler, testNetworkScheduler, listOf(observer))
        val networkHelper = SettingsManager.makeNetworkHelper(client, connectivity)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val parameters = ResourceRefresher.Parameters(
            SettingsManager.KEY_SETTINGS,
            server.url("/settings.json").toUrl(),
            15.minutes,
            20.seconds
        )
        val refresher = ResourceRefresherImpl(
            networkHelper,
            TestDataObjectConvertible.Converter,
            parameters,
            ResourceCacheImpl(getSharedDataStore(app), "settings_retry", TestDataObjectConvertible.Converter),
            cooldownHelper = cooldown,
            logger = SystemLogger
        )
        refresher.errors.subscribe { errors.add(it) }
        refresher.resource.subscribe { resources.add(it) }
        return refresher
    }

    /**
     * Returns a latch released once the first request completed and its retry decision is about
     * to be made, in the same Tealium scheduler task.
     */
    private fun expectFirstRetryDecision(): CountDownLatch {
        val latch = CountDownLatch(1)
        onAttemptResultObserved = {
            onAttemptResultObserved = null
            latch.countDown()
        }
        return latch
    }

    /**
     * Waits for the first retry decision, and for the task that made it to end: the retry is then
     * waiting for connectivity.
     */
    private fun awaitRetryWaitingForConnectivity(retryDecided: CountDownLatch) {
        assertTrue(retryDecided.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        testTealiumScheduler.awaitQueuedTasks(TIMEOUT_SECONDS)
    }

    private fun enqueue(vararg responses: MockResponse) = responses.forEach(server::enqueue)

    private fun status(code: Int) = MockResponse().setResponseCode(code)

    private fun resourceResponse() = MockResponse()
        .setResponseCode(200)
        .setBody(RESOURCE.asDataItem().toString())

    private companion object {
        const val TIMEOUT_SECONDS = 10L

        /** Longer than the first settings backoff, up to 100ms of jitter. */
        const val BEYOND_FIRST_BACKOFF_MILLIS = 300L
        val RESOURCE = TestDataObjectConvertible("abc", 123)
    }
}
