package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.CompositeDisposable
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.ObservableState
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.api.pubsub.Subscribable
import com.tealium.core.api.pubsub.SubscribableState
import com.tealium.core.internal.pubsub.impl.ObservableStateImpl

/**
 * Add the current disposable to a specific container for easier cleanup.
 */
fun <T: Disposable> T.addTo(container: CompositeDisposable): T = apply {
    container.add(this)
}

/**
 * Subscribes the given [observer] to a single emission of the source.
 */
fun <T> Observable<T>.subscribeOnce(observer: Observer<T>): Disposable {
    return take(1)
        .subscribe(observer)
}

/**
 * Convenience method for merging a [Iterable] group of [Observable]s of the same type.
 *
 * This is shorthand for [Observables.merge].
 */
fun <T> Iterable<Observable<T>>.merge(): Observable<T> {
    return Observables.merge(this)
}

fun <T> passthroughTransform() : (T) -> T {
    return { it }
}

fun <T> Observable<T>.map(): Observable<T> {
    return map(passthroughTransform())
}

fun <T> Subscribable<T>.asObservable() : Observable<T> {
    return object : Observable<T>, Subscribable<T> by this { }
}

/**
 * Convenience method that maps any incoming emission to [Unit]
 */
@Suppress("RedundantUnitExpression")
fun <T> Observable<T>.mapToUnit(): Observable<Unit> {
    return map { _ -> Unit }
}

fun <T> SubscribableState<T>.asObservableState() : ObservableState<T> {
    return ObservableStateImpl(this)
}

/**
 * Convenience method for converting an observable of nullable items, into an observable
 * of non-nullable items.
 *
 * This is shorthand for `mapNotNull { it }` but is preferable to that since no additional
 * anonymous class needs to be generated.
 */
fun <T> Observable<T?>.filterNotNull(): Observable<T> {
    return mapNotNull { it }
}