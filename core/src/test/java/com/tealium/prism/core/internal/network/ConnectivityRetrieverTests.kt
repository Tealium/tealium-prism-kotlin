package com.tealium.prism.core.internal.network

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.network.Connectivity
import com.tealium.prism.core.api.network.Connectivity.ConnectivityType
import com.tealium.prism.core.api.network.Connectivity.Status
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.internal.misc.SynchronousScheduler
import com.tealium.tests.common.SystemLogger
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
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
    private val synchronousScheduler: Scheduler = Scheduler.SYNCHRONOUS

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

        connectionStatus = Observables.stateSubject(Status.Connected(ConnectivityType.WIFI))
        connectivityRetriever = ConnectivityRetriever(mockApplication, statusSubject = connectionStatus, scheduler = synchronousScheduler, logger = SystemLogger)
    }

    @Test
    fun isConnected_Returns_True_When_ConnectionStatus_Is_Connected() {
        connectionStatus.onNext(Status.Connected(ConnectivityType.WIFI))

        assertTrue(connectivityRetriever.isConnected())
    }

    @Test
    fun isConnected_Returns_False_When_ConnectionStatus_Is_NotConnected() {
        connectionStatus.onNext(Status.NotConnected)

        assertFalse(connectivityRetriever.isConnected())
    }

    @Test
    fun isConnected_Returns_False_When_ConnectionStatus_Is_Unknown() {
        connectionStatus.onNext(Status.Unknown)

        assertFalse(connectivityRetriever.isConnected())
    }

    @Test
    fun connectionType_Returns_Wifi_When_Network_Has_Wifi_Capability() {
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        val connectionType = connectivityRetriever.connectionType()

        assertEquals(ConnectivityType.WIFI, connectionType)
    }

    @Test
    fun connectionType_Returns_Cellular_When_Network_Has_Cellular_Capability() {
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns false

        val connectionType = connectivityRetriever.connectionType()

        assertEquals(ConnectivityType.CELLULAR, connectionType)
    }

    @Test
    fun connectionType_Returns_Ethernet_When_Network_Has_Ethernet_Capability() {
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns true

        val connectionType = connectivityRetriever.connectionType()

        assertEquals(ConnectivityType.ETHERNET, connectionType)
    }

    @Test
    fun connectionType_Returns_Unknown_When_Network_Has_No_Expected_Capability() {
        every { mockCapabilities.hasTransport(any()) } returns false

        val connectionType = connectivityRetriever.connectionType()

        assertEquals(ConnectivityType.UNKNOWN, connectionType)
    }

    @Test
    fun connectionType_Prioritises_Wifi_Capability_When_Network_Has_Multiple_Capabilities() {
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns true

        val connectionType = connectivityRetriever.connectionType()

        assertEquals(ConnectivityType.WIFI, connectionType)
    }

    @Test
    fun onAvailable_Sets_Status_To_Connected_Wifi_When_Network_Has_Wifi_Capability() {
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        connectionStatus.onNext(Status.NotConnected)

        connectivityRetriever.onAvailable(mockNetwork)
        assertEquals(
            Status.Connected(ConnectivityType.WIFI),
            connectivityRetriever.connectionStatus.value
        )
    }

    @Test
    fun onAvailable_Sets_Status_To_Connected_Cellular_When_Network_Has_Cellular_Capability() {
        every { mockCapabilities.hasTransport(any()) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true
        connectionStatus.onNext(Status.NotConnected)

        connectivityRetriever.onAvailable(mockNetwork)
        assertEquals(
            Status.Connected(ConnectivityType.CELLULAR),
            connectivityRetriever.connectionStatus.value
        )
    }

    @Test
    fun onAvailable_Sets_Status_To_Connected_Ethernet_When_Network_Has_Ethernet_Capability() {
        every { mockCapabilities.hasTransport(any()) } returns false
        every { mockCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns true
        connectionStatus.onNext(Status.NotConnected)

        connectivityRetriever.onAvailable(mockNetwork)
        assertEquals(
            Status.Connected(ConnectivityType.ETHERNET),
            connectivityRetriever.connectionStatus.value
        )
    }

    @Test
    fun onAvailable_Sets_Status_To_Connected_Unknown_When_Network_Has_Unexpected_Capability() {
        every { mockCapabilities.hasTransport(any()) } returns false
        connectionStatus.onNext(Status.NotConnected)

        connectivityRetriever.onAvailable(mockNetwork)
        assertEquals(
            Status.Connected(ConnectivityType.UNKNOWN),
            connectivityRetriever.connectionStatus.value
        )
    }

    @Test
    fun onLosing_Sets_Status_To_Unknown() {
        connectionStatus.onNext(Status.NotConnected)

        connectivityRetriever.onLosing(mockNetwork, 100)
        assertEquals(
            Status.Unknown,
            connectivityRetriever.connectionStatus.value
        )
    }

    @Test
    fun onLost_Sets_Status_To_NotConnected() {
        connectionStatus.onNext(Status.Connected(ConnectivityType.WIFI))

        connectivityRetriever.onLost(mockNetwork)
        assertEquals(
            Status.NotConnected,
            connectivityRetriever.connectionStatus.value
        )
    }

    @Test
    fun onUnavailable_Sets_Status_To_NotConnected() {
        connectionStatus.onNext(Status.Connected(ConnectivityType.WIFI))

        connectivityRetriever.onUnavailable()
        assertEquals(
            Status.NotConnected,
            connectivityRetriever.connectionStatus.value
        )
    }
}
