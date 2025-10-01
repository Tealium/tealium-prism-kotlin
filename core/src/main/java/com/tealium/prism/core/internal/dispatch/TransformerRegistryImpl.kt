package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.transform.TransformerRegistry

/**
 * [TransformerRegistry] wrapper that delegates its methods to the actual [TransformerCoordinator]
 */
class TransformerRegistryImpl(private val transformerCoordinator: TransformerCoordinator) :
    TransformerRegistry by transformerCoordinator