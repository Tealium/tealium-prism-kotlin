package com.tealium.core.api.pubsub

/**
 * Functional interface representing an Observer of items to
 * be emitted by an [Observable].
 */
fun interface Observer<T> {

    /**
     * Called whenever there has been a new item emitted
     * by the upstream [Observable].
     *
     * @param value The updated value
     */
    fun onNext(value: T)
}