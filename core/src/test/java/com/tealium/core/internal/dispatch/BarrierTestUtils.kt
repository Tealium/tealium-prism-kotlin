package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.barriers.BarrierFactory
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.barriers.BarrierState
import com.tealium.core.api.barriers.ConfigurableBarrier
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.modules.TealiumContext
import com.tealium.core.api.pubsub.Observable

/**
 * Creates a new test [Barrier] with the given values
 */
fun barrier(id: String, state: Observable<BarrierState>): ConfigurableBarrier {
    return object : ConfigurableBarrier {
        override val id: String
            get() = id
        override val onState: Observable<BarrierState>
            get() = state
    }
}

/**
 * Creates a new test [BarrierFactory] returning the provided [barrier] when [BarrierFactory.create]
 * is called.
 */
fun barrierFactory(barrier: ConfigurableBarrier, defaultScope: Set<BarrierScope>? = null) : BarrierFactory = object : BarrierFactory {
    override val id: String
        get() = barrier.id

    override fun defaultScope(): Set<BarrierScope> =
        defaultScope ?: super.defaultScope()

    override fun create(context: TealiumContext, configuration: DataObject): ConfigurableBarrier {
        return barrier
    }
}

fun List<ScopedBarrier>.containsBarrier(barrier: Barrier): Boolean =
    firstOrNull { (b, _) -> barrier == b } != null