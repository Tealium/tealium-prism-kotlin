package com.tealium.core.internal.dispatch

import com.tealium.core.api.Scheduler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/**
 * Models an asynchronous task, in that the callback provided will signal that the task is completed
 * when called.
 */
fun interface CompletableTask<T> {

    /**
     * The block of code to execute, prior to executing the given [onComplete] callback with the
     * result of the block.
     *
     * @param onComplete the block of code to execute on completion.
     */
    fun execute(onComplete: (T) -> Unit)
}

/**
 * Utility class to assist in certain task-based events.
 */
object Tasks {

    /**
     * Executes all given tasks, and then notifies the result using the given executor.
     * Resulting notification will be in the same order as the tasks were provided - although if any
     * tasks return a null result, then it will be omitted from the results.
     *
     * @param notifyOn The [Scheduler] to use when notifying the [notify] block
     * @param completableTasks The list of tasks to execute
     * @param notify The block of code to call once all [completableTasks] have been completed
     */
    fun <T> execute(
        notifyOn: Scheduler,
        completableTasks: List<CompletableTask<T>>,
        notify: (List<T>) -> Unit
    ) {
        if (completableTasks.isEmpty()) {
            notify(listOf())
            return
        }

        val results = ConcurrentHashMap<Int, T>(completableTasks.size)
        val latch = CountDownLatch(completableTasks.size)

        completableTasks.forEachIndexed { idx, task ->
            task.execute { result ->
                if (result != null) {
                    results[idx] = result
                }
                latch.countDown()

                if (latch.count == 0L) {
                    notifyOn.execute {
                        notify(results.entries.sortedBy { it.key }
                            .map { it.value }
                            .toList())
                    }
                }
            }
        }
    }
}
