package com.tealium.core.internal.dispatch

import com.tealium.core.api.logger.Logger
import com.tealium.core.api.logger.logIfWarnEnabled
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.rules.InvalidMatchException
import com.tealium.core.api.rules.matches
import com.tealium.core.api.tracking.Dispatch
import com.tealium.core.api.transform.DispatchScope
import com.tealium.core.api.transform.TransformationSettings
import com.tealium.core.api.transform.Transformer
import com.tealium.core.internal.logger.LogCategory

class TransformerCoordinatorImpl(
    private val registeredTransformers: ObservableState<List<Transformer>>,
    private val transformations: ObservableState<Set<TransformationSettings>>,
    private val scheduler: Scheduler,
    private val logger: Logger
) : TransformerCoordinator {

    private var additionalTransformations: Set<TransformationSettings> = setOf()

    val allTransformations: Set<TransformationSettings>
        get() = transformations.value + additionalTransformations

    override fun transform(
        dispatch: Dispatch,
        dispatchScope: DispatchScope,
        completion: (Dispatch?) -> Unit
    ) {
        recursiveSerialApply(
            getTransformations(dispatch, dispatchScope),
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

    private fun getTransformations(
        dispatch: Dispatch,
        scope: DispatchScope
    ): Set<TransformationSettings> {
        val transformations = allTransformations.filter { transformation ->
            transformation.matchesScope(scope) && matchesConditions(transformation, dispatch)
        }

        return transformations.toSet()
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

    private fun recursiveSerialApply(
        transformations: Set<TransformationSettings>,
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

        transformer.applyTransformation(
            transformation,
            dispatch,
            scope,
            completion
        )
    }

    override fun registerTransformation(transformation: TransformationSettings) {
        if (additionalTransformations.find {
                transformationIdsMatch(it, transformation)
            } != null) return

        additionalTransformations = additionalTransformations.toMutableSet().apply {
            add(transformation)
        }
    }

    override fun unregisterTransformation(transformation: TransformationSettings) {
        additionalTransformations = additionalTransformations.toMutableSet().apply {
            removeAll { transformationIdsMatch(it, transformation) }
        }
    }

    private fun transformationIdsMatch(
        t1: TransformationSettings,
        t2: TransformationSettings
    ): Boolean =
        t1.id == t2.id && t1.transformerId == t2.transformerId
}