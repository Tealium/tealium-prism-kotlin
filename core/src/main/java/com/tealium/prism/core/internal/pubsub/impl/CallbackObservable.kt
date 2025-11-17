package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.addTo

/**
 * The [CallbackObservable] can be used for executing a task asynchronously, whilst still emitting
 * the value downstream at a later point in time.
 *
 * @param block Executes the task, provided with a given observer that can emit values downstream.
 */
class CallbackObservable<T>(
    private val block: (Observer<T>) -> Disposable
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        val container = DisposableContainer()
        val parent = CallbackObserver(observer, container)
        block.invoke(parent)
            .addTo(container)

        return container
    }

    class CallbackObserver<T>(
        private val observer: Observer<T>,
        private val disposable: Disposable
    ) : Observer<T> {

        override fun onNext(value: T) {
            if (disposable.isDisposed) return

            observer.onNext(value)
        }
    }
}
