package com.tealium.prism.core.internal.pubsub

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertNoSubscribers
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertSelfDisposingSubscriberIsReleased
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertStayingSubscriberIsRetained
import com.tealium.prism.core.internal.pubsub.ObservableUtils.assertSubscriberCount
import com.tealium.prism.core.internal.pubsub.ObservableUtils.recordingObserver
import com.tealium.tests.common.assertThrows
import com.tealium.tests.common.assertWithTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

private const val REENTRANT_VALUE = 3

class ReplaySubjectReentrancyTests {

    @Test
    fun subscribe_Delivers_Reentrant_Value_After_Replay_When_Size_3_Holding_2_Emits_On_First() {
        assertReentrantEmit(Observables.replaySubject(3), emitOn = 1)
    }

    @Test
    fun subscribe_Delivers_Reentrant_Value_After_Replay_When_Size_3_Holding_2_Emits_On_Last() {
        assertReentrantEmit(Observables.replaySubject(3), emitOn = 2)
    }

    @Test
    fun subscribe_Delivers_Reentrant_Value_After_Replay_When_Size_2_Full_Emits_On_First() {
        assertReentrantEmit(Observables.replaySubject(2), emitOn = 1)
    }

    @Test
    fun subscribe_Delivers_Reentrant_Value_After_Replay_When_Size_2_Full_Emits_On_Last() {
        assertReentrantEmit(Observables.replaySubject(2), emitOn = 2)
    }

    @Test
    fun subscribe_Delivers_Reentrant_Value_After_Replay_When_Unbounded_Emits_On_First() {
        assertReentrantEmit(Observables.replaySubject(), emitOn = 1)
    }

    @Test
    fun subscribe_Delivers_Reentrant_Value_After_Replay_When_Unbounded_Emits_On_Last() {
        assertReentrantEmit(Observables.replaySubject(), emitOn = 2)
    }

    @Test
    fun subscribe_Delivers_Reentrant_Value_After_Replay_When_Size_1_Full_Emits_On_Only() {
        assertReentrantEmit(Observables.replaySubject(1), cached = listOf(1), emitOn = 1)
    }

    @Test
    fun subscribe_Delivers_Reentrant_Value_To_Earlier_Observer_Immediately_And_To_New_Observer_After_Replay() {
        val subject = cachedSubject(1, 2)
        val log = mutableListOf<String>()
        subject.subscribe { log.add("earlier:$it") }
        log.clear()

        subject.subscribe { value ->
            log.add("new:$value:start")
            if (value == 1) subject.onNext(3)
            log.add("new:$value:end")
        }

        assertEquals(
            listOf(
                "new:1:start", "earlier:3", "new:1:end",
                "new:2:start", "new:2:end",
                "new:3:start", "new:3:end"
            ),
            log
        )
    }

    @Test
    fun combine_Delivers_Reentrant_Value_When_Subscriber_Emits_Into_Last_Subscribed_Source() {
        val first = cachedSubject(0)
        val second = cachedSubject(0)
        val received = mutableListOf<Int>()

        Observables.combine(first, second) { a, b -> a + b }
            .subscribe { value ->
                received.add(value)
                if (value == 0) second.onNext(1)
            }

        assertEquals(listOf(0, 1), received)
    }

    @Test
    fun subscribe_Delivers_Completion_After_Replay_When_Subscriber_Completes_From_Replay() {
        val subject = cachedSubject(1, 2)
        val log = mutableListOf<String>()
        subject.subscribe(recordingObserver(log, "earlier"))
        log.clear()

        subject.subscribe(recordingObserver(log, "new") { value ->
            if (value == 1) subject.onComplete()
            log.add("new:$value:end")
        })

        assertEquals(
            listOf(
                "new:next:1", "earlier:complete", "new:1:end",
                "new:next:2", "new:2:end",
                "new:complete"
            ),
            log
        )
    }

    @Test
    fun subscribe_Delivers_Value_Before_Completion_When_Earlier_Subscriber_Completes_On_Value_Emitted_From_Replay() {
        val subject = cachedSubject(1)
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
    fun take_Receives_Nothing_After_Completion_When_Emitting_From_Its_Replay() {
        val subject = cachedSubject(1, 2)
        val log = mutableListOf<String>()

        subject.asObservable().take(1)
            .subscribe(recordingObserver(log) { if (it == 1) subject.onNext(3) })

        assertEquals(listOf("next:1", "complete"), log)
        subject.assertNoSubscribers()
    }

    @Test
    fun takeWhile_Receives_Nothing_After_Completion_When_Inclusive_And_Emitting_From_Its_Replay() {
        val subject = cachedSubject(1, 2)
        val log = mutableListOf<String>()

        subject.asObservable().takeWhile(inclusive = true) { false }
            .subscribe(recordingObserver(log) { subject.onNext(3) })
        subject.onNext(4)

        assertEquals(listOf("next:1", "complete"), log)
        subject.assertNoSubscribers()
    }

    @Test
    fun subscribe_Unregisters_Subscriber_When_It_Disposes_Itself_During_Replay() {
        cachedSubject(1, 2).assertSelfDisposingSubscriberIsReleased()
    }

    @Test
    fun subscribe_Retains_Subscriber_When_It_Does_Not_Dispose_Itself() {
        cachedSubject(1).assertStayingSubscriberIsRetained()
    }

    @Test
    fun subscribe_Rethrows_Drops_Rest_Of_Replay_And_Buffer_Then_Forwards_Later_Values_When_Replay_Callback_Throws() {
        val subject = cachedSubject(1, 2)
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
        val subject = cachedSubject(1)
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

    @Test
    fun subscribe_Delivers_Completion_After_Buffered_Values_When_Completed_During_Drain() {
        val subject = cachedSubject(1)
        val log = mutableListOf<String>()

        subject.subscribe(recordingObserver(log) { value ->
            if (value == 1) subject.onNext(2)
            if (value == 2) {
                subject.onNext(3)
                subject.onComplete()
            }
        })

        assertEquals(listOf("next:1", "next:2", "next:3", "complete"), log)
    }

    @Test
    fun subscribe_Does_Not_Change_Ongoing_Replay_And_Empties_Cache_When_Replay_Callback_Clears_Cache() {
        val subject = cachedSubject(1, 2)
        val received = mutableListOf<Int>()

        subject.subscribe { value ->
            received.add(value)
            if (value == 1) subject.clear()
        }

        assertEquals(
            "The replay delivers the copy of the cache taken at subscription",
            listOf(1, 2),
            received
        )
        assertNull(subject.last())
    }

    @Test
    fun subscribe_Does_Not_Change_Ongoing_Replay_And_Trims_Cache_When_Replay_Callback_Resizes_Cache() {
        val subject = cachedSubject(1, 2, 3, 4)
        val received = mutableListOf<Int>()

        subject.subscribe { value ->
            received.add(value)
            if (value == 1) subject.resize(2)
        }

        assertEquals(
            "The replay delivers the copy of the cache taken at subscription",
            listOf(1, 2, 3, 4),
            received
        )
        val late = mutableListOf<Int>()
        subject.subscribe { late.add(it) }
        assertEquals(listOf(3, 4), late)
    }

    @Test
    fun subscribe_Replays_Both_Observers_When_Replay_Callback_Subscribes_Another_Observer() {
        val subject = cachedSubject(1, 2)
        val receivedByFirst = mutableListOf<Int>()
        val receivedBySecond = mutableListOf<Int>()

        subject.subscribe { value ->
            receivedByFirst.add(value)
            if (value == 1) {
                subject.subscribe { nested ->
                    receivedBySecond.add(nested)
                    if (nested == 1) subject.onNext(3)
                }
            }
        }

        assertEquals(listOf(1, 2, 3), receivedByFirst)
        assertEquals(listOf(1, 2, 3), receivedBySecond)
    }

    @Test
    fun subscribe_Does_Not_Interleave_Emissions_From_Other_Thread_With_Replay() {
        val subject = cachedSubject(1, 2)
        val received = mutableListOf<Int>()
        val emitter = Thread { subject.onNext(3) }

        subject.subscribe { value ->
            received.add(value)
            if (value == 1) {
                emitter.start()
                // BLOCKED means the emitter waits for the subject lock, which the replay holds
                assertWithTimeout(5_000, delay = 1) { emitter.state == Thread.State.BLOCKED }
            }
        }
        emitter.join(5_000)

        assertFalse(emitter.isAlive)
        assertEquals(listOf(1, 2, 3), received)
    }

    @Test
    fun subscribe_Delivers_Values_Emitted_During_Drain_In_Emission_Order() {
        val subject = cachedSubject(1, 2)
        val log = mutableListOf<String>()

        subject.subscribe(recordingObserver(log) { value ->
            if (value == 1) subject.onNext(3)
            if (value == 2) subject.onNext(4)
            if (value == 3) subject.onNext(5)
        })

        assertEquals(listOf("next:1", "next:2", "next:3", "next:4", "next:5"), log)
        assertEquals(5, subject.last())
    }

    @Test
    fun subscribe_Delivers_Null_Values_From_Cache_And_Replay_Callback_In_Emission_Order() {
        val subject = cachedSubject<Int?>(null, 2)
        val log = mutableListOf<String>()

        subject.subscribe(recordingObserver(log) { value ->
            if (value == null && log.size == 1) {
                subject.onNext(null)
                subject.onNext(3)
            }
        })

        assertEquals(listOf("next:null", "next:2", "next:null", "next:3"), log)
    }

    private fun assertReentrantEmit(
        subject: ReplaySubject<Int>,
        cached: List<Int> = listOf(1, 2),
        emitOn: Int
    ) {
        cached.forEach(subject::onNext)
        val received = mutableListOf<Int>()

        subject.subscribe { value ->
            received.add(value)
            if (value == emitOn) subject.onNext(REENTRANT_VALUE)
        }

        assertEquals(cached + REENTRANT_VALUE, received)
        subject.assertSubscriberCount(1)
    }

    private fun <T> cachedSubject(vararg values: T): ReplaySubject<T> {
        val subject = Observables.replaySubject<T>()
        values.forEach(subject::onNext)
        return subject
    }
}
