package com.tealium.prism.core.api.misc

import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.internal.misc.LooperScheduler
import com.tealium.prism.core.internal.misc.SynchronousScheduler

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

    companion object {

        private val _main by lazy { LooperScheduler() }
        private val _synchronous by lazy { SynchronousScheduler() }

        /**
         * A shared [Scheduler] whose implementation submits tasks for execution on the Android main
         * thread.
         */
        @JvmStatic
        val MAIN: Scheduler
            get() = _main

        /**
         * A [Scheduler] implementation whereby all tasks that are submitted will be executed in a
         * synchronous manner. They will be executed immediately in the calling [Thread], and any
         * delays specified will be ignored.
         */
        @JvmStatic
        val SYNCHRONOUS: Scheduler
            get() = _synchronous
    }
}
