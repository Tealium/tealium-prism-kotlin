package com.tealium.prism.core.internal.misc

import android.os.Handler
import android.os.Looper
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.Schedulers
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.internal.pubsub.CompletedDisposable
import com.tealium.prism.core.internal.pubsub.DisposableRunnable
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory


class SchedulersImpl(
    override val main: Scheduler = Scheduler.MAIN,
    override val tealium: Scheduler,
    override val io: Scheduler
) : Schedulers

/**
 * A [Scheduler] that executes work on a [Looper].
 *
 * The [execute] and [schedule] methods will execute tasks immediately if called from the same [Looper].
 * Otherwise, if the calling [Looper] is different, then tasks will be added to the back of the task queue.
 *
 * [schedule] with a delay parameter will always push tasks onto the back of the task queue.
 */
class LooperScheduler internal constructor(
    private val looper: Looper = Looper.getMainLooper(),
    private val handler: Handler = Handler(looper)
) : Scheduler {

    constructor(looper: Looper) : this(looper = looper, handler = Handler(looper))

    private fun isMyLooper(): Boolean = Looper.myLooper() == looper

    override fun execute(runnable: Runnable) {
        if (isMyLooper()) {
            runnable.run()
            return
        }

        handler.post(runnable)
    }

    override fun schedule(runnable: Runnable): Disposable {
        if (isMyLooper()) {
            runnable.run()
            return CompletedDisposable
        }

        val disposableRunnable = DisposableRunnable(runnable)
        handler.post(disposableRunnable)
        return disposableRunnable
    }

    override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable {
        val disposableRunnable = DisposableRunnable(runnable)
        handler.postDelayed(
            disposableRunnable,
            delay.unit.toMillis(delay.number)
        )
        return disposableRunnable
    }
}

/**
 * A [Scheduler] that executes work on a single thread only.
 *
 * The [execute] and [schedule] methods will execute tasks immediately if called on from the same thread.
 * Otherwise, if the calling thread is different, then tasks will be added to the back of the task queue.
 *
 * [schedule] with a delay parameter will always push tasks onto the back of the task queue.
 */
class SingleThreadedScheduler internal constructor(
    private val threadFactory: SingleThreadFactory? = SingleThreadFactory("tealium"),
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        threadFactory
    )
) : Scheduler {

    constructor(threadName: String) : this(SingleThreadFactory(threadName))

    /**
     * Custom ThreadFactory that maintains a property the contains the last [Thread] that it created.
     */
    class SingleThreadFactory(
        private val name: String
    ) : ThreadFactory {
        var thread: Thread? = null
            private set

        override fun newThread(r: Runnable?): Thread {
            return Thread(r, name).also {
                thread = it
            }
        }
    }

    private fun isCurrentThread(): Boolean =
        threadFactory != null && threadFactory.thread == Thread.currentThread()

    override fun execute(runnable: Runnable) {
        if (isCurrentThread()) {
            runnable.run()
            return
        }

        executorService.execute(runnable)
    }

    override fun schedule(runnable: Runnable): Disposable {
        if (isCurrentThread()) {
            runnable.run()
            return CompletedDisposable
        }

        val disposableRunnable = DisposableRunnable(runnable)
        executorService.submit(disposableRunnable)
        return disposableRunnable
    }

    override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable {
        val disposableRunnable = DisposableRunnable(runnable)
        executorService.schedule(disposableRunnable, delay.number, delay.unit)
        return disposableRunnable
    }
}

/**
 * A [Scheduler] that is backed by a pool of Threads
 */
class ThreadPoolScheduler internal constructor(
    private val executorService: ScheduledExecutorService = Executors.newScheduledThreadPool(0),
) : Scheduler {

    constructor(minThreads: Int) : this(Executors.newScheduledThreadPool(minThreads))

    override fun execute(runnable: Runnable) {
        executorService.execute(runnable)
    }

    override fun schedule(runnable: Runnable): Disposable {
        val disposableRunnable = DisposableRunnable(runnable)
        executorService.execute(disposableRunnable)
        return disposableRunnable
    }

    override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable {
        val disposableRunnable = DisposableRunnable(runnable)
        executorService.schedule(disposableRunnable, delay.number, delay.unit)
        return disposableRunnable
    }
}

/**
 * Scheduler that executes the given [Runnable] in a synchronous manner in the caller thread.
 * All methods run the provided [Runnable]s this way - even if they are scheduled for a later date.
 */
class SynchronousScheduler: Scheduler {
    override fun execute(runnable: Runnable) {
        runnable.run()
    }

    override fun schedule(runnable: Runnable): Disposable {
        runnable.run()
        return Disposables.disposed()
    }

    override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable =
        schedule(runnable)
}