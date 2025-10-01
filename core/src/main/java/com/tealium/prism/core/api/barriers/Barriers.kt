package com.tealium.prism.core.api.barriers

import com.tealium.prism.core.internal.barriers.BatchingBarrier
import com.tealium.prism.core.internal.network.ConnectivityBarrier

/**
 * Utility object for getting built-in [BarrierFactory] objects when configuring the Tealium instance.
 *
 * Some barriers are added to the system by default, but remain accessible here to allow users to
 * override the "scopes" that they apply to.
 *
 * @see Barrier
 * @see BarrierScope
 */
object Barriers {

    /**
     * Returns the [BarrierFactory] for creating the "ConnectivityBarrier". Use this barrier to only
     * dispatch events when connectivity is required.
     */
    fun connectivity() : BarrierFactory =
        ConnectivityBarrier.Factory()

    /**
     * Returns the [BarrierFactory] for creating the "ConnectivityBarrier". Use this barrier to only
     * dispatch events when connectivity is required.
     *
     * @param defaultScope Set of [BarrierScope]s to use by default in case no other scope was
     * configured in the settings.
     */
    fun connectivity(defaultScope: Set<BarrierScope>) : BarrierFactory =
        ConnectivityBarrier.Factory(defaultScope)


    /**
     * Returns the [BarrierFactory] for creating the "BatchingBarrier". Use this barrier to only
     * dispatch events when a certain number of queued events has been reached for any of the
     * Dispatcher in scope.
     */
    fun batching(): BarrierFactory =
        BatchingBarrier.Factory()

    /**
     * Returns the [BarrierFactory] for creating the "BatchingBarrier". Use this barrier to only
     * dispatch events when a certain number of queued events has been reached for any of the
     * Dispatcher in scope.
     *
     * @param defaultScope Set of [BarrierScope]s to use by default in case no other scope was
     * configured in the settings.
     */
    fun batching(defaultScope: Set<BarrierScope>): BarrierFactory =
        BatchingBarrier.Factory(defaultScope)

    // TODO - other BarrierFactory getters.
}