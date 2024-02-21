package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.addTo

/**
 * The [TakeWhileObservable] will emit values that it receives from the [source] until the given
 * [predicate] returns false.
 * Once the [predicate] returns false, then the subscription will be disposed of.
 */
class TakeWhileObservable<T>(
    private val source: Observable<T>,
    private val predicate: (T) -> Boolean
) : Observable<T> {
    override fun subscribe(observer: Observer<T>): Disposable {
        val container = DisposableContainer()
        val parent = TakeWhileObserver(observer, predicate, container)

        source.subscribe(parent)
            .addTo(container)
        return container
    }

    class TakeWhileObserver<T>(
        private val observer: Observer<T>,
        private val predicate: (T) -> Boolean,
        private val container: DisposableContainer
    ): Observer<T> {

        override fun onNext(value: T) {
            if (container.isDisposed) return

            if (predicate.invoke(value)) {
                observer.onNext(value)
            } else {
                // TODO... inclusive
                container.dispose()
            }
        }
    }
}


