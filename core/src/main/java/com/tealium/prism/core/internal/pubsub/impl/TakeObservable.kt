package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.UpstreamLinkable
import com.tealium.prism.core.internal.pubsub.UpstreamLinkableImpl
import com.tealium.prism.core.internal.pubsub.LinkableObserver
import com.tealium.prism.core.internal.pubsub.subscribeAndLink

/**
 * The [TakeObservable] emits only a set number of emissions downstream that it receives from the
 * [source]. Once the count is reached, [Observer.onComplete] is called on the downstream observer
 * and the subscription is disposed.
 */
class TakeObservable<T>(
    private val source: Observable<T>,
    private val count: Int
) : Observable<T> {

    init {
        if (count <= 0)
            throw IllegalArgumentException("count must be a positive integer; but was $count")
    }

    override fun subscribe(observer: Observer<T>): Disposable {
        return source.subscribeAndLink {
            TakeObserver(observer, count)
        }
    }

    class TakeObserver<T>(
        private val downstream: Observer<T>,
        private var remaining: Int,
        disposable: UpstreamLinkable = UpstreamLinkableImpl()
    ) : LinkableObserver<T>, UpstreamLinkable by disposable {

        private var completed = false

        override fun onNext(value: T) {
            if (completed || isDisposed || remaining-- <= 0) return

            val shouldStop = remaining <= 0
            downstream.onNext(value)
            if (shouldStop) {
                onComplete()
            }
        }

        override fun onComplete() {
            if (completed) return
            completed = true

            downstream.onComplete()
            dispose()
        }
    }
}
