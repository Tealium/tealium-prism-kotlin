package com.tealium.prism.core.internal.barriers

import com.tealium.prism.core.api.barriers.BarrierFactory
import com.tealium.prism.core.api.barriers.BarrierScope
import com.tealium.prism.core.api.barriers.BarrierState
import com.tealium.prism.core.api.barriers.ConfigurableBarrier
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.misc.QueueMetrics
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject

class BatchingBarrier(
    private val queueMetrics: QueueMetrics,
    private val dispatchers: ObservableState<List<Dispatcher>>,
    initialBatchSize: Int?
) : ConfigurableBarrier {

    private val _batchSize: StateSubject<Int?> =
        Observables.stateSubject(initialBatchSize?.coerceAtLeast(1))

    val batchSize: Int?
        get() = _batchSize.value

    constructor(
        context: TealiumContext,
        configuration: DataObject
    ) : this(
        context.queueMetrics,
        context.moduleManager.modules
            .mapState { it.filterIsInstance<Dispatcher>() },
        configuration.batchSize,
    )

    override fun onState(dispatcherId: String): Observable<BarrierState> {
        val queueSize = queueMetrics.queueSizePendingDispatch(dispatcherId)

        return batchSizeReached(queueSize, dispatcherId)
            .map { shouldOpen ->
                if (shouldOpen) BarrierState.Open else BarrierState.Closed
            }.distinct()
    }

    private fun batchSizeReached(
        queueSize: Observable<Int>,
        dispatcherId: String
    ): Observable<Boolean> =
        queueSize
            .combine(dispatcherBatchSize(dispatcherId)) { queueSize, batchSize ->
                queueSize >= batchSize
            }.distinct()

    private fun dispatcherBatchSize(dispatcherId: String): Observable<Int> =
        _batchSize.combine(dispatchers) { configuredBatchSize, dispatchers ->
            val dispatchLimit = dispatchers.find { it.id == dispatcherId }
                ?.dispatchLimit
                ?.coerceAtLeast(1)
            if (dispatchLimit == null) return@combine DEFAULT_BATCH_SIZE

            configuredBatchSize?.coerceIn(1, dispatchLimit)
                ?: dispatchLimit
        }.distinct()

    override fun updateConfiguration(configuration: DataObject) {
        _batchSize.onNext(configuration.batchSize)
    }

    override val id: String
        get() = BARRIER_ID

    companion object {
        const val BARRIER_ID = "BatchingBarrier"
        const val KEY_BATCH_SIZE = "batch_size"
        const val DEFAULT_BATCH_SIZE = 1

        private inline val DataObject.batchSize: Int?
            get() = getInt(KEY_BATCH_SIZE)?.coerceAtLeast(1)
    }

    class Factory(
        private val scopes: Set<BarrierScope>? = null
    ) : BarrierFactory {

        override val id: String
            get() = BARRIER_ID

        override fun defaultScope(): Set<BarrierScope> =
            scopes ?: super.defaultScope()

        override fun create(
            context: TealiumContext,
            configuration: DataObject
        ): ConfigurableBarrier {
            return BatchingBarrier(context, configuration)
        }
    }
}
