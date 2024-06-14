package com.tealium.core.api.barriers

/**
 * The [BarrierRegistry] is responsible for registering and unregistering additional [Barrier]s and
 * [ScopedBarrier]s outside of those provided by the main SDK settings.
 */
interface BarrierRegistry {

    /**
     * Registers an additional [Barrier]
     *
     * @param barrier The [Barrier] to add to the list of barriers.
     */
    fun registerBarrier(barrier: Barrier)

    /**
     * Unregisters the given [barrier] if it's currently in registered.
     *
     * @param barrier The [Barrier] to remove from the list of barriers.
     */
    fun unregisterBarrier(barrier: Barrier)

    /**
     * Registers an additional [ScopedBarrier]
     *
     * @param scopedBarrier The [ScopedBarrier] to add to the list of scoped barriers.
     */
    fun registerScopedBarrier(scopedBarrier: ScopedBarrier)

    /**
     * Unregisters the given [scopedBarrier] if it's currently in registered.
     *
     * @param scopedBarrier The [ScopedBarrier] to remove from the list of scoped barriers.
     */
    fun unregisterScopedBarrier(scopedBarrier: ScopedBarrier)
}