package com.tealium.core.api.misc

import com.tealium.core.api.pubsub.Disposable

/**
 * A queue to submit tasks to for asynchronous execution.
 */
interface Scheduler {

    /**
     * Schedules a [Runnable] for execution at some point in the future. Tasks will typically be
     * executed on a first-in-first-out manner, but this depends on the [Scheduler] implementation.
     */
    fun execute(runnable: Runnable)

    /**
     * Schedules a [Runnable] for execution at some point in the future. Tasks will typically be
     * executed on a first-in-first-out manner, but this depends on the [Scheduler] implementation.
     *
     * Submitted tasks are cancellable by the returned [Disposable] instance.
     */
    fun schedule(runnable: Runnable): Disposable

    /**
     * Schedules a [Runnable] for execution at some point in the future, but delayed by the provided
     * [delay]. Tasks will not be executed before this delay.
     *
     * Submitted tasks are cancellable by the returned [Disposable] instance.
     */
    fun schedule(delay: TimeFrame, runnable: Runnable): Disposable
}
