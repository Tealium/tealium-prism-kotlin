package com.tealium.core.internal.network

import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.NetworkException.NetworkIOException
import com.tealium.core.api.network.NetworkResult
import com.tealium.core.api.network.NetworkResult.Failure
import com.tealium.core.api.network.NetworkResult.Success
import com.tealium.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.core.api.network.RetryPolicy.RetryAfterEvent
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ConnectivityInterceptorTests {

    @MockK
    private lateinit var connectivity: Connectivity

    private lateinit var connectivityStatus: StateSubject<Connectivity.Status>
    private lateinit var connectivityInterceptor: ConnectivityInterceptor

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        connectivityStatus = Observables.stateSubject(Connectivity.Status.Connected)
        every { connectivity.onConnectionStatusUpdated } returns connectivityStatus

        connectivityInterceptor = ConnectivityInterceptor(connectivity)
    }

    @Test
    fun shouldRetryReturnsRetryAfterEventForNoConnection() {
        val result: NetworkResult = Failure(NetworkIOException(IOException()))
        every { connectivity.isConnected() } returns false
        connectivityStatus.onNext(Connectivity.Status.NotConnected)

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
}