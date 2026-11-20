package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertSelfDisposingSubscriberIsReleased
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertStayingSubscriberIsRetained
import com.tealium.prism.core.internal.pubsub.ObservableUtils.getMockObserver
import com.tealium.prism.core.internal.pubsub.ObservableUtils.recordingObserver
import com.tealium.tests.common.assertThrows
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
        subject.onNext(2)
        subject.subscribe(observer1)

        assertEquals(1, subject.value)
        verifyOrder {
            observer1.onNext(1)
            observer1.onComplete()
        }
        confirmVerified(observer1)
    }

    @Test
    fun subscribe_Delivers_Reentrant_Value_To_Earlier_Observer_Immediately_And_To_New_Observer_After_Replay() {
        val subject = Observables.stateSubject(1)
        val log = mutableListOf<String>()
        subject.subscribe { log.add("earlier:$it") }
        log.clear()

        subject.subscribe { value ->
            log.add("new:$value:start")
            if (value == 1) subject.onNext(2)
            log.add("new:$value:end")
        }

        assertEquals(
            listOf("new:1:start", "earlier:2", "new:1:end", "new:2:start", "new:2:end"),
            log
        )
        assertEquals(2, subject.value)
    }

    @Test
    fun subscribe_Delivers_Reentrant_Value_After_Replay_When_Subscriber_Emits_From_Replay() {
        val subject = Observables.stateSubject(1)
        val log = mutableListOf<String>()

        subject.subscribe(recordingObserver(log) { value ->
            if (value == 1) subject.onNext(2)
            log.add("$value:end")
        })

        assertEquals(listOf("next:1", "1:end", "next:2", "2:end"), log)
        assertEquals(2, subject.value)
    }

    @Test
    fun subscribe_Delivers_Completion_After_Replay_When_Subscriber_Completes_From_Replay() {
        val subject = Observables.stateSubject(1)
        val log = mutableListOf<String>()
        subject.subscribe(recordingObserver(log, "earlier"))
        log.clear()

        subject.subscribe(recordingObserver(log, "new") { value ->
            subject.onComplete()
            log.add("new:$value:end")
        })
        subject.onNext(2)

        assertEquals(listOf("new:next:1", "earlier:complete", "new:1:end", "new:complete"), log)
    }

    @Test
    fun subscribe_Delivers_Value_Before_Completion_When_Earlier_Subscriber_Completes_On_Value_Emitted_From_Replay() {
        val subject = Observables.stateSubject(1)
        val log = mutableListOf<String>()
        subject.subscribe(recordingObserver(log, "earlier") { if (it == 2) subject.onComplete() })
        log.clear()

        subject.subscribe(recordingObserver(log, "new") { if (it == 1) subject.onNext(2) })

        assertEquals(
            listOf("new:next:1", "earlier:next:2", "earlier:complete", "new:next:2", "new:complete"),
            log
        )
    }

    @Test
    fun combine_Delivers_Reentrant_Value_When_Subscriber_Emits_Into_Last_Subscribed_Source() {
        val first = Observables.stateSubject(0)
        val second = Observables.stateSubject(0)
        val received = mutableListOf<Int>()

        Observables.combine(first, second) { a, b -> a + b }
            .subscribe { value ->
                received.add(value)
                if (value == 0) second.onNext(1)
            }

        assertEquals(listOf(0, 1), received)
    }

    @Test
    fun take_Receives_Nothing_After_Completion_When_Emitting_From_Its_Replay() {
        val subject = Observables.stateSubject(1)
        val log = mutableListOf<String>()

        subject.asObservable().take(1)
            .subscribe(recordingObserver(log) { if (it == 1) subject.onNext(2) })

        assertEquals(listOf("next:1", "complete"), log)
        subject.assertNoSubscribers()
    }

    @Test
    fun takeWhile_Receives_Nothing_After_Completion_When_Inclusive_And_Emitting_From_Its_Replay() {
        val subject = Observables.stateSubject(1)
        val log = mutableListOf<String>()

        subject.asObservable().takeWhile(inclusive = true) { false }
            .subscribe(recordingObserver(log) { subject.onNext(2) })
        subject.onNext(3)

        assertEquals(listOf("next:1", "complete"), log)
        subject.assertNoSubscribers()
    }

    @Test
    fun subscribe_Unregisters_Subscriber_When_It_Disposes_Itself_During_Replay() {
        Observables.stateSubject(1).assertSelfDisposingSubscriberIsReleased()
    }

    @Test
    fun subscribe_Retains_Subscriber_When_It_Does_Not_Dispose_Itself() {
        Observables.stateSubject(1).assertStayingSubscriberIsRetained()
    }

    @Test
    fun subscribe_Rethrows_Drops_Buffer_And_Forwards_Later_Values_When_Replay_Callback_Throws() {
        val subject = Observables.stateSubject(1)
        val received = mutableListOf<Int>()

        assertThrows<IllegalStateException> {
            subject.subscribe { value ->
                received.add(value)
                if (value == 1) {
                    subject.onNext(9)
                    throw IllegalStateException("replay failure")
                }
            }
        }
        subject.onNext(3)

        assertEquals(listOf(1, 3), received)
    }

    @Test
    fun subscribe_Drops_Buffer_And_Later_Values_Reach_Observer_When_Callback_Throws_During_Drain() {
        val subject = Observables.stateSubject(1)
        val earlier = mutableListOf<Int>()
        subject.subscribe { earlier.add(it) }
        val received = mutableListOf<Int>()

        assertThrows<IllegalStateException> {
            subject.subscribe { value ->
                received.add(value)
                if (value == 1) {
                    subject.onNext(2)
                    subject.onNext(3)
                }
                if (value == 2) throw IllegalStateException("drain failure")
            }
        }
        subject.onNext(5)

        assertEquals(listOf(1, 2, 3, 5), earlier)
        assertEquals(listOf(1, 2, 5), received)
    }
}
