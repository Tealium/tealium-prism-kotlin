package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.misc.ActivityManager
import com.tealium.core.api.misc.QueueMetrics
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.pubsub.mapToUnit

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
    private val applicationStatus: Observable<ActivityManager.ApplicationStatus>,
    private val queueMetrics: QueueMetrics
) : BarrierCoordinator {

    private val flushTrigger: Subject<Unit> = Observables.publishSubject()

    override fun flush() {
        flushTrigger.onNext(Unit)
    }

    private fun queueIsBeingFlushedForDispatcher(dispatcherId: String): Observable<Boolean> =
        applicationStatus
            .mapToUnit()
            .merge(flushTrigger)
            .flatMapLatest {
                queueMetrics.queueSizePendingDispatch(dispatcherId)
                    .map { size -> size > 0 }
                    .takeWhile(inclusive = true) { it }
            }.startWith(false)
            .distinct()

    override fun onBarriersState(dispatcherId: String): Observable<BarrierState> {
        return barriers.map { barriers ->
            filterBarriersForDispatcher(barriers, dispatcherId)
        }.flatMapLatest { barriers ->
            filterFlushableBarriers(barriers, dispatcherId)
        }.flatMapLatest { barriers ->
            areBarriersOpen(barriers, dispatcherId)
        }.distinct()
    }

    private fun filterFlushableBarriers(
        barriers: List<Barrier>,
        dispatcherId: String,
    ): Observable<List<Barrier>> {
        return queueIsBeingFlushedForDispatcher(dispatcherId)
            .flatMapLatest { isFlushing ->
                if (!isFlushing) {
                    return@flatMapLatest Observables.just(barriers)
                }

                val nonFlushableBarriers = barriers
                    .map { barrier ->
                        barrier.isFlushable
                            .distinct()
                            .map { flushable -> if (flushable) null else barrier }
                    }
                Observables.combine(nonFlushableBarriers) { it.filterNotNull().toList() }
            }
    }

    private fun filterBarriersForDispatcher(
        barriers: List<ScopedBarrier>,
        dispatcherId: String,
    ): List<Barrier> {
        return barriers
            .filter { scopedBarrier ->
                scopedBarrier.scopes.contains(BarrierScope.All)
                        || scopedBarrier.scopes.contains(BarrierScope.Dispatcher(dispatcherId))
            }.map { scopedBarrier -> scopedBarrier.barrier }
    }

    private fun areBarriersOpen(
        barriers: List<Barrier>,
        dispatcherId: String
    ): Observable<BarrierState> {
        if (barriers.isEmpty()) return Observables.just(BarrierState.Open)

        return Observables.combine(
            barriers.map { it.onState(dispatcherId) }
        ) { barrierStates ->
            barrierStates.firstOrNull { it == BarrierState.Closed } ?: BarrierState.Open
        }
    }
}
