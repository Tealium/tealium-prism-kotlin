package com.tealium.core.internal.dispatch

import com.tealium.core.api.Barrier
import com.tealium.core.api.BarrierState
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.Subject

class BarrierCoordinatorImpl(
    private val registeredBarriers: Set<Barrier>,
    initialBarriers: Set<ScopedBarrier>,
    val scopedBarriers: Subject<Set<ScopedBarrier>> =
        Observables.stateSubject(initialBarriers)
) : BarrierCoordinator {

    override fun onBarriersState(dispatcherId: String): Observable<BarrierState> {
        return scopedBarriers
            .flatMapLatest {
                areBarriersOpen(getAllBarriers(it, dispatcherId))
            }.distinct()
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
    ): Observable<BarrierState> {
        if (barriers.isEmpty()) return Observables.just(BarrierState.Open)

        return Observables.combine(
            barriers.map { it.onState }
        ) { barrierStates ->
            barrierStates.firstOrNull { it == BarrierState.Closed } ?: BarrierState.Open
        }
    }
}
