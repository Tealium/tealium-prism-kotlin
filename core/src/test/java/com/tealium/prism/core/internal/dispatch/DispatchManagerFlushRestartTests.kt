package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.misc.ActivityManager
import com.tealium.prism.core.api.modules.Dispatcher
import com.tealium.prism.core.internal.barriers.BatchingBarrier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checks that dispatcher-list changes and barrier re-emissions don't restart the flush, using the real BarrierCoordinatorImpl and QueueManagerImpl of [DispatchLoopTestsBase].
 */
class DispatchManagerFlushRestartTests : DispatchLoopTestsBase() {

    /**
     * Leaves a closed flushable barrier (like a [BatchingBarrier] below its batch size) holding a backlog
     * after the launch flush ended, with the barriers re-emittable through [scopedBarriers].
     */
    private fun setUpClosedFlushableBarrierWithBacklog(other: Dispatcher? = null) {
        setUpLoop(dispatchLimit = 10) {
            listOf(batchingBarrier(batchSize = 10))
        }
        other?.let { onDispatchers.onNext(listOf(dispatcher, it)) }
        applicationStatus.onNext(ActivityManager.ApplicationStatus.Init())
        start()
        settle() // The launch flush ends immediately, as the queue is empty
        track(5)
    }

    @Test
    fun changing_The_Dispatchers_List_Does_Not_Dispatch_The_Backlog_Held_By_A_Closed_Flushable_Barrier() {
        val other = RecordingDispatcher("other", dispatchLimit = 10, scheduler = scheduler)
        setUpClosedFlushableBarrierWithBacklog(other)

        onDispatchers.onNext(listOf(dispatcher))
        onDispatchers.onNext(listOf(dispatcher, other))
        settle()

        assertSettled(sent = 0, queued = 5)
        assertEquals("The re-added dispatcher dispatched nothing", 0, other.sentIds.size)
    }

    @Test
    fun reemitting_The_Barriers_Does_Not_Dispatch_The_Backlog_Held_By_A_Closed_Flushable_Barrier() {
        setUpClosedFlushableBarrierWithBacklog()

        scopedBarriers.onNext(scopedBarriers.value)
        settle()

        assertSettled(sent = 0, queued = 5)
    }
}
