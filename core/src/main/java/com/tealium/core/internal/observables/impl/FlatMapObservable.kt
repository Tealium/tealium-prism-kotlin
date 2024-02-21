package com.tealium.core.internal.observables.impl

import com.tealium.core.internal.observables.CompositeDisposable
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.addTo
import com.tealium.core.internal.observables.DisposableContainer

/**
 * The [FlatMapObservable] transforms each of the [source] emissions into new observables - using
 * the given [transform]. All emissions from the resulting observables will be emitted downstream.
 */
class FlatMapObservable<T, L>(
    private val source: Observable<T>,
    private val transform: (T) -> Observable<L>
) : Observable<L> {
    override fun subscribe(observer: Observer<L>): Disposable {
        val container = DisposableContainer()
        val parent = FlatMapObserver(observer, transform, container)

        source.subscribe(parent)
            .addTo(container)
        return container
    }

    class FlatMapObserver<T, L>(
        private val observer: Observer<L>,
        private val transform: (T) -> Observable<L>,
        private val container: CompositeDisposable = DisposableContainer()
    ) : Observer<T> {

        override fun onNext(value: T) {
            transform(value).subscribe(observer)
                .addTo(container)
        }
    }
}