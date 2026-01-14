package com.tealium.prism.core.api.misc

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CallbackUtilsTests {

    @RelaxedMockK
    private lateinit var callback: Callback<TealiumResult<Boolean>>

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun success_Completes_Callback_With_Success() {
        callback.success(true)

        verify { callback.onComplete(match { it.getOrThrow() }) }
    }

    @Test
    fun failure_Completes_Callback_With_Failure() {
        val throwable = Exception()
        callback.failure(throwable)

        verify { callback.onComplete(match { it.exceptionOrNull() == throwable }) }
    }
}