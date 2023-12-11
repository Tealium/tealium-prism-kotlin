package com.tealium.core.internal.dispatch

import com.tealium.core.api.DispatchScope
import com.tealium.core.api.Transformer


/**
 * Describes the relationship between this [ScopedTransformation] and [Transformer] that it belongs
 * to, as well as any [TransformationScope]s that it may be applicable for.
 */
class ScopedTransformation(
    /**
     * The unique identifier for this transformation.
     */
    val id: String,

    /**
     * The identifier of the [Transformer] to use to apply the transformation identified by [id].
     */
    val transformerId: String,

    /**
     * The set of [TransformationScope]s that this transformation is applicable for.
     */
    val scope: Set<TransformationScope>
) {

    /**
     * A [ScopedTransformation] may match multiple [TransformationScope]s. If the given [dispatchScope]
     * matches the scope of any of the [TransformationScope]s listed in [scope] then it will
     * return true, else false.
     */
    fun matchesScope(dispatchScope: DispatchScope): Boolean {
        return scope.firstOrNull { transformationScope ->
            transformationScope.matches(dispatchScope)
        } != null
    }
}