package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.api.listeners.SubscribableState
import com.tealium.core.internal.observables.impl.ObservableStateImpl

/**
 * Add the current disposable to a specific container for easier cleanup.
 */
fun Disposable.addTo(container: CompositeDisposable): Disposable = apply {
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