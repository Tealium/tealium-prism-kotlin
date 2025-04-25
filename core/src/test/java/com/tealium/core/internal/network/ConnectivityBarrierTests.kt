package com.tealium.core.internal.network

import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.network.Connectivity
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.StateSubject
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class ConnectivityBarrierTests {

    @MockK
    private lateinit var connectivity: Connectivity

    private lateinit var  connectivityStatus: StateSubject<Connectivity.Status>
    private lateinit var connectivityBarrier: ConnectivityBarrier

    @Before
    fun setUp() {
        MockKAnnotations.init(this)

        connectivityStatus = Observables.stateSubject(Connectivity.Status.NotConnected)
        every { connectivity.onConnectionStatusUpdated } returns connectivityStatus

        connectivityBarrier = ConnectivityBarrier(connectivity)
    }

    @Test
    fun barrier_IsOpen_When_ConnectivityConnected() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        connectivityBarrier.onState.subscribe(verifier)
        connectivityStatus.onNext(Connectivity.Status.Connected)

        verify {
            verifier(BarrierState.Open)
        }
    }

    @Test
    fun barrier_IsClose_When_ConnectivityNotConnected() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        connectivityBarrier.onState.subscribe(verifier)
        connectivityStatus.onNext(Connectivity.Status.NotConnected)

        verify {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun barrier_IsClosed_When_ConnectivityUnknown() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        connectivityBarrier.onState.subscribe(verifier)
        connectivityStatus.onNext(Connectivity.Status.Unknown)

        verify {
            verifier(BarrierState.Closed)
        }
    }

    @Test
    fun barrier_Transitions_When_ConnectivityStatus_Updated() {
        val verifier = mockk<(BarrierState) -> Unit>(relaxed = true)

        connectivityBarrier.onState.subscribe(verifier)

        connectivityStatus.onNext(Connectivity.Status.NotConnected)
        connectivityStatus.onNext(Connectivity.Status.Connected)
        connectivityStatus.onNext(Connectivity.Status.Unknown)

        verify {
            verifier(BarrierState.Closed)
            verifier(BarrierState.Open)
            verifier(BarrierState.Closed)
        }
    }
}

