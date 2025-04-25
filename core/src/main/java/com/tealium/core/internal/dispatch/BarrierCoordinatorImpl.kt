package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables

/**
 * Utility alias to link a [Barrier] implementation with its known scope.
 */
typealias ScopedBarrier = Pair<Barrier, Set<BarrierScope>>
inline val ScopedBarrier.barrier: Barrier
    get() = this.first
inline val ScopedBarrier.scopes: Set<BarrierScope>
    get() = this.second

class BarrierCoordinatorImpl(
    private val barriers: Observable<List<ScopedBarrier>>,
) : BarrierCoordinator {

    override fun onBarriersState(dispatcherId: String): Observable<BarrierState> {
        return barriers.flatMapLatest { barriers ->
            areBarriersOpen(getBarriersForDispatcher(barriers, dispatcherId))
        }.distinct()
    }

    private fun getBarriersForDispatcher(
        barriers: List<ScopedBarrier>,
        dispatcherId: String
    ): List<Barrier> {
        return barriers.filter { scopedBarrier ->
            scopedBarrier.scopes.contains(BarrierScope.All)
                    || scopedBarrier.scopes.contains(BarrierScope.Dispatcher(dispatcherId))
        }.map { scopedBarrier -> scopedBarrier.barrier }
    }

    private fun areBarriersOpen(
        barriers: List<Barrier>
    ): Observable<BarrierState> {
        if (barriers.isEmpty()) return Observables.just(BarrierState.Open)

        return Observables.combine(
            barriers.map { it.onState }
        ) { barrierStates ->
            barrierStates.firstOrNull { it == BarrierState.Closed } ?: BarrierState.Open
        }
    }
}
