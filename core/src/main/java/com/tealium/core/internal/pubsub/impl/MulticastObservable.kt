package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.api.pubsub.Subject
import com.tealium.core.internal.pubsub.Subscription

/**
 * The [MulticastObservable] shares a single connection to the [source] between all [Observer]s
 * that [subscribe] to this [Observable].
 *
 * Subscription to the [source] begins when the first [Observer] subscribes.
 *
 * By default, previous emissions are not replayed, but can be configured to do so by injecting
 * a different [subject].
 */
class MulticastObservable<T>(
    private val source: Observable<T>,
    private val subject: Subject<T> = Observables.publishSubject()
): Observable<T> {
    private var sourceSubscription: Disposable? = null

    override fun subscribe(observer: Observer<T>): Disposable {
        val innerSubscription = subject.subscribe(observer)

        // subscribe only once
        sourceSubscription = sourceSubscription
            ?: source.subscribe(subject)

        return Subscription {
            innerSubscription.dispose()
            if (subject.count == 0) {
                sourceSubscription?.dispose()
                sourceSubscription = null
            }
        }
    }
}