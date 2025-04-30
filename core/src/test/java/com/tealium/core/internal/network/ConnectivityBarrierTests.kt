package com.tealium.core.internal.network

import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.network.Connectivity.ConnectivityType
import com.tealium.core.api.network.Connectivity.Status
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import io.mockk.MockKAnnotations
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Before
import org.junit.Test

class ConnectivityBarrierTests {

    private lateinit var connectivityStatus: StateSubject<Connectivity.Status>
    private lateinit var isWifiOnly: StateSubject<Boolean>
    private lateinit var connectivityBarrier: ConnectivityBarrier

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        connectivityStatus = Observables.stateSubject(Status.NotConnected)
        isWifiOnly = Observables.stateSubject(false)

        connectivityBarrier = ConnectivityBarrier(connectivityStatus, isWifiOnly)
    }

    @Test
    fun barrier_IsOpen_When_ConnectivityConnected() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        connectivityBarrier.onState.subscribe(verifier)
        connectivityStatus.onNext(Status.Connected(ConnectivityType.WIFI))

        verify {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun barrier_IsClose_When_ConnectivityNotConnected() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        connectivityBarrier.onState.subscribe(verifier)
        connectivityStatus.onNext(Status.NotConnected)

        verify {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun barrier_IsClosed_When_ConnectivityUnknown() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        connectivityBarrier.onState.subscribe(verifier)
        connectivityStatus.onNext(Status.Unknown)

        verify {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun barrier_Transitions_When_ConnectivityStatus_Updated() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        connectivityBarrier.onState.subscribe(verifier)

        connectivityStatus.onNext(Status.NotConnected)
        connectivityStatus.onNext(Status.Connected(ConnectivityType.WIFI))
        connectivityStatus.onNext(Status.Unknown)

        verify {
            verifier(BarrierState.Closed)
            verifier(BarrierState.Open)
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun barrier_Closed_When_IsWifi_True_But_Connectivity_Cellular() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        connectivityStatus.onNext(Status.Connected(ConnectivityType.CELLULAR))
        isWifiOnly.onNext(true)

        connectivityBarrier.onState.subscribe(verifier)

        verify {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun barrier_Open_When_IsWifi_True_And_Connectivity_Wifi() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        connectivityStatus.onNext(Status.Connected(ConnectivityType.WIFI))
        isWifiOnly.onNext(true)

        connectivityBarrier.onState.subscribe(verifier)

        verify {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun barrier_Open_When_IsWifi_True_And_Connectivity_Ethernet() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        connectivityStatus.onNext(Status.Connected(ConnectivityType.ETHERNET))
        isWifiOnly.onNext(true)

        connectivityBarrier.onState.subscribe(verifier)

        verify {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun barrier_Open_When_IsWifi_False_And_Connectivity_Cellular() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        connectivityStatus.onNext(Status.Connected(ConnectivityType.CELLULAR))
        isWifiOnly.onNext(false)

        connectivityBarrier.onState.subscribe(verifier)

        verify {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun barrier_Transitions_To_Closed_When_IsWifi_Becomes_True_And_Connectivity_Cellular() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        connectivityStatus.onNext(Status.Connected(ConnectivityType.CELLULAR))
        isWifiOnly.onNext(false)

        connectivityBarrier.onState.subscribe(verifier)
        isWifiOnly.onNext(true)

        verifyOrder {
            verifier(BarrierState.Open)
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun barrier_Does_Not_Emit_If_State_Has_Not_Changed() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)
        connectivityStatus.onNext(Status.Connected(ConnectivityType.CELLULAR))
        isWifiOnly.onNext(false)

        connectivityBarrier.onState.subscribe(verifier)
        connectivityStatus.onNext(Status.Connected(ConnectivityType.WIFI))
        connectivityStatus.onNext(Status.Connected(ConnectivityType.ETHERNET))
        isWifiOnly.onNext(false)

        verify(exactly = 1) {
            verifier(BarrierState.Open)
        }
    }
}

