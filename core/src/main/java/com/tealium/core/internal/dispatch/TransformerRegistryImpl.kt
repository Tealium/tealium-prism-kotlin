package com.tealium.core.internal.dispatch

import com.tealium.core.api.transformations.TransformerRegistry

/**
 * [TransformerRegistry] wrapper that delegates its methods to the actual [TransformerCoordinator]
 */
class TransformerRegistryImpl(private val transformerCoordinator: TransformerCoordinator) :
    TransformerRegistry by transformerCoordinator