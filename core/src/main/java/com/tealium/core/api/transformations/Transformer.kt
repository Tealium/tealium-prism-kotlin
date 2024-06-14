package com.tealium.core.api.transformations

import com.tealium.core.api.Dispatch

/**
 * A [Transformer] has the ability to mutate a [Dispatch] at certain points of the [Dispatch]
 * lifecycle as defined by [DispatchScope].
 */
interface Transformer {

    /**
     * The unique identifier for this [Transformer].
     */
    val id: String

    /**
     * Transforms the given [Dispatch] using the transformation identified by [transformationId]
     *
     * @param transformationId The id of the transformation to apply
     * @param dispatch The [Dispatch] that is being transformed.
     * @param scope The [DispatchScope] that identifies when this transformation is happening.
     * @param completion Block to execute with the mutated, or non-mutated [dispatch] if it should
     * proceed through the system. Return null to this completion to delete this [dispatch] and stop
     * any further processing.
     */
    fun applyTransformation(
        transformationId: String,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    )
}
