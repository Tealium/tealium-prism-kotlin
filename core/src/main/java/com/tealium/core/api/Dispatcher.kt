package com.tealium.core.api

/**
 * A [Dispatcher] is a specialized [Module] that is the destination for any [Dispatch]es tracked
 * through the SDK.
 */
interface Dispatcher: Module {
    /**
     * Called when a new [Dispatch] is ready to be processed by this [Dispatcher].
     */
    fun dispatch(dispatches: List<Dispatch>)
}