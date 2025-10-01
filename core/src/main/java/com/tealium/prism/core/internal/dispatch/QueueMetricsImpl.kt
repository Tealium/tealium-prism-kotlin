package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.misc.QueueMetrics
import com.tealium.prism.core.api.pubsub.Observable

class QueueMetricsImpl(
    private val queueManager: QueueManager,
) : QueueMetrics {

    override fun queueSizePendingDispatch(processorId: String): Observable<Int> =
        queueManager.queueSizePendingDispatch(processorId)
}