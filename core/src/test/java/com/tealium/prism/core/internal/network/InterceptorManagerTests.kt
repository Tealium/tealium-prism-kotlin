package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.RequestInterceptor
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.prism.core.api.pubsub.Observables
import com.tealium.tests.common.RecordingInterceptor
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class InterceptorManagerTests {

    private val request: HttpRequest = HttpRequest.get("http://localhost").build()
    private val result: NetworkResult = mockk()

    @Test
    fun decide_Returns_Retry_When_Interceptor_Returns_AfterDelay() {
        val calls = mutableListOf<String>()
        val policy = RetryPolicy.AfterDelay(0.milliseconds)
        val interceptor = RecordingInterceptor("interceptor", calls) { consecutiveRetryCount ->
            if (consecutiveRetryCount == 0) policy else DoNotRetry
        }
        val manager = InterceptorManager(listOf(interceptor))

        val retry = manager.decide(request, RetryContext.INITIAL, result)

        assertNotNull(retry)
        assertSame(policy, retry!!.policy)
        assertSame(interceptor, retry.context.lastRetryInterceptor)
        assertEquals(listOf("interceptor.shouldRetry(0)"), calls)
    }

    @Test
    fun decide_Returns_Retry_When_Interceptor_Returns_AfterEvent() {
        val calls = mutableListOf<String>()
        val policy = RetryPolicy.AfterEvent(Observables.just(Unit))
        val interceptor = RecordingInterceptor("interceptor", calls) { consecutiveRetryCount ->
            if (consecutiveRetryCount == 0) policy else DoNotRetry
        }
        val manager = InterceptorManager(listOf(interceptor))

        val retry = manager.decide(request, RetryContext.INITIAL, result)

        assertNotNull(retry)
        assertSame(policy, retry!!.policy)
        assertEquals(listOf("interceptor.shouldRetry(0)"), calls)
    }

    @Test
    fun notifyAttemptResult_Calls_OnAttemptResult_In_Order() {
        val calls = mutableListOf<String>()
        val first = RecordingInterceptor("first", calls)
        val second = RecordingInterceptor("second", calls)
        val manager = InterceptorManager(listOf(first, second))

        manager.notifyAttemptResult(request, 0, result)

        assertEquals(listOf("first.onAttemptResult(0)", "second.onAttemptResult(0)"), calls)
    }

    @Test
    fun notifyAttemptResult_Forwards_RetryCount_To_Every_Interceptor() {
        val calls = mutableListOf<String>()
        val first = RecordingInterceptor("first", calls)
        val second = RecordingInterceptor("second", calls)
        val manager = InterceptorManager(listOf(first, second))

        manager.notifyAttemptResult(request, 3, result)

        assertEquals(listOf("first.onAttemptResult(3)", "second.onAttemptResult(3)"), calls)
    }

    @Test
    fun decide_Asks_ShouldRetry_In_Reverse_Order() {
        val calls = mutableListOf<String>()
        val first = RecordingInterceptor("first", calls)
        val second = RecordingInterceptor("second", calls)
        val manager = InterceptorManager(listOf(first, second))

        manager.decide(request, RetryContext.INITIAL, result)

        assertEquals(
            listOf("second.shouldRetry(0)", "first.shouldRetry(0)"),
            calls
        )
    }

    @Test
    fun decide_Stops_Asking_When_An_Interceptor_Retries() {
        val calls = mutableListOf<String>()
        val first = RecordingInterceptor("first", calls)
        val second = RecordingInterceptor("second", calls) { RetryPolicy.AfterDelay(0.milliseconds) }
        val third = RecordingInterceptor("third", calls)
        val manager = InterceptorManager(listOf(first, second, third))

        val retry = manager.decide(request, RetryContext.INITIAL, result)

        assertSame(second, retry?.context?.lastRetryInterceptor)
        assertEquals(
            listOf("third.shouldRetry(0)", "second.shouldRetry(0)"),
            calls
        )
    }

    @Test
    fun decide_Passes_Each_Interceptor_Its_Own_ConsecutiveRetryCount() {
        // asked in reverse order: `other` is asked before `winner`, and has no opinion
        val calls = mutableListOf<String>()
        val winner = RecordingInterceptor("winner", calls) { RetryPolicy.AfterDelay(0.milliseconds) }
        val other = RecordingInterceptor("other", calls)
        val manager = InterceptorManager(listOf(winner, other))
        // as if `other` had already proposed 2 retries in a row
        val context = RetryContext.INITIAL
            .nextRetry(other)
            .nextRetry(other)

        val retry = manager.decide(request, context, result)

        assertEquals(
            listOf("other.shouldRetry(2)", "winner.shouldRetry(0)"),
            calls
        )
        assertSame(winner, retry?.context?.lastRetryInterceptor)
        assertEquals(context.retryCount + 1, retry?.context?.retryCount)
        assertEquals(1, retry?.context?.consecutiveRetryCount)
    }

    @Test
    fun decide_Asks_None_When_MaxRetryCount_Is_Reached() {
        val calls = mutableListOf<String>()
        val first = RecordingInterceptor("first", calls) { RetryPolicy.AfterDelay(0.milliseconds) }
        val second = RecordingInterceptor("second", calls) { RetryPolicy.AfterDelay(0.milliseconds) }
        val manager = InterceptorManager(listOf(first, second), maxRetryCount = 2)
        val context = RetryContext.INITIAL
            .nextRetry(first)
            .nextRetry(first)
        assertEquals(2, context.retryCount)

        val retry = manager.decide(request, context, result)

        assertNull(retry)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun notifyAttemptResult_Calls_OnAttemptResult_On_Every_Interceptor_When_MaxRetryCount_Is_Reached() {
        val calls = mutableListOf<String>()
        val first = RecordingInterceptor("first", calls) { RetryPolicy.AfterDelay(0.milliseconds) }
        val second = RecordingInterceptor("second", calls) { RetryPolicy.AfterDelay(0.milliseconds) }
        val manager = InterceptorManager(listOf(first, second), maxRetryCount = 0)

        manager.notifyAttemptResult(request, 0, result)

        assertEquals(listOf("first.onAttemptResult(0)", "second.onAttemptResult(0)"), calls)
    }

    @Test
    fun decide_Asks_Interceptors_When_MaxRetryCount_Is_Not_Reached() {
        val calls = mutableListOf<String>()
        val interceptor = RecordingInterceptor("interceptor", calls) { RetryPolicy.AfterDelay(0.milliseconds) }
        val manager = InterceptorManager(listOf(interceptor), maxRetryCount = 2)
        val context = RetryContext.INITIAL.nextRetry(interceptor)

        val retry = manager.decide(request, context, result)

        assertEquals(2, retry?.context?.retryCount)
        assertEquals(listOf("interceptor.shouldRetry(1)"), calls)
    }

    @Test
    fun decide_Returns_Null_When_No_Interceptor_Retries() {
        val calls = mutableListOf<String>()
        val manager = InterceptorManager(
            listOf(RecordingInterceptor("first", calls), RecordingInterceptor("second", calls))
        )

        assertNull(manager.decide(request, RetryContext.INITIAL, result))
    }

    @Test
    fun newManager_Uses_Parent_Interceptors_Followed_By_Additional_Ones() {
        val first = mockInterceptor()
        val second = mockInterceptor()
        val third = mockInterceptor()
        val manager = InterceptorManager(listOf(first, second))

        val derived = manager.newManager(listOf(third), maxRetryCount = null)

        assertEquals(3, derived.interceptors.size)
        assertSame(first, derived.interceptors[0])
        assertSame(second, derived.interceptors[1])
        assertSame(third, derived.interceptors[2])
    }

    @Test
    fun newManager_Does_Not_Change_Parent_Interceptors() {
        val first = mockInterceptor()
        val second = mockInterceptor()
        val manager = InterceptorManager(listOf(first))

        manager.newManager(listOf(second), maxRetryCount = null)

        assertEquals(1, manager.interceptors.size)
        assertSame(first, manager.interceptors[0])
    }

    @Test
    fun newManager_Uses_Given_MaxRetryCount_And_Does_Not_Inherit_The_Parents() {
        val manager = InterceptorManager(listOf(mockInterceptor()), maxRetryCount = 5)

        val derived = manager.newManager(listOf(mockInterceptor()), maxRetryCount = 2)

        assertEquals(5, manager.maxRetryCount)
        assertEquals(2, derived.maxRetryCount)
    }

    private fun mockInterceptor(): RequestInterceptor = mockk()
}
