package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.pubsub.CompositeDisposable
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.internal.pubsub.addTo

/**
 * The [CombineObservable] will combine emissions for observables of two different types, using the
 * given [combiner] to determine what to emit downstream.
 *
 * Emissions downstream only occur when both sources have emitted at least once.
 */
class CombineObservable<T1, T2, R>(
    private val source: Observable<T1>,
    private val other: Observable<T2>,
    private val combiner: (T1, T2) -> R
) : Observable<R> {

    override fun subscribe(observer: Observer<R>): Disposable {
        val parent = CombineCoordinator(observer, source, other, combiner)

        return parent.subscribe()
    }

    class CombineCoordinator<T1, T2, R>(
        private val observer: Observer<R>,
        private val source1: Observable<T1>,
        private val source2: Observable<T2>,
        private val combiner: (T1, T2) -> R,
    ): Observer<R> {
        private var item1: PendingEmission<T1>? = null
        private var item2: PendingEmission<T2>? = null
        private val container: CompositeDisposable = DisposableContainer()

        private fun publish() {
            val first = item1
            val second = item2
            if (first != null && second != null) {
                observer.onNext(combiner.invoke(first.value, second.value))
            }
        }

        override fun onNext(value: R) {
            // do nothing
        }

        fun subscribe(): Disposable {
            source1.subscribe {
                item1 = PendingEmission(it)
                publish()
            }.addTo(container)

            source2.subscribe {
                item2 = PendingEmission(it)
                publish()
            }.addTo(container)

            return container
        }
    }
}