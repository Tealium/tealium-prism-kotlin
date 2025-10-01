package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.prism.core.api.pubsub.Single
import com.tealium.prism.core.api.tracking.TrackResult

/**
 * The [Trace] is responsible for handling Tealium trace registration.
 *
 * Joining a trace will add the trace id to each event for filtering server side. Users can leave
 * the trace when finished.
 */
interface Trace {

    /**
     * Attempts to kill the visitor session for the current trace.
     *
     * The Trace will remain active until [leave] is called.
     *
     * @return An async result for optional error handling
     */
    fun killVisitorSession() : Single<TealiumResult<TrackResult>>

    /**
     * Joins a Trace for the given [id]. The trace id will be added to all future
     * events that are tracked until either [leave] is called, or the current session expires.
     *
     * @return An async result for optional error handling
     */
    fun join(id: String) : Single<TealiumResult<Unit>>

    /**
     * Leaves the current trace if one is has been joined.
     *
     * @return An async result for optional error handling
     */
    fun leave() : Single<TealiumResult<Unit>>
}


