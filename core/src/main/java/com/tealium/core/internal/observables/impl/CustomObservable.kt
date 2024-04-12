package com.tealium.core.internal.observables.impl

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.observables.Observable
import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.DisposableContainer
import com.tealium.core.internal.observables.addTo

/**
 * The [CustomObservable] allows a subscription handler to be provided in order to facilitate
 * creation of observables inline using lambdas. This is useful for creating extension functions
 * that can provide custom logic for controlling emissions downstream.
 *
 * e.g.
 * ```kotlin
 * fun <T> Observable<T?>.filterNotNull(): Observable<T> {
 *     return CustomObservable { observer ->
 *         this.subscribe { value ->
 *             if (value != null) {
 *                 observer.onNext(value)
 *             }
 *         }
 *     }
 * }
 *
 * /* Can be inlined as follows */
 * Observables.just(1, null, 2)
 *     .filterNotNull()
 *     .subscribe {
 *         println(it) // 1, 2
 *     }`
 * ```
 *
 */
class CustomObservable<T>(
    private val source: Observable<T>? = null,
    private val subscriptionHandler: (Observer<T>) -> Disposable
): Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        if (source == null) {
            return subscriptionHandler.invoke(observer)
        }

        val container = DisposableContainer()
        subscriptionHandler.invoke(observer)
            .addTo(container)

        source.subscribe(observer)
                .addTo(container)

        return container
    }
}