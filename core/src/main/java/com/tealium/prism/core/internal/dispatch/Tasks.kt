package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.internal.pubsub.DisposableContainer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Models an asynchronous task, in that the callback provided will signal that the task is completed
 * when called.
 */
fun interface CompletableTask<T> {

    /**
     * The block of code to execute, prior to executing the given [onComplete] callback with the
     * result of the block.
     *
     * [onComplete] should be invoked exactly once, and may be invoked from any thread.
     * [Tasks.execute] ignores any invocation after the first.
     *
     * @param onComplete the block of code to execute on completion.
     * @return A [Disposable] that cancels the task's in-progress work when disposed.
     */
    fun execute(onComplete: (T) -> Unit): Disposable
}

/**
 * Utility class to assist in certain task-based events.
 */
object Tasks {

    /**
     * Executes all given tasks, and then notifies the result using the given executor.
     * [notify] is invoked at most once, after every task has signaled completion, unless the
     * returned [Disposable] has been disposed first.
     * Resulting notification will be in the same order as the tasks were provided - although if any
     * tasks return a null result, then it will be omitted from the results.
     *
     * @param notifyOn The [Scheduler] to use when notifying the [notify] block.
     * @param completableTasks The list of tasks to execute
     * @param notify The block of code to call once all [completableTasks] have been completed
     * @return A [Disposable] that, when disposed, cancels all in-progress tasks and prevents
     * [notify] from being called.
     */
    fun <T> execute(
        notifyOn: Scheduler,
        completableTasks: List<CompletableTask<T>>,
        notify: (List<T>) -> Unit
    ): Disposable {
        if (completableTasks.isEmpty()) {
            notify(listOf())
            return Disposables.disposed()
        }

        val container = DisposableContainer()
        val results = ConcurrentHashMap<Int, T>(completableTasks.size)
        val remaining = AtomicInteger(completableTasks.size)

        completableTasks.forEachIndexed { idx, task ->
            val completed = AtomicBoolean(false)
            container.add(task.execute { result ->
                if (!completed.compareAndSet(false, true)) return@execute

                if (result != null) {
                    results[idx] = result
                }

                if (remaining.decrementAndGet() == 0) {
                    val sorted = results.entries.sortedBy { it.key }.map { it.value }.toList()
                    notifyOn.execute {
                        if (!container.isDisposed) notify(sorted)
                    }
                }
            })
        }

        return container
    }
}
