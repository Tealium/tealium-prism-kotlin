package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.subscribe

class ResubscribingObservable<T>(
    private val source: Observable<T>,
    private val predicate: (T) -> Boolean
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable =
        ResubscribingWhileCoordinator(source, observer, predicate)

    class ResubscribingWhileCoordinator<T>(
        private val source: Observable<T>,
        private val downstream: Observer<T>,
        private val predicate: (T) -> Boolean,
        private val container: CompositeDisposable = Disposables.composite()
    ) : Disposable by container {

        init {
            subscribeOnce()
        }

        private fun subscribeOnce() {
            // Per-call flag: first() fires onNext+onComplete on match; emitted=false means upstream ended without matching.
            source.take(1)
                .subscribe(container, object : Observer<T> {
                    var emitted = false
                    override fun onNext(value: T) {
                        emitted = true
                        handleElement(value)
                    }

                    override fun onComplete() {
                        if (!emitted) {
                            handleUpstreamCompleted()
                        }
                    }

                    fun handleElement(element: T) {
                        if (isDisposed) return

                        downstream.onNext(element)
                        if (predicate(element)) {
                            subscribeOnce()
                        } else {
                            handleUpstreamCompleted()
                        }
                    }

                    private fun handleUpstreamCompleted() {
                        if (isDisposed) return

                        downstream.onComplete()
                        dispose()
                    }
                })
        }
    }
}
