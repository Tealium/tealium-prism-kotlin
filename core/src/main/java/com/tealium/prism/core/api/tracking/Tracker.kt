package com.tealium.prism.core.api.tracking

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.modules.Collector
import com.tealium.prism.core.api.modules.Dispatcher

/**
 * The [Tracker] is responsible for handling any [Dispatch] requests. That entails collecting all
 * available data from the [Collector]s that are available on this [Tealium] instance.
 */
interface Tracker {

    /**
     * Requests for an event to be dispatched to any existing [Dispatcher]s currently in the system.
     *
     * @param dispatch The [Dispatch] to be sent.
     * @param source The source of the [Dispatch]
     */
    fun track(dispatch: Dispatch, source: DispatchContext.Source)

    /**
     * Requests for an event to be dispatched to any existing [Dispatcher]s currently in the system,
     * with a [TrackResultListener] to be notified of when the event has been accepted or dropped.
     *
     * @param dispatch The [Dispatch] to be sent.
     * @param source The source of the [Dispatch]
     * @param onComplete The listener to notify once the [dispatch] has been accepted or dropped.
     */
    fun track(dispatch: Dispatch, source: DispatchContext.Source, onComplete: TrackResultListener?)
}