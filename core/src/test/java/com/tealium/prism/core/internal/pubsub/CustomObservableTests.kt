package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertSubscriberCount
import com.tealium.prism.core.internal.pubsub.impl.CustomObservable
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class CustomObservableTests {

    @Test
    fun customObservable_WithoutSource_SendsAll() {
        val custom = CustomObservable<Int> { observer ->
            for (i in 1..3) {
                observer.onNext(i)
            }

            Subscription()
        }
        val onNext = mockk<Observer<Int>>(relaxed = true)

        custom.subscribe(onNext)

        verify {
            onNext.onNext(1)
            onNext.onNext(2)
            onNext.onNext(3)
        }
    }

    @Test
    fun customObservable_DisposesSourceSubscription_And_CustomSubscription() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<Observer<Int>>(relaxed = true)
        val onDispose = mockk<() -> Unit>(relaxed = true)

        val custom = CustomObservable(subject) { observer ->
            observer.onNext(1)
            Subscription(onDispose)
        }

        val disposable = custom.subscribe(onNext)
        subject.assertSubscriberCount(1)

        subject.onNext(2)
        disposable.dispose()

        subject.assertNoSubscribers()
        verifyOrder {
            onNext.onNext(1)
            onNext.onNext(2)
            onDispose.invoke()
        }
    }

    @Test
    fun customObservable_FiltersNotNull() {
        val subject = Observables.publishSubject<Int?>()
        val onNext = mockk<Observer<Int>>(relaxed = true)

        subject.customFilterNotNull()
            .subscribe(onNext)
        subject.assertSubscriberCount(1)

        subject.onNext(1)
        subject.onNext(null)
        subject.onNext(2)

        verifyOrder {
            onNext.onNext(1)
            onNext.onNext(2)
        }
        confirmVerified(onNext)
    }

    private fun <T> Observable<T?>.customFilterNotNull(): Observable<T> {
        return CustomObservable { observer ->
            this.subscribe {
                if (it != null) {
                    observer.onNext(it)
                }
            }
        }
    }
}