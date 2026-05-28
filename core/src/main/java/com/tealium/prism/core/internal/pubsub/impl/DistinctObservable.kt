package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.subscribeWrapAndLink

/**
 * The [DistinctObservable] only emits downstream when the previous value is not equal to the latest.
 */
class DistinctObservable<T>(
    private val source: Observable<T>,
    private val equals: (T, T) -> Boolean
): Observable<T> {
    override fun subscribe(observer: Observer<T>): Disposable {
       return source.subscribeWrapAndLink {
           DistinctObserver(observer, equals)
       }
    }

    class DistinctObserver<T>(
        private val observer: Observer<T>,
        private val equals: (T, T) -> Boolean
    ): Observer<T> {
        private var previous: T? = null

        override fun onNext(value: T) {
            val last = previous
            if (last == null || !equals.invoke(last, value)) {
                previous = value
                observer.onNext(value)
            }
        }

        override fun onComplete() = observer.onComplete()
    }
}