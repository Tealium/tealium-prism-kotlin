package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.internal.pubsub.DisposableRunnable
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ThreadPoolSchedulerTests {

    private lateinit var task: Runnable
    private lateinit var executorService: ScheduledExecutorService
    private lateinit var scheduler: ThreadPoolScheduler

    @Before
    fun setUp() {
        task = mockk(relaxed = true)
        executorService = spyk(MockDelegatingScheduler(Executors.newScheduledThreadPool(0)))

        scheduler = ThreadPoolScheduler(executorService)
    }

    @Test
    fun execute_Schedules_Tasks_When_Called_On_Same_Scheduler() {
        scheduler.execute {
            scheduler.execute(task)
        }

        verify(timeout = 100) {
            executorService.execute(task)
            task.run()
        }
    }

    @Test
    fun execute_Schedules_Tasks_When_Called_On_Different_Scheduler() {
        scheduler.execute(task)

        verify(timeout = 100) {
            executorService.execute(task)
            task.run()
        }
    }

    @Test
    fun schedule_Schedules_Tasks_When_Called_On_Same_Scheduler() {
        scheduler.execute {
            scheduler.schedule(task)
        }

        verify(timeout = 100) {
            executorService.execute(match { it is DisposableRunnable })
            task.run()
        }
    }

    @Test
    fun schedule_Schedules_Tasks_When_Called_On_Different_Scheduler() {
        scheduler.schedule(task)

        verify(timeout = 100) {
            executorService.execute(match { it is DisposableRunnable })
        }
    }

    @Test
    fun schedule_With_Delay_Schedules_Tasks_When_Called_On_Same_Scheduler() {
        scheduler.execute {
            scheduler.schedule(0.milliseconds, task)
        }

        verify(timeout = 100) {
            executorService.schedule(match { it is DisposableRunnable }, 0, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun schedule_With_Delay_Schedules_Tasks_When_Called_On_Different_Scheduler() {
        scheduler.schedule(0.milliseconds, task)

        verify(timeout = 100) {
            executorService.schedule(match { it is DisposableRunnable }, 0, TimeUnit.MILLISECONDS)
        }
    }
}