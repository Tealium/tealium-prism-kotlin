package com.tealium.prism.core.api.tracking

/**
 * This listener is used to be notified of a change in processing status for a particular [Dispatch]
 */
fun interface TrackResultListener {

    /**
     * This method will be called with the processing status of the [Dispatch]
     *
     * @param status The processing status of the relevant dispatch
     * */
    fun onTrackResultReady(status: TrackResult)
}
