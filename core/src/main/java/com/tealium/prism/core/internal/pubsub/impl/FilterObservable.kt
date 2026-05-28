package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.subscribeWrapAndLink

/**
 * The [FilterObservable] will only emit values downstream whereby the [predicate] returns true. And
 * conversely, any where the predicate returns false will not be emitted.
 */
class FilterObservable<T>(
    private val source: Observable<T>,
    private val predicate: (T) -> Boolean
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        return source.subscribeWrapAndLink {
            FilterObserver(observer, predicate)
        }
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

        override fun onComplete() = observer.onComplete()
    }
}