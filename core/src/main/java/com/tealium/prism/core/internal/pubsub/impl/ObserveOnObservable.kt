package com.tealium.prism.core.internal.pubsub.impl

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.CompositeDisposable
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.api.pubsub.addTo

/**
 * The [ObserveOnObservable] allows the downstream to receive emissions on a given Thread/Executor.
 */
class ObserveOnObservable<T>(
    private val source: Observable<T>,
    private val scheduler: Scheduler,
) : Observable<T> {

    override fun subscribe(observer: Observer<T>): Disposable {
        val observeOnObserver = ObserveOnObserver(observer, scheduler)
        source.subscribe(observeOnObserver)
            .addTo(observeOnObserver)
        return observeOnObserver
    }

    class ObserveOnObserver<T>(
        private val observer: Observer<T>,
        private val scheduler: Scheduler,
        disposable: CompositeDisposable = Disposables.composite(scheduler)
    ) : Observer<T>, CompositeDisposable by disposable {

        private var completed = false
        override fun onNext(value: T) {
            scheduler.execute {
                if (completed || isDisposed) return@execute

                observer.onNext(value)
            }
        }

        override fun onComplete() {
            scheduler.execute {
                if (completed) return@execute
                completed = true

                observer.onComplete()
                dispose()
            }
        }
    }
}
