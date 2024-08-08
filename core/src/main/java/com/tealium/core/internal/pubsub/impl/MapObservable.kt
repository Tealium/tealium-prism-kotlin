package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer

/**
 * The [MapObservable] applies the given [transform] to each emission from the give [source] before
 * propagating it downstream.
 */
class MapObservable<T, L>(
    private val source: Observable<T>,
    private val transform: (T) -> L
) : Observable<L> {
    override fun subscribe(observer: Observer<L>): Disposable {
        val parent = MapObserver(observer, transform)

        return source.subscribe(parent)
    }

    class MapObserver<T, L>(
        private val observer: Observer<L>,
        private val transform: (T) -> L
    ) : Observer<T> {

        override fun onNext(value: T) {
            val transformed: L = transform(value)
            observer.onNext(transformed)
        }
    }
}