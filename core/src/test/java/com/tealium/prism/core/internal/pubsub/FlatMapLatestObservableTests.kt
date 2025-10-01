package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertSubscriberCount
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
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

    @Test
    fun flatMapLatest_Conflates_Reentrant_Emissions_When_Multiple_Occur_During_Transform() {
        val subject = Observables.publishSubject<Int>()

        val onNext = mockk<(Int) -> Unit>(relaxed = true)
        var transformCount = 0
        subject.flatMapLatest<Int> { value ->
            transformCount++
            if (value == 1) {
                subject.onNext(2)
                subject.onNext(3)
                subject.onNext(4)
                Observables.empty()
            } else {
                Observables.empty()
            }
        }.subscribe(onNext)

        subject.onNext(1)

        assertEquals(2, transformCount)
    }

    @Test
    fun flatMapLatest_Disposes_Previous_Subscriptions_When_Reentrant_Emissions_Occur() {
        val subject = Observables.publishSubject<Int>()
        val innerSubject1 = Observables.publishSubject<Int>()
        val innerSubject2 = Observables.publishSubject<Int>()
        val innerSubject3 = Observables.publishSubject<Int>()

        val onNext = mockk<(Int) -> Unit>(relaxed = true)

        subject.flatMapLatest { value ->
            if (value == 1) {
                innerSubject1
                    .startWith(2)
                    .forEach { subject.onNext(it) }
            } else if (value == 2) {
                innerSubject1
                    .startWith(3)
                    .forEach { subject.onNext(it) }
            } else {
                innerSubject3
            }
        }.subscribe(onNext)

        // Emit first value, which synchronously emits second value
        subject.onNext(1)

        // Emit from all inner subjects
        innerSubject1.onNext(100)
        innerSubject2.onNext(200)
        innerSubject3.onNext(300)

        innerSubject1.assertNoSubscribers()
        innerSubject2.assertNoSubscribers()
        innerSubject3.assertSubscriberCount(1)
        verify(exactly = 0) { onNext.invoke(100) }
        verify(exactly = 0) { onNext.invoke(200) }
        verify(exactly = 1) {
            onNext.invoke(2)
            onNext.invoke(3)
            onNext.invoke(300)
        } // Only latest should emit
    }

    @Test
    fun flatMapLatest_Emits_Legitimate_Null_Values() {
        val subject = Observables.publishSubject<Int?>()
        val onNext = mockk<(Int?) -> Unit>(relaxed = true)

        subject.flatMapLatest { int ->
            Observables.just(int)
        }.subscribe(onNext)

        subject.onNext(null)

        verify {
            onNext.invoke(null)
        }
    }
}