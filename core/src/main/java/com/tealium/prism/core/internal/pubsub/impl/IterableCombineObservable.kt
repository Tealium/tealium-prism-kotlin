package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.subscribe
import com.tealium.prism.core.internal.pubsub.DisposableContainer

/**
 * The [IterableCombineObservable] combines all the emissions from the given set of [sources].
 *
 * Downstream emissions only occur once all source observables have emitted at least one value, and
 * the resulting emission is the result of applying the [combiner] function on the full set of latest
 * emissions from all [sources].
 *
 * Downstream [Observer.onComplete] is called once all source observables have completed.
 *
 * @see CombineObservable
 */
class IterableCombineObservable<T, R>(
    private val sources: Iterable<Observable<T>>,
    private val combiner: (Iterable<T>) -> R
) : Observable<R> {

    override fun subscribe(observer: Observer<R>): Disposable {
        return IterableCombineCoordinator(observer, sources, combiner)
    }

    class IterableCombineCoordinator<T, R>(
        private val downstream: Observer<R>,
        sources: Iterable<Observable<T>>,
        private val combiner: (Iterable<T>) -> R,
        private val container: CompositeDisposable = DisposableContainer()
    ) : Disposable by container {
        private val sourceList = sources.toList()
        private val emissions: MutableList<PendingEmission<T>?> = sourceList.map { null }.toMutableList()
        private val sourceCompleted: MutableList<Boolean> = sourceList.map { false }.toMutableList()
        private val completed
            get() = sourceCompleted.all { isCompleted -> isCompleted }


        private fun onSourceNext(index: Int, value: T) {
            if (isDisposed || sourceCompleted[index]) return

            emissions[index] = PendingEmission(value)

            val latest = emissions
            if (latest.all { it != null }) {
                downstream.onNext(combiner.invoke(latest.filterNotNull().map { it.value }))
            }
        }

        private fun onSourceComplete(index: Int) {
            if (isDisposed || sourceCompleted[index]) return
            sourceCompleted[index] = true

            if (emissions[index] == null || completed) {
                downstream.onComplete()
                dispose()
            }
        }

        init {
            sourceList.forEachIndexed { idx, source ->
                source.subscribe(container,object : Observer<T> {
                    override fun onNext(value: T) {
                        onSourceNext(idx, value)
                    }
                    override fun onComplete() {
                        onSourceComplete(idx)
                    }
                })
            }
        }
    }
}
