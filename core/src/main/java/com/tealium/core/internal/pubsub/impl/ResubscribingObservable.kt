package com.tealium.core.internal.pubsub.impl

import com.tealium.core.api.pubsub.CompositeDisposable
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.api.pubsub.Observable
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.internal.pubsub.DisposableContainer
import com.tealium.core.internal.pubsub.addTo
import com.tealium.core.internal.pubsub.subscribeOnce

class ResubscribingObservable<T>(
    private val source: Observable<T>,
    private val predicate: (T) -> Boolean
): Observable<T> {

    private fun subscribeOnceInfiniteLoop(
        observer: Observer<T>,
        disposables: CompositeDisposable
    ): Disposable {
        return source.subscribeOnce { element ->
            if (disposables.isDisposed) return@subscribeOnce

            observer.onNext(element)
            if (predicate(element)) {
                subscribeOnceInfiniteLoop(
                    observer,
                    disposables
                ).addTo(disposables)
            }
        }
    }

    override fun subscribe(observer: Observer<T>): Disposable {
        val container = DisposableContainer()
        subscribeOnceInfiniteLoop(observer, container)
            .addTo(container)

        return container
    }
}