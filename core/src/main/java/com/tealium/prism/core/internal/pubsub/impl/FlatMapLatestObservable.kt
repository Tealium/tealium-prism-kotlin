package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.api.pubsub.addTo

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
        val parent = FlatMapLatestObserver(observer, transform, container)

        source.subscribe(parent)
            .addTo(container)
        return container
    }

    class FlatMapLatestObserver<T, L>(
        private val observer: Observer<L>,
        private val transform: (T) -> Observable<L>,
        private val container: CompositeDisposable = DisposableContainer(),
    ) : Observer<T> {
        private var subscription: Disposable? = null
        private var isSubscribing = false
        private var latestEmission: PendingEmission<T>? = null

        override fun onNext(value: T) {
            latestEmission = PendingEmission(value)
            if (isSubscribing) return

            isSubscribing = true
            try {
                var emission = latestEmission
                while (emission != null) {
                    latestEmission = null
                    subscription?.let { old ->
                        old.dispose()
                        container.remove(old)
                    }

                    subscription = transform(emission.value).subscribe(observer)
                    emission = latestEmission
                }
                subscription?.addTo(container)
            } finally {
                isSubscribing = false
            }
        }
    }

}