package com.tealium.prism.core.api.pubsub

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.internal.pubsub.AsyncDisposableContainer
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.internal.pubsub.Subscription


/**
 * Contains factory methods for creating common [Disposable] instances for use with the Tealium SDK.
 */
object Disposables {

    /**
     * Creates a [Disposable] which calls the given [onDispose] function when the subscription
     * is disposed.
     *
     * The returned implementation is not considered  to be thread-safe, so interaction is expected
     * to be constrained to an appropriate thread by the user.
     *
     * @param onDispose Optional callback to execute when this [Disposable] is disposed.
     *
     * @return A [Disposable] to use to dispose of the subscription
     */
    @JvmOverloads
    fun subscription(onDispose: (() -> Unit)? = null): Disposable =
        Subscription(onDispose)

    /**
     * Creates a [CompositeDisposable] which can be used to store multiple [Disposable]
     * instances for bulk disposal.
     *
     * Additional [Disposable] instances can be added via [CompositeDisposable.add], or existing
     * instances can be removed via [CompositeDisposable.remove].
     *
     * The returned implementation is not considered to be thread-safe, so interaction is expected
     * to be constrained to an appropriate thread by the user.
     *
     * @return A [Disposable] to use to dispose of multiple subscriptions at once.
     */
    fun composite(): CompositeDisposable =
        DisposableContainer()

    /**
     * Creates a [CompositeDisposable] which can be used to store multiple [Disposable]
     * instances for bulk disposal. All methods are executed using the given [scheduler] to ensure
     * operation is thread-safe.
     *
     * Additional [Disposable] instances can be added via [CompositeDisposable.add], or existing
     * instances can be removed via [CompositeDisposable.remove].
     *
     *
     * @param scheduler The [Scheduler] implementation to use for all operations of this [Disposable]
     *
     * @return A [Disposable] to use to dispose of multiple subscriptions at once, whilst ensuring
     *          that all operations happen on the given [scheduler]
     */
    fun composite(scheduler: Scheduler): CompositeDisposable =
        AsyncDisposableContainer(scheduler)

    /**
     * Returns a [Disposable] implementation that:
     *  - always returns `true` for [Disposable.isDisposed]
     *  - does nothing for [Disposable.dispose]
     *
     *  @return A disposed [Disposable]
     */
    fun disposed() = CompletedDisposable
}