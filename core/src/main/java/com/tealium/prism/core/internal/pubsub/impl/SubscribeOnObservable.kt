package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.addTo
import com.tealium.prism.core.internal.pubsub.AsyncDisposableContainer

/**
 * The [SubscribeOnObservable] allows the subscription to upstream observables to take place on the
 * given [scheduler]. Completion from the upstream is also forwarded to the downstream observer.
 */
class SubscribeOnObservable<T>(
    private val source: Observable<T>,
    private val scheduler: Scheduler
) : Observable<T> {

    override fun subscribe(observer: Observer<T>) : Disposable {
        val subscription = AsyncDisposableContainer(disposeOn = scheduler)
        scheduler.execute {
            source.subscribe(object : Observer<T> {
                override fun onNext(value: T) = observer.onNext(value)

                override fun onComplete() {
                    observer.onComplete()
                    subscription.dispose()
                }
            }).addTo(subscription)
        }
        return subscription
    }
}
