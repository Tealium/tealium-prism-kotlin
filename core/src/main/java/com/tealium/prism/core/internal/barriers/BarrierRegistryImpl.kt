package com.tealium.prism.core.internal.barriers

import com.tealium.prism.core.api.barriers.BarrierRegistrar

/**
 * Wrapper implementation of the [BarrierRegistrar] that will delegate all required methods to the
 * provided [BarrierManager]
 */
class BarrierRegistrarImpl(private val barrierManager: BarrierManager) :
    BarrierRegistrar by barrierManager