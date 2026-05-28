package com.tealium.prism.core.api.pubsub

import com.tealium.prism.core.internal.pubsub.AnonymousObserver

/**
 * A [Subscribable] object allows you to subscribe an [Observer] to receive updates from a specific
 * source. Each updated value will be provided through the given [Observer.onNext] method.
 *
 * Updates can be immediate, or asynchronous depending on the underlying source.
 *
 * To stop observing updates, use the [Disposable.dispose] method of the returned [Disposable].
 */
interface Subscribable<T> {

    /**
     * Subscribes the given [observer] to receive updates, including an [Observer.onComplete]
     * notification when the upstream source terminates.
     *
     * Use this overload when you need to react to completion — for example when composing operators
     * or when the subscription represents a finite sequence.
     *
     * @param observer The observer to receive values and the completion signal.
     * @return A [Disposable] for cancelling the subscription.
     */
    fun subscribe(observer: Observer<T>): Disposable

    /**
     * Subscribes the given [onNext] [Consumer] to receive updates, where no [Observer.onComplete] is required.
     *
     * Use this overload when you **don't** need to react to completion.
     *
     * @param onNext The [Consumer] to receive emitted values.
     * @return A [Disposable] for cancelling the subscription.
     */
    fun subscribe(onNext: Consumer<T>): Disposable =
        subscribe(onNext, {})

    /**
     * Subscribes the given [onNext] [Consumer] to receive updates, and the given [onComplete] [Runnable]
     * to receive the [Observer.onComplete] signal
     *
     * Use this overload when you need to react to both emitted values and completion.
     *
     * @param onNext The [Consumer] to receive emitted values.
     * @param onComplete The block of code to run upon completion
     * @return A [Disposable] for cancelling the subscription.
     */
    fun subscribe(onNext: Consumer<T>, onComplete: Runnable): Disposable {
        val anonymousObserver = AnonymousObserver(onNext, onComplete)

        val upstream = subscribe(anonymousObserver)
        anonymousObserver.setUpstream(upstream)

        return anonymousObserver
    }
}
