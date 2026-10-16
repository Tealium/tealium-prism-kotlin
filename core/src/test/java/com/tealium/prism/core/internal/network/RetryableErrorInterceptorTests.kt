package com.tealium.prism.core.internal.network

import com.tealium.prism.core.api.misc.TimeFrameUtils.milliseconds
import com.tealium.prism.core.api.misc.TimeFrameUtils.seconds
import com.tealium.prism.core.api.network.HttpRequest
import com.tealium.prism.core.api.network.HttpResponse
import com.tealium.prism.core.api.network.NetworkException.CancelledException
import com.tealium.prism.core.api.network.NetworkException.NetworkIOException
import com.tealium.prism.core.api.network.NetworkException.Non200Exception
import com.tealium.prism.core.api.network.NetworkException.UnexpectedException
import com.tealium.prism.core.api.network.NetworkResult
import com.tealium.prism.core.api.network.NetworkResult.Failure
import com.tealium.prism.core.api.network.NetworkResult.Success
import com.tealium.prism.core.api.network.RequestInterceptors
import com.tealium.prism.core.api.network.RetryAfterAboveMaxDelay
import com.tealium.prism.core.api.network.RetryPolicy
import com.tealium.prism.core.api.network.RetryPolicy.DoNotRetry
import com.tealium.tests.common.assertAfterDelay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RetryableErrorInterceptorTests {

    private val transientDelay = 500L
    private val throttlingDelay = 3_000L

    /** Wed, 21 Oct 2026 07:28:00 GMT */
    private val retryDate = 1_792_567_680_000L
    private val retryDateHeader = "Wed, 21 Oct 2026 07:28:00 GMT"

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Result_Is_Success() {
        val result = Success(HttpResponse(localhost, 200, "", emptyMap()))

        assertSame(DoNotRetry, decide(interceptor(maxAttempts = null), result))
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Status_Is_Not_Retryable() {
        assertSame(DoNotRetry, decide(interceptor(maxAttempts = null), failure(400)))
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Cancelled() {
        assertSame(DoNotRetry, decide(interceptor(maxAttempts = null), Failure(CancelledException)))
    }

    @Test
    fun shouldRetry_Returns_DoNotRetry_When_Exception_Is_Unexpected() {
        val result = Failure(UnexpectedException(MalformedURLException()))

        assertSame(DoNotRetry, decide(interceptor(maxAttempts = null), result))
    }

    @Test
    fun shouldRetry_Retries_At_Large_ConsecutiveRetryCount_When_MaxAttempts_Is_Null() {
        val interceptor = interceptor(maxAttempts = null)

        assertEquals(transientDelay, assertAfterDelay(decide(interceptor, failure(503), 10_000)))
        assertEquals(transientDelay, assertAfterDelay(decide(interceptor, failure(503), Int.MAX_VALUE)))
    }

    @Test
    fun shouldRetry_Retries_Twice_Then_Stops_When_MaxAttempts_Is_3() {
        val interceptor = interceptor(maxAttempts = 3)
        val result = failure(503)

        assertEquals(transientDelay, assertAfterDelay(decide(interceptor, result, 0)))
        assertEquals(transientDelay, assertAfterDelay(decide(interceptor, result, 1)))
        assertSame(DoNotRetry, decide(interceptor, result, 2))
        assertSame(DoNotRetry, decide(interceptor, result, 3))
    }

    @Test
    fun shouldRetry_Never_Retries_When_MaxAttempts_Is_1() {
        val interceptor = interceptor(maxAttempts = 1)

        assertSame(DoNotRetry, decide(interceptor, failure(503), 0))
        assertSame(DoNotRetry, decide(interceptor, failure(429), 0))
    }

    @Test
    fun shouldRetry_Does_Not_Overflow_When_ConsecutiveRetryCount_Is_Max_Int() {
        val interceptor = interceptor(maxAttempts = Int.MAX_VALUE)

        assertSame(DoNotRetry, decide(interceptor, failure(503), Int.MAX_VALUE))
        assertSame(DoNotRetry, decide(interceptor, failure(503), Int.MAX_VALUE - 1))
        assertEquals(transientDelay, assertAfterDelay(decide(interceptor, failure(503), Int.MAX_VALUE - 2)))
    }

    @Test
    fun shouldRetry_Uses_Throttling_Backoff_When_Status_Is_429() {
        assertEquals(throttlingDelay, assertAfterDelay(decide(interceptor(maxAttempts = null), failure(429))))
    }

    @Test
    fun shouldRetry_Uses_Transient_Backoff_When_Status_Is_503() {
        assertEquals(transientDelay, assertAfterDelay(decide(interceptor(maxAttempts = null), failure(503))))
    }

    @Test
    fun shouldRetry_Uses_Transient_Backoff_When_Request_Timed_Out() {
        val result = Failure(NetworkIOException(SocketTimeoutException()))

        assertEquals(transientDelay, assertAfterDelay(decide(interceptor(maxAttempts = null), result)))
    }

    @Test
    fun shouldRetry_Passes_ConsecutiveRetryCount_To_Backoff() {
        val retryCountBackoff = Backoff { retryCount -> retryCount.toLong() }
        val interceptor = RetryableErrorInterceptor(
            maxAttempts = null,
            maxDelay = 20_000,
            transientBackoff = retryCountBackoff,
            throttlingBackoff = retryCountBackoff,
            retryAfterAboveMaxDelay = RetryAfterAboveMaxDelay.GIVE_UP
        )

        assertEquals(0L, assertAfterDelay(decide(interceptor, failure(503), 0)))
        assertEquals(4L, assertAfterDelay(decide(interceptor, failure(429), 4)))
    }

    @Test
    fun shouldRetry_Uses_RetryAfter_When_Above_Backoff() {
        val interceptor = interceptor(maxAttempts = null)

        assertEquals(7_000L, assertAfterDelay(decide(interceptor, failure(503, "Retry-After" to "7"))))
        assertEquals(7_000L, assertAfterDelay(decide(interceptor, failure(429, "Retry-After" to " 7 "))))
    }

    @Test
    fun shouldRetry_Uses_Backoff_When_RetryAfter_Is_Zero() {
        val interceptor = interceptor(maxAttempts = null)

        assertEquals(transientDelay, assertAfterDelay(decide(interceptor, failure(503, "Retry-After" to "0"))))
        assertEquals(throttlingDelay, assertAfterDelay(decide(interceptor, failure(429, "Retry-After" to "0"))))
    }

    @Test
    fun shouldRetry_Uses_Backoff_When_RetryAfter_Is_Below_Backoff() {
        val interceptor = interceptor(maxAttempts = null)

        assertEquals(throttlingDelay, assertAfterDelay(decide(interceptor, failure(429, "Retry-After" to "2"))))
    }

    @Test
    fun shouldRetry_Uses_RetryAfter_When_Equal_To_MaxDelay() {
        val interceptor = interceptor(maxAttempts = null, maxDelay = 20_000)

        assertEquals(20_000L, assertAfterDelay(decide(interceptor, failure(503, "Retry-After" to "20"))))
    }

    @Test
    fun shouldRetry_Gives_Up_When_RetryAfter_Is_Above_MaxDelay_By_Default() {
        val interceptor = interceptor(maxAttempts = null, maxDelay = 20_000)

        assertSame(DoNotRetry, decide(interceptor, failure(503, "Retry-After" to "21")))
        assertSame(DoNotRetry, decide(interceptor, failure(429, "Retry-After" to "500")))
    }

    @Test
    fun shouldRetry_Waits_For_RetryAfter_When_RetryAfterAboveMaxDelay_Is_Wait() {
        val interceptor = interceptor(
            maxAttempts = null,
            maxDelay = 20_000,
            retryAfterAboveMaxDelay = RetryAfterAboveMaxDelay.WAIT
        )

        assertEquals(21_000L, assertAfterDelay(decide(interceptor, failure(503, "Retry-After" to "21"))))
        assertEquals(500_000L, assertAfterDelay(decide(interceptor, failure(429, "Retry-After" to "500"))))
    }

    @Test
    fun shouldRetry_Ignores_RetryAfter_When_MaxAttempts_Is_Reached() {
        val policy = decide(interceptor(maxAttempts = 2), failure(503, "Retry-After" to "7"), 1)

        assertSame(DoNotRetry, policy)
    }

    @Test
    fun shouldRetry_Measures_RetryAfter_Date_Against_Date_Header() {
        val result = failure(503, "Retry-After" to retryDateHeader, "Date" to httpDate(-30_000))

        assertEquals(30_000L, assertAfterDelay(decide(interceptor(maxAttempts = null, maxDelay = 100_000), result)))
    }

    @Test
    fun shouldRetry_Measures_RetryAfter_Date_Against_Now_When_Date_Header_Is_Missing() {
        val interceptor = interceptor(maxAttempts = null, maxDelay = 100_000, now = { retryDate - 12_000 })

        assertEquals(12_000L, assertAfterDelay(decide(interceptor, failure(503, "Retry-After" to retryDateHeader))))
    }

    @Test
    fun shouldRetry_Prefers_Date_Header_Over_Skewed_Device_Clock() {
        val interceptor = interceptor(maxAttempts = null, maxDelay = 100_000, now = { retryDate - 5 * 3_600_000 })
        val result = failure(503, "Retry-After" to retryDateHeader, "Date" to httpDate(-30_000))

        assertEquals(30_000L, assertAfterDelay(decide(interceptor, result)))
    }

    @Test
    fun shouldRetry_Measures_RetryAfter_Date_Against_Now_When_Date_Header_Is_Invalid() {
        val interceptor = interceptor(maxAttempts = null, maxDelay = 100_000, now = { retryDate - 12_000 })
        val result = failure(503, "Retry-After" to retryDateHeader, "Date" to "not a date")

        assertEquals(12_000L, assertAfterDelay(decide(interceptor, result)))
    }

    @Test
    fun shouldRetry_Uses_Backoff_When_RetryAfter_Date_Is_In_The_Past() {
        val interceptor = interceptor(maxAttempts = null, now = { retryDate + 60_000 })

        assertEquals(
            transientDelay,
            assertAfterDelay(decide(interceptor, failure(503, "Retry-After" to retryDateHeader)))
        )

        val result = failure(429, "Retry-After" to retryDateHeader, "Date" to httpDate(60_000))
        assertEquals(throttlingDelay, assertAfterDelay(decide(interceptor, result)))
    }

    @Test
    fun shouldRetry_Uses_Backoff_When_RetryAfter_Date_Is_Below_Backoff() {
        val result = failure(429, "Retry-After" to retryDateHeader, "Date" to httpDate(-2_000))

        assertEquals(throttlingDelay, assertAfterDelay(decide(interceptor(maxAttempts = null), result)))
    }

    @Test
    fun shouldRetry_Gives_Up_When_RetryAfter_Date_Is_Above_MaxDelay_By_Default() {
        val result = failure(503, "Retry-After" to retryDateHeader, "Date" to httpDate(-21_000))

        assertSame(DoNotRetry, decide(interceptor(maxAttempts = null, maxDelay = 20_000), result))
    }

    @Test
    fun shouldRetry_Waits_For_RetryAfter_Date_When_RetryAfterAboveMaxDelay_Is_Wait() {
        val result = failure(503, "Retry-After" to retryDateHeader, "Date" to httpDate(-500_000))
        val interceptor = interceptor(
            maxAttempts = null,
            maxDelay = 20_000,
            retryAfterAboveMaxDelay = RetryAfterAboveMaxDelay.WAIT
        )

        assertEquals(500_000L, assertAfterDelay(decide(interceptor, result)))
    }

    @Test
    fun shouldRetry_Uses_Backoff_When_RetryAfter_Uses_Obsolete_Date_Format() {
        val interceptor = interceptor(maxAttempts = null, maxDelay = 100_000, now = { retryDate - 30_000 })

        for (value in listOf("Wednesday, 21-Oct-26 07:28:00 GMT", "Wed Oct 21 07:28:00 2026")) {
            val transient = decide(interceptor, failure(503, "Retry-After" to value))
            val throttling = decide(interceptor, failure(429, "Retry-After" to value))
            assertEquals(value, transientDelay, assertAfterDelay(transient))
            assertEquals(value, throttlingDelay, assertAfterDelay(throttling))
        }
    }

    @Test
    fun shouldRetry_Uses_Backoff_When_RetryAfter_Is_Garbage() {
        val interceptor = interceptor(maxAttempts = null)

        for (value in listOf("soon", "", "1.5", "Wed, 99 Foo 2026 07:28:00 GMT")) {
            val policy = decide(interceptor, failure(503, "Retry-After" to value))
            assertEquals(value, transientDelay, assertAfterDelay(policy))
        }
    }

    @Test
    fun shouldRetry_Uses_Backoff_When_RetryAfter_Is_Negative() {
        val result = failure(503, "Retry-After" to "-5")

        assertEquals(transientDelay, assertAfterDelay(decide(interceptor(maxAttempts = null), result)))
    }

    @Test
    fun retryWithBackoff_Uses_Full_Jitter_Delays() {
        val interceptor = RequestInterceptors.retryWithBackoff(
            maxAttempts = null,
            transientBaseDelay = 100.milliseconds,
            throttlingBaseDelay = 1.seconds,
            maxDelay = 20.seconds
        ) as RetryableErrorInterceptor

        assertEquals(20_000L, interceptor.maxDelay)
        assertNull(interceptor.maxAttempts)
        for (retryCount in 0 until 10) {
            val transient = assertAfterDelay(decide(interceptor, failure(503), retryCount))
            assertTrue("$transient", transient in 0..minOf(20_000L, 100L shl retryCount))

            val throttling = assertAfterDelay(decide(interceptor, failure(429), retryCount))
            assertTrue("$throttling", throttling in 0..minOf(20_000L, 1_000L shl retryCount))
        }
        assertRandomDelays(interceptor, 3, failure(503), upperBound = 800)
        assertRandomDelays(interceptor, 3, failure(429), upperBound = 8_000)
    }

    @Test
    fun retryWithBackoff_Uses_Defaults() {
        val interceptor = RequestInterceptors.retryWithBackoff() as RetryableErrorInterceptor

        assertEquals(3, interceptor.maxAttempts)
        assertEquals(20_000L, interceptor.maxDelay)
        assertSame(RetryAfterAboveMaxDelay.GIVE_UP, interceptor.retryAfterAboveMaxDelay)
        assertRandomDelays(interceptor, 1, failure(503), upperBound = 200)
        assertRandomDelays(interceptor, 1, failure(429), upperBound = 2_000)
    }

    @Test
    fun retryWithBackoff_Returns_RetryableErrorInterceptor_With_Given_Configuration() {
        val interceptor = RequestInterceptors.retryWithBackoff(
            maxAttempts = 5,
            maxDelay = 7.seconds,
            retryAfterAboveMaxDelay = RetryAfterAboveMaxDelay.WAIT
        ) as RetryableErrorInterceptor

        assertEquals(5, interceptor.maxAttempts)
        assertEquals(7_000L, interceptor.maxDelay)
        assertSame(RetryAfterAboveMaxDelay.WAIT, interceptor.retryAfterAboveMaxDelay)
    }

    private fun interceptor(
        maxAttempts: Int?,
        maxDelay: Long = 20_000,
        retryAfterAboveMaxDelay: RetryAfterAboveMaxDelay = RetryAfterAboveMaxDelay.GIVE_UP,
        now: () -> Long = System::currentTimeMillis
    ) = RetryableErrorInterceptor(
        maxAttempts = maxAttempts,
        maxDelay = maxDelay,
        transientBackoff = Backoff { transientDelay },
        throttlingBackoff = Backoff { throttlingDelay },
        retryAfterAboveMaxDelay = retryAfterAboveMaxDelay,
        now = now
    )

    private fun decide(
        interceptor: RetryableErrorInterceptor,
        result: NetworkResult,
        consecutiveRetryCount: Int = 0
    ): RetryPolicy = interceptor.shouldRetry(request, consecutiveRetryCount, result)

    private fun failure(status: Int, vararg headers: Pair<String, String>): NetworkResult {
        val response = HttpResponse(
            localhost,
            status,
            "",
            headers.associate { (name, value) -> name to listOf(value) }
        )
        return Failure(Non200Exception(status, response))
    }

    /**
     * Samples the delay several times, asserting every sample is within `0..upperBound` and that
     * they are not all equal.
     */
    private fun assertRandomDelays(
        interceptor: RetryableErrorInterceptor,
        consecutiveRetryCount: Int,
        result: NetworkResult,
        upperBound: Long
    ) {
        val samples = (0 until 20).map {
            assertAfterDelay(decide(interceptor, result, consecutiveRetryCount))
        }
        samples.forEach { sample ->
            assertTrue("Expected a delay in 0..$upperBound, got $sample", sample in 0..upperBound)
        }
        assertTrue("Delays should be random, got $samples", samples.toSet().size > 1)
    }

    /**
     * The IMF-fixdate of [retryDate] shifted by [millisFromRetryDate].
     */
    private fun httpDate(millisFromRetryDate: Long): String {
        val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("GMT")
        return formatter.format(Date(retryDate + millisFromRetryDate))
    }

    private companion object {
        val localhost = URL("https://localhost/")
        val request: HttpRequest = HttpRequest.get("https://localhost/").build()
    }
}
