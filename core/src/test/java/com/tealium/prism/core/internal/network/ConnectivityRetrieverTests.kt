package com.tealium.prism.core.internal.network

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
import android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET
import android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.os.Build
import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.network.Connectivity.ConnectivityType
import com.tealium.prism.core.api.network.Connectivity.Status
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.tests.common.ManualScheduler
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConnectivityRetrieverTests {

    private val application: Application = mockk()
    private val connectivityManager: ConnectivityManager = mockk()
    private val logger: Logger = mockk(relaxed = true)

    private lateinit var statusSubject: StateSubject<Status>
    private lateinit var retriever: ConnectivityRetriever

    /** Status emissions and registration calls, in the order they happened. */
    private val events = mutableListOf<Any>()

    private object Registered

    private val status: Status
        get() = retriever.connectionStatus.value

    private val wifi = mockk<Network>()
    private val cellular = mockk<Network>()
    private val vpn = mockk<Network>()

    @Before
    fun setup() {
        every { application.getSystemService(any()) } returns connectivityManager
        every { connectivityManager.activeNetwork } returns null
        every { connectivityManager.getNetworkCapabilities(any()) } returns null
        // each registration method only exists on some API levels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            every { connectivityManager.registerDefaultNetworkCallback(any<ConnectivityRetriever>()) } answers {
                events.add(Registered)
            }
        } else {
            every {
                connectivityManager.registerNetworkCallback(any(), any<ConnectivityRetriever>())
            } answers {
                events.add(Registered)
            }
        }

        statusSubject = Observables.stateSubject(Status.Unknown)
        statusSubject.subscribe { events.add(it) }
        retriever = ConnectivityRetriever(
            application,
            Scheduler.SYNCHRONOUS,
            logger,
            statusSubject
        )
    }

    // region isConnected

    @Test
    fun isConnected_Is_True_Only_When_Status_Is_Connected() {
        statusSubject.onNext(Status.Connected(ConnectivityType.WIFI))
        assertTrue(retriever.isConnected())

        statusSubject.onNext(Status.NotConnected)
        assertFalse(retriever.isConnected())

        statusSubject.onNext(Status.Unknown)
        assertFalse(retriever.isConnected())
    }

    // endregion

    // region subscribe

    @Config(sdk = [24, 33])
    @Test
    fun subscribe_Registers_Default_Network_Callback_From_Api_24() {
        retriever.subscribe()

        verify(exactly = 1) {
            connectivityManager.registerDefaultNetworkCallback(retriever)
        }
        verify(exactly = 0) {
            connectivityManager.registerNetworkCallback(any(), any<ConnectivityRetriever>())
        }
    }

    @Config(sdk = [23])
    @Test
    fun subscribe_Registers_Internet_Listen_Without_Validated_Or_Not_Vpn_On_Api_23() {
        val request = slot<NetworkRequest>()
        every {
            connectivityManager.registerNetworkCallback(capture(request), any<ConnectivityRetriever>())
        } answers { events.add(Registered) }

        retriever.subscribe()

        verify(exactly = 1) {
            connectivityManager.registerNetworkCallback(any(), any<ConnectivityRetriever>())
        }
        val capabilities = request.captured.capabilities()
        assertTrue(capabilities.hasCapability(NET_CAPABILITY_INTERNET))
        assertFalse(capabilities.hasCapability(NET_CAPABILITY_VALIDATED))
        assertFalse(capabilities.hasCapability(NET_CAPABILITY_NOT_VPN))
    }

    @Test
    fun subscribe_Registers_Before_Emitting_Initial_State() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))

        retriever.subscribe()

        assertEquals(
            listOf(Status.Unknown, Registered, Status.Connected(ConnectivityType.WIFI)),
            events
        )
    }

    @Test
    fun subscribe_Emits_NotConnected_When_There_Is_No_Active_Network() {
        retriever.subscribe()

        assertEquals(Status.NotConnected, status)
        assertEquals(listOf(Status.Unknown, Registered, Status.NotConnected), events)
    }

    @Test
    fun subscribe_Emits_NotConnected_When_Capabilities_Are_Null() {
        every { connectivityManager.activeNetwork } returns wifi

        retriever.subscribe()

        assertEquals(Status.NotConnected, status)
    }

    @Test
    fun subscribe_Emits_Unknown_And_Does_Not_Read_Initial_State_When_Registration_Throws() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        every {
            connectivityManager.registerDefaultNetworkCallback(any<ConnectivityRetriever>())
        } throws SecurityException("denied")

        retriever.subscribe()

        assertEquals(Status.Unknown, status)
        assertEquals(ConnectivityType.UNKNOWN, retriever.connectionType())
        verify(exactly = 1) { logger.error(any(), any<String>(), any()) }
        verify(exactly = 0) { connectivityManager.activeNetwork }
    }

    @Config(sdk = [23])
    @Test
    fun subscribe_Emits_Unknown_And_Does_Not_Read_Initial_State_When_Listen_Registration_Throws_On_Api_23() {
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN, internet = false, validated = false))
        mockActiveNetworkInfo(ConnectivityType.WIFI, connected = true)
        every {
            connectivityManager.registerNetworkCallback(any(), any<ConnectivityRetriever>())
        } throws SecurityException("denied")

        retriever.subscribe()

        assertEquals(Status.Unknown, status)
        assertEquals(ConnectivityType.UNKNOWN, retriever.connectionType())
        verify(exactly = 1) { logger.error(any(), any<String>(), any()) }
        verify(exactly = 0) { connectivityManager.activeNetwork }
        verifyNoActiveNetworkInfoRead()
    }

    @Test
    fun subscribe_Emits_NotConnected_When_Network_Is_Lost_While_Registering() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        every {
            connectivityManager.registerDefaultNetworkCallback(any<ConnectivityRetriever>())
        } answers {
            // gone before the callback saw it, so no onLost will be delivered
            every { connectivityManager.activeNetwork } returns null
            events.add(Registered)
        }

        retriever.subscribe()

        assertEquals(Status.NotConnected, status)
        assertEquals(listOf(Status.Unknown, Registered, Status.NotConnected), events)
    }

    @Test
    fun subscribe_Applies_Initial_State_Before_Queued_Callbacks() {
        val scheduler = ManualScheduler()
        retriever = ConnectivityRetriever(application, scheduler, logger, statusSubject)
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        // the network is lost after registering; the callback is queued behind subscribe
        every {
            connectivityManager.registerDefaultNetworkCallback(any<ConnectivityRetriever>())
        } answers {
            retriever.onAvailable(wifi)
            retriever.onLost(wifi)
        }

        retriever.subscribe()
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        scheduler.runAll()

        assertEquals(Status.NotConnected, status)
    }

    @Test
    fun subscribe_Leaves_Status_Unknown_And_Still_Registers_When_Reading_Initial_State_Throws() {
        every { connectivityManager.activeNetwork } throws SecurityException("no permission")

        retriever.subscribe()

        assertEquals(Status.Unknown, status)
        verify(exactly = 1) { logger.error(any(), any<String>(), any()) }
        verify(exactly = 1) { connectivityManager.registerDefaultNetworkCallback(retriever) }
    }

    @Test
    fun subscribe_Does_Not_Register_Twice_When_Called_Twice() {
        retriever.subscribe()
        retriever.subscribe()

        verify(exactly = 1) { connectivityManager.registerDefaultNetworkCallback(retriever) }
    }

    @Test
    fun subscribe_Resets_State_When_Registration_Throws_After_Unsubscribe() {
        mockUnregister()
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.subscribe()
        retriever.unsubscribe()
        every {
            connectivityManager.registerDefaultNetworkCallback(any<ConnectivityRetriever>())
        } throws SecurityException("denied")

        retriever.subscribe()

        assertEquals(Status.Unknown, status)
        assertEquals(ConnectivityType.UNKNOWN, retriever.connectionType())
    }

    @Config(sdk = [23, 27])
    @Test
    fun subscribe_Resets_Connection_Type_When_Initial_State_Has_No_Network_After_Split_Tunnel_Vpn_Before_Api_28() {
        mockUnregister()
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN, internet = false, validated = false))
        mockActiveNetworkInfo(ConnectivityType.WIFI, connected = true)
        retriever.subscribe()
        assertEquals(ConnectivityType.WIFI, retriever.connectionType())
        retriever.unsubscribe()
        every { connectivityManager.activeNetwork } returns null

        retriever.subscribe()

        assertEquals(Status.NotConnected, status)
        assertEquals(ConnectivityType.UNKNOWN, retriever.connectionType())
    }

    @Config(sdk = [23, 27])
    @Test
    fun subscribe_Stops_Tracking_Previous_Network_When_Initial_State_Is_Split_Tunnel_Vpn_Before_Api_28() {
        mockUnregister()
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.subscribe()
        retriever.unsubscribe()
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN, internet = false, validated = false))
        mockActiveNetworkInfo(ConnectivityType.CELLULAR, connected = true)
        retriever.subscribe()
        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)

        // the old Wi-Fi is no longer tracked, so its loss does not concern the status
        retriever.onLost(wifi)

        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)
        assertEquals(ConnectivityType.CELLULAR, retriever.connectionType())
    }

    @Test
    fun unsubscribe_Does_Not_Unregister_When_Registration_Failed() {
        every {
            connectivityManager.registerDefaultNetworkCallback(any<ConnectivityRetriever>())
        } throws SecurityException("denied")

        retriever.subscribe()
        retriever.unsubscribe()

        verify(exactly = 0) { connectivityManager.unregisterNetworkCallback(retriever) }
    }

    @Test
    fun unsubscribe_Does_Not_Unregister_When_Not_Subscribed() {
        retriever.unsubscribe()

        verify(exactly = 0) { connectivityManager.unregisterNetworkCallback(retriever) }
    }

    @Test
    fun unsubscribe_Unregisters_The_Callback_When_Subscribed() {
        mockUnregister()
        retriever.subscribe()

        retriever.unsubscribe()

        verify(exactly = 1) { connectivityManager.unregisterNetworkCallback(retriever) }
    }

    @Test
    fun unsubscribe_Unregisters_Once_When_Called_Twice() {
        mockUnregister()
        retriever.subscribe()

        retriever.unsubscribe()
        retriever.unsubscribe()

        verify(exactly = 1) { connectivityManager.unregisterNetworkCallback(retriever) }
    }

    // endregion

    // region API 25 / 26+ capability reads

    @Config(sdk = [25])
    @Test
    fun onAvailable_Reads_Capabilities_Once_On_Api_25() {
        mockNetworkCapabilities(wifi, createCapabilities(TRANSPORT_WIFI))

        retriever.onAvailable(wifi)

        verify(exactly = 1) { connectivityManager.getNetworkCapabilities(wifi) }
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
    }

    @Config(sdk = [25])
    @Test
    fun onAvailable_Does_Not_Emit_When_Capabilities_Are_Null_On_Api_25() {
        retriever.onAvailable(wifi)

        assertEquals(Status.Unknown, status)
    }

    @Config(sdk = [26, 33])
    @Test
    fun onAvailable_Does_Not_Read_Capabilities_From_Api_26_And_Waits_For_Capabilities_Changed() {
        mockNetworkCapabilities(wifi, createCapabilities(TRANSPORT_WIFI))

        retriever.onAvailable(wifi)

        verify(exactly = 0) { connectivityManager.getNetworkCapabilities(any()) }
        assertEquals(Status.Unknown, status)

        retriever.onCapabilitiesChanged(wifi, createCapabilities(TRANSPORT_WIFI))

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
    }

    @Test
    fun subscribe_Does_Not_Emit_Again_When_Callbacks_Redeliver_The_Initial_Network() {
        val wifiCapabilities = createCapabilities(TRANSPORT_WIFI)
        mockActiveNetwork(wifi, wifiCapabilities)
        retriever.subscribe()
        val emitted = events.size

        retriever.onAvailable(wifi)
        retriever.onCapabilitiesChanged(wifi, wifiCapabilities)

        assertEquals(emitted, events.size)
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
    }

    // endregion

    // region status derivation

    @Config(sdk = [23, 25, 26, 33])
    @Test
    fun onCapabilitiesChanged_Ignores_Networks_Other_Than_The_Current_One() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        connect(wifi, createCapabilities(TRANSPORT_WIFI))

        retriever.onCapabilitiesChanged(cellular, createCapabilities(TRANSPORT_CELLULAR))

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
    }

    @Config(sdk = [23, 25, 26, 33])
    @Test
    fun onCapabilitiesChanged_Updates_Type_By_Transport_Priority() {
        connect(wifi, createCapabilities(TRANSPORT_WIFI))
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        retriever.onCapabilitiesChanged(wifi, createCapabilities(TRANSPORT_CELLULAR))
        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)

        retriever.onCapabilitiesChanged(wifi, createCapabilities(TRANSPORT_ETHERNET))
        assertEquals(Status.Connected(ConnectivityType.ETHERNET), status)

        retriever.onCapabilitiesChanged(wifi, createCapabilities())
        assertEquals(Status.Connected(ConnectivityType.UNKNOWN), status)

        retriever.onCapabilitiesChanged(
            wifi,
            createCapabilities(TRANSPORT_ETHERNET, TRANSPORT_CELLULAR, TRANSPORT_WIFI)
        )
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
    }

    @Config(sdk = [23, 25, 26, 33])
    @Test
    fun onCapabilitiesChanged_Emits_Connected_When_Captive_Portal_Is_Set() {
        connect(wifi, createCapabilities(TRANSPORT_WIFI, validated = false, captivePortal = true))

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
    }

    @Config(sdk = [23, 25, 26, 33])
    @Test
    fun onCapabilitiesChanged_Emits_NotConnected_When_Internet_Is_Missing() {
        connect(wifi, createCapabilities(TRANSPORT_WIFI, internet = false, validated = false))

        assertEquals(Status.NotConnected, status)
    }

    @Config(sdk = [23, 25, 26, 33])
    @Test
    fun onCapabilitiesChanged_Does_Not_Emit_When_Only_Validated_Changes() {
        connect(wifi, createCapabilities(TRANSPORT_WIFI))
        val emitted = events.size

        retriever.onCapabilitiesChanged(
            wifi,
            createCapabilities(TRANSPORT_WIFI, validated = false)
        )
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        retriever.onCapabilitiesChanged(wifi, createCapabilities(TRANSPORT_WIFI))
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
        assertEquals(emitted, events.size)
    }

    @Config(sdk = [23, 25, 26, 33])
    @Test
    fun onCapabilitiesChanged_Does_Not_Emit_When_Status_Is_Unchanged() {
        connect(wifi, createCapabilities(TRANSPORT_WIFI))
        val emitted = events.size

        retriever.onCapabilitiesChanged(wifi, createCapabilities(TRANSPORT_WIFI))

        assertEquals(emitted, events.size)
    }

    // endregion

    // region onLost

    @Test
    fun onLost_Emits_NotConnected_When_Current_Network_Has_No_Replacement() {
        deliver(wifi, createCapabilities(TRANSPORT_WIFI))

        retriever.onLost(wifi)

        assertEquals(Status.NotConnected, status)
        assertEquals(ConnectivityType.UNKNOWN, retriever.connectionType())
    }

    @Config(sdk = [24, 33])
    @Test
    fun onLost_Does_Not_Read_The_Active_Network_From_Api_24() {
        deliver(wifi, createCapabilities(TRANSPORT_WIFI))
        clearMocks(connectivityManager, answers = false)

        retriever.onLost(wifi)

        assertEquals(Status.NotConnected, status)
        verify(exactly = 0) { connectivityManager.activeNetwork }
    }

    @Config(sdk = [23])
    @Test
    fun onLost_Does_Not_Readopt_The_Lost_Network_On_Api_23() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.onAvailable(wifi)
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        // the service reports the loss before it stops returning the network as active
        retriever.onLost(wifi)

        assertEquals(Status.NotConnected, status)
        assertEquals(ConnectivityType.UNKNOWN, retriever.connectionType())
    }

    @Config(sdk = [23])
    @Test
    fun onLost_Emits_NotConnected_When_Active_Network_Lacks_Internet_On_Api_23() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.onAvailable(wifi)
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN, internet = false, validated = false))

        retriever.onLost(wifi)

        assertEquals(Status.NotConnected, status)
    }

    @Config(sdk = [23, 25])
    @Test
    fun subscribe_Emits_Underlying_Type_And_Does_Not_Track_Split_Tunnel_Vpn_Before_Api_26() {
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN, internet = false, validated = false))
        mockActiveNetworkInfo(ConnectivityType.WIFI, connected = true)
        retriever.subscribe()
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
        assertEquals(ConnectivityType.WIFI, retriever.connectionType())

        // the VPN was not adopted, so the first reported network is
        mockNetworkCapabilities(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.onAvailable(wifi)

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
        assertEquals(ConnectivityType.WIFI, retriever.connectionType())
    }

    @Config(sdk = [23, 25, 27])
    @Test
    fun subscribe_Emits_NotConnected_When_Split_Tunnel_Vpn_Network_Info_Is_Not_Connected_Before_Api_28() {
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN, internet = false, validated = false))
        mockActiveNetworkInfo(ConnectivityType.WIFI, connected = false)

        retriever.subscribe()

        assertEquals(Status.NotConnected, status)
        assertEquals(ConnectivityType.UNKNOWN, retriever.connectionType())
    }

    @Config(sdk = [23])
    @Test
    fun onAvailable_Adopts_Network_When_Active_Network_Is_Split_Tunnel_Vpn_On_Api_23() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.onAvailable(wifi)

        // the VPN is active without INTERNET and is never reported by the listen
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN, internet = false, validated = false))
        mockNetworkCapabilities(cellular, createCapabilities(TRANSPORT_CELLULAR))
        retriever.onAvailable(cellular)

        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)
    }

    @Config(sdk = [23])
    @Test
    fun onCapabilitiesChanged_Adopts_Network_When_It_Becomes_The_Active_Network_On_Api_23() {
        mockActiveNetwork(cellular, createCapabilities(TRANSPORT_CELLULAR))
        retriever.onAvailable(cellular)
        mockNetworkCapabilities(wifi, createCapabilities(TRANSPORT_WIFI))

        // wifi is reported before it validates; cellular is still the default
        retriever.onAvailable(wifi)
        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)

        // a change on wifi while cellular is still the default changes nothing
        retriever.onCapabilitiesChanged(wifi, createCapabilities(TRANSPORT_WIFI))
        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)

        // wifi validates and becomes the default
        every { connectivityManager.activeNetwork } returns wifi
        retriever.onCapabilitiesChanged(wifi, createCapabilities(TRANSPORT_WIFI))

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
        assertEquals(ConnectivityType.WIFI, retriever.connectionType())

        // the old default going away is ignored now that wifi is current
        clearMocks(connectivityManager, answers = false)
        retriever.onLost(cellular)

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
        assertEquals(ConnectivityType.WIFI, retriever.connectionType())
        verify(exactly = 0) { connectivityManager.getNetworkCapabilities(any()) }
        verify(exactly = 0) { connectivityManager.activeNetwork }
    }

    @Config(sdk = [24, 33])
    @Test
    fun onCapabilitiesChanged_Of_Another_Network_Does_Not_Read_The_Active_Network_From_Api_24() {
        deliver(cellular, createCapabilities(TRANSPORT_CELLULAR))
        clearMocks(connectivityManager, answers = false)

        retriever.onCapabilitiesChanged(wifi, createCapabilities(TRANSPORT_WIFI))

        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)
        verify(exactly = 0) { connectivityManager.activeNetwork }
    }

    @Config(sdk = [23])
    @Test
    fun onLost_Adopts_The_Active_Network_As_Replacement_On_Api_23() {
        deliver(wifi, createCapabilities(TRANSPORT_WIFI))
        mockActiveNetwork(cellular, createCapabilities(TRANSPORT_CELLULAR))

        retriever.onLost(wifi)

        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)
        retriever.onCapabilitiesChanged(cellular, createCapabilities(TRANSPORT_ETHERNET))
        assertEquals(Status.Connected(ConnectivityType.ETHERNET), status)
    }

    @Test
    fun onLost_Does_Not_Change_Status_When_Network_Is_Not_Current() {
        deliver(wifi, createCapabilities(TRANSPORT_WIFI))

        retriever.onLost(cellular)

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
    }

    // endregion

    // region API 23 listen

    @Config(sdk = [23])
    @Test
    fun onLost_Adopts_New_Default_After_Handover_On_Api_23() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        mockNetworkCapabilities(cellular, createCapabilities(TRANSPORT_CELLULAR))

        retriever.onAvailable(wifi)
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        // cellular is reported by the listen but is not the default
        retriever.onAvailable(cellular)
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        every { connectivityManager.activeNetwork } returns cellular
        retriever.onLost(wifi)

        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)
    }

    @Config(sdk = [23])
    @Test
    fun onLost_Emits_NotConnected_Then_onAvailable_Recovers_When_No_Active_Network_On_Api_23() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.onAvailable(wifi)

        every { connectivityManager.activeNetwork } returns null
        retriever.onLost(wifi)
        assertEquals(Status.NotConnected, status)

        mockNetworkCapabilities(cellular, createCapabilities(TRANSPORT_CELLULAR))
        retriever.onAvailable(cellular)
        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)
    }

    @Config(sdk = [23])
    @Test
    fun onLost_Adopts_Active_Network_When_Full_Tunnel_Vpn_Is_Lost_On_Api_23() {
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN))
        mockActiveNetworkInfo(ConnectivityType.WIFI)
        mockNetworkCapabilities(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.subscribe()
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        // the network under the VPN is not the default
        retriever.onAvailable(wifi)
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        // the VPN goes away and wifi is the default
        every { connectivityManager.activeNetwork } returns wifi
        retriever.onLost(vpn)
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
    }

    // endregion

    // region VPN type

    @Config(sdk = [23, 27, 28])
    @Test
    fun onCapabilitiesChanged_Resolves_Vpn_Type_From_Active_Network_Info_Before_Api_29() {
        mockActiveNetworkInfo(ConnectivityType.WIFI)
        mockNetworkCapabilities(vpn, createCapabilities(TRANSPORT_VPN))

        deliver(vpn, createCapabilities(TRANSPORT_VPN))

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
        assertEquals(ConnectivityType.WIFI, retriever.connectionType())
    }

    @Config(sdk = [27])
    @Test
    fun onCapabilitiesChanged_Maps_Vpn_Network_Info_Type_Or_Unknown_On_Api_27() {
        mockNetworkCapabilities(vpn, createCapabilities(TRANSPORT_VPN))

        retriever.onAvailable(vpn)
        mockActiveNetworkInfo(ConnectivityType.CELLULAR)
        retriever.onCapabilitiesChanged(vpn, createCapabilities(TRANSPORT_VPN))
        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)

        mockActiveNetworkInfo(ConnectivityType.ETHERNET)
        retriever.onCapabilitiesChanged(vpn, createCapabilities(TRANSPORT_VPN))
        assertEquals(Status.Connected(ConnectivityType.ETHERNET), status)

        mockNoActiveNetworkInfo()
        retriever.onCapabilitiesChanged(vpn, createCapabilities(TRANSPORT_VPN))
        assertEquals(Status.Connected(ConnectivityType.UNKNOWN), status)
    }

    @Config(sdk = [29])
    @Test
    fun onCapabilitiesChanged_Resolves_Vpn_Type_From_Transports_From_Api_29() {
        deliver(vpn, createCapabilities(TRANSPORT_VPN, TRANSPORT_WIFI))
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        retriever.onCapabilitiesChanged(vpn, createCapabilities(TRANSPORT_VPN))
        assertEquals(Status.Connected(ConnectivityType.UNKNOWN), status)

        verifyNoActiveNetworkInfoRead()
    }

    @Config(sdk = [23])
    @Test
    fun onAvailable_And_onLost_Refresh_Vpn_Type_When_Other_Networks_Change_On_Api_23() {
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN))
        mockActiveNetworkInfo(ConnectivityType.WIFI)
        retriever.subscribe()
        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        // the VPN now runs over cellular; the underlying networks report it
        mockActiveNetworkInfo(ConnectivityType.CELLULAR)
        retriever.onAvailable(cellular)

        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)

        mockActiveNetworkInfo(ConnectivityType.WIFI)
        retriever.onLost(cellular)

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)

        // a capability change on a network that is not the active one refreshes it too
        mockActiveNetworkInfo(ConnectivityType.CELLULAR)
        retriever.onCapabilitiesChanged(cellular, createCapabilities(TRANSPORT_CELLULAR))

        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)
    }

    @Config(sdk = [23])
    @Test
    fun onAvailable_Does_Not_Emit_When_Refreshed_Vpn_Type_Is_Unchanged_On_Api_23() {
        mockActiveNetwork(vpn, createCapabilities(TRANSPORT_VPN))
        mockActiveNetworkInfo(ConnectivityType.WIFI)
        retriever.subscribe()
        val emitted = events.size

        retriever.onAvailable(cellular)
        retriever.onLost(cellular)

        assertEquals(emitted, events.size)
    }

    @Config(sdk = [23])
    @Test
    fun onAvailable_Does_Not_Refresh_Type_When_Current_Is_Not_Vpn_On_Api_23() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.subscribe()
        val emitted = events.size

        retriever.onAvailable(cellular)
        retriever.onLost(cellular)

        assertEquals(emitted, events.size)
        verifyNoActiveNetworkInfoRead()
    }

    // endregion

    // region validated handover

    @Config(sdk = [23])
    @Test
    fun onCapabilitiesChanged_Adopts_Active_Network_When_Current_Loses_Validated_On_Api_23() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.onAvailable(wifi)
        mockNetworkCapabilities(cellular, createCapabilities(TRANSPORT_CELLULAR))
        every { connectivityManager.activeNetwork } returns cellular

        retriever.onCapabilitiesChanged(
            wifi,
            createCapabilities(TRANSPORT_WIFI, validated = false)
        )

        assertEquals(Status.Connected(ConnectivityType.CELLULAR), status)
        assertEquals(ConnectivityType.CELLULAR, retriever.connectionType())
    }

    @Config(sdk = [23])
    @Test
    fun onCapabilitiesChanged_Keeps_Current_When_It_Loses_Validated_And_Is_Still_Active_On_Api_23() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.onAvailable(wifi)

        retriever.onCapabilitiesChanged(
            wifi,
            createCapabilities(TRANSPORT_WIFI, validated = false)
        )

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
    }

    @Config(sdk = [24, 33])
    @Test
    fun onCapabilitiesChanged_Does_Not_Read_Active_Network_When_Current_Loses_Validated_From_Api_24() {
        deliver(wifi, createCapabilities(TRANSPORT_WIFI))
        clearMocks(connectivityManager, answers = false)

        retriever.onCapabilitiesChanged(
            wifi,
            createCapabilities(TRANSPORT_WIFI, validated = false)
        )

        assertEquals(Status.Connected(ConnectivityType.WIFI), status)
        verify(exactly = 0) { connectivityManager.activeNetwork }
    }

    // endregion

    // region connectionType

    @Test
    fun connectionType_Is_Unknown_Before_Any_Network_Is_Current() {
        assertEquals(ConnectivityType.UNKNOWN, retriever.connectionType())
    }

    @Test
    fun connectionType_Returns_Wifi_And_Does_Not_Query_ConnectivityManager() {
        mockActiveNetwork(wifi, createCapabilities(TRANSPORT_WIFI))
        retriever.subscribe()
        clearMocks(connectivityManager, answers = false)

        val type = retriever.connectionType()

        assertEquals(ConnectivityType.WIFI, type)
        verify(exactly = 0) { connectivityManager.activeNetwork }
        verify(exactly = 0) { connectivityManager.getNetworkCapabilities(any()) }
    }

    @Test
    fun connectionType_Is_Unknown_Until_Capabilities_Arrive_For_A_New_Network() {
        deliver(wifi, createCapabilities(TRANSPORT_WIFI))

        retriever.onAvailable(cellular)

        assertEquals(ConnectivityType.UNKNOWN, retriever.connectionType())
        retriever.onCapabilitiesChanged(cellular, createCapabilities(TRANSPORT_CELLULAR))
        assertEquals(ConnectivityType.CELLULAR, retriever.connectionType())
    }

    // endregion

    /**
     * Reports [network] as available with [capabilities], the way the OS does for the API level:
     * before API 26 onAvailable reads the capabilities, from API 26 they follow in
     * onCapabilitiesChanged.
     */
    private fun connect(network: Network, capabilities: NetworkCapabilities) {
        mockNetworkCapabilities(network, capabilities)
        retriever.onAvailable(network)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            retriever.onCapabilitiesChanged(network, capabilities)
        }
    }

    private fun mockUnregister() {
        every {
            connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        } returns Unit
    }

    /** Reports [network] as available and its capabilities as changed, in that order. */
    private fun deliver(network: Network, capabilities: NetworkCapabilities) {
        retriever.onAvailable(network)
        retriever.onCapabilitiesChanged(network, capabilities)
    }

    private fun mockActiveNetwork(network: Network, capabilities: NetworkCapabilities) {
        every { connectivityManager.activeNetwork } returns network
        mockNetworkCapabilities(network, capabilities)
    }

    private fun mockNetworkCapabilities(network: Network, capabilities: NetworkCapabilities) {
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
    }

    // NetworkInfo is deprecated, but it is what the retriever reads below API 29.
    @Suppress("DEPRECATION")
    private fun mockActiveNetworkInfo(type: ConnectivityType, connected: Boolean = true) {
        val info = mockk<android.net.NetworkInfo>()
        every { info.type } returns when (type) {
            ConnectivityType.WIFI -> ConnectivityManager.TYPE_WIFI
            ConnectivityType.CELLULAR -> ConnectivityManager.TYPE_MOBILE
            ConnectivityType.ETHERNET -> ConnectivityManager.TYPE_ETHERNET
            ConnectivityType.UNKNOWN -> throw IllegalArgumentException("Not a network info type")
        }
        every { info.isConnected } returns connected
        every { connectivityManager.activeNetworkInfo } returns info
    }

    @Suppress("DEPRECATION")
    private fun mockNoActiveNetworkInfo() {
        every { connectivityManager.activeNetworkInfo } returns null
    }

    @Suppress("DEPRECATION")
    private fun verifyNoActiveNetworkInfoRead() {
        verify(exactly = 0) { connectivityManager.activeNetworkInfo }
    }

    private fun createCapabilities(
        vararg transports: Int,
        internet: Boolean = true,
        validated: Boolean = true,
        captivePortal: Boolean = false
    ): NetworkCapabilities {
        val flags = setOfNotNull(
            NET_CAPABILITY_INTERNET.takeIf { internet },
            NET_CAPABILITY_VALIDATED.takeIf { validated },
            NET_CAPABILITY_CAPTIVE_PORTAL.takeIf { captivePortal }
        )
        val capabilities = mockk<NetworkCapabilities>()
        every { capabilities.hasTransport(any()) } answers { firstArg<Int>() in transports }
        every { capabilities.hasCapability(any()) } answers { firstArg<Int>() in flags }
        return capabilities
    }

    /** The capabilities field of a request is hidden API, and its accessors are not on API 23. */
    private fun NetworkRequest.capabilities(): NetworkCapabilities =
        NetworkRequest::class.java.getField("networkCapabilities").get(this) as NetworkCapabilities
}
