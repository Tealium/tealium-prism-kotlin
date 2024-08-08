package com.tealium.core.api.transform

import com.tealium.core.api.modules.Collector
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.data.TealiumSerializable
import com.tealium.core.api.data.TealiumValue

/**
 * Sets out the available extension points during the [Dispatch] lifecycle.
 */
sealed class TransformationScope(val value: String): TealiumSerializable {

    /**
     * This scope happens directly after all data collection has been completed from any [Collector]
     * implementations in the system, but before the [Dispatch] has been queued.
     */
    object AfterCollectors : TransformationScope("aftercollectors")

    /**
     * This scope happens during the process of sending [Dispatch]es to individual [Dispatcher]s but
     * this scope will be run for all [Dispatcher]s in the system.
     *
     * @see Dispatcher
     */
    object AllDispatchers : TransformationScope("alldispatchers")

    /**
     * This scope happens during the process of sending [Dispatch]es to an individual [Dispatcher] as
     * identified by the [dispatcher] id given.
     */
    data class Dispatcher(val dispatcher: String) : TransformationScope(dispatcher)

    override fun asTealiumValue(): TealiumValue {
        return TealiumValue.string(value)
    }
}