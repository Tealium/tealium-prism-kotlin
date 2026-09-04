package com.tealium.prism.core.internal.misc

import com.tealium.prism.core.api.misc.Callback
import com.tealium.prism.core.api.misc.TealiumResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class CallbackUtilsTests {

    private val callback = mockk<Callback<TealiumResult<Unit>>>(relaxed = true)

    @Test
    fun completing_Reports_Success_When_Block_Completes() {
        callback.completing { }

        verify(exactly = 1) {
            callback.onComplete(match { it.isSuccess })
        }
    }

    @Test
    fun completing_Reports_The_Original_Exception_When_Block_Throws() {
        val thrown = IllegalStateException("from the block")

        callback.completing { throw thrown }

        verify(exactly = 1) {
            callback.onComplete(match { it.exceptionOrNull() === thrown })
        }
    }

    @Test
    fun completing_Reports_The_Original_Error_When_Block_Throws() {
        val thrown = TestError()

        callback.completing { throw thrown }

        verify(exactly = 1) {
            callback.onComplete(match { it.exceptionOrNull() === thrown })
        }
    }

    @Test
    fun completing_Delivers_Once_When_The_Callback_Throws() {
        every { callback.onComplete(any()) } throws IllegalStateException("from the callback")

        assertThrows(IllegalStateException::class.java) {
            callback.completing { }
        }

        verify(exactly = 1) {
            callback.onComplete(any())
        }
    }

    @Test
    fun completing_Rethrows_When_The_Callback_Throws() {
        val thrown = IllegalStateException("from the callback")
        every { callback.onComplete(any()) } throws thrown

        val escaped = assertThrows(IllegalStateException::class.java) {
            callback.completing { }
        }

        assertSame(thrown, escaped)
    }

    private class TestError : Error("simulated")
}
