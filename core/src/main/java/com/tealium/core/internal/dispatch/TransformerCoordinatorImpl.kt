package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.DispatchScope
import com.tealium.core.api.Transformer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow

class TransformerCoordinatorImpl(
    private val registeredTransformers: Set<Transformer>,
    private val scopedTransformations: StateFlow<Set<ScopedTransformation>>
) : TransformerCoordinator {

    override suspend fun transform(dispatch: Dispatch, dispatchScope: DispatchScope): Dispatch? {
        return apply(
            getTransformations(dispatchScope),
            dispatch,
            dispatchScope
        )
    }

    override suspend fun transform(
        dispatches: List<Dispatch>,
        dispatchScope: DispatchScope
    ): List<Dispatch> = coroutineScope {
        dispatches.map {
            async {
                transform(it, dispatchScope)
            }
        }.awaitAll()
            .filterNotNull()
    }

    private fun getTransformations(scope: DispatchScope): Set<ScopedTransformation> {
        return scopedTransformations.value.filter { it.matchesScope(scope) }.toSet()
    }

    private suspend fun apply(
        transformations: Set<ScopedTransformation>,
        dispatch: Dispatch?,
        scope: DispatchScope
    ): Dispatch? {
        if (transformations.isEmpty()) return dispatch
        if (dispatch == null) return null

        return transformations.fold(dispatch) { acc: Dispatch?, transformation: ScopedTransformation ->
            apply(transformation, acc, scope)
        }
    }

    private suspend fun apply(
        transformation: ScopedTransformation,
        dispatch: Dispatch?,
        scope: DispatchScope
    ): Dispatch? {
        if (dispatch == null) return null

        return registeredTransformers.firstOrNull {
            it.id == transformation.transformerId
        }?.applyTransformation(
            transformation.id,
            dispatch,
            scope,
        )
    }
}