package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfWarnEnabled
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Observable
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
    /**
     * The registered [Transformer]s, emitting only once the modules of a settings pass are all created.
     *
     * An unresolved [TransformationSettings.transformerId] is not an error - the transformation is
     * passed over and the dispatch goes on untransformed - so reading a premature empty list would
     * silently skip a transformation whose module simply had not been created yet.
     */
    private val onTransformers: Observable<List<Transformer>>,
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
        // Resolved once per dispatch, then threaded through the whole apply chain, so that a single
        // dispatch is transformed against a single snapshot of the transformers. The transformations
        // are read at the same moment, so a call held back by a settings pass applies the settings
        // and the transformers of the same pass.
        onTransformers.subscribeOnce { transformers ->
            val transformations = scopedTransformations(dispatchScope)
            serialApply(transformers, transformations, dispatch, dispatchScope, completion)
        }
    }

    override fun transform(
        dispatches: List<Dispatch>,
        dispatchScope: DispatchScope,
        completion: (List<Dispatch>) -> Unit
    ) {
        if (dispatches.isEmpty()) {
            completion(emptyList())
            return
        }

        // Resolved once per batch rather than once per dispatch: every dispatch in the batch is
        // transformed against the same snapshot of the transformers, and the gate on
        // [onTransformers] is only paid a single time. The transformations are read at the same
        // moment, for the same reason as in the single-dispatch overload.
        onTransformers.subscribeOnce { transformers ->
            val transformations = scopedTransformations(dispatchScope)
            Tasks.execute(scheduler, dispatches.map { dispatch ->
                CompletableTask { onComplete: (Dispatch?) -> Unit ->
                    serialApply(transformers, transformations, dispatch, dispatchScope, onComplete)
                    // TODO: Individual transforms are not yet cancellable.
                    Disposables.disposed()
                }
            }) {
                completion(it.filterNotNull())
            }
        }
    }

    private fun scopedTransformations(dispatchScope: DispatchScope): List<TransformationSettings> =
        sortedTransformations.filter { transformation ->
            transformation.scope.matches(dispatchScope)
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
        transformers: List<Transformer>,
        transformations: List<TransformationSettings>,
        dispatch: Dispatch?,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) = recursiveSerialApply(transformers, transformations, 0, dispatch, scope, completion)

    private fun recursiveSerialApply(
        transformers: List<Transformer>,
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
            recursiveSerialApply(
                transformers, transformations, nextIndex, dispatch, scope, completion
            )
            return
        }

        apply(transformers, nextTransformation, dispatch, scope) { transformedDispatch ->
            recursiveSerialApply(
                transformers, transformations, nextIndex, transformedDispatch, scope, completion
            )
        }
    }

    /**
     * Applies the [transformation] with the [Transformer] that implements it, taken from the
     * [transformers] resolved for this dispatch or batch.
     *
     * When no transformer implements it, the dispatch is reported untransformed rather than dropped:
     * a transformation nobody can apply is not a decision to discard the event.
     */
    private fun apply(
        transformers: List<Transformer>,
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        val transformer = transformers.firstOrNull {
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