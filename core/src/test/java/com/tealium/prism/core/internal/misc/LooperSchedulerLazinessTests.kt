package com.tealium.prism.core.internal.misc

import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * `Scheduler.MAIN` and `Scheduler.SYNCHRONOUS` are `@JvmField`s, so they are created together when
 * `Scheduler` is first used. Code that only reads `SYNCHRONOUS` therefore also creates `MAIN`, a
 * `LooperScheduler`.
 *
 * `Looper.getMainLooper()` fails outside Android. If `LooperScheduler()` called it on creation,
 * every plain JVM test that reads `Scheduler.SYNCHRONOUS` would fail too. So it must only be called
 * once the scheduler is used.
 *
 * Deliberately not Robolectric, which provides a real main [Looper] and would hide the problem.
 * Relies on `unitTests.isReturnDefaultValues`, so that `Looper.myLooper()` and `Handler` are inert.
 */
class LooperSchedulerLazinessTests {

    private lateinit var scheduler: LooperScheduler

    @Before
    fun setUp() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk()

        scheduler = LooperScheduler()
    }

    @After
    fun tearDown() {
        unmockkStatic(Looper::class)
    }

    @Test
    fun constructor_Does_Not_Resolve_Main_Looper() {
        verify(exactly = 0) { Looper.getMainLooper() }
    }

    // Also proves the static mock intercepts the call, which stops the test above passing vacuously
    @Test
    fun execute_Resolves_Main_Looper_Once_When_Used() {
        scheduler.execute { }
        scheduler.execute { }

        verify(exactly = 1) { Looper.getMainLooper() }
    }
}
