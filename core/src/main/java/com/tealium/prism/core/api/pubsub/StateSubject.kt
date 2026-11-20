package com.tealium.prism.core.api.pubsub

/**
 * A [StateSubject] is a specialized [Subject] that also maintains the latest emission
 * as state, retrievable via the [value] getter.
 *
 * New values can be emitted to subscribers via the [Observer.onNext] method.
 *
 * A new subscriber first receives the current [value]. Values emitted into this subject while a
 * new subscriber receives the current [value] reach it afterwards. Subscribing to a completed [StateSubject]
 * delivers the current [value], then the completion; after completion, new values are ignored.
 */
interface StateSubject<T>: Subject<T>, ObservableState<T> {

    /**
     * Returns the [StateSubject] as an [ObservableState] that therefore cannot be published to.
     */
    fun asObservableState() : ObservableState<T>
}