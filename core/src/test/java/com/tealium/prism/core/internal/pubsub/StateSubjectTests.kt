package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.prism.core.internal.pubsub.ObservableUtils.getMockObserver
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateSubjectTests {

    @Test
    fun stateSubject_Emits_Preset_Default_On_Subscribe() {
        val subject = Observables.stateSubject(0)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        subject.subscribe(observer1)
        subject.subscribe(observer2)

        subject.onNext(1)

        verifyOrder {
            observer1.onNext(0)
            observer2.onNext(0)
            observer1.onNext(1)
            observer2.onNext(1)
        }
        confirmVerified(observer1, observer2)
    }

    @Test
    fun stateSubject_Emits_Latest_On_Subscription_To_Multiple_Subscribers() {
        val subject = Observables.stateSubject<Int>(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        subject.subscribe(observer1)
        subject.onNext(2)
        subject.subscribe(observer2)


        verifyOrder {
            observer1.onNext(1)
            observer1.onNext(2)
            observer2.onNext(2)
        }
        confirmVerified(observer1, observer2)
    }

    @Test
    fun stateSubject_Value_Is_The_Latest() {
        val subject = Observables.stateSubject(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)

        assertEquals(1, subject.value)

        subject.subscribe(observer1)
        subject.onNext(2)

        assertEquals(2, subject.value)

        verifyOrder {
            observer1.onNext(1)
            observer1.onNext(2)
        }
        confirmVerified(observer1)
    }

    @Test
    fun stateSubject_Value_Is_Updated_Before_Emission() {
        val subject = Observables.stateSubject(1)
        val observer1: Observer<Int> = getMockObserver {
            assertEquals(it, subject.value)
        }

        subject.subscribe(observer1)
        subject.onNext(2)

        verifyOrder {
            observer1.onNext(1)
            observer1.onNext(2)
        }
        confirmVerified(observer1)
    }

    @Test
    fun stateSubject_Disposal_Does_Not_Affect_Other_Subscribers() {
        val subject = Observables.stateSubject<Int>(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        subject.subscribe(observer1)
        val subscription = subject.subscribe(observer2)

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
    fun stateSubject_Dispose_Clears_Subscription() {
        val subject = Observables.stateSubject(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        val subscription1 = subject.subscribe(observer1)
        val subscription2 = subject.subscribe(observer2)

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
    fun onComplete_Calls_Subscriber_OnComplete() {
        val subject = Observables.stateSubject(1)
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
        val subject = Observables.stateSubject(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)
        val observer2 = mockk<Observer<Int>>(relaxed = true)

        subject.subscribe(observer1)
        subject.subscribe(observer2)

        subject.onComplete()

        subject.assertNoSubscribers()
    }

    @Test
    fun onNext_Does_Not_Emit_Downstream_After_OnComplete() {
        val subject = Observables.stateSubject(1)
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
        val subject = Observables.stateSubject(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)

        subject.onComplete()
        val sub = subject.subscribe(observer1)

        assertTrue(sub.isDisposed)
    }

    @Test
    fun subscribe_Calls_Observer_OnComplete_When_Subscribed_After_OnComplete() {
        val subject = Observables.stateSubject(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)

        subject.onComplete()
        subject.subscribe(observer1)

        verify {
            observer1.onComplete()
        }
    }

    @Test
    fun subscribe_Emits_Latest_State_When_Subscribed_After_OnComplete() {
        val subject = Observables.stateSubject(1)
        val observer1 = mockk<Observer<Int>>(relaxed = true)

        subject.onComplete()
        subject.subscribe(observer1)

        verify {
            observer1.onNext(1)
        }
    }
}