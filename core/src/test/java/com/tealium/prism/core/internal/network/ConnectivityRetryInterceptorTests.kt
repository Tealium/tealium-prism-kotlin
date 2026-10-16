package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.Connectivity.ConnectivityType
import com.tealium.prism.core.api.network.Connectivity.Status
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkException.NetworkIOException
import com.tealium.prism.core.api.network.NetworkException.UnexpectedException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.prism.core.api.network.RequestInterceptors
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URL

class ConnectivityRetryInterceptorTests {

    private val connectivity = FakeConnectivity(Status.Connected(ConnectivityType.WIFI))
    private val interceptor = ConnectivityRetryInterceptor(connectivity)

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Connected() {
        val policy = interceptor.shouldRetry(request, 0, connectionErrorResult)

        assertSame(DoNotRetry, policy)
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Not_Connected_But_Error_Is_Not_Retryable() {
        connectivity.status.onNext(Status.NotConnected)

        val policy = interceptor.shouldRetry(request, 0, Failure(UnexpectedException(null)))

        assertSame(DoNotRetry, policy)
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Not_Connected_But_Result_Is_Success() {
        connectivity.status.onNext(Status.NotConnected)

        val policy = interceptor.shouldRetry(request, 0, Success(HttpResponse(localhost, 200, "", emptyMap())))

        assertSame(DoNotRetry, policy)
    }

    @Test
    fun shouldRetry_Returns_AfterEvent_When_Not_Connected() {
        connectivity.status.onNext(Status.NotConnected)

        val policy = interceptor.shouldRetry(request, 0, connectionErrorResult)

        assertTrue(policy is RetryPolicy.AfterEvent)
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Status_Is_Unknown() {
        connectivity.status.onNext(Status.Unknown)

        val policy = interceptor.shouldRetry(request, 0, connectionErrorResult)

        assertSame(DoNotRetry, policy)
    }

    @Test
    fun shouldRetry_Returns_Event_That_Fires_On_Unknown_And_Connected_When_Not_Connected() {
        connectivity.status.onNext(Status.NotConnected)
        val policy = interceptor.shouldRetry(request, 0, connectionErrorResult) as RetryPolicy.AfterEvent
        val emissions = mutableListOf<Any?>()
        policy.event.subscribe { emissions.add(it) }

        connectivity.status.onNext(Status.NotConnected)
        assertEquals(emptyList<Any?>(), emissions)

        connectivity.status.onNext(Status.Unknown)
        connectivity.status.onNext(Status.NotConnected)
        connectivity.status.onNext(Status.Connected(ConnectivityType.CELLULAR))
        assertEquals(listOf(Status.Unknown, Status.Connected(ConnectivityType.CELLULAR)), emissions)
    }

    @Test
    fun retryWhenConnected_Returns_ConnectivityRetryInterceptor_With_Given_Connectivity() {
        val interceptor = RequestInterceptors.retryWhenConnected(connectivity) as ConnectivityRetryInterceptor

        assertSame(connectivity, interceptor.connectivity)
    }

    private companion object {
        val localhost = URL("https://localhost/")
        val request: HttpRequest = HttpRequest.get("https://localhost/").build()
        val connectionErrorResult: NetworkResult = Failure(NetworkIOException(IOException()))
    }
}
