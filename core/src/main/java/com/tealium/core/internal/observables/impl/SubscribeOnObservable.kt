package com.tealium.core.internal.observables.impl

import com.tealium.core.api.Scheduler
import com.tealium.core.internal.observables.AsyncSubscription
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer

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