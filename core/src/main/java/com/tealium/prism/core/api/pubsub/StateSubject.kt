package com.tealium.prism.core.api.pubsub

/**
 * A [StateSubject] is a specialized [Subject] that also maintains the latest emission
 * as state, retrievable via the [value] getter.
 *
 * New values can be emitted to subscribers via the [Observer.onNext] method.
 */
interface StateSubject<T>: Subject<T>, ObservableState<T> {

    /**
     * Returns the [StateSubject] as an [ObservableState] that therefore cannot be published to.
     */
    fun asObservableState() : ObservableState<T>
}