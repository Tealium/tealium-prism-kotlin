package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.ObservableUtils.assertNoSubscribers
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySubjectTests {

    @Test
    fun replaySubject_EvictsOldEntries() {
        val subject = Observables.replaySubject<Int>(2)
        val observer1 = mockk<Observer<Int>>(relaxed = true)

        subject.onNext(1)
        subject.onNext(2)
        subject.onNext(3)
        subject.subscribe(observer1)


        verifyOrder {
            observer1.onNext(2)
            observer1.onNext(3)
        }
    }

    @Test
    fun replaySubject_EmitsToMultipleSubscribers() {
        val subject = Observables.replaySubject<Int>(2)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        subject.subscribe(observer1)
        subject.subscribe(observer2)

        subject.onNext(1)

        verifyOrder {
            observer1.onNext(1)
            observer2.onNext(1)
        }
    }

    @Test
    fun replaySubject_EmitsCachedToNewSubscribers() {
        val subject = Observables.replaySubject<Int>(2)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        subject.onNext(1)
        subject.subscribe(observer1)

        subject.onNext(2)
        subject.subscribe(observer2)

        verifyOrder {
            observer1.onNext(1)
            observer1.onNext(2)

            observer2.onNext(1)
            observer2.onNext(2)
        }
    }

    @Test
    fun replaySubject_Disposal_DoesNotAffectOtherSubscribers() {
        val subject = Observables.replaySubject<Int>(2)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        subject.subscribe(observer1)
        val subscription = subject.subscribe(observer2)

        subject.onNext(1)
        subscription.dispose()
        subject.onNext(2)

        verifyOrder {
            observer1.onNext(1)
            observer2.onNext(1)

            observer1.onNext(2)
        }
        verify(inverse = true) {
            observer2.onNext(2)
        }
    }

    @Test
    fun replaySubject_Dispose_ClearsSubscription() {
        val subject = Observables.replaySubject<Int>(2)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        val subscription1 = subject.subscribe(observer1)
        val subscription2 = subject.subscribe(observer2)

        subject.onNext(1)
        subscription1.dispose()
        subscription2.dispose()
        subject.onNext(2)

        subject.assertNoSubscribers()
        verifyOrder {
            observer1.onNext(1)
            observer2.onNext(1)

        }
        verify(inverse = true) {
            observer1.onNext(2)
            observer2.onNext(2)
        }
    }
}