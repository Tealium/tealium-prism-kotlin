package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.logger.Logger
import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Observable
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.prism.core.api.pubsub.Observer
import com.tealium.prism.core.internal.pubsub.Subscription
import com.tealium.tests.common.ManualScheduler
import com.tealium.tests.common.Recorder
import com.tealium.tests.common.RecordingInterceptor
import com.tealium.tests.common.SystemLogger
import com.tealium.tests.common.assertCancelled
import com.tealium.tests.common.awaitQueuedTasks
import com.tealium.tests.common.testTealiumScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference
import java.net.URL
import java.util.Collections.synchronizedList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RetryLoopTests {

    private val request: HttpRequest = HttpRequest.get("http://localhost").build()
    private val logger: Logger = SystemLogger

    private lateinit var scheduler: ManualScheduler
    private lateinit var attempts: FakeAttempts
    private lateinit var calls: MutableList<String>
    private lateinit var results: MutableList<NetworkResult>

    @Before
    fun setUp() {
        scheduler = ManualScheduler()
        attempts = FakeAttempts()
        calls = mutableListOf()
        results = mutableListOf()
    }

    @Test
    fun start_Sends_First_Attempt_On_Tealium() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls))
        scheduler.runAll()
        assertEquals(0, attempts.sent.size)

        loop.start()
        // queued on the scheduler, not sent from the caller
        assertEquals(0, attempts.sent.size)
        scheduler.runAll()

        assertEquals(1, attempts.sent.size)
        assertFalse(loop.isDisposed)
    }

    @Test
    fun start_Does_Nothing_When_Already_Started() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls))

        loop.start()
        loop.start()
        scheduler.runAll()

        assertEquals(1, attempts.sent.size)
    }

    @Test
    fun start_Completes_With_Cancelled_Without_Attempt_When_Disposed_Before() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls))

        loop.dispose()
        loop.start()
        scheduler.runAll()

        assertEquals(0, attempts.sent.size)
        assertCancelledOnce()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun start_Notifies_Every_Interceptor_Before_Deciding_When_Attempt_Completes() {
        val loop = createLoop(
            RecordingInterceptor("first", calls),
            RecordingInterceptor("second", calls)
        )
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)

        assertEquals(
            listOf(
                "first.onAttemptResult(0)",
                "second.onAttemptResult(0)",
                "second.shouldRetry(0)",
                "first.shouldRetry(0)"
            ),
            calls
        )
        assertEquals(listOf(failure), results)
    }

    @Test
    fun start_Completes_With_Result_And_Is_Disposed_When_Not_Retried() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls))
        loop.start()
        scheduler.runAll()

        attempts.respond(0, success)

        assertEquals(listOf(success), results)
        assertTrue(loop.isDisposed)
        assertEquals(0, attempts.sent[0].disposeCount)
    }

    @Test
    fun start_Sends_Next_Attempt_When_Retry_Delay_Elapses() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls) { count ->
            if (count < 2) RetryPolicy.AfterDelay(100.milliseconds) else DoNotRetry
        })
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)
        assertEquals(2, attempts.sent.size)
        attempts.respond(1, failure)
        assertEquals(3, attempts.sent.size)
        attempts.respond(2, success)

        assertEquals(listOf(success), results)
        assertEquals(
            listOf("interceptor.shouldRetry(0)", "interceptor.shouldRetry(1)", "interceptor.shouldRetry(2)"),
            calls.filter { it.contains("shouldRetry") }
        )
    }

    @Test
    fun start_Notifies_Total_RetryCount_Of_Each_Attempt_When_Retried() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls) { count ->
            if (count < 2) RetryPolicy.AfterDelay(0.milliseconds) else DoNotRetry
        })
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)
        scheduler.runAll()
        attempts.respond(1, failure)
        scheduler.runAll()
        attempts.respond(2, success)

        assertEquals(listOf(success), results)
        assertEquals(
            listOf(
                "interceptor.onAttemptResult(0)",
                "interceptor.onAttemptResult(1)",
                "interceptor.onAttemptResult(2)"
            ),
            calls.filter { it.contains("onAttemptResult") }
        )
    }

    @Test
    fun start_Does_Not_Reset_RetryCount_Of_Attempts_When_Retry_Waited_For_Event() {
        val event = RecordingEvent<Unit>()
        var offline = true
        val loop = createLoop(
            RecordingInterceptor("retryable", calls) { count ->
                if (count == 0) RetryPolicy.AfterDelay(0.milliseconds) else DoNotRetry
            },
            // asked first: waits for the event while "offline", like a connectivity wait
            RecordingInterceptor("connectivity", calls) {
                if (offline) RetryPolicy.AfterEvent(event) else DoNotRetry
            }
        )
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)
        offline = false
        event.emit(Unit)
        scheduler.runAll()
        attempts.respond(1, failure)
        scheduler.runAll()
        attempts.respond(2, success)

        assertEquals(listOf(success), results)
        assertEquals(3, attempts.sent.size)
        assertEquals(
            listOf(
                "retryable.onAttemptResult(0)",
                "connectivity.onAttemptResult(0)",
                "retryable.onAttemptResult(1)",
                "connectivity.onAttemptResult(1)",
                "retryable.onAttemptResult(2)",
                "connectivity.onAttemptResult(2)"
            ),
            calls.filter { it.contains("onAttemptResult") }
        )
        // unlike the retry count, the consecutive counts are per interceptor: the second retry is
        // the first one the retryable interceptor proposes
        assertEquals(
            listOf("connectivity.shouldRetry(0)", "connectivity.shouldRetry(1)", "retryable.shouldRetry(0)"),
            calls.filter { it.contains("shouldRetry") }.take(3)
        )
    }

    @Test
    fun start_Sends_Next_Attempt_When_Retry_Event_Emits() {
        val event = RecordingEvent<Unit>()
        val loop = createLoop(RecordingInterceptor("interceptor", calls) { count ->
            if (count == 0) RetryPolicy.AfterEvent(event) else DoNotRetry
        })
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)
        assertEquals(1, attempts.sent.size)
        assertEquals(1, event.subscribeCount)

        event.emit(Unit)
        scheduler.runAll()
        assertEquals(2, attempts.sent.size)

        attempts.respond(1, success)
        assertEquals(listOf(success), results)
    }

    @Test
    fun start_Does_Not_Ask_Interceptors_When_MaxRetryCount_Is_Zero() {
        val loop = createLoop(
            RecordingInterceptor("interceptor", calls) { RetryPolicy.AfterDelay(0.milliseconds) },
            maxRetryCount = 0
        )
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)

        assertEquals(1, attempts.sent.size)
        assertEquals(listOf("interceptor.onAttemptResult(0)"), calls)
        assertEquals(listOf(failure), results)
    }

    @Test
    fun start_Stops_Retrying_When_MaxRetryCount_Is_Reached() {
        val loop = createLoop(
            RecordingInterceptor("interceptor", calls) { RetryPolicy.AfterDelay(0.milliseconds) },
            maxRetryCount = 2
        )
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)
        attempts.respond(1, failure)
        attempts.respond(2, failure)

        assertEquals(3, attempts.sent.size)
        assertEquals(
            listOf("interceptor.shouldRetry(0)", "interceptor.shouldRetry(1)"),
            calls.filter { it.contains("shouldRetry") }
        )
        assertEquals(listOf(failure), results)
    }

    @Test
    fun start_Ignores_Second_Callback_When_Attempt_Calls_Back_Twice() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls))
        loop.start()
        scheduler.runAll()

        attempts.respond(0, success)
        attempts.respond(0, failure)

        assertEquals(listOf(success), results)
        assertEquals(listOf("interceptor.onAttemptResult(0)", "interceptor.shouldRetry(0)"), calls)
    }

    @Test
    fun dispose_Completes_With_Cancelled_And_Aborts_Attempt_When_Attempt_Is_In_Flight() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls))
        loop.start()
        scheduler.runAll()

        loop.dispose()
        assertTrue(loop.isDisposed)
        scheduler.runAll()

        assertCancelledOnce()
        assertEquals(1, attempts.sent[0].disposeCount)
    }

    @Test
    fun dispose_Notifies_Interceptors_Without_Deciding_When_Result_Arrives_After_Dispose() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls) { RetryPolicy.AfterDelay(0.milliseconds) })
        loop.start()
        scheduler.runAll()
        loop.dispose()
        scheduler.runAll()

        attempts.respond(0, success)

        assertCancelledOnce()
        assertEquals(listOf("interceptor.onAttemptResult(0)"), calls)
        assertEquals(1, attempts.sent.size)
    }

    @Test
    fun dispose_Completes_With_Cancelled_When_Result_Task_Is_Queued_Before_Cancel_Task() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls))
        loop.start()
        scheduler.runAll()

        scheduler.execute { attempts.sent[0].callback(success) }
        loop.dispose()
        scheduler.runAll()

        assertCancelledOnce()
        assertEquals(listOf("interceptor.onAttemptResult(0)"), calls)
    }

    @Test
    fun dispose_Does_Nothing_When_Already_Completed() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls))
        loop.start()
        scheduler.runAll()
        attempts.respond(0, success)

        loop.dispose()
        scheduler.runAll()

        assertEquals(listOf(success), results)
        assertEquals(0, attempts.sent[0].disposeCount)
    }

    @Test
    fun dispose_Completes_Once_When_Called_Twice() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls))
        loop.start()
        scheduler.runAll()

        loop.dispose()
        loop.dispose()
        scheduler.runAll()

        assertCancelledOnce()
        assertEquals(1, attempts.sent[0].disposeCount)
    }

    @Test
    fun dispose_Cancels_Pending_Delay_When_Waiting_To_Retry() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls) { RetryPolicy.AfterDelay(100.milliseconds) })
        loop.start()
        scheduler.runAll()
        // delivered directly, as draining the scheduler would also run the delay
        attempts.sent[0].callback(failure)
        val wait = scheduler.queue.single()

        loop.dispose()
        scheduler.runAll()

        assertTrue(wait.isDisposed)
        assertCancelledOnce()
        assertEquals(1, attempts.sent.size)
    }

    @Test
    fun dispose_Cancels_Event_Subscription_When_Event_Never_Emits() {
        val event = RecordingEvent<Unit>()
        val loop = createLoop(RecordingInterceptor("interceptor", calls) { RetryPolicy.AfterEvent(event) })
        loop.start()
        scheduler.runAll()
        attempts.respond(0, failure)
        assertFalse(event.isSubscriptionDisposed)

        loop.dispose()
        scheduler.runAll()

        assertTrue(event.isSubscriptionDisposed)
        assertCancelledOnce()
        assertEquals(1, attempts.sent.size)
    }

    @Test
    fun dispose_Does_Not_Send_Next_Attempt_When_Delay_Fires_After_Dispose() {
        val loop = createLoop(RecordingInterceptor("interceptor", calls) { RetryPolicy.AfterDelay(0.milliseconds) })
        loop.start()
        scheduler.runAll()
        attempts.sent[0].callback(failure)

        // the delay is queued ahead of the cancel task
        loop.dispose()
        scheduler.runAll()

        assertEquals(1, attempts.sent.size)
        assertCancelledOnce()
    }

    @Test
    fun dispose_Does_Not_Send_Next_Attempt_When_Event_Fires_After_Dispose() {
        val event = RecordingEvent<Unit>()
        val loop = createLoop(RecordingInterceptor("interceptor", calls) { RetryPolicy.AfterEvent(event) })
        loop.start()
        scheduler.runAll()
        attempts.respond(0, failure)

        event.emit(Unit)
        loop.dispose()
        scheduler.runAll()

        assertEquals(1, attempts.sent.size)
        assertCancelledOnce()
    }

    @Test
    fun start_Retries_Once_When_Event_Emits_Twice() {
        val event = RecordingEvent<Unit>()
        val loop = createLoop(RecordingInterceptor("interceptor", calls) { count ->
            if (count == 0) RetryPolicy.AfterEvent(event) else DoNotRetry
        })
        loop.start()
        scheduler.runAll()
        attempts.respond(0, failure)

        event.emit(Unit)
        event.emit(Unit)
        scheduler.runAll()

        assertEquals(2, attempts.sent.size)
        assertEquals(1, event.subscribeCount)
    }

    @Test
    fun dispose_Completes_With_Cancelled_Without_Deciding_When_Called_From_OnAttemptResult() {
        lateinit var loop: RetryLoop
        loop = createLoop(
            RecordingInterceptor("interceptor", calls, onResult = { loop.dispose() }) { RetryPolicy.AfterDelay(0.milliseconds) }
        )
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)

        assertCancelledOnce()
        assertEquals(listOf("interceptor.onAttemptResult(0)"), calls)
    }

    @Test
    fun dispose_Completes_With_Cancelled_When_Called_From_ShouldRetry_That_Declines() {
        lateinit var loop: RetryLoop
        loop = createLoop(RecordingInterceptor("interceptor", calls) {
            loop.dispose()
            DoNotRetry
        })
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)

        assertCancelledOnce()
    }

    @Test
    fun dispose_Does_Not_Wait_When_Called_From_ShouldRetry_That_Retries() {
        val event = RecordingEvent<Unit>()
        lateinit var loop: RetryLoop
        loop = createLoop(RecordingInterceptor("interceptor", calls) {
            loop.dispose()
            RetryPolicy.AfterEvent(event)
        })
        loop.start()
        scheduler.runAll()

        attempts.respond(0, failure)

        assertEquals(0, event.subscribeCount)
        assertCancelledOnce()
        assertEquals(1, attempts.sent.size)
    }

    @Test
    fun start_Releases_Completion_When_Completed() {
        val capturing = createLoopCapturing()
        capturing.loop.start()
        scheduler.runAll()

        attempts.respond(0, success)
        attempts.sent.clear()

        assertCollected(capturing.captured)
        // the caller still holds the loop
        assertTrue(capturing.loop.isDisposed)
    }

    @Test
    fun dispose_Releases_Completion_When_Cancelled() {
        val capturing = createLoopCapturing()
        capturing.loop.start()
        scheduler.runAll()

        capturing.loop.dispose()
        scheduler.runAll()
        attempts.sent.clear()

        assertCollected(capturing.captured)
        assertTrue(capturing.loop.isDisposed)
    }

    @Test
    fun dispose_Releases_Loop_From_Event_When_Waiting_For_Event() {
        val event = Observables.publishSubject<Unit>()
        val loopRef = startLoopWaitingFor(event)
        scheduler.runAll()
        // the attempt has completed, so it no longer references the loop
        attempts.sent.clear()

        loopRef.get()!!.dispose()
        scheduler.runAll()

        assertCollected(loopRef)
        assertCancelledOnce()
    }

    @Test
    fun dispose_Releases_Loop_When_Delay_Task_Is_Still_Queued() {
        val loopRef = startLoopWaitingFor(RetryPolicy.AfterDelay(60_000.milliseconds))
        // the attempt has completed, so it no longer references the loop
        attempts.sent.clear()

        loopRef.get()!!.dispose()
        // runs the cancel, leaving the delay task queued as its deadline has not passed
        while (scheduler.queue.peek()?.delayMs == 0L) {
            scheduler.queue.poll()!!.runnable.run()
        }

        assertCancelledOnce()
        assertEquals(listOf(60_000L), scheduler.queue.map { it.delayMs })
        assertCollected(loopRef)
    }

    @Test
    fun dispose_Completes_Once_When_Called_Inline_From_Completion() {
        val completions = synchronizedList(mutableListOf<NetworkResult>())
        val disposedInCompletion = synchronizedList(mutableListOf<Boolean>())
        lateinit var loop: RetryLoop
        loop = RetryLoop(request, callingBackOnTealium(success), InterceptorManager(emptyList()),
            testTealiumScheduler, logger) { result ->
            completions.add(result)
            disposedInCompletion.add(loop.isDisposed)
            loop.dispose()
        }

        loop.start()
        testTealiumScheduler.awaitQueuedTasks()

        assertEquals(listOf(success), completions.toList())
        // the state is settled before the completion is called
        assertEquals(listOf(true), disposedInCompletion.toList())
        assertTrue(loop.isDisposed)
    }

    @Test
    fun dispose_Completes_With_Cancelled_On_Tealium_When_Called_From_Another_Thread() {
        val handle = FakeAttempt {}
        val completed = CountDownLatch(1)
        var completionThread: Thread? = null
        val completions = synchronizedList(mutableListOf<NetworkResult>())
        val loop = RetryLoop(request, { _, _ -> handle }, InterceptorManager(emptyList()),
            testTealiumScheduler, logger) { result ->
            completions.add(result)
            completionThread = Thread.currentThread()
            completed.countDown()
        }
        loop.start()
        testTealiumScheduler.awaitQueuedTasks()

        loop.dispose()

        assertTrue(completed.await(5, TimeUnit.SECONDS))
        testTealiumScheduler.awaitQueuedTasks()
        assertEquals(1, completions.size)
        assertCancelled(completions[0])
        assertEquals("tealium-test", completionThread?.name)
        assertEquals(1, handle.disposeCount)
    }

    @Test
    fun dispose_Completes_During_Dispose_When_Called_On_Tealium() {
        val completions = mutableListOf<NetworkResult>()
        val countAfterDispose = mutableListOf<Int>()
        val loop = RetryLoop(request, { _, _ -> FakeAttempt {} }, InterceptorManager(emptyList()),
            testTealiumScheduler, logger) { completions.add(it) }
        loop.start()

        testTealiumScheduler.execute {
            loop.dispose()
            countAfterDispose.add(completions.size)
        }
        testTealiumScheduler.awaitQueuedTasks()

        assertEquals(listOf(1), countAfterDispose)
    }

    @Test
    fun start_Retries_On_Later_Tealium_Turn_When_Event_Emits_On_Subscribe() {
        val sentInFirstTurn = synchronizedList(mutableListOf<Int>())
        val sent = synchronizedList(mutableListOf<(NetworkResult) -> Unit>())
        val sentCounts = Recorder<Int>()
        val interceptor = RecordingInterceptor("interceptor", synchronizedList(mutableListOf())) { count ->
            if (count == 0) RetryPolicy.AfterEvent(Observables.just(Unit)) else DoNotRetry
        }
        val loop = RetryLoop(request, { _, callback ->
            sent.add(callback)
            sentCounts.record(sent.size)
            FakeAttempt {}
        }, InterceptorManager(listOf(interceptor)), testTealiumScheduler, logger) {}

        testTealiumScheduler.execute {
            loop.start()
            // inline on tealium: decides, then subscribes to an event that emits immediately
            sent[0](failure)
            sentInFirstTurn.add(sent.size)
        }

        // the retry is scheduled from inside the first task, so draining the queue once could
        // return before it runs: wait for the second attempt instead
        assertEquals(1, sentCounts.await())
        assertEquals(2, sentCounts.await())
        assertEquals(listOf(1), sentInFirstTurn.toList())
    }

    @Test
    fun dispose_Releases_Subscription_When_Called_From_Event_Subscribe() {
        lateinit var loop: RetryLoop
        val event = RecordingEvent<Unit> { loop.dispose() }
        val completions = mutableListOf<NetworkResult>()
        val interceptor = RecordingInterceptor("interceptor", mutableListOf()) { RetryPolicy.AfterEvent(event) }
        loop = RetryLoop(request, callingBackOnTealium(failure), InterceptorManager(listOf(interceptor)),
            testTealiumScheduler, logger) { completions.add(it) }

        loop.start()
        testTealiumScheduler.awaitQueuedTasks()
        testTealiumScheduler.awaitQueuedTasks()

        assertTrue(event.isSubscriptionDisposed)
        assertEquals(1, completions.size)
        assertCancelled(completions[0])
    }

    private fun createLoop(vararg interceptors: RequestInterceptor, maxRetryCount: Int? = null): RetryLoop =
        RetryLoop(
            request,
            attempts::send,
            InterceptorManager(interceptors.toList(), maxRetryCount),
            scheduler,
            logger
        ) { results.add(it) }

    /**
     * Creates a loop whose completion is the only strong reference to a [Captured].
     */
    private fun createLoopCapturing(): CapturingLoop {
        val captured = Captured()
        val loop = RetryLoop(request, attempts::send, InterceptorManager(emptyList()), scheduler, logger) {
            captured.results.add(it)
        }
        return CapturingLoop(loop, WeakReference(captured))
    }

    /**
     * Starts a loop whose single attempt fails and waits for [event], without keeping a strong
     * reference to it.
     */
    private fun startLoopWaitingFor(event: Observable<Unit>): WeakReference<RetryLoop> =
        startLoopWaitingFor(RetryPolicy.AfterEvent(event))

    /**
     * Starts a loop whose single attempt fails and waits for [policy], without keeping a strong
     * reference to it. Tasks scheduled by the wait are left queued.
     */
    private fun startLoopWaitingFor(policy: RetryPolicy): WeakReference<RetryLoop> {
        val interceptor = RecordingInterceptor("interceptor", calls) { policy }
        val loop = RetryLoop(request, attempts::send, InterceptorManager(listOf(interceptor)), scheduler, logger) {
            results.add(it)
        }
        loop.start()
        scheduler.runAll()
        attempts.sent[0].callback(failure)
        return WeakReference(loop)
    }

    private fun assertCancelledOnce() {
        assertEquals(1, results.size)
        assertCancelled(results[0])
    }

    private fun assertCollected(ref: WeakReference<*>) {
        repeat(50) {
            if (ref.get() == null) return
            System.gc()
            Thread.sleep(20)
        }
        assertNull("Expected the reference to be collected", ref.get())
    }

    /**
     * An attempt that calls back with [result] on the [testTealiumScheduler].
     */
    private fun callingBackOnTealium(result: NetworkResult): (HttpRequest, (NetworkResult) -> Unit) -> Disposable =
        { _, callback ->
            testTealiumScheduler.execute { callback(result) }
            FakeAttempt {}
        }

    /**
     * Records every attempt the loop sends, and responds to them on the [scheduler].
     */
    private inner class FakeAttempts {
        val sent = mutableListOf<FakeAttempt>()

        fun send(request: HttpRequest, callback: (NetworkResult) -> Unit): Disposable =
            FakeAttempt(callback).also { sent.add(it) }

        /**
         * Calls back the attempt at [index] with [result] on the [scheduler], and drains it.
         */
        fun respond(index: Int, result: NetworkResult) {
            val attempt = sent[index]
            scheduler.execute { attempt.callback(result) }
            scheduler.runAll()
        }
    }

    private class FakeAttempt(val callback: (NetworkResult) -> Unit) : Disposable {
        var disposeCount = 0
            private set

        override val isDisposed: Boolean
            get() = disposeCount > 0

        override fun dispose() {
            disposeCount++
        }
    }

    /**
     * An event that records its subscriptions, and calls [onSubscribe] while subscribing.
     */
    private class RecordingEvent<T>(
        private val onSubscribe: (Observer<T>) -> Unit = {}
    ) : Observable<T> {
        private var observer: Observer<T>? = null
        var subscribeCount = 0
            private set
        var isSubscriptionDisposed = false
            private set

        override fun subscribe(observer: Observer<T>): Disposable {
            subscribeCount++
            this.observer = observer
            val subscription = Subscription {
                isSubscriptionDisposed = true
                this.observer = null
            }
            onSubscribe(observer)
            return subscription
        }

        fun emit(value: T) {
            observer?.onNext(value)
        }
    }

    private class Captured {
        val results = mutableListOf<NetworkResult>()
    }

    private class CapturingLoop(val loop: RetryLoop, val captured: WeakReference<Captured>)

    private companion object {
        val success = Success(HttpResponse(URL("http://localhost"), 200, "OK", emptyMap()))
        val failure = Failure(Non200Exception(503))
    }
}
