package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.internal.pubsub.DisposableRunnable
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class SingleThreadedSchedulerTests {

    private lateinit var task: Runnable
    private lateinit var threadFactory: SingleThreadedScheduler.SingleThreadFactory
    private lateinit var executorService: ScheduledExecutorService
    private lateinit var scheduler: SingleThreadedScheduler

    @Before
    fun setUp() {
        task = mockk(relaxed = true)
        threadFactory = SingleThreadedScheduler.SingleThreadFactory("tealium-test")
        executorService = spyk(MockDelegatingScheduler(Executors.newSingleThreadScheduledExecutor(threadFactory)))

        scheduler = SingleThreadedScheduler(threadFactory, executorService)
    }

    @Test
    fun execute_Executes_Tasks_Immediately_When_Called_On_Same_Scheduler() {
        scheduler.execute {
            scheduler.execute(task)
        }

        verify(timeout = 100) {
            task.run()
        }
        verify(inverse = true) {
            executorService.execute(task)
        }
    }

    @Test
    fun execute_Schedules_Tasks_When_Called_On_Different_Scheduler() {
        scheduler.execute(task)

        verify(timeout = 100) {
            executorService.execute(task)
        }
    }

    @Test
    fun schedule_Executes_Tasks_Immediately_When_Called_On_Same_Scheduler() {
        scheduler.execute {
            scheduler.schedule(task)
        }

        verify(timeout = 100) {
            task.run()
        }
        verify(inverse = true) {
            executorService.submit(task)
        }
    }

    @Test
    fun schedule_Submits_Tasks_When_Called_On_Different_Scheduler() {
        scheduler.schedule(task)

        verify(timeout = 100) {
            executorService.submit(match { it is DisposableRunnable })
        }
    }

    @Test
    fun schedule_Delayed_Schedules_Tasks_When_Called_On_Same_Scheduler() {
        scheduler.execute {
            scheduler.schedule(0.milliseconds, task)
        }

        verify(timeout = 100) {
            executorService.schedule(match { it is DisposableRunnable }, 0, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun schedule_Delayed_Schedules_Tasks_When_Called_On_Different_Scheduler() {
        scheduler.schedule(0.milliseconds, task)

        verify(timeout = 100) {
            executorService.schedule(match { it is DisposableRunnable }, 0, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun execute_Without_ThreadFactory_Always_Schedules() {
        scheduler = SingleThreadedScheduler(null, executorService)

        scheduler.execute(task)

        verify(timeout = 100) {
            executorService.execute(task)
        }
    }

    @Test
    fun schedule_Without_ThreadFactory_Always_Submits() {
        scheduler = SingleThreadedScheduler(null, executorService)

        scheduler.schedule(task)

        verify(timeout = 100) {
            executorService.submit(match { it is DisposableRunnable })
        }
    }

    @Test
    fun schedule_Delayed_Without_ThreadFactory_Always_Schedules() {
        scheduler = SingleThreadedScheduler(null, executorService)

        scheduler.schedule(0.milliseconds, task)

        verify(timeout = 100) {
            executorService.schedule(match { it is DisposableRunnable }, 0, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun singleThreadFactory_Creates_Thread_On_First_Task() {
        assertNull(threadFactory.thread)
        scheduler.execute {
            println("First task creates the Thread")
        }
        Thread.sleep(100)

        val thread = threadFactory.thread
        assertNotNull(thread)
    }
}

// Spyk failing on Java packaged executors, so this is our own impl that simply delegates to the
// provided one anyway, but this can be mocked for method call recording.
class MockDelegatingScheduler(private val delegate: ScheduledExecutorService) :
    ScheduledExecutorService by delegate