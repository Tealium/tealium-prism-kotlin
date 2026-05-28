package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfWarnEnabled
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.rules.InvalidMatchException
import com.tealium.prism.core.api.rules.matches
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer
import com.tealium.prism.core.internal.logger.LogCategory
import com.tealium.prism.core.internal.pubsub.update

class TransformerCoordinatorImpl(
    private val registeredTransformers: ObservableState<List<Transformer>>,
    transformations: ObservableState<List<TransformationSettings>>,
    private val scheduler: Scheduler,
    private val logger: Logger
) : TransformerCoordinator {

    private var additionalTransformations: StateSubject<List<TransformationSettings>> =
        Observables.stateSubject(emptyList())

    var sortedTransformations: List<TransformationSettings> = emptyList()
        private set

    init {
        transformations.combine(additionalTransformations) { configured, additional ->
            (configured + additional)
                .sortedBy(TransformationSettings::order)
        }.subscribe { sortedTransformations ->
            this.sortedTransformations = sortedTransformations
        }
    }

    override fun transform(
        dispatch: Dispatch,
        dispatchScope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        val transformations = sortedTransformations.filter { transformation ->
            transformation.scope.matches(dispatchScope)
        }

        serialApply(transformations, dispatch, dispatchScope, completion)
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
                // TODO: Individual transforms are not yet cancellable.
                Disposables.disposed()
            }
        }) {
            completion(it.filterNotNull())
        }
    }

    private fun matchesConditions(
        transformation: TransformationSettings,
        dispatch: Dispatch
    ): Boolean {
        if (transformation.conditions == null) return true

        return try {
            transformation.conditions.matches(dispatch.payload())
        } catch (ex: InvalidMatchException) {
            logger.logIfWarnEnabled(LogCategory.TRANSFORMATIONS) {
                "Transformation conditions evaluation failed for Dispatch(${dispatch.logDescription()}) and Transformation(${transformation.transformerId}-${transformation.id}). Cause: ${ex.message}"
            }
            false
        }
    }

    private fun serialApply(
        transformations: List<TransformationSettings>,
        dispatch: Dispatch?,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) = recursiveSerialApply(transformations, 0, dispatch, scope, completion)

    private fun recursiveSerialApply(
        transformations: List<TransformationSettings>,
        index: Int,
        dispatch: Dispatch?,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        if (dispatch == null || index >= transformations.size) {
            completion(dispatch)
            return
        }

        val nextTransformation = transformations.elementAt(index)
        val nextIndex = index + 1

        if (!matchesConditions(nextTransformation, dispatch)) {
            // don't apply, but continue iteration.
            recursiveSerialApply(transformations, nextIndex, dispatch, scope, completion)
            return
        }

        apply(nextTransformation, dispatch, scope) { transformedDispatch ->
            recursiveSerialApply(transformations, nextIndex, transformedDispatch, scope, completion)
        }
    }

    private fun apply(
        transformation: TransformationSettings,
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

        transformer.applyTransformation(transformation, dispatch, scope, completion)
    }

    override fun registerTransformation(transformation: TransformationSettings) {
        if (additionalTransformations.value.find {
                transformationIdsMatch(it, transformation)
            } != null) return

        additionalTransformations.update { current -> current + transformation }
    }

    override fun unregisterTransformation(transformation: TransformationSettings) {
        additionalTransformations.update { current ->
            current.filterNot { transformationIdsMatch(it, transformation) }
        }
    }

    private fun transformationIdsMatch(
        t1: TransformationSettings,
        t2: TransformationSettings
    ): Boolean =
        t1.id == t2.id && t1.transformerId == t2.transformerId
}