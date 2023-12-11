package com.tealium.core.internal.dispatch

import com.tealium.core.api.Barrier

/**
 * The [BarrierScope] defines the available scopes that can be assigned to a [Barrier] via a [ScopedBarrier]
 *
 * There are only two available scopes that a [Barrier] can impact:
 *  - [All]
 *  - [Dispatcher]
 *
 * A [Barrier] scoped to [All] will be checked for its state for every [Dispatcher] before dispatching
 * events to it.
 * A [Barrier] scoped to [Dispatcher] will only be checked for its state for the specific [Dispatcher]
 * as identified by the given [Dispatcher] name.
 */
sealed class BarrierScope() {
    /**
     * This [BarrierScope] will affect all [Dispatcher] implementations.
     */
    object All : BarrierScope()

    /**
     * This [BarrierScope] will affect only the [Dispatcher] implementation identified by the provided
     * [dispatcher].
     *
     * @param dispatcher The name of the dispatcher this [BarrierScope] will be guarding.
     */
    data class Dispatcher(val dispatcher: String) : BarrierScope()

    // TODO - TealiumSerializable to assist reading from Settings JSON
}