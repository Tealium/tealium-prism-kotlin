package com.tealium.core.internal.pubsub

import com.tealium.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.core.api.pubsub.Observables
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.core.internal.pubsub.ObservableUtils.assertSubscriberCount
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class FlatMapLatestObservableTests {

    @Test
    fun flatMapLatest_EmitsOnlyLatest() {
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        Observables.just(1, 2, 3)
            .flatMapLatest {
                Observables.callback { observer ->
                    testTealiumScheduler.schedule(100.milliseconds) {
                        observer.onNext(it)
                    }
                }
            }.subscribe(onNext)

        verify(inverse = true, timeout = 1000) {
            onNext(1)
            onNext(2)
        }
        verify(timeout = 1000) {
            onNext(3)
        }
    }

    @Test
    fun flatMapLatest_Dispose_StopsEmitting() {
        val subject = Observables.publishSubject<Int>()
        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        val subscription = subject.flatMapLatest {
            Observables.just(it, it + 1)
        }.subscribe(onNext)

        subject.onNext(1)
        subscription.dispose()
        subject.onNext(3)

        subject.assertNoSubscribers()
        verify {
            onNext(1)
            onNext(2)
        }
        verify(inverse = true) {
            onNext(3)
            onNext(4)
        }
    }

    @Test
    fun flatMapLatest_Disposes_Previous_Subscriptions() {
        val subject = Observables.publishSubject<Boolean>()
        val trues = Observables.publishSubject<Boolean>()
        val falses = Observables.publishSubject<Boolean>()
        val onNext = mockk<(Boolean) -> Unit>(relaxed = true)

        val subscription = subject.flatMapLatest { isTrue ->
            if (isTrue) trues else falses
        }.subscribe(onNext)

        subject.onNext(true)
        trues.assertSubscriberCount(1)
        falses.assertSubscriberCount(0)

        subject.onNext(false)
        trues.assertSubscriberCount(0)
        falses.assertSubscriberCount(1)

        trues.onNext(true) // dropped
        falses.onNext(false)

        subscription.dispose()
        subject.assertNoSubscribers()
        trues.assertNoSubscribers()
        falses.assertNoSubscribers()

        verify {
            onNext(false)
        }
        verify(inverse = true) {
            onNext(true)
        }
    }
}