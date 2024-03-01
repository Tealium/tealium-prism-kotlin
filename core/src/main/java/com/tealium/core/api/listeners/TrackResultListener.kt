package com.tealium.core.api.listeners

import com.tealium.core.api.Dispatch
import com.tealium.core.api.TrackResult

/**
 * This listener is used to be notified of a change in processing status for a particular [Dispatch]
 */
fun interface TrackResultListener {

    /**
     * This method will be called with the processing status of the [Dispatch]
     *
     * @param dispatch The [Dispatch] that this update relates to.
     * @param status The processing status of the given [dispatch]
     * */
    fun onTrackResultReady(dispatch: Dispatch, status: TrackResult)
}
