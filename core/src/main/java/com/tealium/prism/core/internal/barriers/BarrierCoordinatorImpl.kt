package com.tealium.prism.core.internal.barriers

import com.tealium.prism.core.api.barriers.Barrier
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.misc.QueueMetrics
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.api.pubsub.mapToUnit
import com.tealium.prism.core.internal.dispatch.matches
import com.tealium.prism.core.internal.pubsub.DisposableContainer

/**
 * Utility alias to link a [Barrier] implementation with its known scope.
 */
typealias ScopedBarrier = Pair<Barrier, BarrierScope>

inline val ScopedBarrier.barrier: Barrier
    get() = this.first
inline val ScopedBarrier.scope: BarrierScope
    get() = this.second

class BarrierCoordinatorImpl(
    private val barriers: Observable<List<ScopedBarrier>>,
    private val applicationStatus: Observable<ActivityManager.ApplicationStatus>,
    private val queueMetrics: QueueMetrics,
    /**
     * The milliseconds to wait for extra events before definitively flushing
     */
    private val flushDebounceMilliseconds: Int = 200,
    /**
     * The amount of milliseconds a flush operation can last, both in foreground and background
     */
    private val flushTimeoutMilliseconds: Int = 5000,
    private val scheduler: Scheduler
) : BarrierCoordinator {

    private val disposables = DisposableContainer()
    private val queueFlushStates = mutableMapOf<String, StateSubject<Boolean>>()
    private val flushTrigger: Subject<Unit> = Observables.publishSubject()

    override fun flush() {
        flushTrigger.onNext(Unit)
    }

    override fun shutdown() {
        disposables.dispose()
    }

    /**
     * Returns an observable that emits whether the queue of the dispatcher is being flushed.
     *
     * The flush state is shared per dispatcher: new subscribers receive the current state and don't start a flush.
     * A flush starts only when a new application status is emitted, or when [flush] is called.
     * The status replayed to the first subscription is the one that causes the flush on launch.
     * The states live as long as the coordinator: the dispatcher ids are few, and a removed dispatcher's queue is emptied, so it doesn't flush again.
     * Must be called on the tealium scheduler.
     */
    private fun onQueueIsBeingFlushed(dispatcherId: String): Observable<Boolean> {
        queueFlushStates[dispatcherId]?.let { state ->
            return state.asObservable()
        }
        val state = Observables.stateSubject(false)
        queueFlushStates[dispatcherId] = state
        detectQueueFlush(dispatcherId)
            .subscribe(state)
            .addTo(disposables)
        return state.asObservable()
    }

    private fun detectQueueFlush(dispatcherId: String): Observable<Boolean> =
        applicationStatus
            .mapToUnit()
            .merge(flushTrigger)
            .flatMapLatest {
                queueMetrics.queueSizePendingDispatch(dispatcherId)
                    .map { size -> size > 0 } // should flush is true until queue pending dispatches is empty
                    .distinct()
                    // Debounce to make sure queue size is not changing again very soon,
                    // for example when a transformer takes some time
                    // to transform a `sleep` event coming from lifecycle.
                    // Only changes between empty and non-empty restart the debounce, so steady arrivals can't postpone the flush.
                    .debounce(flushDebounceMilliseconds.milliseconds, scheduler)
                    .merge(Observables.just(false).delay(flushTimeoutMilliseconds.milliseconds, scheduler)) // stop flush after a while anyway
                    .takeWhile(inclusive = true) { it } // stop listening when flush ended, include final false to stop the flush
                    .distinct()
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
        return onQueueIsBeingFlushed(dispatcherId)
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
                scopedBarrier.scope.matches(dispatcherId)
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
