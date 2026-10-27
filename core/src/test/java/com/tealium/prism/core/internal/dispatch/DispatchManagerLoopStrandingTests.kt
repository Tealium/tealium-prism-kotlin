package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.internal.barriers.BatchingBarrier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MT-2270: the dispatch loop doesn't strand dispatches in the queue, tested end to end with a real
 * [QueueManagerImpl] and [BarrierCoordinatorImpl].
 */
class DispatchManagerLoopStrandingTests : DispatchLoopTestsBase() {

    // region Barriers

    /**
     * With a batch size of 1, a dequeue that empties the pending queue closes the
     * [com.tealium.prism.core.internal.barriers.BatchingBarrier] from within the dequeue itself.
     */
    @Test
    fun batching_Barrier_Closing_During_A_Dequeue_Does_Not_Strand_The_Dequeued_Dispatches() {
        setUpLoop(dispatchLimit = 10) {
            listOf(batchingBarrier(batchSize = 1))
        }
        start()

        track(100)
        settle()

        assertSettled(sent = 100)
    }

    /**
     * Per-event completions lower the in-flight count one by one, and only a remainder
     * below the batch size waits.
     */
    @Test
    fun batching_Barrier_With_Split_Completions_Leaves_Only_A_Remainder_Below_The_Batch_Size() {
        setUpLoop(dispatchLimit = 10) {
            listOf(batchingBarrier(batchSize = null))
        }
        dispatcher.splitCompletions = true
        start()

        track(105)
        settle()

        val remaining = queued
        assertTrue("queued: $remaining", remaining < 10)
        assertSettled(sent = 105 - remaining, queued = remaining)
    }

    /**
     * A custom barrier reacting synchronously to the pending count closes from within the
     * dequeues.
     */
    @Test
    fun barrier_Closing_On_The_Pending_Count_During_A_Dequeue_Does_Not_Strand_The_Dequeued_Dispatches() {
        setUpLoop(dispatchLimit = 10) {
            listOf(PendingAtLeastBarrier(queueMetrics, threshold = 5))
        }
        start()

        track(60)
        settle()

        val remaining = queued
        assertTrue("queued: $remaining", remaining < 5)
        assertSettled(sent = 60 - remaining, queued = remaining)
    }

    /**
     * A custom barrier reacting synchronously to the in-flight count closes from within
     * every dequeue.
     */
    @Test
    fun barrier_Closing_On_The_InFlight_Count_During_A_Dequeue_Does_Not_Strand_The_Dequeued_Dispatches() {
        setUpLoop(dispatchLimit = 10) {
            listOf(InFlightAtMostBarrier(queueManager, maxInFlight = 5))
        }
        start()

        track(20)
        settle()

        assertSettled(sent = 20)
    }

    // endregion

    // region Consent

    /**
     * The first consent configuration starts a dequeue that closes the [BatchingBarrier]
     * from within itself.
     */
    @Test
    fun batching_Barrier_Closing_During_The_First_Consented_Dequeue_Does_Not_Strand_The_Dequeued_Dispatches() {
        setUpLoop(dispatchLimit = 10, consent = true) {
            listOf(batchingBarrier(batchSize = 10))
        }
        start()
        publishConsent(null)
        store(15)
        assertEquals("Nothing is dequeued without a consent configuration", 0, inFlight)

        publishConsent(consentConfiguration)
        settle()

        assertSettled(sent = 10, queued = 5)
    }

    /**
     * Removing the consent configuration during a dequeue doesn't strand the dequeued
     * batch, and the rest is sent once a configuration is published again.
     */
    @Test
    fun consent_Configuration_Removed_During_A_Dequeue_Does_Not_Strand_The_Dequeued_Dispatches() {
        setUpLoop(dispatchLimit = 10, consent = true)
        start()
        publishConsent(consentConfiguration)
        onFirstDequeue {
            publishConsent(null)
        }

        store(25)
        settle()
        assertSettled(sent = 10, queued = 15)

        publishConsent(consentConfiguration)
        settle()
        assertSettled(sent = 25)
    }

    // endregion

    // region Dispatchers list

    /**
     * The batch is still sent to the dispatcher of the request that dequeued it.
     */
    @Test
    fun dispatchers_Reemitted_During_A_Dequeue_Do_Not_Strand_The_Dequeued_Dispatches() {
        setUpLoop(dispatchLimit = 10)
        start()
        onFirstDequeue {
            onDispatchers.onNext(listOf(dispatcher))
        }

        store(25)
        settle()

        assertSettled(sent = 25)
    }

    // endregion

    // region In-flight and dispatchLimit

    /**
     * With a `dispatchLimit` above the in-flight maximum, the batches are capped at the
     * maximum, and each full batch still requests the next dequeue.
     */
    @Test
    fun the_Whole_Queue_Is_Sent_When_DispatchLimit_Is_Higher_Than_The_Maximum_InFlight() {
        setUpLoop(dispatchLimit = MAXIMUM + 10)
        store(120)

        start()
        settle()

        assertSettled(sent = 120)
        assertEquals(listOf(MAXIMUM, MAXIMUM, 120 - 2 * MAXIMUM), dispatcher.batchSizes)
    }

    /**
     * Same as above, with the queue filled after the loop started, so the first dequeue is
     * triggered by the enqueue rather than by the backlog at startup.
     */
    @Test
    fun the_Whole_Queue_Is_Sent_When_DispatchLimit_Is_Higher_Than_The_Maximum_InFlight_And_Enqueued_After_Start() {
        setUpLoop(dispatchLimit = MAXIMUM + 10)
        start()

        store(120)
        settle()

        assertSettled(sent = 120)
        assertEquals(listOf(MAXIMUM, MAXIMUM, 120 - 2 * MAXIMUM), dispatcher.batchSizes)
    }

    /**
     * A `dispatchLimit` of 0 dequeues one dispatch at a time, without requesting dequeues forever.
     */
    @Test
    fun dispatchLimit_Of_Zero_Dispatches_One_Event_At_A_Time_Without_Spinning() {
        assertDispatchesOneAtATime(dispatchLimit = 0)
    }

    /**
     * A negative `dispatchLimit` is handled as 1, not as "dequeue all".
     */
    @Test
    fun negative_DispatchLimit_Dispatches_One_Event_At_A_Time_Without_Spinning() {
        assertDispatchesOneAtATime(dispatchLimit = -1)
    }

    private fun assertDispatchesOneAtATime(dispatchLimit: Int) {
        setUpLoop(dispatchLimit)
        store(3)

        start()
        settle()

        assertSettled(sent = 3)
        assertEquals(listOf(1, 1, 1), dispatcher.batchSizes)
        assertTrue(
            "dequeues: ${queueManager.dequeueCount}",
            queueManager.dequeueCount <= 3 + 1
        )
    }

    /**
     * Draining a long backlog one dispatch at a time doesn't grow the stack.
     */
    @Test
    fun draining_A_Long_Backlog_Does_Not_Grow_The_Stack() {
        setUpLoop(dispatchLimit = 1)
        store(BACKLOG)

        start()
        settle()

        assertSettled(sent = BACKLOG)
        assertTrue(
            "max stack depth: ${dispatcher.maxStackDepth}",
            dispatcher.maxStackDepth < MAX_STACK_DEPTH
        )
    }

    // endregion

    // region Stop

    /**
     * A `stopDispatchLoop()` issued from within the first dequeue, while the loop is still being set
     * up, leaves it stopped.
     */
    @Test
    fun stopDispatchLoop_From_Within_The_First_Dequeue_Leaves_The_Loop_Stopped() {
        setUpLoop(dispatchLimit = 10)
        store(25)
        onFirstDequeue { stop() }

        start()
        settle()
        store(5)
        settle()

        assertTrue("sent: $sent", sent <= 10)
        // The stopped loop processes no completion, so nothing is deleted.
        assertEquals(30, queued)
    }

    // endregion

    // region Order

    /**
     * A backlog is dispatched in the order it was queued.
     */
    @Test
    fun a_Backlog_Is_Dispatched_In_Queue_Order() {
        setUpLoop(dispatchLimit = 10)
        store(105)

        start()
        settle()

        assertSentInOrder(105)
        assertSettled(sent = 105)
    }

    /**
     * The batches released by a [BatchingBarrier] are dispatched in the order they were queued.
     */
    @Test
    fun a_Burst_With_A_Batching_Barrier_Is_Dispatched_In_Queue_Order() {
        setUpLoop(dispatchLimit = 10) {
            listOf(batchingBarrier(batchSize = 10))
        }
        dispatcher.splitCompletions = true
        start()

        track(105)
        settle()

        val remaining = queued
        assertTrue("queued: $remaining", remaining < 10)
        assertSentInOrder(105 - remaining)
        assertSettled(sent = 105 - remaining, queued = remaining)
    }

    /**
     * The dequeues released every time the in-flight threshold is crossed keep the queue order.
     */
    @Test
    fun dispatches_Released_By_The_InFlight_Threshold_Are_Dispatched_In_Queue_Order() {
        setUpLoop(dispatchLimit = 1)
        store(120)

        start()
        settle()

        assertSentInOrder(120)
        assertSettled(sent = 120)
    }

    /**
     * Publishing the first consent configuration over a backlog dispatches it in queue order.
     */
    @Test
    fun a_Consent_Configuration_Published_Over_A_Backlog_Dispatches_In_Queue_Order() {
        setUpLoop(dispatchLimit = 10, consent = true)
        start()
        store(25)

        publishConsent(consentConfiguration)
        settle()

        assertSentInOrder(25)
        assertSettled(sent = 25)
    }

    /**
     * Re-emitting the dispatchers after the first dequeue, with the next one still
     * scheduled, keeps the queue order.
     */
    @Test
    fun dispatchers_Reemitted_Between_Dequeues_Keep_Queue_Order() {
        setUpLoop(dispatchLimit = 10)
        store(25)
        start()
        assertEquals("Only the first batch is dequeued before the scheduler runs", 10, sent)

        onDispatchers.onNext(listOf(dispatcher))
        settle()

        assertSentInOrder(25)
        assertSettled(sent = 25)
    }

    // endregion

    private companion object {
        const val MAXIMUM = DispatchManagerImpl.MAXIMUM_INFLIGHT_EVENTS_PER_DISPATCHER
        const val BACKLOG = 1000
        const val MAX_STACK_DEPTH = 300
    }
}
