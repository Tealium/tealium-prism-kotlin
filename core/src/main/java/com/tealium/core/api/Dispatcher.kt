package com.tealium.core.api

import kotlinx.coroutines.flow.Flow

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
     * Emissions from the returned flow will remove the [dispatches] from the persistent queue. Typical
     * behaviour would be to return a flow which emits a list of all the dispatches received by this
     * method once they have been processed.
     * If batches have to be split and processed separately, then emissions should only emit the
     * dispatches that have been processed each time.
     *
     * @param dispatches The batch of dispatches to be processed by this [Dispatcher]
     * @return A [Flow] of the processed dispatches
     */
    fun dispatch(dispatches: List<Dispatch>): Flow<List<Dispatch>>
}