package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.pubsub.CompositeDisposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.internal.pubsub.addTo
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.internal.pubsub.DisposableContainer

/**
 * The [FlatMapLatestObservable] transforms emissions from the given [source], returning a new
 * observable provided by the given [transform].
 *
 * Upon a new emission, it will cancel the downstream subscription and create a new one. It will
 * therefore only emit values downstream from the latest observable.
 */
class FlatMapLatestObservable<T, L>(
    private val source: Observable<T>,
    private val transform: (T) -> Observable<L>
) : Observable<L> {

    override fun subscribe(observer: Observer<L>): Disposable {
        val container = DisposableContainer()
        val parent = FlatMapLatestObserver(observer, transform)

        source.subscribe(parent)
            .addTo(container)
        return container
    }

    class FlatMapLatestObserver<T, L>(
        private val observer: Observer<L>,
        private val transform: (T) -> Observable<L>,
        private val container: CompositeDisposable = DisposableContainer()
    ) : Observer<T> {

        private var subscription: Disposable? = null

        override fun onNext(value: T) {
            subscription?.dispose()

            subscription = transform(value).subscribe(observer)
            subscription?.addTo(container)
        }
    }
}