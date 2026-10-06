package com.tealium.prism.core.api

import android.app.Application
import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import com.tealium.tests.common.getDefaultConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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

    @Test
    fun shutdown_Does_Nothing_When_Passed_Stale_Tealium() {
        val config = getDefaultConfig(app, accountName = "stale_tealium")
        val staleTealium = createAndAwait(config)
        Tealium.shutdown(staleTealium)
        val currentTealium = createAndAwait(config)

        Tealium.shutdown(staleTealium)

        try {
            assertSame(currentTealium, await<Tealium?> { done -> Tealium.get(config) { done(it) } })
        } finally {
            Tealium.shutdown(config.key)
        }
    }

    private fun createAndAwait(config: TealiumConfig): Tealium =
        await<TealiumResult<Tealium>> { done -> Tealium.create(config) { done(it) } }.getOrThrow()

    /**
     * Runs [block] and waits up to 5 seconds for it to call the provided completion with a value.
     */
    private fun <T> await(block: (done: (T) -> Unit) -> Unit): T {
        val future = CompletableFuture<T>()
        block { future.complete(it) }
        return future.get(5, TimeUnit.SECONDS)
    }
}