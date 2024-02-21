package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer

/**
 * The [FilterObservable] will only emit values downstream whereby the [predicate] returns true. And
 * conversely, any where the predicate returns false will not be emitted.
 */
class FilterObservable<T>(
    private val source: Observable<T>,
    private val predicate: (T) -> Boolean
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        val parent = FilterObserver(observer, predicate)

        return source.subscribe(parent)
    }

    class FilterObserver<T>(
        private val observer: Observer<T>,
        private val predicate: (T) -> Boolean
    ) : Observer<T> {
        override fun onNext(value: T) {
            if (predicate.invoke(value)) {
                observer.onNext(value)
            }
        }
    }
}