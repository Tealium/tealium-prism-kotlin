package com.tealium.core.internal.dispatch

import com.tealium.core.api.Barrier
import com.tealium.core.api.Dispatcher

/**
 * A [ScopedBarrier] describes which [Dispatcher] implementation any given [Barrier] should be
 * guarding. The [Barrier] implementation is identified by the [barrierId], and the [scope]
 * sets out which scopes that barrier is relevant for.
 *
 * @param barrierId The id of the [Barrier]
 * @param scope A set of [BarrierScope]s that the [Barrier] should be consulted on.
 *
 * @see Barrier
 * @see BarrierScope
 */
class ScopedBarrier(
    val barrierId: String,
    val scope: Set<BarrierScope>
) {
    /**
     * Utility method to determine whether or not this [ScopedBarrier] is applicable to the
     * give [scope]. Used to determine whether a specific [Barrier] should be checked for its
     * state.
     */
    fun matchesScope(scope: BarrierScope): Boolean = this.scope.contains(scope)
}