package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.tracking.DispatchContext

/**
 * A [Collector] is used to provide common data points to all [Dispatch] objects that are tracked
 * through its [collect] method.
 */
interface Collector: Module {

    /**
     * Provides the common data as a [DataObject] that should be added to every [Dispatch] object.
     *
     * @param dispatchContext Some contextual information about the [Dispatch] being enriched.
     */
    fun collect(dispatchContext: DispatchContext): DataObject
}