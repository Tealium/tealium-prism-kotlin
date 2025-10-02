package com.tealium.prism.core.api.network

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.data.TestDataObjectConvertible
import com.tealium.prism.core.api.misc.TealiumIOException
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.minutes
import com.tealium.prism.core.api.misc.TimeFrameUtils.seconds
import com.tealium.prism.core.api.persistence.PersistenceException
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.internal.network.ResourceCacheImpl
import com.tealium.prism.core.internal.network.ResourceRefresherImpl
import com.tealium.prism.core.internal.network.mockGetDataItemConvertibleResponse
import com.tealium.prism.core.internal.persistence.stores.getSharedDataStore
import com.tealium.prism.core.internal.persistence.database.getTimestamp
import com.tealium.tests.common.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
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

    private lateinit var dataObjectCacher: ResourceCache<TestDataObjectConvertible>
    private val exampleResource = TestDataObjectConvertible("value", 10)
    private val localhost = URL("http://localhost/")

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        val app = ApplicationProvider.getApplicationContext<Application>()
        dataObjectCacher =
            ResourceCacheImpl(getSharedDataStore(app), "test", TestDataObjectConvertible.Converter)
    }

    @Test
    fun getResource_DoesNot_Emit_When_Cache_IsEmpty() {
        val observer = mockk<(TestDataObjectConvertible) -> Unit>(relaxed = true)

        val refresher = createRefresher()

        refresher.resource.subscribe(observer)

        verify(inverse = true) {
            observer(any())
        }
    }

    @Test
    fun getResource_Emits_What_Is_Initially_On_Disk() {
        val observer = mockk<(TestDataObjectConvertible) -> Unit>(relaxed = true)
        dataObjectCacher.saveResource(exampleResource, null)

        val refresher = createRefresher()

        refresher.resource.subscribe(observer)

        verify {
            observer(exampleResource)
        }
    }

    @Test
    fun getResource_Emits_Future_Updates() {
        val observer = mockk<(TestDataObjectConvertible) -> Unit>(relaxed = true)
        val onResource = Observables.publishSubject<TestDataObjectConvertible>()

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
        val mockCache = mockk<ResourceCache<TestDataObjectConvertible>>()
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
        val observer = mockk<(TestDataObjectConvertible) -> Unit>(relaxed = true)

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
        val observer = mockk<(TestDataObjectConvertible) -> Unit>(relaxed = true)

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
        every {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        } returns mockk()
        val refresher = createRefresher(lastRefresh = null)

        refresher.requestRefresh()

        verify {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        }
    }

    @Test
    fun requestRefresh_Does_Not_Refresh_When_Already_Refreshing() {
        every {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        } returns mockk()
        val refresher = createRefresher()

        refresher.requestRefresh()
        refresher.requestRefresh()
        refresher.requestRefresh()

        verify(exactly = 1) {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        }
    }

    @Test
    fun requestRefresh_Does_Not_Refresh_When_In_Cooldown_And_Not_Cached() {
        every {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        } returns mockk()
        val cooldownHelper = CooldownHelper.create(10.minutes, 2.minutes)!!
        val refresher =
            createRefresher(cooldownHelper = cooldownHelper, lastRefresh = getTimestamp())

        cooldownHelper.updateStatus(CooldownHelper.CooldownStatus.Failure)
        refresher.requestRefresh()

        verify(exactly = 0) {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        }
    }

    @Test
    fun requestRefresh_Ignores_Cooldown_When_Already_Cached() {
        dataObjectCacher.saveResource(exampleResource, null)
        every {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        } returns mockk()
        val cooldownHelper = mockk<CooldownHelper>()
        val refresher = createRefresher(cooldownHelper = cooldownHelper, lastRefresh = 0)

        refresher.requestRefresh()

        verify(exactly = 0) {
            cooldownHelper.isInCooldown(0)
        }
    }

    @Test
    fun requestRefresh_Does_Not_Refresh_When_Refresh_Interval_Not_Elapsed() {
        every {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        } returns mockk()
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
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        }
    }

    @Test
    fun requestRefresh_Uses_Previous_Etag() {
        dataObjectCacher.saveResource(exampleResource, "abcd1234")
        every {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        } returns mockk()
        val refresher = createRefresher(lastRefresh = null)

        refresher.requestRefresh()

        verify(exactly = 1) {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                "abcd1234",
                TestDataObjectConvertible.Converter,
                any()
            )
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
        every {
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        } returns mockk()
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
            networkHelper.getDataItemConvertible(
                any<URL>(),
                any(),
                TestDataObjectConvertible.Converter,
                any()
            )
        }
    }

    private fun mockCache(
        resource: TestDataObjectConvertible? = null,
        etag: String? = null
    ): ResourceCache<TestDataObjectConvertible> {
        val cache = mockk<ResourceCache<TestDataObjectConvertible>>()
        every { cache.resource } returns resource
        every { cache.etag } returns etag
        every { cache.saveResource(any(), any()) } just Runs
        return cache
    }

    private fun mockSuccessResponse(
        value: TestDataObjectConvertible = exampleResource,
        headers: Map<String, List<String>> = mapOf(),
        statusCode: Int = 200
    ) {
        networkHelper.mockGetDataItemConvertibleResponse(
            TealiumResult.success(
                NetworkHelper.HttpValue(
                    value,
                    HttpResponse(
                        com.tealium.prism.core.internal.network.localhost,
                        statusCode,
                        "",
                        headers,
                        value.asDataItem().toString().toByteArray(Charsets.UTF_8)
                    )
                )
            ),
            TestDataObjectConvertible.Converter
        )
    }

    private fun mockFailureResponse(
        cause: NetworkException = NetworkException.UnexpectedException(null)
    ) {
        networkHelper.mockGetDataItemConvertibleResponse(
            TealiumResult.failure(cause),
            TestDataObjectConvertible.Converter
        )
    }

    private fun createRefresher(
        networkHelper: NetworkHelper = this.networkHelper,
        cache: ResourceCache<TestDataObjectConvertible> = this.dataObjectCacher,
        id: String = "test",
        url: URL = this.localhost,
        refreshInterval: TimeFrame = 10.minutes,
        baseErrorInterval: TimeFrame? = 2.minutes,
        cooldownHelper: CooldownHelper? = null,
        onResourceLoaded: Subject<TestDataObjectConvertible> = Observables.publishSubject(),
        onRefreshError: Subject<TealiumIOException> = Observables.publishSubject(),
        lastRefresh: Long? = null,
        timingProvider: () -> Long = ::getTimestamp
    ): ResourceRefresher<TestDataObjectConvertible> {
        return ResourceRefresherImpl(
            networkHelper,
            TestDataObjectConvertible.Converter,
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
            logger = SystemLogger
        )
    }
}