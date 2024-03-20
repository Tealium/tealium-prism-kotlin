package com.tealium.core.api

import com.tealium.core.api.data.TealiumBundle

/**
 * A [Collector] is used to provide common data points to all [Dispatch] objects that are tracked
 * through its [collect] method.
 */
interface Collector: Module {

    /**
     * Provides the common data as a [TealiumBundle] that should be added to every [Dispatch] object.
     */
    fun collect(): TealiumBundle
}