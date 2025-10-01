package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.AsyncDisposableContainer
import com.tealium.prism.core.internal.pubsub.addTo

/**
 * The [SubscribeOnObservable] allows the subscription to upstream observables to take place on the
 * given [scheduler].
 */
class SubscribeOnObservable<T>(
    private val source: Observable<T>,
    private val scheduler: Scheduler
) : Observable<T> {

    override fun subscribe(observer: Observer<T>) : Disposable {
        val subscription = AsyncDisposableContainer(disposeOn = scheduler)
        scheduler.execute {
            source.subscribe(observer)
                .addTo(subscription)
        }
        return subscription
    }
}