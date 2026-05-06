package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationScope
import com.tealium.prism.core.api.transform.TransformationSettings

/**
 * Describes whether or not this [TransformationScope] matches the [DispatchScope].
 *
 * The [DispatchScope] does not have a separation of [TransformationScope.AllDispatchers] and [TransformationScope.Dispatcher]
 * so this method returns true when
 *  - Both scopes a AfterCollectors
 *  - [TransformationScope] is [TransformationScope.AllDispatchers] and the [DispatchScope] is [DispatchScope.Dispatcher]
 *  - [TransformationScope] is [TransformationScope.Dispatcher] and the [DispatchScope] is [DispatchScope.Dispatcher] as well as having a matching dispatcher id.
 */
fun TransformationScope.matches(dispatchScope: DispatchScope): Boolean {
    return ((this is TransformationScope.AfterCollectors && dispatchScope is DispatchScope.AfterCollectors)
            || (this is TransformationScope.AllDispatchers && dispatchScope is DispatchScope.Dispatcher)
            || (this is TransformationScope.Dispatcher && dispatchScope is DispatchScope.Dispatcher && this.dispatcher == dispatchScope.dispatcher))
}

/**
 * A [TransformationSettings] may match multiple [TransformationScope]s. If the given [dispatchScope]
 * matches the scope of any of the [TransformationScope]s listed in [scope] then it will
 * return true, else false.
 */
fun TransformationSettings.matchesScope(dispatchScope: DispatchScope): Boolean {
    return scope.firstOrNull { transformationScope ->
        transformationScope.matches(dispatchScope)
    } != null
}

/**
 * Convenience method for converting a string value to a [TransformationScope]
 */
fun transformationScopeFromString(scope: String) : TransformationScope {
    return if (scope == TransformationScope.AfterCollectors.value) {
        TransformationScope.AfterCollectors
    } else if (scope == TransformationScope.AllDispatchers.value) {
        TransformationScope.AllDispatchers
    } else {
        TransformationScope.Dispatcher(scope)
    }
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
