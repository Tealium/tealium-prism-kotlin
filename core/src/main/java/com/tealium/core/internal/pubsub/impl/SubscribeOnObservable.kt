package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.misc.Scheduler
import com.tealium.core.internal.pubsub.AsyncSubscription
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer

/**
 * The [SubscribeOnObservable] allows the subscription to upstream observables to take place on the
 * given [scheduler].
 */
class SubscribeOnObservable<T>(
    private val source: Observable<T>,
    private val scheduler: Scheduler
) : Observable<T> {

    override fun subscribe(observer: Observer<T>) : Disposable {
        val subscription = AsyncSubscription(scheduler)
        scheduler.execute() {
            subscription.subscription = source.subscribe(observer)
        }
        return subscription
    }
}