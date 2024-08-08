package com.tealium.core.internal.dispatch

import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.ScopedTransformation
import com.tealium.core.api.transform.TransformerRegistry
import com.tealium.core.api.transform.Transformer

/**
 * The [TransformerCoordinator] is responsible for:
 *  - maintaining a list of enabled [Transformer] implementations
 *  - maintaining a list of [ScopedTransformation]s
 *  - delegating the application of any [ScopedTransformation]s to the appropriate [Transformer]
 */
interface TransformerCoordinator : TransformerRegistry {

    /**
     * Transforms the [dispatch] using any [Transformer]s that are scoped to the given [dispatchScope].
     *
     * @param dispatch The [Dispatch] to be transformed
     * @param dispatchScope The [DispatchScope] used to look up applicable transformations
     * @param completion The block of code to execute with the updated dispatch after transformation,
     * or null if the [dispatch] is to be deleted.
     */
    fun transform(dispatch: Dispatch, dispatchScope: DispatchScope, completion: (Dispatch?) -> Unit)

    /**
     * Transforms all [dispatches] using any [Transformer]s that are scoped to the given [dispatchScope].
     *
     * @param dispatches The list of [Dispatch]es to be transformed
     * @param dispatchScope The [DispatchScope] used to look up applicable transformations
     * @param completion The block of code to execute with the list of updated dispatches after
     * transformation. Entries will be removed if any of the applicable transformations have
     * returned null, indicating it is safe to remove that [Dispatch] from further processing
     */
    fun transform(dispatches: List<Dispatch>, dispatchScope: DispatchScope, completion: (List<Dispatch>) -> Unit)
}

