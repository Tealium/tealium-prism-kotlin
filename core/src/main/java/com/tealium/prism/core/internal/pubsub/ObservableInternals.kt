package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subscribable
import com.tealium.prism.core.api.pubsub.SubscribableState
import com.tealium.prism.core.internal.pubsub.impl.ObservableStateImpl

fun <T> Subscribable<T>.asObservable() : Observable<T> {
    return object : Observable<T>, Subscribable<T> by this { }
}

fun <T> SubscribableState<T>.asObservableState() : ObservableState<T> {
    return ObservableStateImpl(this)
}

/**
 * Utility function to easily publish an updated value based on the existing value.
 *
 * @param block
 *  A block of code for calculating the new value, where the incoming parameter is the
 *  existing value
 */
inline fun <T> StateSubject<T>.update(block: (T) -> T) {
    val old = value
    val new = block(old)
    onNext(new)
}