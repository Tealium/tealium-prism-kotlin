package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.logger.logIfTraceEnabled
import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.inMilliseconds
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkException.CancelledException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.internal.logger.LogCategory
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Sends a [request], retrying it for as long as the [interceptorManager] decides to, and reports
 * the final result to the [completion] exactly once.
 *
 * Each attempt is sent through [attempt], and its result is forwarded to every
 * [RequestInterceptor] (see [InterceptorManager.notifyAttemptResult]), with the number of retries
 * sent before it, before the retry decision is made (see
 * [InterceptorManager.decide]). When a retry is decided, the loop waits for the winning
 * [RetryPolicy] before sending the next attempt.
 *
 * Disposing the loop completes it, immediately and exactly once, with a [Failure] of
 * [CancelledException]: any pending wait is cancelled and the in-flight attempt is disposed, which
 * aborts it on a best-effort basis.
 *
 * An attempt whose result arrives after the loop was disposed is still forwarded to the
 * interceptors, but it is neither retried nor reported to the [completion].
 *
 * Exceptions thrown by a [RequestInterceptor] are not caught by the loop.
 *
 * All the state is confined to the [tealiumScheduler], except for the disposed flag which can be
 * set from any thread by [dispose].
 *
 * @param request The request to send.
 * @param attempt Sends one attempt of the request. It must call back exactly once, on the
 * [tealiumScheduler], and return a [Disposable] that aborts the attempt; a second callback is
 * ignored.
 * @param interceptorManager Notified of the result of every attempt, and asked whether to retry it.
 * @param tealiumScheduler The scheduler that all the state is confined to.
 * @param logger Logs the retries.
 * @param completion Called with the final result of the request, on the [tealiumScheduler].
 */
internal class RetryLoop(
    private val request: HttpRequest,
    private val attempt: (HttpRequest, (NetworkResult) -> Unit) -> Disposable,
    private val interceptorManager: InterceptorManager,
    private val tealiumScheduler: Scheduler,
    private val logger: Logger,
    completion: (NetworkResult) -> Unit
) : Disposable {

    @Volatile
    private var disposed = false

    // tealium-only state
    private var started = false
    private var completed = false
    private var context: RetryContext = RetryContext.INITIAL
    private var inFlight: Disposable? = null
    private var pendingWait: Disposable? = null
    private var completion: ((NetworkResult) -> Unit)? = completion

    /**
     * `true` once the loop has either completed or been disposed.
     */
    override val isDisposed: Boolean
        get() = disposed

    /**
     * Sends the first attempt, on the [tealiumScheduler].
     *
     * Does nothing if the loop was already started or disposed: a loop disposed before it is started
     * completes with a [CancelledException] without sending any attempt.
     */
    fun start() {
        tealiumScheduler.execute {
            if (started || disposed) return@execute
            started = true

            sendAttempt()
        }
    }

    /**
     * Completes the loop with a [Failure] of [CancelledException], cancelling any pending wait and
     * aborting the in-flight attempt.
     *
     * Can be called from any thread. The completion happens on the [tealiumScheduler]: when called
     * from the [tealiumScheduler] itself, the completion is therefore called before this method
     * returns. Has no effect once the loop is completed.
     */
    override fun dispose() {
        disposed = true
        tealiumScheduler.execute(::cancel)
    }

    private fun cancel() {
        // `disposed` is also set by dispose(), so it can't tell whether there is anything to cancel
        if (completed) return

        complete(Failure(CancelledException))
    }

    private fun sendAttempt() {
        var called = false
        val handle = attempt(request) { result ->
            if (called) return@attempt
            called = true

            onAttemptCompleted(result)
        }
        // an attempt that called back inline is no longer in flight
        if (!called) inFlight = handle
    }

    private fun onAttemptCompleted(result: NetworkResult) {
        inFlight = null

        // a late result still reaches the interceptors; the context is still this attempt's, as
        // it only advances when the next attempt is sent
        interceptorManager.notifyAttemptResult(request, context.retryCount, result)
        if (disposed) return

        val retry = interceptorManager.decide(request, context, result)
        // an interceptor may have disposed the loop
        if (disposed) return

        if (retry == null) {
            complete(result)
            return
        }

        val next = retry.context
        val wait = retry.policy.wait(tealiumScheduler) { onWaitFinished(next) }
        if (wait == null) {
            complete(result)
            return
        }

        // waiting may have disposed the loop, e.g. from the event's subscribe
        if (disposed) {
            wait.dispose()
            return
        }
        pendingWait = wait

        logger.logIfTraceEnabled(LogCategory.HTTP_CLIENT) {
            "Retrying request ${request.url} Retry count: ${next.retryCount}"
        }
    }

    private fun onWaitFinished(next: RetryContext) {
        if (disposed) return

        pendingWait = null
        context = next
        sendAttempt()
    }

    private fun complete(result: NetworkResult) {
        completed = true
        disposed = true

        val wait = pendingWait
        val attempt = inFlight
        val completion = completion
        pendingWait = null
        inFlight = null
        this.completion = null

        wait?.dispose()
        attempt?.dispose()

        completion?.invoke(result)
    }
}

/**
 * Waits for this policy on the [scheduler], then calls [retry] on a later turn of the
 * [scheduler]; never inline. A delayed task, even with no delay, always runs on a later turn of
 * the [scheduler].
 *
 * @return A [Disposable] that cancels the wait, or `null` when this policy does not retry.
 */
private fun RetryPolicy.wait(scheduler: Scheduler, retry: () -> Unit): Disposable? =
    when (this) {
        DoNotRetry -> null
        is RetryPolicy.AfterDelay -> waitForDelay(delay, scheduler, retry)
        is RetryPolicy.AfterEvent -> waitForEvent(event, scheduler, retry)
    }

private fun waitForDelay(delay: TimeFrame, scheduler: Scheduler, retry: () -> Unit): Disposable {
    val holder = DelayWait(retry)
    // the scheduled task only references the holder, which drops the retry once disposed
    holder.task = scheduler.schedule(
        TimeFrame(max(0, delay.inMilliseconds()), TimeUnit.MILLISECONDS)
    ) { holder.run() }
    return holder
}

private fun <T> waitForEvent(
    event: Observable<T>,
    scheduler: Scheduler,
    retry: () -> Unit
): Disposable {
    // created before subscribing, as the event may emit during the subscription
    val holder = EventWait()
    val subscription = event
        // a RetryPolicy.AfterEvent may emit on any thread; this keeps the holder and the loop on
        // the scheduler, and runs inline when the event already emits on it, as the SDK's do;
        // operators go after observeOn, so that take(1) runs and is disposed on the scheduler
        .observeOn(scheduler)
        .take(1)
        .subscribe {
            if (!holder.isDisposed) {
                // never retry inline: an event that emits during the subscription would otherwise
                // retry inside wait(), before the loop stores the wait. A delayed task, even with
                // no delay, always runs on a later turn
                holder.scheduledTask = scheduler.schedule(TimeFrame(0, TimeUnit.MILLISECONDS)) {
                    retry()
                }
            }
        }

    holder.subscription = subscription
    return holder
}

/**
 * The wait for a [RetryPolicy.AfterDelay]: the delayed [task], which calls [run].
 *
 * Disposing a scheduled task only flags it, so the task stays queued until its deadline. The
 * [retry], and through it the loop and its request, is therefore dropped once the wait is disposed
 * or has run, leaving the queued task with only this holder.
 */
// TODO: DisposableRunnable could drop its wrapped runnable on dispose, which would release every
//  disposed scheduled task, not only this one; this holder could then be removed.
private class DelayWait(retry: () -> Unit) : Disposable {
    private var retry: (() -> Unit)? = retry
    var task: Disposable? = null

    override val isDisposed: Boolean
        get() = retry == null

    fun run() {
        val retry = retry ?: return
        this.retry = null
        task = null

        retry()
    }

    override fun dispose() {
        retry = null
        task?.dispose()
        task = null
    }
}

/**
 * The wait for a [RetryPolicy.AfterEvent]: the subscription to its event, and then the task that
 * retries.
 */
private class EventWait : Disposable {
    var scheduledTask: Disposable? = null
        set(value) {
            field = value
            if (disposed) value?.dispose()
        }
    var subscription: Disposable? = null
        set(value) {
            field = value
            if (disposed) value?.dispose()
        }
    private var disposed = false

    override val isDisposed: Boolean
        get() = disposed

    override fun dispose() {
        if (disposed) return
        disposed = true

        scheduledTask?.dispose()
        scheduledTask = null
        subscription?.dispose()
        subscription = null
    }
}
