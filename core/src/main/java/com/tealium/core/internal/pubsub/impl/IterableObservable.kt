package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.internal.pubsub.Subscription

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