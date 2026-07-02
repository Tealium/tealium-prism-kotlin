package com.tealium.prism.core.ktx

import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.ReplaySubject
import com.tealium.prism.core.api.pubsub.StateSubject
import com.tealium.prism.core.api.pubsub.Subject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscribableExtensionsTest {

    private lateinit var subject: Subject<String>

    @Before
    fun setUp() {
        subject = Observables.publishSubject()
    }

    // region asFlow()

    @Test
    fun asFlow_Emits_All_Values() = runTest(UnconfinedTestDispatcher()) {
        val collected = mutableListOf<String>()

        val job = launch {
            subject.asFlow().collect { collected.add(it) }
        }

        subject.onNext("a")
        subject.onNext("b")
        subject.onNext("c")
        subject.onComplete()

        job.join()

        assertEquals(listOf("a", "b", "c"), collected)
    }

    @Test
    fun asFlow_Completes_When_Subscribable_Completes() = runTest(UnconfinedTestDispatcher()) {
        val job = launch {
            subject.asFlow().collect { }
        }

        subject.onComplete()
        job.join()

        assertTrue(job.isCompleted)
    }

    @Test
    fun asFlow_Disposes_Subscription_On_Completion() = runTest(UnconfinedTestDispatcher()) {
        val job = launch {
            subject.asFlow().collect { }
        }

        subject.onComplete()
        job.join()

        assertEquals(0, subject.count)
    }

    @Test
    fun asFlow_Disposes_Subscription_When_Collector_Cancelled() = runTest(UnconfinedTestDispatcher()) {
        val job = launch {
            try {
                subject.asFlow().collect { }
            } catch (_: CancellationException) {
            }
        }

        job.cancel()
        job.join()

        assertEquals(0, subject.count)
    }

    @Test
    fun asFlow_Each_Collector_Gets_Independent_Subscription() = runTest(UnconfinedTestDispatcher()) {
        val collected1 = mutableListOf<String>()
        val collected2 = mutableListOf<String>()

        val job1 = launch {
            subject.asFlow().collect { collected1.add(it) }
        }
        val job2 = launch {
            subject.asFlow().collect { collected2.add(it) }
        }

        subject.onNext("x")
        subject.onComplete()

        job1.join()
        job2.join()

        assertEquals(listOf("x"), collected1)
        assertEquals(listOf("x"), collected2)
    }

    @Test
    fun asFlow_Does_Not_Emit_After_Cancellation() = runTest(UnconfinedTestDispatcher()) {
        val collected = mutableListOf<String>()

        val job = launch {
            try {
                subject.asFlow().collect { collected.add(it) }
            } catch (_: CancellationException) {
            }
        }

        subject.onNext("before")
        job.cancel()
        job.join()

        subject.onNext("after")

        // Only the emission before cancellation should have been collected.
        assertEquals(listOf("before"), collected)
    }

    // endregion

    // region collect()

    @Test
    fun collect_Receives_All_Emissions() = runTest(UnconfinedTestDispatcher()) {
        val collected = mutableListOf<String>()

        val job = launch {
            subject.collect { collected.add(it) }
        }

        subject.onNext("one")
        subject.onNext("two")
        subject.onComplete()

        job.join()

        assertEquals(listOf("one", "two"), collected)
    }

    @Test
    fun collect_Completes_When_Subscribable_Completes() = runTest(UnconfinedTestDispatcher()) {
        val job = launch {
            subject.collect { }
        }

        subject.onComplete()
        job.join()

        assertTrue(job.isCompleted)
    }

    // endregion

    // region ReplaySubject / StateSubject — replay behaviour via asFlow() and collect()

    @Test
    fun asFlow_ReplaySubject_Replays_Cached_Emissions_To_New_Collector() = runTest(UnconfinedTestDispatcher()) {
        val replay: ReplaySubject<String> = Observables.replaySubject(3)
        replay.onNext("one")
        replay.onNext("two")
        replay.onNext("three")

        val collected = mutableListOf<String>()
        val job = launch {
            replay.asFlow().collect { collected.add(it) }
        }

        replay.onComplete()
        job.join()

        // All three cached values should be replayed to the late subscriber.
        assertEquals(listOf("one", "two", "three"), collected)
    }

    @Test
    fun asFlow_ReplaySubject_Respects_Cache_Size_Limit() = runTest(UnconfinedTestDispatcher()) {
        val replay: ReplaySubject<String> = Observables.replaySubject(2)
        replay.onNext("one")
        replay.onNext("two")
        replay.onNext("three") // "one" is evicted from the cache

        val collected = mutableListOf<String>()
        val job = launch {
            replay.asFlow().collect { collected.add(it) }
        }

        replay.onComplete()
        job.join()

        // Only the two most recent values should be replayed.
        assertEquals(listOf("two", "three"), collected)
    }

    @Test
    fun collect_ReplaySubject_Replays_Cached_Emissions_To_New_Collector() = runTest(UnconfinedTestDispatcher()) {
        val replay: ReplaySubject<String> = Observables.replaySubject(3)
        replay.onNext("a")
        replay.onNext("b")

        val collected = mutableListOf<String>()
        val job = launch {
            replay.collect { collected.add(it) }
        }

        replay.onNext("c")
        replay.onComplete()
        job.join()

        // Cached values "a" and "b" should be replayed, followed by the live emission "c".
        assertEquals(listOf("a", "b", "c"), collected)
    }

    @Test
    fun asFlow_StateSubject_Replays_Current_Value_To_New_Collector() = runTest(UnconfinedTestDispatcher()) {
        val state: StateSubject<String> = Observables.stateSubject("initial")
        state.onNext("current")

        val collected = mutableListOf<String>()
        val job = launch {
            state.asFlow().collect { collected.add(it) }
        }

        state.onComplete()
        job.join()

        // The StateSubject should replay only its current value to the late subscriber.
        assertEquals(listOf("current"), collected)
    }

    @Test
    fun collect_StateSubject_Replays_Current_Value_Then_Subsequent_Emissions() = runTest(UnconfinedTestDispatcher()) {
        val state: StateSubject<String> = Observables.stateSubject("initial")

        val collected = mutableListOf<String>()
        val job = launch {
            state.collect { collected.add(it) }
        }

        state.onNext("updated")
        state.onComplete()
        job.join()

        // Initial value should be replayed, followed by the subsequent emission.
        assertEquals(listOf("initial", "updated"), collected)
    }

    // endregion
}
