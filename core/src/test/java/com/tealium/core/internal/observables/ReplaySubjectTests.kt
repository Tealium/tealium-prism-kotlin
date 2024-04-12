package com.tealium.core.internal.observables

import com.tealium.core.api.listeners.Observer
import com.tealium.core.internal.observables.ObservableUtils.assertNoSubscribers
import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
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
    fun replaySubject_WithNoSize_IsUnbounded() {
        val subject = Observables.replaySubject<Int>()
        val observer = mockk<Observer<Int>>(relaxed = true)

        for (i in 0 .. 100) {
            subject.onNext(i)
        }
        subject.subscribe(observer)

        verifyOrder {
            for (i in 0 .. 100) {
                observer.onNext(i)
            }
        }
    }

    @Test
    fun replaySubject_WithZeroSize_ReplaysNothing() {
        val subject = Observables.replaySubject<Int>(0)
        val observer = mockk<Observer<Int>>(relaxed = true)

        subject.onNext(1)
        subject.subscribe(observer)

        verify {
            observer wasNot Called
        }
    }

    @Test
    fun replaySubject_WithNegativeSize_IsUnbounded() {
        val subject = Observables.replaySubject<Int>(-1)
        val observer = mockk<Observer<Int>>(relaxed = true)

        for (i in 0 .. 100) {
            subject.onNext(i)
        }
        subject.subscribe(observer)

        verifyOrder {
            for (i in 0 .. 100) {
                observer.onNext(i)
            }
        }
    }

    @Test
    fun replaySubject_Resize_ShrinksCache_RemovingOldest() {
        val subject = Observables.replaySubject<Int>(3)
        val observer = mockk<Observer<Int>>(relaxed = true)

        subject.onNext(1)
        subject.onNext(2)
        subject.onNext(3)
        subject.resize(2)
        subject.subscribe(observer)

        verifyOrder {
            observer.onNext(2)
            observer.onNext(3)
        }
    }

    @Test
    fun replaySubject_Resize_GrowsCache_MaintainingExisting() {
        val subject = Observables.replaySubject<Int>(2)
        val observer = mockk<Observer<Int>>(relaxed = true)

        subject.onNext(1) // will be evicted
        subject.onNext(2)
        subject.onNext(3)
        subject.resize(3)
        subject.onNext(4)
        subject.subscribe(observer)

        verifyOrder {
            observer.onNext(2)
            observer.onNext(3)
            observer.onNext(4)
        }
    }

    @Test
    fun replaySubject_Resize_GrowsToUnbounded() {
        val subject = Observables.replaySubject<Int>(2)
        val observer = mockk<Observer<Int>>(relaxed = true)

        subject.onNext(1) // will be evicted
        subject.onNext(2)
        subject.onNext(3)

        subject.resize(-1)

        subject.onNext(4)
        subject.onNext(5)
        subject.subscribe(observer)

        verifyOrder {
            observer.onNext(2)
            observer.onNext(3)
            observer.onNext(4)
            observer.onNext(5)
        }
    }

    @Test
    fun replaySubject_ResizeToZero_StopsReplays() {
        val subject = Observables.replaySubject<Int>(2)
        val observer = mockk<Observer<Int>>(relaxed = true)

        subject.onNext(1)
        subject.onNext(2)

        subject.resize(0)
        subject.onNext(3)

        subject.subscribe(observer)
        subject.onNext(4)

        verify {
            observer.onNext(4)
        }
        confirmVerified(observer)
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