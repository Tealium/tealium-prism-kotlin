package com.tealium.prism.core.internal.misc

import android.os.Handler
import android.os.Looper
import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.tests.common.testTealiumScheduler
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LooperSchedulerTests {

    private lateinit var myLooper: Looper
    private lateinit var handler: Handler
    private lateinit var task: Runnable
    private lateinit var scheduler: LooperScheduler


    @Before
    fun setUp() {
        val looper = Looper.myLooper()
        if (looper == null) {
            Looper.prepare()
        }
        myLooper = Looper.myLooper()!!
        handler = spyk(Handler(myLooper))
        task = mockk(relaxed = true)

        scheduler = LooperScheduler(myLooper, handler)
    }

    @Test
    fun execute_Executes_Tasks_Immediately_When_Called_On_Same_Looper() {
        scheduler.execute(task)

        verify {
            task.run()
        }
        verify(inverse = true) {
            handler.post(any())
        }
    }

    @Test
    fun execute_Schedules_Tasks_When_Called_On_Different_Looper() {
        testTealiumScheduler.execute {
            scheduler.execute(task)
        }

        verify(timeout = 100) {
            handler.post(task)
        }
    }

    @Test
    fun schedule_Executes_Tasks_Immediately_When_Called_On_Same_Looper() {
        scheduler.schedule(task)

        verify {
            task.run()
        }
        verify(inverse = true) {
            handler.post(any())
        }
    }

    @Test
    fun schedule_Schedules_Tasks_When_Called_On_Different_Looper() {
        testTealiumScheduler.execute {
            scheduler.schedule(task)
        }

        verify(timeout = 100) {
            handler.post(any())
        }
    }

    @Test
    fun schedule_With_Delay_Schedules_Tasks_When_Called_On_Same_Looper() {
        scheduler.schedule(0.milliseconds, task)

        verify(timeout = 100) {
            handler.postDelayed(any(), 0)
        }
    }

    @Test
    fun schedule_With_Delay_Schedules_Tasks_When_Called_On_Different_Looper() {
        testTealiumScheduler.execute {
            scheduler.schedule(0.milliseconds, task)
        }

        verify(timeout = 100) {
            handler.postDelayed(any(), 0)
        }
    }
}