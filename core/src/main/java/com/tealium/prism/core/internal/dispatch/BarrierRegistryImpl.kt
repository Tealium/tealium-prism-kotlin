package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.barriers.BarrierRegistry

/**
 * Wrapper implementation of the [BarrierRegistry] that will delegate all required methods to the
 * provided [BarrierManager]
 */
class BarrierRegistryImpl(private val barrierManager: BarrierManager) :
    BarrierRegistry by barrierManager