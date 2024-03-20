package com.tealium.core.api

import com.tealium.core.api.listeners.Disposable
import com.tealium.core.internal.persistence.TimeFrame

/**
 * A queue to submit tasks to for asynchronous execution.
 */
interface Scheduler {

    /**
     * Schedules a [Runnable] for execution at some point in the future. Tasks will be executed on a
     * first-in-first-out manner.
     */
    fun execute(runnable: Runnable)

    /**
     * Schedules a [Runnable] for execution at some point in the future. Tasks will be executed on a
     * first-in-first-out manner.
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
