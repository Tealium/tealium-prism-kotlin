package com.tealium.core.internal.observables.impl

import com.tealium.core.api.Scheduler
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.addTo

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
            scheduler.execute() {
                if (disposable.isDisposed) return@execute

                observer.onNext(value)
            }
        }
    }
}