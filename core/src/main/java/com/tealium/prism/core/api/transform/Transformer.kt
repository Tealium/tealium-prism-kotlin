package com.tealium.prism.core.api.transform

import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.modules.Module
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.tracking.Dispatch

/**
 * A [Transformer] has the ability to mutate a [Dispatch] at certain points of the [Dispatch]
 * lifecycle as defined by [DispatchScope].
 */
interface Transformer: Module {

    /**
     * Applies the specified [transformation] to the given [Dispatch] for the [DispatchScope] in
     * which this transformation is called.
     *
     * Threading: this method is called on the Tealium thread, and the caller disposes the returned
     * [Disposable] on the Tealium thread. [completion] must be called on the Tealium thread (see
     * [TealiumContext.schedulers][com.tealium.prism.core.api.modules.TealiumContext.schedulers]);
     * it may be called synchronously, before this method returns.
     *
     * Completion: call [completion] exactly once, unless the caller has disposed the returned
     * [Disposable]. Pass the transformed or unchanged [dispatch] for it to proceed, or `null` to drop
     * it and stop any further processing.
     *
     * Cancellation: the caller may dispose the returned [Disposable] to cancel the transformation.
     * After that, calling [completion] is not required, and the caller ignores any call. Disposing
     * after [completion] has been called must have no effect. Implementations with nothing to cancel,
     * including synchronous ones, return [Disposables.disposed].
     *
     * @param transformation The transformation to apply
     * @param dispatch The [Dispatch] that is being transformed.
     * @param scope The [DispatchScope] that identifies when this transformation is happening.
     * @param completion The [Callback] to call, via [Callback.onComplete], with the result, as
     * described above.
     * @return A [Disposable] that cancels in-progress work, or [Disposables.disposed] if there is
     * nothing to cancel.
     */
    fun applyTransformation(
        transformation: TransformationSettings,
        dispatch: Dispatch,
        scope: DispatchScope,
        completion: Callback<Dispatch?>
    ): Disposable
}
