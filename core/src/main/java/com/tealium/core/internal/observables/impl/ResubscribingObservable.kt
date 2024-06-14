package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.Observable
import com.tealium.core.internal.observables.SubscriptionWrapper
import com.tealium.core.internal.observables.subscribeOnce

class ResubscribingObservable<T>(
    private val source: Observable<T>,
    private val predicate: (T) -> Boolean
): Observable<T> {

    private fun subscribeOnceInfiniteLoop(
        observer: Observer<T>,
        subscription: SubscriptionWrapper
    ): Disposable {
        return source.subscribeOnce { element ->
            if (subscription.isDisposed) return@subscribeOnce

            subscription.subscription?.dispose()
            observer.onNext(element)
            if (predicate(element)) {
                subscription.subscription = subscribeOnceInfiniteLoop(
                    observer,
                    subscription
                )
            }
        }
    }

    override fun subscribe(observer: Observer<T>): Disposable {
        val subscription = SubscriptionWrapper()
        subscription.subscription =
            subscribeOnceInfiniteLoop(observer, subscription)

        return subscription
    }
}