package com.tealium.core.internal.pubsub

import com.tealium.core.api.pubsub.Observables
import com.tealium.core.api.pubsub.Observer
import com.tealium.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.core.internal.pubsub.ObservableUtils.getMockObserver
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class StateSubjectTests {

    @Test
    fun stateSubject_EmitsPresetDefault_OnSubscribe() {
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
    fun stateSubject_EmitsLatestOnSubscription_ToMultipleSubscribers() {
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
    fun stateSubject_Value_IsTheLatest() {
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
    fun stateSubject_Value_IsUpdated_BeforeEmission() {
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
    fun stateSubject_Disposal_DoesNotAffectOtherSubscribers() {
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
    fun stateSubject_Dispose_ClearsSubscription() {
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
}