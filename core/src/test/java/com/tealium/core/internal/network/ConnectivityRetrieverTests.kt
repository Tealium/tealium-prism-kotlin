package com.tealium.core.internal.network

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.Connectivity.ConnectivityType
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert
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

    private lateinit var connectionStatus: MutableStateFlow<Connectivity.Status>
    private lateinit var connectivityRetriever: ConnectivityRetriever

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

        connectionStatus = MutableStateFlow(Connectivity.Status.Connected)
        connectivityRetriever = ConnectivityRetriever(mockApplication, statusFlow = connectionStatus)
    }

    @Test
    fun connectivityReceiver_InstanceIsTheSame() {
        val connectivity1 = ConnectivityRetriever.getInstance(mockApplication)
        val connectivity2 = ConnectivityRetriever.getInstance(mockApplication)

        Assert.assertSame(connectivity1, connectivity2)
    }

    @Test
    fun connectivity_IsConnected() {
        connectionStatus.value = Connectivity.Status.Connected

        assertTrue(connectivityRetriever.isConnected())
    }

    @Test
    fun connectivity_IsNotConnected() {
        connectionStatus.value = Connectivity.Status.NotConnected

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
        connectionStatus.value = Connectivity.Status.NotConnected

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
        connectionStatus.value = Connectivity.Status.NotConnected

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
        connectionStatus.value = Connectivity.Status.Connected

        connectivityRetriever.onLost(network)
        assertEquals(
            Connectivity.Status.NotConnected,
            connectivityRetriever.onConnectionStatusUpdated.value
        )
        assertFalse(connectivityRetriever.isConnected())
    }

    @Test
    fun onUnavailableSetsStatusToUnavailable() {
        connectionStatus.value = Connectivity.Status.Connected

        connectivityRetriever.onUnavailable()
        assertEquals(
            Connectivity.Status.NotConnected,
            connectivityRetriever.onConnectionStatusUpdated.value
        )
        assertFalse(connectivityRetriever.isConnected())
    }
}
