package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.LinkableObserver
import com.tealium.prism.core.internal.pubsub.UpstreamLinkable
import com.tealium.prism.core.internal.pubsub.UpstreamLinkableImpl

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
        val linkable = CallbackObserver(observer)
        val subscription = block.invoke(linkable)
        linkable.setUpstream(subscription)
        return linkable
    }

    class CallbackObserver<T>(
        private val observer: Observer<T>,
        disposable: UpstreamLinkable = UpstreamLinkableImpl()
    ) : LinkableObserver<T>, UpstreamLinkable by disposable {

        private var completed = false

        override fun onNext(value: T) {
            if (completed || isDisposed) return

            observer.onNext(value)
            onComplete()
        }

        override fun onComplete() {
            if (completed || isDisposed) return
            completed = true

            observer.onComplete()
            dispose()
        }
    }
}
