package com.tealium.prism.core.api.pubsub

/**
 * Interface representing an Observer of items emitted by an [Observable].
 *
 * Implementors need to handle both [onNext] for each emitted value and [onComplete] when the
 * upstream source has terminated. No further [onNext] emissions should be delivered after the
 * [onComplete] has been propagated downstream.
 *
 * Any [Disposable] subscriptions held to upstream [Observable]s should be disposed of after [onComplete]
 * has been emitted downstream.
 */
interface Observer<T> {

    /**
     * Called whenever there has been a new item emitted by the upstream [Observable].
     *
     * @param value The updated value
     */
    fun onNext(value: T)

    /**
     * Called when the upstream source has terminated and no further [onNext] calls will be made.
     */
    fun onComplete()
}