package com.tealium.tests.common

import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.internal.pubsub.DisposableRunnable
import java.util.PriorityQueue
import java.util.Queue

/**
 * Test utility [Scheduler] that allows for manual progression of the queued tasks via interaction
 * with the [queue] property
 */
class ManualScheduler : Scheduler {

    private var sequence = 0

    // PriorityQueue is not stable for clashing numbers, so use secondary "sequence" to ensure
    // proper order.
    val queue: Queue<ScheduledRunnable> =
        PriorityQueue(10, compareBy<ScheduledRunnable> { it.delayMs }.thenBy { it.sequence })

    override fun execute(runnable: Runnable) {
        queue.add(ScheduledRunnable(runnable, 0L, sequence++))
    }

    override fun schedule(runnable: Runnable): Disposable =
        schedule(0.milliseconds, runnable)

    override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable {
        val disposableRunnable = ScheduledRunnable(runnable, delay.number, sequence++)
        queue.add(disposableRunnable)
        return disposableRunnable
    }

    class ScheduledRunnable(
        val runnable: DisposableRunnable,
        val delayMs: Long,
        val sequence: Int
    ) :
        Disposable by runnable {
        constructor(runnable: Runnable, delayMs: Long, sequence: Int) : this(
            DisposableRunnable(runnable), delayMs, sequence
        )
    }
}