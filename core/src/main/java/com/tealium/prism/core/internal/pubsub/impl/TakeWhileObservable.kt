package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.addTo

/**
 * The [TakeWhileObservable] will emit values that it receives from the [source] until the given
 * [predicate] returns false.
 * Once the [predicate] returns false, then the subscription will be disposed of.
 */
class TakeWhileObservable<T>(
    private val source: Observable<T>,
    private val predicate: (T) -> Boolean,
    private val inclusive: Boolean = false
) : Observable<T> {
    override fun subscribe(observer: Observer<T>): Disposable {
        val container = DisposableContainer()
        val parent = TakeWhileObserver(observer, predicate, container, inclusive)

        source.subscribe(parent)
            .addTo(container)
        return container
    }

    class TakeWhileObserver<T>(
        private val observer: Observer<T>,
        private val predicate: (T) -> Boolean,
        private val container: DisposableContainer,
        private val inclusive: Boolean = false
    ): Observer<T> {

        override fun onNext(value: T) {
            if (container.isDisposed) return

            if (predicate.invoke(value)) {
                observer.onNext(value)
            } else {
                if (inclusive) {
                    observer.onNext(value)
                }
                container.dispose()
            }
        }
    }
}


