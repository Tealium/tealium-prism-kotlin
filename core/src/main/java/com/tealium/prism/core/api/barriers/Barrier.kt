package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.pubsub.Observables

/**
 * Defines a utility that can influence whether or not it is safe to continue processing events for
 * any [Dispatcher] implementations that may rely on this [Barrier].
 *
 * A typical example could be for [Dispatcher]s that may require connectivity, which might use a
 * Connectivity Barrier to control whether or not it should continue processing events.
 *
 * Updates to the barrier state should be emitted via the [onState] observable.
 */
interface Barrier {

    /**
     * The flow of this barrier's current state.
     *
     * [BarrierState.Closed] should be emitted to disallow further processing, and [BarrierState.Open]
     * to allow processing again.
     */
    fun onState(dispatcherId: String): Observable<BarrierState>

    /**
     * States whether or not this [Barrier] can be bypassed for "flush" events.
     *
     * @return An [Observable] that emits true if this [Barrier] can be bypassed; else false
     */
    val isFlushable: Observable<Boolean>
        get() = Observables.just(true)
}
