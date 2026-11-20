package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfWarnEnabled
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
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
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.internal.pubsub.update
import java.util.concurrent.atomic.AtomicBoolean

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
    ): Disposable {
        // Resolved once per dispatch, then threaded through the whole apply chain, so that a single
        // dispatch is transformed against a single snapshot of the transformers. The transformations
        // are read at the same moment, so a call held back by a settings pass applies the settings
        // and the transformers of the same pass.
        return onTransformers.take(1)
            .async<Dispatch?> { transformers, onNext ->
                val transformations = scopedTransformations(dispatchScope)
                serialApply(transformers, transformations, dispatch, dispatchScope, onNext::accept)
            }
            .subscribe { completion(it) }
    }

    override fun transform(
        dispatches: List<Dispatch>,
        dispatchScope: DispatchScope,
        completion: (List<Dispatch>) -> Unit
    ): Disposable {
        if (dispatches.isEmpty()) {
            completion(emptyList())
            return Disposables.disposed()
        }

        // Resolved once per batch rather than once per dispatch: every dispatch in the batch is
        // transformed against the same snapshot of the transformers, and the gate on
        // [onTransformers] is only paid a single time. The transformations are read at the same
        // moment, for the same reason as in the single-dispatch overload.
        return onTransformers.take(1)
            .async<List<Dispatch?>> { transformers, onNext ->
                val transformations = scopedTransformations(dispatchScope)
                Tasks.execute(scheduler, dispatches.map { dispatch ->
                    CompletableTask { onComplete: (Dispatch?) -> Unit ->
                        serialApply(transformers, transformations, dispatch, dispatchScope, onComplete)
                    }
                }) { transformed ->
                    onNext.accept(transformed)
                }
            }
            .subscribe { completion(it.filterNotNull()) }
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

    /**
     * Applies the [transformations] to the [dispatch] one after the other, each step starting only
     * once the previous one has completed.
     *
     * @return A [Disposable] for the whole chain. Disposing it disposes the running transformation
     * and stops the chain before its next step; [completion] is then never called. Disposing it after
     * [completion] has been called is harmless, and disposes the steps that have already completed,
     * as [Transformer.applyTransformation] allows.
     */
    private fun serialApply(
        transformers: List<Transformer>,
        transformations: List<TransformationSettings>,
        dispatch: Dispatch?,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ): Disposable {
        val container = DisposableContainer()

        fun applyNext(index: Int, dispatch: Dispatch?) {
            if (container.isDisposed) return

            if (dispatch == null || index >= transformations.size) {
                completion(dispatch)
                return
            }

            val transformation = transformations[index]
            if (!matchesConditions(transformation, dispatch)) {
                // don't apply, but continue iteration.
                applyNext(index + 1, dispatch)
                return
            }

            // Every step's Disposable is added, not only the latest: a synchronous transformer
            // completes inside `apply`, so the next step has already added its own by then.
            container.add(apply(transformers, transformation, dispatch, scope) {
                applyNext(index + 1, it)
            })
        }

        applyNext(0, dispatch)
        return container
    }


    /**
     * Applies the [transformation] with the [Transformer] that implements it, taken from the
     * [transformers] resolved for this dispatch or batch.
     *
     * When no transformer implements it, the dispatch is reported untransformed rather than dropped:
     * a transformation nobody can apply is not a decision to discard the event.
     *
     * The step's [completion] takes effect at most once: [Transformer.applyTransformation] requires
     * exactly one call, but a transformer that calls it again would re-run the rest of the chain, so
     * later calls are logged and ignored. It may be called from any thread: the rest of the chain is
     * resumed on the tealium scheduler.
     *
     * @return The [Disposable] returned by the [Transformer], which cancels the running
     * transformation; an already disposed one when no transformer implements it.
     */
    private fun apply(
        transformers: List<Transformer>,
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ): Disposable {
        val transformer = transformers.firstOrNull {
            it.id == transformation.transformerId
        }
        if (transformer == null) {
            completion(dispatch)
            return Disposables.disposed()
        }

        val completed = AtomicBoolean(false)
        return transformer.applyTransformation(transformation, dispatch, scope) { transformed ->
            if (completed.compareAndSet(false, true)) {
                // Resumed on the Tealium scheduler, so the rest of the chain, the coordinator's
                // state and the callers never run on a transformer's thread. Inline when the
                // transformer completes on the Tealium thread already.
                scheduler.execute { completion(transformed) }
            } else {
                logger.logIfWarnEnabled(LogCategory.TRANSFORMATIONS) {
                    "Transformer(${transformer.id}) completed Transformation(${transformation.id}) more than once; ignoring."
                }
            }
        }
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
