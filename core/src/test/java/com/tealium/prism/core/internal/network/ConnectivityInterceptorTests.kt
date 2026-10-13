package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.Connectivity
import com.tealium.prism.core.api.network.NetworkException.NetworkIOException
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.prism.core.api.network.RetryPolicy.RetryAfterEvent
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
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

        connectivityStatus =
            Observables.stateSubject(Connectivity.Status.Connected(Connectivity.ConnectivityType.WIFI))
        every { connectivity.connectionStatus } returns connectivityStatus

        connectivityInterceptor = ConnectivityInterceptor(connectivity)
    }

    @Test
    fun shouldRetry_Returns_RetryAfterEvent_When_NotConnected() {
        val result: NetworkResult = Failure(NetworkIOException(IOException()))
        connectivityStatus.onNext(Connectivity.Status.NotConnected)

        val retryPolicy = connectivityInterceptor.shouldRetry(mockk(), result, 0)

        Assert.assertTrue(retryPolicy is RetryAfterEvent<*>)
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Status_Is_Unknown() {
        val result: NetworkResult = Failure(NetworkIOException(IOException()))
        connectivityStatus.onNext(Connectivity.Status.Unknown)

        val retryPolicy = connectivityInterceptor.shouldRetry(mockk(), result, 0)

        Assert.assertTrue(retryPolicy is DoNotRetry)
    }

    @Test
    fun shouldRetry_Returns_RetryAfterEvent_That_Emits_On_Unknown_And_Connected_When_NotConnected() {
        val result: NetworkResult = Failure(NetworkIOException(IOException()))
        connectivityStatus.onNext(Connectivity.Status.NotConnected)
        val emissions = mutableListOf<Connectivity.Status>()

        val retryPolicy = connectivityInterceptor.shouldRetry(mockk(), result, 0)
        @Suppress("UNCHECKED_CAST")
        (retryPolicy as RetryAfterEvent<Connectivity.Status>).event.subscribe { emissions.add(it) }
        connectivityStatus.onNext(Connectivity.Status.NotConnected)
        connectivityStatus.onNext(Connectivity.Status.Unknown)
        connectivityStatus.onNext(Connectivity.Status.NotConnected)
        connectivityStatus.onNext(Connectivity.Status.Connected(Connectivity.ConnectivityType.WIFI))

        Assert.assertEquals(
            listOf(
                Connectivity.Status.Unknown,
                Connectivity.Status.Connected(Connectivity.ConnectivityType.WIFI)
            ),
            emissions
        )
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Retryable_Failure_And_Connected() {
        val result: NetworkResult = Failure(NetworkIOException(IOException()))

        val retryPolicy = connectivityInterceptor.shouldRetry(mockk(), result, 0)

        Assert.assertTrue(retryPolicy is DoNotRetry)
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Failure_Is_Not_Retryable_And_NotConnected() {
        val result: NetworkResult = Failure(Non200Exception(404))
        connectivityStatus.onNext(Connectivity.Status.NotConnected)

        val retryPolicy = connectivityInterceptor.shouldRetry(mockk(), result, 0)

        Assert.assertTrue(retryPolicy is DoNotRetry)
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Result_Is_Success() {
        val result: NetworkResult = Success(mockk())

        val retryPolicy = connectivityInterceptor.shouldRetry(mockk(), result, 0)

        Assert.assertTrue(retryPolicy is DoNotRetry)
    }
}