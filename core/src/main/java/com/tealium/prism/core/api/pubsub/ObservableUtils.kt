@file:JvmName("ObservableUtils")

package com.tealium.prism.core.api.pubsub

/**
 * Add the current disposable to a specific container for easier cleanup.
 *
 * @param container the [CompositeDisposable] to add this [Disposable] to.
 * @return This [Disposable]
 */
fun <T: Disposable> T.addTo(container: CompositeDisposable): T = apply {
    container.add(this)
}

/**
 * Convenience method for merging a [Iterable] group of [Observable]s of the same type.
 *
 * This is shorthand for [Observables.merge].
 */
fun <T> Iterable<Observable<T>>.merge(): Observable<T> {
    return Observables.merge(this)
}

/**
 * Convenience method that maps any incoming emission to [Unit]
 */
@Suppress("RedundantUnitExpression")
fun <T> Observable<T>.mapToUnit(): Observable<Unit> {
    return map { _ -> Unit }
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