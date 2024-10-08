package com.tealium.core.api.modules

import com.tealium.core.api.data.DataObject

/**
 * A [Collector] is used to provide common data points to all [Dispatch] objects that are tracked
 * through its [collect] method.
 */
interface Collector: Module {

    /**
     * Provides the common data as a [DataObject] that should be added to every [Dispatch] object.
     */
    fun collect(): DataObject
}