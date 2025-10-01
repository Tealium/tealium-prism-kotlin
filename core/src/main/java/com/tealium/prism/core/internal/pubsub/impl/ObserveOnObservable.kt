package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.addTo

/**
 * The [ObserveOnObservable] allows the downstream to receive emissions on a given Thread/Executor.
 */
class ObserveOnObservable<T>(
    private val source: Observable<T>,
    private val scheduler: Scheduler,
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        val container = DisposableContainer()
        val parent = ObserveOnObserver(observer, scheduler, container)

        source.subscribe(parent)
            .addTo(container)

        return container
    }

    class ObserveOnObserver<T>(
        private val observer: Observer<T>,
        private val scheduler: Scheduler,
        private val disposable: Disposable
    ) : Observer<T> {

        override fun onNext(value: T) {
            scheduler.execute {
                if (disposable.isDisposed) return@execute

                observer.onNext(value)
            }
        }
    }
}