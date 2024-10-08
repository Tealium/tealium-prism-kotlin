package com.tealium.core.api.barriers

import com.tealium.core.api.modules.Dispatcher
import com.tealium.core.api.data.DataObject
import com.tealium.core.api.data.DataItemConvertible
import com.tealium.core.api.data.DataItem
import com.tealium.core.internal.misc.Converters.ScopedBarrierConverter.KEY_BARRIER_ID
import com.tealium.core.internal.misc.Converters.ScopedBarrierConverter.KEY_SCOPES

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
data class ScopedBarrier(
    val barrierId: String,
    val scope: Set<BarrierScope>
) : DataItemConvertible {

    override fun asDataItem(): DataItem {
        return DataObject.create {
            put(KEY_BARRIER_ID, barrierId)
            put(KEY_SCOPES, DataItem.convert(scope))
        }.asDataItem()
    }
}