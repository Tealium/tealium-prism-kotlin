package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.pubsub.ObservableState

/**
 * Synchronous access to the visitor ID.
 *
 * Available through [ModuleManager] while visitor ID handling is enabled.
 */
interface VisitorIdProvider : Module {
    /** The current visitor ID, updated on every change. */
    val visitorId: ObservableState<String>
}