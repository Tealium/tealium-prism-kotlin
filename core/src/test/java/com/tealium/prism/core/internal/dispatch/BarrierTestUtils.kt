package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.Barrier
import com.tealium.prism.core.api.barriers.BarrierFactory
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.barriers.ConfigurableBarrier
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.barriers.ScopedBarrier

fun barrier(
    barrierId: String,
    state: Observable<BarrierState>,
    flushable: Boolean = false
): ConfigurableBarrier =
    barrier(barrierId, state, Observables.just(flushable))

/**
 * Creates a new test [Barrier] with the given values
 */
fun barrier(
    barrierId: String,
    state: Observable<BarrierState>,
    flushable: Observable<Boolean>
): ConfigurableBarrier {
    return object : ConfigurableBarrier {
        override val id: String
            get() = barrierId

        override fun onState(dispatcherId: String): Observable<BarrierState> = state

        override val isFlushable: Observable<Boolean>
            get() = flushable
    }
}

/**
 * Creates a new test [BarrierFactory] returning the provided [barrier] when [BarrierFactory.create]
 * is called.
 */
fun barrierFactory(
    barrier: ConfigurableBarrier,
    defaultScopes: Set<BarrierScope>? = null,
    enforcedSettings: DataObject? = null
): BarrierFactory = object : BarrierFactory {
    override val id: String
        get() = barrier.id

    override fun defaultScopes(): Set<BarrierScope> =
        defaultScopes ?: super.defaultScopes()

    override fun create(context: TealiumContext, configuration: DataObject): ConfigurableBarrier {
        return barrier
    }

    override fun getEnforcedSettings(): DataObject =
        enforcedSettings ?: super.getEnforcedSettings()
}

fun List<ScopedBarrier>.containsBarrier(barrier: Barrier): Boolean =
    firstOrNull { (b, _) -> barrier == b } != null