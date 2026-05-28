package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.CompletedDisposable

/**
 * The [IterableObservable] will immediately emit all values specified in [elements] to the observer
 * during subscription, followed by [Observer.onComplete].
 */
class IterableObservable<T>(
    private val elements: Iterable<T>,
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        elements.forEach {
            observer.onNext(it)
        }
        observer.onComplete()
        return CompletedDisposable
    }
}
