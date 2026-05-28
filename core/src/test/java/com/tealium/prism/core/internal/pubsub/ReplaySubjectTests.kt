package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySubjectTests {

    @Test
    fun replaySubject_Evicts_Old_Entries() {
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
    fun replaySubject_Emits_To_Multiple_Subscribers() {
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
    fun replaySubject_Emits_Cached_To_New_Subscribers() {
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
    fun replaySubject_With_No_Size_Is_Unbounded() {
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
    fun replaySubject_With_Zero_Size_Replays_Nothing() {
        val subject = Observables.replaySubject<Int>(0)
        val observer = mockk<Observer<Int>>(relaxed = true)

        subject.onNext(1)
        subject.subscribe(observer)

        verify {
            observer wasNot Called
        }
    }

    @Test
    fun replaySubject_With_Negative_Size_Is_Unbounded() {
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
    fun replaySubject_Resize_Shrinks_Cache_Removing_Oldest() {
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
    fun replaySubject_Resize_Grows_Cache_Maintaining_Existing() {
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
    fun replaySubject_Resize_Grows_To_Unbounded() {
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
    fun replaySubject_Resize_To_Zero_Stops_Replays() {
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
    fun replaySubject_Disposal_Does_Not_Affect_Other_Subscribers() {
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
    fun replaySubject_Dispose_Clears_Subscription() {
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

    @Test
    fun last_Returns_Null_When_No_Values_Have_Been_Published() {
        val replaySubject = Observables.replaySubject<Int>(3)
        assertNull(replaySubject.last())
    }

    @Test
    fun last_Returns_Last_Value_When_One_Has_Been_Published() {
        val replaySubject = Observables.replaySubject<Int>(3)
        replaySubject.onNext(1)

        assertEquals(1, replaySubject.last())
    }

    @Test
    fun last_Returns_Only_Last_Value_When_Multiple_Have_Been_Published() {
        val replaySubject = Observables.replaySubject<Int>(3)
        replaySubject.onNext(1)
        replaySubject.onNext(2)
        replaySubject.onNext(3)

        assertEquals(3, replaySubject.last())
    }

    @Test
    fun last_Returns_Null_After_Clearing() {
        val replaySubject = Observables.replaySubject<Int>(3)
        replaySubject.onNext(1)
        replaySubject.onNext(2)

        replaySubject.clear()

        assertNull(replaySubject.last())
    }

    @Test
    fun last_Returns_Last_Published_After_Resizing() {
        val replaySubject = Observables.replaySubject<Int>(3)
        replaySubject.onNext(1)
        replaySubject.onNext(2)

        replaySubject.resize(1)

        assertEquals(2, replaySubject.last())
    }

    @Test
    fun last_Returns_Null_After_Resizing_To_Zero() {
        val replaySubject = Observables.replaySubject<Int>(3)
        replaySubject.onNext(1)
        replaySubject.onNext(2)
        
        replaySubject.resize(0)

        assertNull(replaySubject.last())
    }

    @Test
    fun onComplete_Calls_Subscriber_OnComplete() {
        val subject = Observables.replaySubject<Int>(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        subject.subscribe(observer1)
        subject.subscribe(observer2)

        subject.onComplete()

        verify {
            observer1.onComplete()
            observer2.onComplete()
        }
    }

    @Test
    fun onComplete_Removes_Subscribers() {
        val subject = Observables.replaySubject<Int>(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        subject.subscribe(observer1)
        subject.subscribe(observer2)

        subject.onComplete()

        subject.assertNoSubscribers()
    }

    @Test
    fun onNext_Does_Not_Emit_Downstream_After_OnComplete() {
        val subject = Observables.replaySubject<Int>(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)

        subject.subscribe(observer1)

        subject.onComplete()
        subject.onNext(2)

        verify(inverse = true) {
            observer1.onNext(2)
        }
    }

    @Test
    fun subscribe_Returns_Disposed_When_Subscribed_After_OnComplete() {
        val subject = Observables.replaySubject<Int>(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)

        subject.onComplete()
        val sub = subject.subscribe(observer1)

        assertTrue(sub.isDisposed)
    }

    @Test
    fun subscribe_Calls_Observer_OnComplete_When_Subscribed_After_OnComplete() {
        val subject = Observables.replaySubject<Int>(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)

        subject.onComplete()
        subject.subscribe(observer1)

        verify {
            observer1.onComplete()
        }
    }

    @Test
    fun subscribe_Emits_Latest_Cache_When_Subscribed_After_OnComplete() {
        val subject = Observables.replaySubject<Int>(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)

        subject.onNext(1)
        subject.onComplete()
        subject.subscribe(observer1)

        verify {
            observer1.onNext(1)
        }
    }
}