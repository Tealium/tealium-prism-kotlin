package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RetryPolicy
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RetryContextTests {

    @Test
    fun initial_Has_Zero_Counts_And_No_Last_Interceptor() {
        val context = RetryContext.INITIAL

        assertEquals(0, context.retryCount)
        assertEquals(0, context.consecutiveRetryCount)
        assertNull(context.lastRetryInterceptor)
    }

    @Test
    fun nextRetry_Increments_RetryCount() {
        val interceptor = mockInterceptor()

        val context = RetryContext.INITIAL.nextRetry(interceptor)
        assertEquals(1, context.retryCount)

        val secondContext = context.nextRetry(interceptor)
        assertEquals(2, secondContext.retryCount)
    }

    @Test
    fun nextRetry_Increments_ConsecutiveRetryCount_When_Same_Interceptor_Retries_Twice() {
        val interceptor = mockInterceptor()

        val context = RetryContext.INITIAL
            .nextRetry(interceptor)
            .nextRetry(interceptor)

        assertEquals(2, context.consecutiveRetryCount)
        assertSame(interceptor, context.lastRetryInterceptor)
    }

    @Test
    fun nextRetry_Resets_ConsecutiveRetryCount_To_One_When_Different_Interceptor_Retries() {
        val firstInterceptor = mockInterceptor()
        val secondInterceptor = mockInterceptor()

        val context = RetryContext.INITIAL
            .nextRetry(firstInterceptor)
            .nextRetry(firstInterceptor)
            .nextRetry(secondInterceptor)

        assertEquals(3, context.retryCount)
        assertEquals(1, context.consecutiveRetryCount)
        assertSame(secondInterceptor, context.lastRetryInterceptor)
    }

    @Test
    fun consecutiveRetryCount_Returns_Run_For_Last_Interceptor_And_Zero_For_Another() {
        val firstInterceptor = mockInterceptor()
        val secondInterceptor = mockInterceptor()

        val context = RetryContext.INITIAL
            .nextRetry(firstInterceptor)
            .nextRetry(firstInterceptor)
            .nextRetry(firstInterceptor)

        assertEquals(3, context.consecutiveRetryCount(firstInterceptor))
        assertEquals(0, context.consecutiveRetryCount(secondInterceptor))
    }

    @Test
    fun consecutiveRetryCount_Returns_Zero_When_Context_Is_Initial() {
        val interceptor = mockInterceptor()

        assertEquals(0, RetryContext.INITIAL.consecutiveRetryCount(interceptor))
    }

    @Test
    fun consecutiveRetryCount_Compares_Interceptors_By_Identity_Not_Equality() {
        val interceptor = EqualInterceptor("same")
        val equalInterceptor = EqualInterceptor("same")
        assertEquals(interceptor, equalInterceptor)

        val context = RetryContext.INITIAL
            .nextRetry(interceptor)
            .nextRetry(interceptor)

        assertEquals(2, context.consecutiveRetryCount(interceptor))
        assertEquals(0, context.consecutiveRetryCount(equalInterceptor))
        assertEquals(1, context.nextRetry(equalInterceptor).consecutiveRetryCount)
    }

    private fun mockInterceptor(): RequestInterceptor = mockk()

    /**
     * A [RequestInterceptor] that is `equals` to every other instance with the same [name].
     */
    private data class EqualInterceptor(val name: String) : RequestInterceptor {
        override fun shouldRetry(
            request: HttpRequest,
            consecutiveRetryCount: Int,
            result: NetworkResult
        ): RetryPolicy = RetryPolicy.DoNotRetry
    }
}
