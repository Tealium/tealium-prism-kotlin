package com.tealium.core.internal.dispatch

import com.tealium.core.api.Dispatch
import com.tealium.core.api.DispatchScope
import com.tealium.core.api.Transformer
import com.tealium.core.internal.observables.StateSubject
import java.util.concurrent.ExecutorService

class TransformerCoordinatorImpl(
    private val registeredTransformers: Set<Transformer>,
    private val scopedTransformations: StateSubject<Set<ScopedTransformation>>,
    private val executorService: ExecutorService
) : TransformerCoordinator {

    override fun transform(
        dispatch: Dispatch,
        dispatchScope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        recursiveSerialApply(
            getTransformations(dispatchScope),
            dispatch,
            dispatchScope,
            completion
        )
    }

    override fun transform(
        dispatches: List<Dispatch>,
        dispatchScope: DispatchScope,
        completion: (List<Dispatch>) -> Unit
    ) {
        Tasks.execute(executorService, dispatches.map { dispatch ->
            CompletableTask { onComplete: (Dispatch?) -> Unit ->
                transform(dispatch, dispatchScope) { transformed ->
                    onComplete(transformed)
                }
            }
        }) {
            completion(it.filterNotNull())
        }
    }

    private fun getTransformations(scope: DispatchScope): Set<ScopedTransformation> {
        return scopedTransformations.value.filter { it.matchesScope(scope) }.toSet()
    }

    private fun recursiveSerialApply(
        transformations: Set<ScopedTransformation>,
        dispatch: Dispatch?,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        if (transformations.isEmpty() || dispatch == null) {
            completion(dispatch)
            return
        }

        val firstTransformation = transformations.first()
        val remainingTransformations =
            transformations.toMutableSet().apply { remove(firstTransformation) }

        apply(firstTransformation, dispatch, scope) { transformedDispatch ->
            recursiveSerialApply(remainingTransformations, transformedDispatch, scope, completion)
        }

    }

    private fun apply(
        transformation: ScopedTransformation,
        dispatch: Dispatch?,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        if (dispatch == null) {
            completion(null)
            return
        }

        registeredTransformers.firstOrNull {
            it.id == transformation.transformerId
        }?.applyTransformation(
            transformation.id,
            dispatch,
            scope,
            completion
        )
    }
}