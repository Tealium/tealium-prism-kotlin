package com.tealium.prism.core.api.misc

import com.tealium.prism.core.api.pubsub.Observable

/**
 * A utility providing some basic insight into the number of queued events for each processor.
 */
interface QueueMetrics {

    /**
     * Returns an observable that will receive the current number of events queued for the given
     * [processorId], that are not already in-flight.
     *
     * @param processorId The id of the processor to get the queue size for.
     */
    fun queueSizePendingDispatch(processorId: String): Observable<Int>
}