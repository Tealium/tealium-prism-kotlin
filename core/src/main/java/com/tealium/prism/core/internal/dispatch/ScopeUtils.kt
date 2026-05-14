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
 * Convenience method for converting a string value to a [BarrierScope]
 */
fun barrierScopeFromString(scope: String): BarrierScope {
    return if (scope == BarrierScope.All.value) {
        BarrierScope.All
    } else {
        BarrierScope.Dispatcher(scope)
    }
}