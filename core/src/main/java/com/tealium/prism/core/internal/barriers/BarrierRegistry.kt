package com.tealium.prism.core.internal.barriers

import androidx.annotation.VisibleForTesting
import com.tealium.prism.core.api.Modules
import com.tealium.prism.core.api.barriers.BarrierFactory
import com.tealium.prism.core.internal.network.ConnectivityBarrier
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Internal singleton available to hold all default barriers and add any additional ones that may be registered at init time.
 */
object BarrierRegistry {
    /**
     * The barriers created within the Core library.
     */
    private val _defaultBarriers: List<BarrierFactory> = listOf(
        ConnectivityBarrier.Factory(),
        BatchingBarrier.Factory(defaultScopes = emptySet())
    )

    /**
     * The optional barriers that need to be installed alongside the Core library.
     */
    private val additionalBarriers = CopyOnWriteArrayList<BarrierFactory>()

    /**
     * A list of default barriers that will be added to the provided barriers.
     */
    val defaultBarriers: List<BarrierFactory>
        get() = _defaultBarriers + additionalBarriers

    /**
     * Add a default barrier to the registry.
     */
    fun addDefaultBarrier(barrier: BarrierFactory) {
        additionalBarriers.add(barrier)
    }

    /**
     * Add default barriers to the registry.
     */
    fun addDefaultBarriers(barriers: List<BarrierFactory>) {
        additionalBarriers.addAll(barriers)
    }

    /**
     * Clear all additional barriers.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun clearAdditionalBarriers() {
        additionalBarriers.clear()
    }
}
