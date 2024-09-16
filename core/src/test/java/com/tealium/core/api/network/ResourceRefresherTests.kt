package com.tealium.core.api.network

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.core.api.data.TestBundleSerializable
import com.tealium.core.api.misc.TealiumIOException
import com.tealium.core.api.misc.TealiumResult
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.minutes
import com.tealium.core.api.misc.TimeFrameUtils.seconds
import com.tealium.core.api.persistence.PersistenceException
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.network.ResourceCacheImpl
import com.tealium.core.internal.network.ResourceRefresherImpl
import com.tealium.core.internal.network.mockGetTealiumDeserializableResponse
import com.tealium.core.internal.persistence.getSharedDataStore
import com.tealium.core.internal.persistence.getTimestamp
import com.tealium.tests.common.AltSystemLogger
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class ResourceRefresherTests {

    @MockK
    private lateinit var networkHelper: NetworkHelper

    private lateinit var bundleCacher: ResourceCache<TestBundleSerializable>
    private val exampleResource = TestBundleSerializable("value", 10)
    private val localhost = URL("http://localhost/")

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        val app = ApplicationProvider.getApplicationContext<Application>()
        bundleCacher =
            ResourceCacheImpl(getSharedDataStore(app), "test", TestBundleSerializable.Deserializer)
    }

    @Test
    fun getResource_DoesNot_Emit_When_Cache_IsEmpty() {
        val observer = mockk<(TestBundleSerializable) -> Unit>(relaxed = true)

        val refresher = createRefresher()

        refresher.resource.subscribe(observer)

        verify(inverse = true) {
            observer(any())
        }
    }

    @Test
    fun getResource_Emits_What_Is_Initially_On_Disk() {
        val observer = mockk<(TestBundleSerializable) -> Unit>(relaxed = true)
        bundleCacher.saveResource(exampleResource, null)

        val refresher = createRefresher()

        refresher.resource.subscribe(observer)

        verify {
            observer(exampleResource)
        }
    }

    @Test
    fun getResource_Emits_Future_Updates() {
        val observer = mockk<(TestBundleSerializable) -> Unit>(relaxed = true)
        val onResource = Observables.publishSubject<TestBundleSerializable>()

        val refresher = createRefresher(onResourceLoaded = onResource)
        refresher.resource.subscribe(observer)

        onResource.onNext(exampleResource)

        verify {
            observer(exampleResource)
        }
    }

    @Test
    fun getErrors_DoesNot_Emit_Errors_For_304_Response() {
        val observer = mockk<(TealiumIOException) -> Unit>(relaxed = true)
        mockFailureResponse(NetworkException.Non200Exception(304))

        val refresher = createRefresher()
        refresher.errors.subscribe(observer)
        refresher.requestRefresh()

        verify(inverse = true) {
            observer(any())
        }
    }

    @Test
    fun getErrors_Emits_Errors_From_Unexpected_Exception() {
        val observer = mockk<(TealiumIOException) -> Unit>(relaxed = true)
        mockFailureResponse()

        val refresher = createRefresher()
        refresher.errors.subscribe(observer)
        refresher.requestRefresh()

        verify {
            observer(match { it is NetworkException.UnexpectedException })
        }
    }

    @Test
    fun getErrors_Emits_Errors_From_Saving_Resource() {
        val observer = mockk<(TealiumIOException) -> Unit>(relaxed = true)
        val mockCache = mockk<ResourceCache<TestBundleSerializable>>()
        every { mockCache.resource } returns null
        every {
            mockCache.saveResource(
                any(),
                any()
            )
        } throws PersistenceException("Something failed.", IOException())

        mockSuccessResponse()
        val refresher = createRefresher(cache = mockCache)

        refresher.errors.subscribe(observer)
        refresher.requestRefresh()

        verify {
            observer(match { it is PersistenceException })
        }
    }

    @Test
    fun requestRefresh_Refreshes_From_Remote() {
        val observer = mockk<(TestBundleSerializable) -> Unit>(relaxed = true)

        mockSuccessResponse()
        val refresher = createRefresher()

        refresher.resource.subscribe(observer)
        refresher.requestRefresh()

        verify {
            observer(exampleResource)
        }
    }

    @Test
    fun requestRefresh_Discards_From_Remote_When_Validator_Invalid() {
        val observer = mockk<(TestBundleSerializable) -> Unit>(relaxed = true)

        mockSuccessResponse()
        val refresher = createRefresher()

        var validatorChecked = false
        refresher.resource.subscribe(observer)
        refresher.requestRefresh {
            validatorChecked = true
            false
        }

        assertTrue(validatorChecked)
        verify(inverse = true) {
            observer(exampleResource)
        }
    }

    @Test
    fun requestRefresh_Always_Refreshes_When_Last_Refresh_Is_Null() {
        every { networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any()) } returns mockk()
        val refresher = createRefresher(lastRefresh = null)

        refresher.requestRefresh()

        verify {
            networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any())
        }
    }

    @Test
    fun requestRefresh_Does_Not_Refresh_When_Already_Refreshing() {
        every { networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any()) } returns mockk()
        val refresher = createRefresher()

        refresher.requestRefresh()
        refresher.requestRefresh()
        refresher.requestRefresh()

        verify(exactly = 1) {
            networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any())
        }
    }

    @Test
    fun requestRefresh_Does_Not_Refresh_When_In_Cooldown_And_Not_Cached() {
        every { networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any()) } returns mockk()
        val cooldownHelper = CooldownHelper.create(10.minutes, 2.minutes)!!
        val refresher =
            createRefresher(cooldownHelper = cooldownHelper, lastRefresh = getTimestamp())

        cooldownHelper.updateStatus(CooldownHelper.CooldownStatus.Failure)
        refresher.requestRefresh()

        verify(exactly = 0) {
            networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any())
        }
    }

    @Test
    fun requestRefresh_Ignores_Cooldown_When_Already_Cached() {
        bundleCacher.saveResource(exampleResource, null)
        every { networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any()) } returns mockk()
        val cooldownHelper = mockk<CooldownHelper>()
        val refresher = createRefresher(cooldownHelper = cooldownHelper, lastRefresh = 0)

        refresher.requestRefresh()

        verify(exactly = 0) {
            cooldownHelper.isInCooldown(0)
        }
    }

    @Test
    fun requestRefresh_Does_Not_Refresh_When_Refresh_Interval_Not_Elapsed() {
        every { networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any()) } returns mockk()
        val timingProvider = mockk<() -> Long>()
        every { timingProvider.invoke() } returnsMany listOf(0, 60, 120)

        val refresher = createRefresher(
            timingProvider = timingProvider,
            lastRefresh = 0,
            cooldownHelper = null,
            refreshInterval = 1.minutes,
            baseErrorInterval = null
        )

        refresher.requestRefresh() // fail
        refresher.requestRefresh() // fail
        refresher.requestRefresh() // success

        verify(exactly = 1) {
            networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any())
        }
    }

    @Test
    fun requestRefresh_Uses_Previous_Etag() {
        bundleCacher.saveResource(exampleResource, "abcd1234")
        every { networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any()) } returns mockk()
        val refresher = createRefresher(lastRefresh = null)

        refresher.requestRefresh()

        verify(exactly = 1) {
            networkHelper.getTealiumDeserializable(any<URL>(), "abcd1234", TestBundleSerializable.Deserializer, any())
        }
    }

    @Test
    fun requestRefresh_Saves_Successful_Result() {
        mockSuccessResponse(
            value = exampleResource,
            headers = mapOf(HttpRequest.Headers.ETAG to listOf("new_etag"))
        )
        val cache = mockCache()
        val refresher = createRefresher(lastRefresh = null, cache = cache)

        refresher.requestRefresh()

        verify(exactly = 1) {
            cache.saveResource(exampleResource, "new_etag")
        }
    }

    @Test
    fun cooldownHelper_Set_To_Success_On_Successful_NetworkResult() {
        mockSuccessResponse(value = exampleResource)
        val cooldownHelper = mockk<CooldownHelper>(relaxed = true)
        val refresher = createRefresher(lastRefresh = null, cooldownHelper = cooldownHelper)

        refresher.requestRefresh()

        verify(exactly = 1) {
            cooldownHelper.updateStatus(CooldownHelper.CooldownStatus.Success)
        }
    }

    @Test
    fun cooldownHelper_Set_To_Success_On_304_NetworkResult() {
        mockFailureResponse(NetworkException.Non200Exception(304))
        val cooldownHelper = mockk<CooldownHelper>(relaxed = true)
        val refresher = createRefresher(lastRefresh = null, cooldownHelper = cooldownHelper)

        refresher.requestRefresh()

        verify(exactly = 1) {
            cooldownHelper.updateStatus(CooldownHelper.CooldownStatus.Success)
        }
    }

    @Test
    fun cooldownHelper_Set_To_Failure_On_Non_Valid_NetworkResult() {
        mockSuccessResponse()
        val cooldownHelper = mockk<CooldownHelper>(relaxed = true)
        val refresher = createRefresher(lastRefresh = null, cooldownHelper = cooldownHelper)

        refresher.requestRefresh { false }

        verify(exactly = 1) {
            cooldownHelper.updateStatus(CooldownHelper.CooldownStatus.Failure)
        }
    }

    @Test
    fun cooldownHelper_Set_To_Failure_On_Failed_NetworkResult() {
        mockFailureResponse()
        val cooldownHelper = mockk<CooldownHelper>(relaxed = true)
        val refresher = createRefresher(lastRefresh = null, cooldownHelper = cooldownHelper)

        refresher.requestRefresh()

        verify(exactly = 1) {
            cooldownHelper.updateStatus(CooldownHelper.CooldownStatus.Failure)
        }
    }

    @Test
    fun setRefreshInterval_Updates_CooldownHelper() {
        val cooldownHelper = mockk<CooldownHelper>(relaxed = true)
        val refresher = createRefresher(lastRefresh = null, cooldownHelper = cooldownHelper)

        refresher.setRefreshInterval(5.minutes)

        verify {
            cooldownHelper.maxInterval = 5.minutes
        }
    }

    @Test
    fun setRefreshInterval_Updates_RefreshInterval() {
        every { networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any()) } returns mockk()
        val timingProvider = mockk<() -> Long>()
        every { timingProvider.invoke() } returns 60

        val refresher = createRefresher(
            timingProvider = timingProvider,
            lastRefresh = 0,
            cooldownHelper = null,
            refreshInterval = 1.minutes,
            baseErrorInterval = null
        )

        refresher.requestRefresh() // fail
        refresher.setRefreshInterval(59.seconds)
        refresher.requestRefresh() // success

        verify(exactly = 1) {
            networkHelper.getTealiumDeserializable(any<URL>(), any(), TestBundleSerializable.Deserializer, any())
        }
    }

    private fun mockCache(
        resource: TestBundleSerializable? = null,
        etag: String? = null
    ): ResourceCache<TestBundleSerializable> {
        val cache = mockk<ResourceCache<TestBundleSerializable>>()
        every { cache.resource } returns resource
        every { cache.etag } returns etag
        every { cache.saveResource(any(), any()) } just Runs
        return cache
    }

    private fun mockSuccessResponse(
        value: TestBundleSerializable = exampleResource,
        headers: Map<String, List<String>> = mapOf(),
        statusCode: Int = 200
    ) {
        networkHelper.mockGetTealiumDeserializableResponse(
            TealiumResult.success(
                NetworkHelper.HttpValue(
                    value,
                    HttpResponse(com.tealium.core.internal.network.localhost, statusCode, "", headers, value.asTealiumValue().toString())
                )
            ),
            TestBundleSerializable.Deserializer
        )
    }

    private fun mockFailureResponse(
        cause: NetworkException = NetworkException.UnexpectedException(null)
    ) {
        networkHelper.mockGetTealiumDeserializableResponse(
            TealiumResult.failure(cause),
            TestBundleSerializable.Deserializer
        )
    }

    private fun createRefresher(
        networkHelper: NetworkHelper = this.networkHelper,
        cache: ResourceCache<TestBundleSerializable> = this.bundleCacher,
        id: String = "test",
        url: URL = this.localhost,
        refreshInterval: TimeFrame = 10.minutes,
        baseErrorInterval: TimeFrame? = 2.minutes,
        cooldownHelper: CooldownHelper? = null,
        onResourceLoaded: Subject<TestBundleSerializable> = Observables.publishSubject(),
        onRefreshError: Subject<TealiumIOException> = Observables.publishSubject(),
        lastRefresh: Long? = null,
        timingProvider: () -> Long = ::getTimestamp
    ): ResourceRefresher<TestBundleSerializable> {
        return ResourceRefresherImpl(
            networkHelper,
            TestBundleSerializable.Deserializer,
            ResourceRefresher.Parameters(
                id, url, refreshInterval, baseErrorInterval
            ),
            cache,
            cooldownHelper = cooldownHelper ?: CooldownHelper.create(
                refreshInterval,
                baseErrorInterval
            ),
            onResourceLoaded = onResourceLoaded,
            _onRefreshError = onRefreshError,
            lastRefresh = lastRefresh,
            timingProvider = timingProvider,
            logger = AltSystemLogger
        )
    }
}