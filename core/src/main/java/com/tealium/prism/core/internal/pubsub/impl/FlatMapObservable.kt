package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.addTo
import com.tealium.prism.core.internal.pubsub.DisposableContainer

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