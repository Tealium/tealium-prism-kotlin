package com.tealium.tests.common

import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.tracking.Dispatch
import com.tealium.prism.core.api.transform.DispatchScope
import com.tealium.prism.core.api.transform.TransformationSettings
import com.tealium.prism.core.api.transform.Transformer

data class PendingTransformation(
    val settings: TransformationSettings,
    val dispatch: Dispatch
)

class PendingStep(
    val transformation: PendingTransformation,
    val completion: Callback<Dispatch?>,
    val disposable: Disposable = Disposables.subscription()
) {
    val dispatch: Dispatch
        get() = transformation.dispatch
}

/**
 * A [Transformer] that holds every completion until the test releases it, to simulate
 * transformations that complete asynchronously.
 */
class AsyncTransformer(
    override val id: String,
    override val version: String = "1.0"
) : Transformer {
    val pending = mutableListOf<PendingStep>()

    /** Every step ever applied, including those already completed. */
    val started = mutableListOf<PendingStep>()

    override fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: Callback<Dispatch?>
    ): Disposable {
        val step = PendingStep(PendingTransformation(transformation, dispatch), completion)
        pending.add(step)
        started.add(step)
        return step.disposable
    }

    fun complete(step: PendingStep, result: Dispatch?) {
        pending.remove(step)
        step.completion.onComplete(result)
    }

    /** Completes a step a second time, violating the [Transformer] contract on purpose. */
    fun completeAgain(step: PendingStep, result: Dispatch?) {
        step.completion.onComplete(result)
    }

    fun completeAll() {
        pending.toList().forEach { complete(it, it.dispatch) }
    }
}
