package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.api.pubsub.subscribe
import com.tealium.prism.core.internal.pubsub.DisposableContainer

/**
 * The [FlatMapObservable] transforms each of the [source] emissions into new observables - using
 * the given [transform]. All emissions from the resulting observables will be emitted downstream.
 *
 * Downstream [Observer.onComplete] is called once the upstream source has completed *and* all
 * inner observables produced by [transform] have also completed. For infinite sources (e.g.
 * [com.tealium.prism.core.api.pubsub.Subject]s), completion will never be signalled.
 */
class FlatMapObservable<T, L>(
    private val source: Observable<T>,
    private val transform: (T) -> Observable<L>
) : Observable<L> {
    override fun subscribe(observer: Observer<L>): Disposable {
        val flatMapObserver = FlatMapObserver(observer, transform)

        source.subscribe(flatMapObserver)
            .addTo(flatMapObserver)

        return flatMapObserver
    }

    class FlatMapObserver<T, L>(
        private val observer: Observer<L>,
        private val transform: (T) -> Observable<L>,
        private val container: CompositeDisposable = DisposableContainer()
    ) : Observer<T>, CompositeDisposable by container {
        private var activeInnerCount = 0
        private var upstreamCompleted = false

        override fun onNext(value: T) {
            if (upstreamCompleted) return

            activeInnerCount++
            transform(value).subscribe(container,object : Observer<L> {
                override fun onNext(value: L) = observer.onNext(value)

                override fun onComplete() {
                    activeInnerCount--
                    maybeComplete()
                }
            })
        }

        override fun onComplete() {
            if (upstreamCompleted) return

            upstreamCompleted = true
            maybeComplete()
        }

        private fun maybeComplete() {
            if (upstreamCompleted && activeInnerCount == 0) {
                observer.onComplete()
                container.dispose()
            }
        }
    }
}
