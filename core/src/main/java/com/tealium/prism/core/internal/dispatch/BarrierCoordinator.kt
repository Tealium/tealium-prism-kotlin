package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.Barrier
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.tracking.Dispatch

/**
 * The [BarrierCoordinator] is responsible for computing the state of all [Barrier] implementations
 * scoped to any given [Dispatcher]
 */
interface BarrierCoordinator {

    /**
     * Returns a [Observable] of [BarrierState]s that are specific to the [Dispatcher] identified by the
     * [dispatcherId].
     *
     * The [BarrierState] values emitted from the returned Observable are a combination of the states of
     * any [Barrier] which is scoped to [BarrierScope.All], as well as any [Barrier] which is scoped
     * explicitly to the provided [dispatcherId].
     *
     * If any [Barrier] scoped to the [Dispatcher] is [BarrierState.Closed] then the resulting emission
     * from the returned [Observable]. That is, the combined [BarrierState]s are all required to be
     * [BarrierState.Open] in order for the resulting emission to also be [BarrierState.Open]
     *
     * @param dispatcherId The id of the [Dispatcher]
     * @return A [Observable] of [BarrierState]s for the [Dispatcher]
     */
    fun onBarriersState(dispatcherId: String): Observable<BarrierState>

    /**
     * Places the [BarrierCoordinator] into a "flushing" state for each dispatcher until any queued
     * [Dispatch]es have been processed and the queue size is reduced to zero.
     *
     * The status of any barrier whose [Barrier.isFlushable] property returns `true` will be ignored
     * until the flush is completed.
     */
    fun flush()
}