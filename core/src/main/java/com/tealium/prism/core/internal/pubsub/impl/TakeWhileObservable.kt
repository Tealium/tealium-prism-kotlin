package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.LinkableObserver
import com.tealium.prism.core.internal.pubsub.UpstreamLinkable
import com.tealium.prism.core.internal.pubsub.UpstreamLinkableImpl
import com.tealium.prism.core.internal.pubsub.subscribeAndLink

/**
 * The [TakeWhileObservable] will emit values that it receives from the [source] until the given
 * [predicate] returns false. Once the [predicate] returns false the subscription is disposed and
 * [Observer.onComplete] is called on the downstream observer.
 */
class TakeWhileObservable<T>(
    private val source: Observable<T>,
    private val predicate: (T) -> Boolean,
    private val inclusive: Boolean = false
) : Observable<T> {
    override fun subscribe(observer: Observer<T>): Disposable {
        return source.subscribeAndLink {
            TakeWhileObserver(observer, predicate, inclusive)
        }
    }

    class TakeWhileObserver<T>(
        private val downstream: Observer<T>,
        private val predicate: (T) -> Boolean,
        private val inclusive: Boolean = false,
        upstream: UpstreamLinkable = UpstreamLinkableImpl()
    ): LinkableObserver<T>, UpstreamLinkable by upstream {

        private var isTerminating = false
        private var completed = false

        override fun onNext(value: T) {
            if (completed || isTerminating || isDisposed) return // avoid reentrancy

            if (predicate.invoke(value)) {
                downstream.onNext(value)
                return
            }

            if (inclusive) {
                isTerminating = true
                downstream.onNext(value)
            }
            onComplete()
        }

        override fun onComplete() {
            if (completed) return
            completed = true

            downstream.onComplete()
            dispose()
        }
    }
}
