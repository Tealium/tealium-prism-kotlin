package com.tealium.prism.core.api

import android.app.Application
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
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
        val callback = mockk<Callback<TealiumResult<Tealium>>>(relaxed = true)
        Tealium.create(getDefaultConfig(app), callback)

        verify(timeout = 5000) {
            callback.onComplete(match { result ->
                result.isSuccess
                        && result.getOrNull() != null
            })
        }
    }

    @Test
    fun tealium_MultipleInstances_ShareProcessingThread() {
        val threads = mutableSetOf<Thread>()

        val callback = mockk<Callback<TealiumResult<Tealium>>>()
        every { callback.onComplete(any()) } answers {
            threads.add(Thread.currentThread())
        }

        Tealium.create(
            getDefaultConfig(app, accountName = "tealium1"),
            callback
        )
        Tealium.create(
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