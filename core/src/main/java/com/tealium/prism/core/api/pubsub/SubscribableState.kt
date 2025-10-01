package com.tealium.prism.core.api.pubsub

/**
 * A [SubscribableState] object allows you to subscribe an [Observer] to receive updates from a specific
 * source - as with [Subscribable]. Each updated value will be provided through the given [Observer.onNext] method.
 *
 * The [SubscribableState] also maintains the current value of the property for synchronous access
 *
 * Updates can be immediate, or asynchronous depending on the underlying source.
 *
 * To stop observing updates, use the [Disposable.dispose] method of the returned [Disposable].
 *
 * @see Subscribable
 */
interface SubscribableState<T> : Subscribable<T> {

    /**
     * The current value of the property.
     */
    val value: T
}