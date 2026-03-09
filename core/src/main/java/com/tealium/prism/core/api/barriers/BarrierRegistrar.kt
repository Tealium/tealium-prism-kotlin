package com.tealium.prism.core.api.barriers

/**
 * The [BarrierRegistrar] is responsible for registering and unregistering additional [Barrier]s
 * outside of those provided by the main SDK settings.
 *
 * Note. Barriers registered using the [BarrierRegistrar] will not receive updated settings.
 */
interface BarrierRegistrar {

    /**
     * Registers or updates an additional [Barrier] with the applied [scopes]
     *
     * @param barrier The [Barrier] to add to the list of barriers.
     * @param scopes The set of [BarrierScope]s that this [barrier] applies to.
     */
    fun registerScopedBarrier(barrier: Barrier, scopes: Set<BarrierScope>)

    /**
     * Unregisters the given [barrier] if it's currently registered.
     *
     * @param barrier The [Barrier] to remove from the list of barriers.
     */
    fun unregisterScopedBarrier(barrier: Barrier)
}