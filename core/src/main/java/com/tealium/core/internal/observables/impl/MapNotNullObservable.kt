package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer

/**
 * The [MapNotNullObservable] applies a [transform] to each emission from the [source] observable.
 * If the result of the [transform] is null, then the value is not propagated downstream. It therefore
 * has the effect of changing a stream of possibly null values into a stream of non-null values.
 */
class MapNotNullObservable<T, L>(
    private val source: Observable<T>,
    private val transform: (T) -> L?
) : Observable<L> {
    override fun subscribe(observer: Observer<L>): Disposable {
        val parent = MapNotNullObserver(observer, transform)

        return source.subscribe(parent)
    }

    class MapNotNullObserver<T, L>(
        private val observer: Observer<L>,
        private val transform: (T) -> L?
    ) : Observer<T> {

        override fun onNext(value: T) {
            val transformed: L? = transform(value)
            if (transformed != null) {
                observer.onNext(transformed)
            }
        }
    }
}