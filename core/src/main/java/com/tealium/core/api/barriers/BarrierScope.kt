package com.tealium.core.api.barriers

import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItem

/**
 * The [BarrierScope] defines the available scopes that can be assigned to a [Barrier] via a [BarrierSettings]
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
sealed class BarrierScope(val value: String): DataItemConvertible {
    /**
     * This [BarrierScope] will affect all [Dispatcher] implementations.
     */
    object All : BarrierScope("all")

    /**
     * This [BarrierScope] will affect only the [Dispatcher] implementation identified by the provided
     * [dispatcher].
     *
     * @param dispatcher The name of the dispatcher this [BarrierScope] will be guarding.
     */
    data class Dispatcher(val dispatcher: String) : BarrierScope(dispatcher)

    override fun asDataItem(): DataItem {
        return DataItem.string(value)
    }
}