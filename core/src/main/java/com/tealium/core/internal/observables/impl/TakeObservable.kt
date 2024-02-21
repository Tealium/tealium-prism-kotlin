package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer

/**
 * The [TakeObservable] emits only a set number of emissions downstream that it receives from the [source].
 */
class TakeObservable<T>(
    private val source: Observable<T>,
    private val count: Int
) : Observable<T> {
    override fun subscribe(observer: Observer<T>): Disposable {
        val parent = TakeObserver(observer, count)

        return source.subscribe(parent)
    }

    class TakeObserver<T>(
        private val observer: Observer<T>,
        private val count: Int
    ): Observer<T> {
        private var observed = 0

        override fun onNext(value: T) {
            if (++observed <= count) {
                observer.onNext(value)
            }
        }
    }
}


