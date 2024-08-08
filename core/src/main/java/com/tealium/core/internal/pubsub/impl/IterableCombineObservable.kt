package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.internal.pubsub.addTo

/**
 * The [IterableCombineObservable] combines all the emissions from the given set of [sources].
 *
 * Downstream emissions only occur once all source observables have emitted at least one value, and
 * the resulting emission is the result of applying the [combiner] function on the full set of latest
 * emissions from all [sources].
 *
 * @see CombineObservable
 */
class IterableCombineObservable<T, R>(
    private val sources: Iterable<Observable<T>>,
    private val combiner: (Iterable<T>) -> R
) : Observable<R> {

    override fun subscribe(observer: Observer<R>): Disposable {
        val parent = IterableCombineCoordinator(observer, sources, combiner)

        return parent.subscribe()
    }

    class IterableCombineCoordinator<T, R>(
        private val observer: Observer<R>,
        private val sources: Iterable<Observable<T>>,
        private val combiner: (Iterable<T>) -> R,
    ) : Observer<R> {

        private var emissions: MutableList<T?> = sources.map { null }.toMutableList()
        private val container = DisposableContainer()

        private fun publish() {
            val latest = emissions
            if (latest.all { it != null }) {
                observer.onNext(combiner.invoke(latest.mapNotNull { it }))
            }
        }

        override fun onNext(value: R) {
            // do nothing
        }

        fun subscribe(): Disposable {
            sources.forEachIndexed { idx, source ->
                source.subscribe {
                    emissions[idx] = it
                    publish()
                }.addTo(container)
            }

            return container
        }
    }
}