package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.subscribeWrapAndLink

/**
 * The [MapObservable] applies the given [transform] to each emission from the give [source] before
 * propagating it downstream.
 */
class MapObservable<T, L>(
    private val source: Observable<T>,
    private val transform: (T) -> L
) : Observable<L> {
    override fun subscribe(observer: Observer<L>): Disposable {
        return source.subscribeWrapAndLink {
            MapObserver(observer, transform)
        }
    }

    class MapObserver<T, L>(
        private val downstream: Observer<L>,
        private val transform: (T) -> L,
    ) : Observer<T> {

        override fun onNext(value: T) {
            val transformed: L = transform(value)
            downstream.onNext(transformed)
        }

        override fun onComplete() {
            downstream.onComplete()
        }
    }
}