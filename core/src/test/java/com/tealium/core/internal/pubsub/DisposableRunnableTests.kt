package com.tealium.core.internal.pubsub

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class DisposableRunnableTests {

    @Test
    fun run_Runs_The_Runnable_When_Not_Disposed() {
        val runnable = mockk<Runnable>(relaxed = true)

        val disposableRunnable = DisposableRunnable(runnable)
        disposableRunnable.run()

        verify { runnable.run() }
    }

    @Test
    fun run_Does_Not_Run_The_Runnable_When_Disposed() {
        val runnable = mockk<Runnable>(relaxed = true)

        val disposableRunnable = DisposableRunnable(runnable)
        disposableRunnable.dispose()
        disposableRunnable.run()

        verify(inverse = true) { runnable.run() }
    }
}