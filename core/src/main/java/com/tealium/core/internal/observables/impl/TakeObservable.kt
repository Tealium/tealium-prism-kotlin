package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.addTo

/**
 * The [TakeObservable] emits only a set number of emissions downstream that it receives from the [source].
 */
class TakeObservable<T>(
    private val source: Observable<T>,
    private val count: Int
) : Observable<T> {
    override fun subscribe(observer: Observer<T>): Disposable {
        val container = DisposableContainer()
        val parent = TakeObserver(observer, count, container)

        source.subscribe(parent)
            .addTo(container)

        return container
    }

    class TakeObserver<T>(
        private val observer: Observer<T>,
        private val count: Int,
        private val disposable: Disposable
    ): Observer<T> {
        private var observed = 0

        override fun onNext(value: T) {
            if (disposable.isDisposed) return

            if (++observed >= count) {
                disposable.dispose()
            }

            observer.onNext(value)
        }
    }
}


