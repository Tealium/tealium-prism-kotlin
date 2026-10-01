package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.subscribeWrapAndLink

/**
 * The [DistinctObservable] only emits downstream when the previous value is not equal to the latest.
 *
 * The first value is always emitted, `null` included. Each value is compared to the value received
 * immediately before it, whether or not that one was emitted.
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
        // wrapped so that a `null` value can be told apart from no value received yet
        private var previous: PendingEmission<T>? = null

        override fun onNext(value: T) {
            val last = previous
            // stored for every value, including suppressed ones, so each value is compared to
            // the one received immediately before it
            previous = PendingEmission(value)
            if (last == null || !equals.invoke(last.value, value)) {
                observer.onNext(value)
            }
        }

        override fun onComplete() = observer.onComplete()
    }
}