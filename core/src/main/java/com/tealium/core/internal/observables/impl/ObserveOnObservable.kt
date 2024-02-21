package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.addTo
import java.util.concurrent.ExecutorService

/**
 * The [ObserveOnObservable] allows the downstream to receive emissions on a given Thread/Executor.
 */
class ObserveOnObservable<T>(
    private val source: Observable<T>,
    private val executor: ExecutorService,
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        val container = DisposableContainer()
        val parent = ObserveOnObserver(observer, executor, container)

        source.subscribe(parent)
            .addTo(container)

        return container
    }

    class ObserveOnObserver<T>(
        private val observer: Observer<T>,
        private val executor: ExecutorService,
        private val disposable: Disposable
    ) : Observer<T> {

        override fun onNext(value: T) {
            executor.submit {
                if (disposable.isDisposed) return@submit

                observer.onNext(value)
            }
        }
    }
}