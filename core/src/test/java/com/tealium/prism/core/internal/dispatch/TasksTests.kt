package com.tealium.prism.core.internal.dispatch

import com.tealium.prism.core.api.misc.Scheduler
import com.tealium.prism.core.api.pubsub.Disposable
import com.tealium.prism.core.api.pubsub.Disposables
import com.tealium.prism.core.internal.misc.SingleThreadedScheduler
import com.tealium.tests.common.race
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TasksTests {

    // race() only bounds its background side; a blocked caller-side action would otherwise hang
    // the run instead of failing. The race test normally completes in tens of milliseconds.
    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    lateinit var executorService: ScheduledExecutorService

    @Before
    fun setUp() {
        executorService = Executors.newSingleThreadScheduledExecutor()
    }

    @After
    fun tearDown() {
        executorService.shutdown()
    }

    private fun <T> syncTask(returns: T): CompletableTask<T> {
        return CompletableTask { callback ->
            callback(returns)
            Disposables.disposed()
        }
    }

    private fun <T> asyncTask(returns: T, delay: Long, unit: TimeUnit = TimeUnit.MILLISECONDS): CompletableTask<T> {
        return CompletableTask { callback ->
            executorService.schedule({
                callback(returns)
            }, delay, unit)
            Disposables.disposed()
        }
    }

    @Test
    fun taskGroup_NotifiesImmediately_WhenTaskListIsEmpty() {
        val notify = mockk<(List<Int>) -> Unit>(relaxed = true)

        val disposable = Tasks.execute(notifyOn = testTealiumScheduler, emptyList(), notify)

        verify { notify(emptyList()) }
        Assert.assertTrue(disposable.isDisposed)
    }

    @Test
    fun taskGroup_NotifiesOn_GivenExecutor() {
        var notifyThread: Thread? = null
        val executorService = Executors.newSingleThreadScheduledExecutor() {
            notifyThread = Thread(it, "notifyThread")
            notifyThread!!
        }
        val scheduler = SingleThreadedScheduler(null, executorService)
        val assertion = mockk<(Boolean) -> Unit>()
        val notify: (List<Int>) -> Unit = {
            Assert.assertEquals(notifyThread, Thread.currentThread())
            Assert.assertEquals(listOf(1,2,3), it)
            assertion(true)
        }

        Tasks.execute(notifyOn = scheduler, listOf(
            syncTask(1),
            syncTask(2),
            syncTask(3)
        ), notify)

        verify(timeout = 1000) {
            assertion(true)
        }
    }

    @Test
    fun taskGroup_ExecutesSynchronously_WhenPossible() {
        val notify = mockk<(List<Int>) -> Unit>()

        Tasks.execute(notifyOn = testTealiumScheduler, listOf(
            syncTask(1),
            syncTask(2),
            syncTask(3)
        ), notify)

        verify(timeout = 1000) {
            notify(listOf(1, 2, 3))
        }
    }

    @Test
    fun taskGroup_Notifies_When_AllTasksAreCompleted() {
        val notify = mockk<(List<Int>) -> Unit>()

        Tasks.execute(notifyOn = testTealiumScheduler, listOf(
            asyncTask(1, 1000, TimeUnit.MILLISECONDS),
            syncTask(2),
            syncTask(3)
        ), notify)

        verify(timeout = 500, inverse = true) {
            notify(listOf(1, 2, 3))
        }
        verify(timeout = 1500) {
            notify(listOf(1, 2, 3))
        }
    }

    @Test
    fun taskGroup_NotifiesResults_InOriginalOrder() {
        val notify = mockk<(List<Int>) -> Unit>()

        Tasks.execute(notifyOn = testTealiumScheduler, listOf(
            asyncTask(1, 100, TimeUnit.MILLISECONDS),
            asyncTask(2, 200, TimeUnit.MILLISECONDS),
            asyncTask(3, 300, TimeUnit.MILLISECONDS),
        ), notify)

        verify(timeout = 1000) {
            notify(listOf(1, 2, 3))
        }
    }

    @Test
    fun taskGroup_DoesNotNotify_WhenDisposedBeforeCompletion() {
        val notify = mockk<(List<Int>) -> Unit>()

        val disposable = Tasks.execute(notifyOn = testTealiumScheduler, listOf(
            asyncTask(1, 500, TimeUnit.MILLISECONDS),
        ), notify)

        disposable.dispose()

        verify(timeout = 1000, inverse = true) {
            notify(any())
        }
    }

    @Test
    fun execute_Notifies_Once_With_First_Results_When_A_Task_Completes_Twice() {
        val notifications = mutableListOf<List<Int>>()
        val pending = HeldTask<Int>()

        Tasks.execute(
            notifyOn = Scheduler.SYNCHRONOUS,
            listOf(doubleCompletingTask(1, 99), pending)
        ) { notifications.add(it) }

        // The duplicate completion must not count for the still-pending task.
        Assert.assertEquals(emptyList<List<Int>>(), notifications)

        pending.complete(2)

        Assert.assertEquals(listOf(listOf(1, 2)), notifications)

        // A duplicate completion after notify must be ignored too.
        pending.complete(3)

        Assert.assertEquals(listOf(listOf(1, 2)), notifications)
    }

    /**
     * Probabilistic race test (see [race]): it never fails on correct code, but a regression is
     * only caught with high probability, not on every run. An intermittent failure here
     * therefore indicates a real bug, not flakiness.
     *
     * Guards against two tasks completing at the same moment on different threads and both
     * notifying (the old `countDown()` then `count == 0` check-then-act).
     */
    @Test
    fun execute_Notifies_Once_When_Tasks_Complete_Concurrently_On_Different_Threads() {
        repeat(2000) { iteration ->
            val first = HeldTask<Int>()
            val second = HeldTask<Int>()
            val notifications = CopyOnWriteArrayList<List<Int>>()
            Tasks.execute(notifyOn = Scheduler.SYNCHRONOUS, listOf(first, second)) {
                notifications.add(it)
            }

            race({ first.complete(1) }, { second.complete(2) })

            Assert.assertEquals("Iteration $iteration", listOf(listOf(1, 2)), notifications.toList())
        }
    }

    @Test
    fun execute_Notifies_Results_In_Original_Order_When_Tasks_Complete_In_Reverse_Order() {
        val tasks = listOf(HeldTask<Int>(), HeldTask<Int>(), HeldTask<Int>())
        val notifications = mutableListOf<List<Int>>()

        Tasks.execute(notifyOn = Scheduler.SYNCHRONOUS, tasks) { notifications.add(it) }

        for (idx in tasks.indices.reversed()) {
            tasks[idx].complete(idx + 1)
        }

        Assert.assertEquals(listOf(listOf(1, 2, 3)), notifications)
    }

    @Test
    fun execute_Disposes_In_Progress_Tasks_When_Disposed() {
        val tasks = listOf(HeldTask<Int>(), HeldTask<Int>())
        val notify = mockk<(List<Int>) -> Unit>(relaxed = true)

        val subscription = Tasks.execute(notifyOn = Scheduler.SYNCHRONOUS, tasks, notify)
        Assert.assertFalse(tasks.any { it.disposable.isDisposed })

        subscription.dispose()

        Assert.assertTrue(tasks.all { it.disposable.isDisposed })
        verify(exactly = 0) { notify(any()) }
    }

    /**
     * A task that signals completion twice, synchronously: [first] and then [second]. Only the
     * first result should be kept.
     */
    private fun <T> doubleCompletingTask(first: T, second: T): CompletableTask<T> {
        return CompletableTask { callback ->
            callback(first)
            callback(second)
            Disposables.disposed()
        }
    }

    /**
     * A task that holds on to its completion callback so the test can trigger it, from any thread,
     * via [complete].
     */
    private class HeldTask<T> : CompletableTask<T> {
        @Volatile
        private var onComplete: ((T) -> Unit)? = null

        val disposable: Disposable = Disposables.subscription()

        override fun execute(onComplete: (T) -> Unit): Disposable {
            this.onComplete = onComplete
            return disposable
        }

        fun complete(result: T) {
            onComplete!!.invoke(result)
        }
    }
}
