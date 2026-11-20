package com.tealium.tests.common

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.inMilliseconds
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.internal.pubsub.DisposableRunnable
import java.util.PriorityQueue

/**
 * Test utility [Scheduler] with a virtual clock.
 *
 * Tasks without a delay (or with a delay of zero or less) run synchronously in the calling thread,
 * like a queue that is already being executed on. Delayed tasks never run on their own: they run
 * when the clock is moved forward with [advanceTimeBy], in due time order.
 *
 * Differently from [ManualScheduler], which drains everything regardless of the delays, this lets
 * tests assert what happened before and after a given amount of time.
 */
class VirtualTimeScheduler : Scheduler {

    private var sequence = 0
    private var nowMs = 0L

    // PriorityQueue is not stable for clashing numbers, so use secondary "sequence" to ensure
    // proper order.
    private val delayed = PriorityQueue(10, compareBy<DelayedRunnable> { it.dueMs }.thenBy { it.sequence })

    override fun execute(runnable: Runnable) = runnable.run()

    override fun schedule(runnable: Runnable): Disposable {
        runnable.run()
        return Disposables.disposed()
    }

    override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable {
        val delayMs = delay.inMilliseconds()
        if (delayMs <= 0) return schedule(runnable)

        return DelayedRunnable(DisposableRunnable(runnable), nowMs + delayMs, sequence++)
            .also { delayed.add(it) }
            .runnable
    }

    /**
     * Moves the clock forward by [milliseconds], running in order every delayed task that becomes
     * due, including the ones scheduled by the tasks being run, as long as they are due in time.
     */
    fun advanceTimeBy(milliseconds: Long) {
        val target = nowMs + milliseconds
        while (delayed.peek()?.let { it.dueMs <= target } == true) {
            val next = delayed.poll()
            nowMs = next.dueMs
            next.runnable.run()
        }
        nowMs = target
    }

    private class DelayedRunnable(
        val runnable: DisposableRunnable,
        val dueMs: Long,
        val sequence: Int
    )
}
