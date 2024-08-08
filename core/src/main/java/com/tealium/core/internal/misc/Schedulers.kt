package com.tealium.core.internal.misc

import android.os.Handler
import android.os.Looper
import com.tealium.core.api.misc.Scheduler
import com.tealium.core.api.misc.Schedulers
import com.tealium.core.api.pubsub.Disposable
import com.tealium.core.internal.pubsub.DisposableRunnable
import com.tealium.core.api.misc.TimeFrame
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService


class SchedulersImpl(
    override val main: Scheduler = MainScheduler(),
    override val tealium: Scheduler,
    override val io: Scheduler
) : Schedulers {

    constructor(
        mainLooper: Looper,
        tealiumExecutorService: ScheduledExecutorService,
        ioExecutorService: ExecutorService
    ) : this(
        main = MainScheduler(mainLooper),
        tealium = TealiumScheduler(tealiumExecutorService),
        io = IoScheduler(ioExecutorService, tealiumExecutorService)
    )

}

class MainScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : Scheduler {

    constructor(looper: Looper) : this(Handler(looper))

    override fun execute(runnable: Runnable) {
        handler.post(runnable)
    }

    override fun schedule(runnable: Runnable): Disposable {
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

class TealiumScheduler(
    private val executorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
) : Scheduler {
    override fun execute(runnable: Runnable) {
        executorService.execute(runnable)
    }

    override fun schedule(runnable: Runnable): Disposable {
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

class IoScheduler(
    private val executorService: Executor = Executors.newCachedThreadPool(),
    private val scheduler: ScheduledExecutorService
) : Scheduler {

    override fun execute(runnable: Runnable) {
        executorService.execute(runnable)
    }

    override fun schedule(runnable: Runnable): Disposable {
        val disposableRunnable = DisposableRunnable(runnable)
        scheduler.submit(disposableRunnable)
        return disposableRunnable
    }

    override fun schedule(delay: TimeFrame, runnable: Runnable): Disposable {
        val disposableRunnable = DisposableRunnable(runnable)
        scheduler.schedule({
            executorService.execute(disposableRunnable)
        }, delay.number, delay.unit)
        return disposableRunnable
    }
}
