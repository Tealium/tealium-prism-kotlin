@file:JvmName("ObservableUtils")

package com.tealium.prism.core.api.pubsub

import com.tealium.prism.core.internal.pubsub.AnonymousObserver
import com.tealium.prism.core.internal.pubsub.UnsubscribingObserver

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

/**
 * Subscribes the given [observer] to receive updates, including an [Observer.onComplete]
 * notification when the upstream source terminates. The subscription is stored in the given [composite]
 *
 * Upon the [Observer.onComplete] signal, the [observer] is removed from the given [composite]
 *
 * **Note.** adding to the [composite] happens on the caller thread, and removals may happen on a different
 * one. As such, users should be certain that either [composite] is thread-safe, or that the caller
 * and disposal thread are the same.
 *
 * @param observer The observer to receive values and the completion signal.
 * @return A [Disposable] for cancelling the subscription.
 */
fun <T> Observable<T>.subscribe(composite: CompositeDisposable, observer: Observer<T>): Disposable {
    val unsubscribingObserver = UnsubscribingObserver(composite, observer)

    val upstream = subscribe(unsubscribingObserver)
    unsubscribingObserver.setUpstream(upstream)
    composite.add(unsubscribingObserver)

    return unsubscribingObserver
}

/**
 * Subscribes the given [onNext] [Consumer] to receive updates. The subscription is stored in
 * the given [composite]
 *
 * Upon the [Observer.onComplete] signal, the [onNext] is removed from the given [composite]
 *
 * **Note.** adding to the [composite] happens on the caller thread, and removals may happen on a different
 * one. As such, users should be certain that either [composite] is thread-safe, or that the caller
 * and disposal thread are the same.
 *
 * @param onNext The [Consumer] to receive emitted values.
 * @return A [Disposable] for cancelling the subscription.
 */
fun <T> Observable<T>.subscribe(composite: CompositeDisposable, onNext: Consumer<T>): Disposable =
    subscribe(composite, onNext, {})

/**
 * Subscribes the given [onNext] [Consumer] to receive updates and the given [onComplete] [Runnable]
 * to receive the [Observer.onComplete] signal. The subscription is stored in the given [composite]
 *
 * Upon the [Observer.onComplete] signal, the [onNext] is removed from the given [composite]
 *
 * **Note.** adding to the [composite] happens on the caller thread, and removals may happen on a different
 * one. As such, users should be certain that either [composite] is thread-safe, or that the caller
 * and disposal thread are the same.
 *
 * @param onNext The [Consumer] to receive emitted values.
 * @param onComplete The block of code to run upon completion
 * @return A [Disposable] for cancelling the subscription.
 */
fun <T> Observable<T>.subscribe(composite: CompositeDisposable, onNext: Consumer<T>, onComplete: Runnable): Disposable =
    subscribe(composite, AnonymousObserver(onNext, onComplete))