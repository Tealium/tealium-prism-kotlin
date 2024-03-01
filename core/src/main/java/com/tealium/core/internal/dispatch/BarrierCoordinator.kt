package com.tealium.core.internal.dispatch

import com.tealium.core.api.BarrierState
import com.tealium.core.api.Barrier
import com.tealium.core.api.Dispatcher
import com.tealium.core.internal.observables.Observable

/**
 * The [BarrierCoordinator] is responsible for maintaining available [com.tealium.core.api.Barrier]
 * implementations.
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
}