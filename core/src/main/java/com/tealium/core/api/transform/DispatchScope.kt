package com.tealium.core.api.transform

/**
 * Defines the positions during event processing that can be extended.
 *
 * @see Transformer
 */
sealed class DispatchScope {

    /**
     * This scope happens after data has been collected by any [Collector] implementations in
     * the system; it is also prior to being stored on disk.
     */
    object AfterCollectors : DispatchScope()

    /**
     * This scope happens when the [Dispatch] is being sent to any given [Dispatcher].
     */
    data class Dispatcher(val dispatcher: String) : DispatchScope()
}