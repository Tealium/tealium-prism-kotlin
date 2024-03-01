package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer

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