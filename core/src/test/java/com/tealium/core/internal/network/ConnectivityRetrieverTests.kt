package com.tealium.core.internal.network

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.Connectivity.ConnectivityType
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import com.tealium.tests.common.SynchronousScheduler
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 33])
class ConnectivityRetrieverTests {

    @MockK
    lateinit var mockApplication: Application

    @MockK
    lateinit var mockConnectivityManager: ConnectivityManager

    @MockK
    lateinit var mockNetwork: Network

    @MockK
    lateinit var mockCapabilities: NetworkCapabilities

    private lateinit var connectionStatus: StateSubject<Connectivity.Status>
    private lateinit var connectivityRetriever: ConnectivityRetriever
    private val synchronousScheduler: Scheduler = SynchronousScheduler()

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { mockApplication.getSystemService(any()) } returns mockConnectivityManager
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(any()) } returns mockCapabilities

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            every {
                mockConnectivityManager.registerDefaultNetworkCallback(
                    any<ConnectivityRetriever>()
                )
            } just Runs
        } else {
            every {
                mockConnectivityManager.registerNetworkCallback(
                    any(),
                    any<ConnectivityRetriever>()
                )
            } just Runs
        }

        connectionStatus = Observables.stateSubject(Connectivity.Status.Connected)
        connectivityRetriever = ConnectivityRetriever(mockApplication, statusSubject = connectionStatus, scheduler = synchronousScheduler)
    }

    @Test
    fun connectivity_IsConnected() {
        connectionStatus.onNext(Connectivity.Status.Connected)

        assertTrue(connectivityRetriever.isConnected())
    }

    @Test
    fun connectivity_IsNotConnected() {
        connectionStatus.onNext(Connectivity.Status.NotConnected)

        assertFalse(connectivityRetriever.isConnected())
    }

    @Test
    fun validConnectionType() {
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true

        val connectionType = connectivityRetriever.connectionType()

        assertEquals(ConnectivityType.WIFI, connectionType)
    }

    @Test
    fun onAvailableSetsStatusToAvailable() {
        val network: Network = mockk()
        connectionStatus.onNext(Connectivity.Status.NotConnected)

        connectivityRetriever.onAvailable(network)
        assertEquals(
            Connectivity.Status.Connected,
            connectivityRetriever.onConnectionStatusUpdated.value
        )
        assertTrue(connectivityRetriever.isConnected())
    }

    @Test
    fun onLosingSetsStatusToUnknown() {
        val network: Network = mockk()
        connectionStatus.onNext(Connectivity.Status.NotConnected)

        connectivityRetriever.onLosing(network, 100)
        assertEquals(
            Connectivity.Status.Unknown,
            connectivityRetriever.onConnectionStatusUpdated.value
        )
        assertFalse(connectivityRetriever.isConnected())
    }

    @Test
    fun onLostSetsStatusToUnavailable() {
        val network: Network = mockk()
        connectionStatus.onNext(Connectivity.Status.Connected)

        connectivityRetriever.onLost(network)
        assertEquals(
            Connectivity.Status.NotConnected,
            connectivityRetriever.onConnectionStatusUpdated.value
        )
        assertFalse(connectivityRetriever.isConnected())
    }

    @Test
    fun onUnavailableSetsStatusToUnavailable() {
        connectionStatus.onNext(Connectivity.Status.Connected)

        connectivityRetriever.onUnavailable()
        assertEquals(
            Connectivity.Status.NotConnected,
            connectivityRetriever.onConnectionStatusUpdated.value
        )
        assertFalse(connectivityRetriever.isConnected())
    }
}
