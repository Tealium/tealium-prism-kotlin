package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.misc.Callback

/**
 * A [Dispatcher] is a specialized [Module] that is the destination for any [Dispatch]es tracked
 * through the SDK.
 */
interface Dispatcher : Module {

    /**
     * Sets the maximum number of [Dispatch]es that can safely be processed by the [dispatch] implementation.
     * Default is 1
     */
    val dispatchLimit: Int
        get() = 1

    /**
     * Called when a new [Dispatch] is ready to be processed by this [Dispatcher].
     *
     * Calling the [callback] callback will remove the [dispatches] from the persistent queue. Typical
     * behaviour would be to process all events and call the callback with the full list of [dispatches]
     * that were provided.
     *
     * If batches have to be split and processed separately, then multiple callback executions should
     * only pass the dispatches that have been processed each time.
     *
     * @param dispatches The batch of dispatches to be processed by this [Dispatcher]
     * @param callback The callback used to indicate that the dispatches are completed.
     * @return A disposable able to halt the processing of the given dispatches
     */
    fun dispatch(dispatches: List<Dispatch>, callback: Callback<List<Dispatch>>): Disposable
}