package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.ObservableState
import com.tealium.prism.core.api.pubsub.Subscribable
import com.tealium.prism.core.api.pubsub.SubscribableState
import com.tealium.prism.core.internal.pubsub.impl.ObservableStateImpl

fun <T> Subscribable<T>.asObservable() : Observable<T> {
    return object : Observable<T>, Subscribable<T> by this { }
}

fun <T> SubscribableState<T>.asObservableState() : ObservableState<T> {
    return ObservableStateImpl(this)
}
