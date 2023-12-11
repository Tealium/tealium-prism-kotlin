package com.tealium.core.internal.dispatch

import com.tealium.core.api.Collector
import com.tealium.core.api.DispatchScope
import com.tealium.core.api.Dispatch

/**
 * Sets out the available extension points during the [Dispatch] lifecycle.
 */
sealed class TransformationScope {

    /**
     * This scope happens directly after all data collection has been completed from any [Collector]
     * implementations in the system, but before the [Dispatch] has been queued.
     */
    object AfterCollectors : TransformationScope()

    /**
     * This scope happens during the process of sending [Dispatch]es to individual [Dispatcher]s but
     * this scope will be run for all [Dispatcher]s in the system.
     *
     * @see Dispatcher
     */
    object AllDispatchers : TransformationScope()

    /**
     * This scope happens during the process of sending [Dispatch]es to an individual [Dispatcher] as
     * identified by the [dispatcher] id given.
     */
    data class Dispatcher(val dispatcher: String) : TransformationScope()

    /**
     * Describes whether or not this [TransformationScope] matches the [DispatchScope].
     *
     * The [DispatchScope] does not have a separation of [TransformationScope.AllDispatchers] and [TransformationScope.Dispatcher]
     * so this method returns true when
     *  - Both scopes a AfterCollectors
     *  - [TransformationScope] is [TransformationScope.AllDispatchers] and the [DispatchScope] is [DispatchScope.Dispatcher]
     *  - [TransformationScope] is [TransformationScope.Dispatcher] and the [DispatchScope] is [DispatchScope.Dispatcher] as well as having a matching dispatcher id.
     */
    fun matches(dispatchScope: DispatchScope): Boolean {
        return ((this is AfterCollectors && dispatchScope is DispatchScope.AfterCollectors)
                || (this is AllDispatchers && dispatchScope is DispatchScope.Dispatcher)
                || (this is Dispatcher && dispatchScope is DispatchScope.Dispatcher && this.dispatcher == dispatchScope.dispatcher))
    }


    // TODO - TealiumSerializable etc
}