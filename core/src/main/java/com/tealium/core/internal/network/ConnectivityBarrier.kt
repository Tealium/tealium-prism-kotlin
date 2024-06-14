package com.tealium.core.internal.network

import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.network.Connectivity
import com.tealium.core.internal.observables.Observable

class ConnectivityBarrier(
    private val onConnectionStatusUpdated: Observable<Connectivity.Status>
) : Barrier {

    override val id: String = BARRIER_ID
    override val onState: Observable<BarrierState>
        get() = onConnectionStatusUpdated.map { status ->
            when (status) {
                Connectivity.Status.Connected -> BarrierState.Open
                else -> BarrierState.Closed
            }
        }

    companion object {
        const val BARRIER_ID = "ConnectivityBarrier"
    }
}