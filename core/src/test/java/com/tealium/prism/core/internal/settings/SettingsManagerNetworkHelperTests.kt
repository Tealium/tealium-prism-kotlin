package com.tealium.prism.core.internal.settings

import com.tealium.prism.core.api.network.RetryAfterAboveMaxDelay
import com.tealium.prism.core.internal.network.ConnectivityRetryInterceptor
import com.tealium.prism.core.internal.network.FakeConnectivity
import com.tealium.prism.core.internal.network.RecordingNetworkClient
import com.tealium.prism.core.internal.network.RetryableErrorInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SettingsManagerNetworkHelperTests {

    @Test
    fun makeNetworkHelper_Derives_Client_Once_With_Settings_Interceptors_And_Retry_Limit() {
        val client = RecordingNetworkClient()
        val connectivity = FakeConnectivity()

        SettingsManager.makeNetworkHelper(client, connectivity)

        assertEquals("The client should be derived exactly once", 1, client.additionalInterceptors.size)
        val interceptors = client.additionalInterceptors.single()
        assertEquals(2, interceptors.size)

        val retryInterceptor = interceptors.first() as RetryableErrorInterceptor
        assertEquals(3, retryInterceptor.maxAttempts)
        assertEquals(2_000L, retryInterceptor.maxDelay)
        assertSame(RetryAfterAboveMaxDelay.GIVE_UP, retryInterceptor.retryAfterAboveMaxDelay)

        val connectivityInterceptor = interceptors.last() as ConnectivityRetryInterceptor
        assertSame(connectivity, connectivityInterceptor.connectivity)

        assertEquals(listOf<Int?>(10), client.maxRetryCounts)
        assertEquals(10, SettingsManager.SETTINGS_MAX_RETRY_COUNT)
    }
}
