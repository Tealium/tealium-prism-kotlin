package com.tealium.prism.core.api.settings.barriers

import com.tealium.prism.core.internal.barriers.BatchingBarrier

/**
 * A builder used to configure the [BatchingBarrier] settings.
 */
class BatchingBarrierSettingsBuilder : BarrierSettingsBuilder<BatchingBarrierSettingsBuilder>() {
    /**
     * Set the batch size for dispatches.
     * When this number of dispatches is reached, the batch will be released.
     * 
     * @param batchSize The number of dispatches in a batch. Default value of 1 will be used if argument is negative or zero.
     * @return The builder instance for method chaining.
     */
    fun setBatchSize(batchSize: Int) = apply {
        configurationBuilder.put(BatchingBarrier.KEY_BATCH_SIZE, batchSize)
    }
}
