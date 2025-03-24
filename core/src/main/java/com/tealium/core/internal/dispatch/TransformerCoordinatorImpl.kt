package com.tealium.core.internal.dispatch

import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.ScopedTransformation
import com.tealium.core.api.transform.Transformer

class TransformerCoordinatorImpl(
    private val registeredTransformers: ObservableState<List<Transformer>>,
    private val scopedTransformations: ObservableState<Set<ScopedTransformation>>,
    private val scheduler: Scheduler
) : TransformerCoordinator {

    private var additionalTransformations: Set<ScopedTransformation> = setOf()

    private val allTransformations: Set<ScopedTransformation>
        get() = scopedTransformations.value + additionalTransformations

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
        Tasks.execute(scheduler, dispatches.map { dispatch ->
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
        return allTransformations.filter { it.matchesScope(scope) }.toSet()
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
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        val transformer = registeredTransformers.value.firstOrNull {
            it.id == transformation.transformerId
        }
        if (transformer == null) {
            completion(dispatch)
            return
        }

        transformer.applyTransformation(
            transformation.id,
            dispatch,
            scope,
            completion
        )
    }

    override fun registerScopedTransformation(transformation: ScopedTransformation) {
        additionalTransformations = additionalTransformations.toMutableSet().apply {
            add(transformation)
        }
    }

    override fun unregisterScopedTransformation(transformation: ScopedTransformation) {
        additionalTransformations = additionalTransformations.toMutableSet().apply {
            remove(transformation)
        }
    }
}