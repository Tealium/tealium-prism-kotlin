package com.tealium.core

import android.app.Application
import com.tealium.core.api.TealiumResult
import com.tealium.core.api.listeners.TealiumCallback
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TealiumTests {

    lateinit var app: Application

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
    }

    @Test
    fun tealium_ReturnsTealiumInstance_WhenReady() {
        val callback = mockk<TealiumCallback<TealiumResult<Tealium>>>(relaxed = true)
        Tealium.create("main", getDefaultConfig(app), callback)

        verify(timeout = 2000) {
            callback.onComplete(match { result ->
                result.isSuccess
                        && result.getOrNull() != null
            })
        }
    }

    @Test
    fun tealium_MultipleInstances_ShareProcessingThread() {
        val threads = mutableSetOf<Thread>()

        val callback = mockk<TealiumCallback<TealiumResult<Tealium>>>()
        every { callback.onComplete(any()) } answers {
            threads.add(Thread.currentThread())
        }

        val tealium1 = Tealium.create(
            "instance1",
            getDefaultConfig(app, accountName = "tealium1"),
            callback
        )
        val tealium2 = Tealium.create(
            "instance2",
            getDefaultConfig(app, accountName = "tealium2"),
            callback
        )

        verify(timeout = 2000, exactly = 2) {
            callback.onComplete(match { result ->
                result.isSuccess
                        && result.getOrNull() != null
            })
        }
        assertTrue(threads.count() == 1) // Set de-duplicated
    }
}