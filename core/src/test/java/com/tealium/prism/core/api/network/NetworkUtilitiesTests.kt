package com.tealium.prism.core.api.network

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tealium.prism.core.api.data.TestDataObjectConvertible
import com.tealium.prism.core.api.misc.TimeFrameUtils.minutes
import com.tealium.prism.core.internal.network.FakeConnectivity
import com.tealium.prism.core.internal.network.mockGetDataItemConvertibleSuccess
import com.tealium.prism.core.internal.persistence.stores.getSharedDataStore
import com.tealium.tests.common.SystemLogger
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL

@RunWith(RobolectricTestRunner::class)
class NetworkUtilitiesTests {

    private val sharedHelper: NetworkHelper = mockk()
    private val networkUtilities = NetworkUtilities(
        connectivity = FakeConnectivity(),
        networkClient = mockk(),
        networkHelper = sharedHelper,
        logger = SystemLogger
    )
    private val parameters = ResourceRefresher.Parameters("resource", URL("https://localhost/resource"), 10.minutes)
    private val resource = TestDataObjectConvertible("value", 1)

    @Test
    fun resourceRefresher_Fetches_Through_Given_NetworkHelper() {
        val retryingHelper: NetworkHelper = mockk()
        retryingHelper.mockGetDataItemConvertibleSuccess(resource, TestDataObjectConvertible.Converter)
        val loaded = mutableListOf<TestDataObjectConvertible>()

        val refresher = networkUtilities.resourceRefresher(
            dataStore(), TestDataObjectConvertible.Converter, parameters, retryingHelper
        )
        refresher.resource.subscribe { loaded.add(it) }
        refresher.requestRefresh()

        assertEquals(listOf(resource), loaded)
        verify(exactly = 1) {
            retryingHelper.getDataItemConvertible(parameters.url, any(), any(), TestDataObjectConvertible.Converter, any())
        }
        verify(exactly = 0) {
            sharedHelper.getDataItemConvertible(any<URL>(), any(), any(), TestDataObjectConvertible.Converter, any())
        }
    }

    @Test
    fun resourceRefresher_Fetches_Through_Shared_NetworkHelper_By_Default() {
        sharedHelper.mockGetDataItemConvertibleSuccess(resource, TestDataObjectConvertible.Converter)
        val loaded = mutableListOf<TestDataObjectConvertible>()

        val refresher = networkUtilities.resourceRefresher(dataStore(), TestDataObjectConvertible.Converter, parameters)
        refresher.resource.subscribe { loaded.add(it) }
        refresher.requestRefresh()

        assertEquals(listOf(resource), loaded)
        verify(exactly = 1) {
            sharedHelper.getDataItemConvertible(parameters.url, any(), any(), TestDataObjectConvertible.Converter, any())
        }
    }

    private fun dataStore() = getSharedDataStore(ApplicationProvider.getApplicationContext<Application>())
}
