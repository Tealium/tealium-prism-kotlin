package com.tealium.core.internal.dispatch

import com.tealium.core.api.Barrier
import com.tealium.core.api.BarrierState
import com.tealium.core.internal.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

class BarrierCoordinatorImpl(
    private val registeredBarriers: Set<Barrier>,
    initialBarriers: Set<ScopedBarrier>,
    val scopedBarriers: MutableStateFlow<Set<ScopedBarrier>> =
        MutableStateFlow(initialBarriers)
) : BarrierCoordinator {

    override fun onBarriersState(dispatcherId: String): Flow<BarrierState> {
        return scopedBarriers
            .flatMapLatest {
                areBarriersOpen(getAllBarriers(it, dispatcherId))
            }.distinctUntilChanged()
    }

    internal fun getAllBarriers(
        scopedBarriers: Set<ScopedBarrier>,
        dispatcherId: String
    ): Set<Barrier> {
        return (getBarriers(scopedBarriers, BarrierScope.All) +
                getBarriers(scopedBarriers, BarrierScope.Dispatcher(dispatcherId)))
    }

    internal fun getBarriers(
        scopedBarriers: Set<ScopedBarrier>,
        scope: BarrierScope
    ): Set<Barrier> {
        return scopedBarriers.filter { it.matchesScope(scope) }
            .mapNotNull { barrierScope ->
                registeredBarriers.firstOrNull { it.id == barrierScope.barrierId }
            }.toSet()
    }

    private fun areBarriersOpen(
        barriers: Set<Barrier>
    ): Flow<BarrierState> {
        if (barriers.isEmpty()) return flowOf(BarrierState.Open)

        return combine(
            barriers.map { it.onState }
        ) { barrierStates ->
            barrierStates.firstOrNull { it == BarrierState.Closed } ?: BarrierState.Open
        }
    }
}
