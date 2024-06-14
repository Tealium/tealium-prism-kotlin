package com.tealium.core.api.barriers

import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.Dispatcher

/**
 * Defines a utility that can influence whether or not it is safe to continue processing events for
 * any [Dispatcher] implementations that may rely on this [Barrier].
 *
 * A typical example could be for [Dispatcher]s that may require connectivity, which might use a
 * Connectivity Barrier to control whether or not it should continue processing events.
 *
 * Updates to the barrier state should be emitted via the [onState] Flow.
 */
interface Barrier {
    /**
     * The unique identifier of this barrier.
     * This String will be used to match up barriers scoped in the configuration JSON.
     */
    val id: String

    /**
     * The flow of this barrier's current state.
     *
     * [BarrierState.Closed] should be emitted to disallow further processing, and [BarrierState.Open]
     * to allow processing again.
     */
    val onState: Observable<BarrierState>
}

