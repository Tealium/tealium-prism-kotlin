package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.Subscription

/**
 * The [IterableObservable] will immediately emit all values specified in [elements] to the observer
 * during subscription.
 */
class IterableObservable<T>(
    private val elements: Iterable<T>,
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        elements.forEach {
            observer.onNext(it)
        }
        return Subscription()
    }
}