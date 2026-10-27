package com.tealium.prism.core.internal.misc

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SchedulerExtensionsTests {

    private lateinit var executorService: ScheduledExecutorService
    private lateinit var scheduler: SingleThreadedScheduler

    @Before
    fun setUp() {
        val threadFactory = SingleThreadedScheduler.SingleThreadFactory("tealium-test")
        executorService = Executors.newSingleThreadScheduledExecutor(threadFactory)
        scheduler = SingleThreadedScheduler(threadFactory, executorService)
    }

    @After
    fun tearDown() {
        executorService.shutdownNow()
    }

    @Test
    fun async_Runs_After_Current_Task_When_Called_From_The_Scheduler() {
        val events = mutableListOf<String>()
        val latch = CountDownLatch(1)

        scheduler.execute {
            scheduler.async {
                events.add("async")
                latch.countDown()
            }
            events.add("current")
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(listOf("current", "async"), events)
    }

    @Test
    fun async_Does_Not_Run_When_Disposed() {
        val ran = CountDownLatch(1)
        val gate = CountDownLatch(1)

        scheduler.execute { gate.await(1, TimeUnit.SECONDS) }
        scheduler.async { ran.countDown() }.dispose()
        gate.countDown()

        assertFalse(ran.await(100, TimeUnit.MILLISECONDS))
    }
}
