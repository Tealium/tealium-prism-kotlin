package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.tracking.Dispatch

/**
 * A [Transformer] has the ability to mutate a [Dispatch] at certain points of the [Dispatch]
 * lifecycle as defined by [DispatchScope].
 */
interface Transformer: Module {

    /**
     * Transforms the given [Dispatch] using the transformation identified by [transformation]
     *
     * @param transformation The transformation to apply
     * @param dispatch The [Dispatch] that is being transformed.
     * @param scope The [DispatchScope] that identifies when this transformation is happening.
     * @param completion Block to execute with the mutated, or non-mutated [dispatch] if it should
     * proceed through the system. Return null to this completion to delete this [dispatch] and stop
     * any further processing.
     */
    fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    )
}
