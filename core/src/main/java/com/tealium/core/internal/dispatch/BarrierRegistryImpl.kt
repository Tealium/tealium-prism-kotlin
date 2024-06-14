package com.tealium.core.internal.dispatch

import com.tealium.core.api.barriers.BarrierRegistry

/**
 * Wrapper implementation of the [BarrierRegistry] that will delegate all required methods to the
 * provided [BarrierCoordinator]
 */
class BarrierRegistryImpl(private val barrierCoordinator: BarrierCoordinator) :
    BarrierRegistry by barrierCoordinator