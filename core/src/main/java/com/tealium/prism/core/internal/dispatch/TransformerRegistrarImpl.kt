package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.transform.TransformerRegistrar

/**
 * [TransformerRegistrar] wrapper that delegates its methods to the actual [TransformerCoordinator]
 */
class TransformerRegistrarImpl(private val transformerCoordinator: TransformerCoordinator) :
    TransformerRegistrar by transformerCoordinator
