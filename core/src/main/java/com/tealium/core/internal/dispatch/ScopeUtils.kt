package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.Barrier
import com.tealium.core.api.barriers.BarrierScope
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.TransformationScope
import com.tealium.core.api.transform.TransformationSettings
import com.tealium.core.internal.settings.BarrierSettings

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
 * Convenience method for converting a string value to a [BarrierScope]
 */
fun barrierScopeFromString(scope: String): BarrierScope {
    return if (scope == BarrierScope.All.value) {
        BarrierScope.All
    } else {
        BarrierScope.Dispatcher(scope)
    }
}