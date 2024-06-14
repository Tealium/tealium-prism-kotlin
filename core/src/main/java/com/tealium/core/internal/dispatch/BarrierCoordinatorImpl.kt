package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.barriers.ScopedBarrier
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.Observables
import com.tealium.core.internal.observables.StateSubject

class BarrierCoordinatorImpl(
    private var registeredBarriers: Set<Barrier>,
    scopedBarriers: Observable<Set<ScopedBarrier>> =
        Observables.stateSubject(setOf()),
    private val additionalScopedBarriers: StateSubject<Set<ScopedBarrier>> =
        Observables.stateSubject(setOf())
) : BarrierCoordinator {

    private val allScopedBarriers =
        scopedBarriers.combine(additionalScopedBarriers) { scoped, additional ->
            scoped + additional
        }

    override fun onBarriersState(dispatcherId: String): Observable<BarrierState> {
        return allScopedBarriers
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

    override fun registerBarrier(barrier: Barrier) {
        registeredBarriers = registeredBarriers.toMutableSet().apply {
            add(barrier)
        }
    }

    override fun unregisterBarrier(barrier: Barrier) {
        registeredBarriers = registeredBarriers.toMutableSet().apply {
            remove(barrier)
        }
    }

    override fun registerScopedBarrier(scopedBarrier: ScopedBarrier) {
        val barriers = additionalScopedBarriers.value

        additionalScopedBarriers.onNext(barriers + scopedBarrier)
    }

    override fun unregisterScopedBarrier(scopedBarrier: ScopedBarrier) {
        val barriers = additionalScopedBarriers.value.toMutableSet()
        barriers.remove(scopedBarrier)

        additionalScopedBarriers.onNext(barriers)
    }
}
