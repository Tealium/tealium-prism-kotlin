package com.tealium.prism.core.api.pubsub

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
     * Subscribes the given [observer] to receive updates
     *
     * @param observer The object to receive the updated values
     * @return A Disposable implementation for cancelling the subscription
     */
    fun subscribe(observer: Observer<T>): Disposable
}