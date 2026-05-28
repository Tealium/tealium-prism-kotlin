package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.internal.pubsub.DisposableContainer

/**
 * The [FlatMapLatestObservable] transforms emissions from the given [source], returning a new
 * observable provided by the given [transform].
 *
 * Upon a new emission, it will cancel the downstream subscription and create a new one. It will
 * therefore only emit values downstream from the latest observable.
 *
 * Downstream [Observer.onComplete] is called once the upstream source has completed *and* the
 * current inner observable has also completed.
 */
class FlatMapLatestObservable<T, L>(
    private val source: Observable<T>,
    private val transform: (T) -> Observable<L>
) : Observable<L> {

    override fun subscribe(observer: Observer<L>): Disposable {
        val flatMapLatestObserver = FlatMapLatestObserver(observer, transform)
        source.subscribe(flatMapLatestObserver)
            .addTo(flatMapLatestObserver)
        return flatMapLatestObserver
    }

    class FlatMapLatestObserver<T, L>(
        private val observer: Observer<L>,
        private val transform: (T) -> Observable<L>,
        private val container: CompositeDisposable = DisposableContainer(),
    ) : Observer<T>, CompositeDisposable by container {
        private var subscription: Disposable? = null
        private var isSubscribing = false
        private var latestEmission: PendingEmission<T>? = null
        private var upstreamCompleted = false
        private var innerCompleted = false

        override fun onNext(value: T) {
            latestEmission = PendingEmission(value)
            if (isSubscribing) return

            isSubscribing = true
            try {
                var emission = latestEmission
                while (emission != null) {
                    latestEmission = null
                    subscription?.let { old ->
                        innerCompleted = false // reset upon new emission
                        old.dispose()
                        container.remove(old)
                    }

                    subscription = transform(emission.value).subscribe(object : Observer<L> {
                        override fun onNext(value: L) = observer.onNext(value)

                        override fun onComplete() {
                            if (innerCompleted) return

                            innerCompleted = true
                            // Only complete downstream if the upstream has also finished
                            if (upstreamCompleted) {
                                complete()
                            }
                        }
                    })
                    emission = latestEmission
                }
                subscription?.addTo(container)
            } finally {
                isSubscribing = false
            }
        }

        override fun onComplete() {
            if (upstreamCompleted) return

            upstreamCompleted = true
            // Only complete downstream if the upstream has also finished
            if (innerCompleted || subscription == null) {
                complete()
            }
        }

        private fun complete() {
            observer.onComplete()
            container.dispose()
        }
    }
}
