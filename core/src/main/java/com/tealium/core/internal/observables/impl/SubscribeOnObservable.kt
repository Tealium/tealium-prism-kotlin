package com.tealium.core.internal.observables.impl

import com.tealium.core.internal.observables.AsyncSubscription
import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer
import java.util.concurrent.ExecutorService

/**
 * The [SubscribeOnObservable] allows the subscription to upstream observables to take place on the
 * given [executor].
 */
class SubscribeOnObservable<T>(
    private val source: Observable<T>,
    private val executor: ExecutorService
) : Observable<T> {

    override fun subscribe(observer: Observer<T>) : Disposable {
        val subscription = AsyncSubscription(executor)
        executor.submit {
            subscription.subscription = source.subscribe(observer)
        }
        return subscription
    }
}