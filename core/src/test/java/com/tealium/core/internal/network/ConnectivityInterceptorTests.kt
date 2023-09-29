package com.tealium.core.internal.network

import com.tealium.core.api.network.AfterEvent
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.DoNotDelay
import com.tealium.core.api.network.DoNotRetry
import com.tealium.core.api.network.Failure
import com.tealium.core.api.network.IOError
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.RetryAfterEvent
import com.tealium.core.api.network.Success
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ConnectivityInterceptorTests {

    @MockK
    private lateinit var connectivity: Connectivity

    private lateinit var connectivityStatus: MutableStateFlow<Connectivity.Status>
    private lateinit var connectivityInterceptor: ConnectivityInterceptor

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        connectivityStatus = MutableStateFlow(Connectivity.Status.Connected)
        every { connectivity.onConnectionStatusUpdated } returns connectivityStatus

        connectivityInterceptor = ConnectivityInterceptor(connectivity)
    }

    @Test
    fun connectivityInterceptor_InstanceIsTheSame() {
        mockkObject(ConnectivityRetriever.Companion)
        every { ConnectivityRetriever.getInstance(any()) } returns mockk()

        val interceptor1 = ConnectivityInterceptor.getInstance(mockk())
        val interceptor2 = ConnectivityInterceptor.getInstance(mockk())

        assertSame(interceptor1, interceptor2)
    }

    @Test
    fun shouldRetryReturnsRetryAfterEventForNoConnection() {
        val result: NetworkResult = Failure(IOError(mockk()))
        every { connectivity.isConnected() } returns false
        connectivityStatus.value = Connectivity.Status.NotConnected

        val retryPolicy = connectivityInterceptor.shouldRetry(mockk(), result, 0)

        Assert.assertTrue(retryPolicy is RetryAfterEvent<*>)
    }

    @Test
    fun shouldRetryReturnsDoNotRetryForSuccessAndAvailableConnection() {
        val result: NetworkResult = Success(mockk())
        every { connectivity.isConnected() } returns true

        val retryPolicy = connectivityInterceptor.shouldRetry(mockk(), result, 0)

        Assert.assertTrue(retryPolicy is DoNotRetry)
    }

    @Test
    fun shouldDelayReturnsAfterDelayForNoConnection() {
        every { connectivity.isConnected() } returns false
        connectivityStatus.value = Connectivity.Status.NotConnected

        val delayPolicy = connectivityInterceptor.shouldDelay(mockk())

        Assert.assertTrue(delayPolicy is AfterEvent<*>)
    }

    @Test
    fun shouldDelayReturnsDoNotDelayForAvailableConnection() {
        every { connectivity.isConnected() } returns true

        val delayPolicy = connectivityInterceptor.shouldDelay(mockk())

        Assert.assertTrue(delayPolicy is DoNotDelay)
    }

}