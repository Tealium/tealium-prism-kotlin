package com.tealium.core.internal.dispatch

import com.tealium.core.internal.TealiumScheduler
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TasksTests {

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
        }
    }

    private fun <T> asyncTask(returns: T, delay: Long, unit: TimeUnit = TimeUnit.MILLISECONDS, executorService: ScheduledExecutorService = this.executorService): CompletableTask<T> {
        return CompletableTask { callback ->
            executorService.schedule({
                callback(returns)
            }, delay, unit)
        }
    }

    @Test
    fun taskGroup_NotifiesOn_GivenExecutor() {
        var notifyThread: Thread? = null
        val executorService = Executors.newSingleThreadScheduledExecutor() {
            notifyThread = Thread(it, "notifyThread")
            notifyThread!!
        }
        val scheduler = TealiumScheduler(executorService)
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
}