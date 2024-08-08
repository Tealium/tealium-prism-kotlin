package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer

/**
 * The [StartWithObservable] will emit the given [initial] items before subscribing to the underlying
 * observable.
 */
class StartWithObservable<T>(
    private val source: Observable<T>,
    private val initial: Iterable<T>
) : Observable<T> {
    override fun subscribe(observer: Observer<T>): Disposable {
        for (value in initial) {
            observer.onNext(value)
        }
        return source.subscribe(observer)
    }
}