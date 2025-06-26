package com.tealium.core.internal.dispatch

import com.tealium.core.api.misc.QueueMetrics
import com.tealium.core.api.pubsub.Observable

class QueueMetricsImpl(
    private val queueManager: QueueManager,
) : QueueMetrics {

    override fun queueSizePendingDispatch(processorId: String): Observable<Int> =
        queueManager.queueSizePendingDispatch(processorId)
}