package com.tealium.core.api.pubsub

/**
 * A [Subject] is an [Observable] that can be used to publish new values to any
 * subscribers, where an [Observable] cannot.
 *
 * New values can be emitted to subscribers via the [Observer.onNext] method.
 */
interface Subject<T>: Observable<T>, Observer<T> {

    /**
     * Returns the number of subscribers to this [Subject]
     */
    val count : Int

    /**
     * Returns this [Subject] as an [Observable] to restrict publishing.
     */
    fun asObservable() : Observable<T>
}