package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings

/**
 * Describes whether this [TransformationScope] matches the [DispatchScope].
 *
 * The [DispatchScope] does not have a separation of [TransformationScope.AllDispatchers] and [TransformationScope.Dispatchers]
 * so this method returns true when
 *  - Both scopes a AfterCollectors
 *  - [TransformationScope] is [TransformationScope.AllDispatchers] and the [DispatchScope] is [DispatchScope.Dispatcher]
 *  - [TransformationScope] is [TransformationScope.Dispatchers] and the [DispatchScope] is [DispatchScope.Dispatcher] as well as having a matching dispatcher id.
 */
fun TransformationScope.matches(dispatchScope: DispatchScope): Boolean =
    when (this) {
        is TransformationScope.AfterCollectors ->
            dispatchScope is DispatchScope.AfterCollectors

        is TransformationScope.AllDispatchers ->
            dispatchScope is DispatchScope.Dispatcher

        is TransformationScope.Dispatchers ->
            dispatchScope is DispatchScope.Dispatcher &&
                    this.dispatcherIds.contains(dispatchScope.dispatcher)
    }

/**
 * A [BarrierScope] matches a given dispatcher id if either
 *  - The [BarrierScope] is [BarrierScope.All]
 *  - The [BarrierScope] is [BarrierScope.Dispatchers] and the provided dispatcher id is in the list of dispatcher ids for this scope.
 */
fun BarrierScope.matches(dispatcherId: String): Boolean =
    when(this) {
        is BarrierScope.All -> true
        is BarrierScope.Dispatchers -> this.dispatcherIds.contains(dispatcherId)
    }
