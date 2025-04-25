package com.tealium.core.api.barriers

import com.tealium.core.internal.network.ConnectivityBarrier

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

    // TODO - other BarrierFactory getters.
}