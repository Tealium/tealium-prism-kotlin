package com.tealium.tests.common

import com.tealium.prism.core.api.misc.Scheduler
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Waits, up to [timeout], for the tasks already submitted to this scheduler to run.
 *
 * Only waits for the tasks queued before this call, not for the tasks they schedule later: a task
 * scheduled with a delay, even of 0, from a task that has not run yet is queued after this call's
 * own task, and may not have run when this returns. To wait for such work, await an explicit
 * condition instead, e.g. with a [Recorder].
 *
 * Only meaningful for a serial scheduler, such as [testTealiumScheduler].
 */
fun Scheduler.awaitQueuedTasks(timeout: Long = 5, unit: TimeUnit = TimeUnit.SECONDS) {
    val latch = CountDownLatch(1)
    execute { latch.countDown() }
    assertTrue("Timed out waiting for the queued tasks", latch.await(timeout, unit))
}

/**
 * Records the values it is given, from any thread, for the test thread to [await].
 */
class Recorder<T> {
    private class Recorded<T>(val value: T)

    private val values = LinkedBlockingQueue<Recorded<T>>()

    fun record(value: T) {
        values.add(Recorded(value))
    }

    /**
     * Returns the oldest value not yet awaited, waiting up to [timeout] (5 seconds by default)
     * for it.
     */
    fun await(timeout: Long = 5, unit: TimeUnit = TimeUnit.SECONDS): T {
        val recorded = values.poll(timeout, unit)
        assertNotNull("Expected a value to be recorded", recorded)
        return recorded!!.value
    }

    fun isEmpty(): Boolean = values.isEmpty()
}
