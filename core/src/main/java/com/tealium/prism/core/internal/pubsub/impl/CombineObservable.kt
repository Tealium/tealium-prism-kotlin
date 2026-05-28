package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.api.pubsub.addTo

/**
 * The [CombineObservable] will combine emissions for observables of two different types, using the
 * given [combiner] to determine what to emit downstream.
 *
 * Emissions downstream only occur when both sources have emitted at least once. Downstream
 * [Observer.onComplete] is called once both source observables have completed.
 */
class CombineObservable<T1, T2, R>(
    private val source: Observable<T1>,
    private val other: Observable<T2>,
    private val combiner: (T1, T2) -> R
) : Observable<R> {

    override fun subscribe(observer: Observer<R>): Disposable {
        return CombineCoordinator(observer, source, other, combiner)
    }

    class CombineCoordinator<T1, T2, R>(
        private val downstream: Observer<R>,
        source1: Observable<T1>,
        source2: Observable<T2>,
        private val combiner: (T1, T2) -> R,
        private val container: CompositeDisposable = DisposableContainer()
    ): Disposable by container {
        private var item1: PendingEmission<T1>? = null
        private var item2: PendingEmission<T2>? = null
        private var firstCompleted = false
        private var secondCompleted = false
        private val completed
            get() = (firstCompleted && secondCompleted)
                    || (item1 == null && firstCompleted)  // There will never be enough emissions for the
                    || (item2 == null && secondCompleted) // [combiner] if either has completed without emission

        private fun maybePublish() {
            if (isDisposed || completed) return

            val first = item1
            val second = item2
            if (first != null && second != null) {
                downstream.onNext(combiner.invoke(first.value, second.value))
            }
        }

        private fun onSourceComplete() {
            if (isDisposed || !completed) return

            downstream.onComplete()
            dispose()
        }

        init {
            source1.subscribe(object : Observer<T1> {
                override fun onNext(value: T1) {
                    item1 = PendingEmission(value)
                    maybePublish()
                }
                override fun onComplete() {
                    if (firstCompleted) return
                    firstCompleted = true
                    onSourceComplete()
                }
            }).addTo(container)

            source2.subscribe(object : Observer<T2> {
                override fun onNext(value: T2) {
                    item2 = PendingEmission(value)
                    maybePublish()
                }
                override fun onComplete() {
                    if (secondCompleted) return
                    secondCompleted = true
                    onSourceComplete()
                }
            }).addTo(container)
        }
    }
}
