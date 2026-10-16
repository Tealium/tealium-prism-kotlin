package com.tealium.prism.core.internal.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTests {

    private val receivedBounds = mutableListOf<Long>()

    // region exponential

    @Test
    fun exponential_Doubles_Delay_When_Retry_Count_Increases() {
        val backoff = Backoff.exponential(initialDelay = 100, maximum = 20_000)

        val delays = (0..7).map(backoff::delay)

        assertEquals(listOf(100L, 200L, 400L, 800L, 1600L, 3200L, 6400L, 12_800L), delays)
    }

    @Test
    fun exponential_Uses_Multiplier_When_Provided() {
        val backoff = Backoff.exponential(initialDelay = 100, multiplier = 3.0, maximum = 20_000)

        val delays = (0..3).map(backoff::delay)

        assertEquals(listOf(100L, 300L, 900L, 2700L), delays)
    }

    @Test
    fun exponential_Returns_Maximum_When_Delay_Exceeds_Maximum() {
        val backoff = Backoff.exponential(initialDelay = 2_000, maximum = 150_000)

        val delays = (0..8).map(backoff::delay)

        assertEquals(
            listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 64_000L, 128_000L, 150_000L, 150_000L),
            delays
        )
    }

    @Test
    fun exponential_Treats_Retry_Count_As_First_Retry_When_Negative() {
        val backoff = Backoff.exponential(initialDelay = 100, maximum = 20_000)

        assertEquals(100L, backoff.delay(-1))
        assertEquals(100L, backoff.delay(-3))
        assertEquals(100L, backoff.delay(Int.MIN_VALUE))
    }

    @Test
    fun exponential_Clamps_Exponent_To_Max_Exponent_When_Retry_Count_Is_Large() {
        val backoff = Backoff.exponential(initialDelay = 1, maximum = Long.MAX_VALUE)
        val expected = 1L shl Backoff.MAX_EXPONENT

        assertEquals(expected, backoff.delay(30))
        for (retryCount in listOf(31, 64, 1000, Int.MAX_VALUE)) {
            assertEquals("retryCount $retryCount", expected, backoff.delay(retryCount))
        }
    }

    @Test
    fun exponential_Returns_Maximum_When_Retry_Count_Is_Large() {
        val backoff = Backoff.exponential(initialDelay = 1_000, maximum = 20_000)

        for (retryCount in listOf(30, 64, 1000, Int.MAX_VALUE)) {
            assertEquals("retryCount $retryCount", 20_000L, backoff.delay(retryCount))
        }
    }

    @Test
    fun exponential_Saturates_To_Maximum_When_Delay_Overflows() {
        val hugeInitialDelay = Backoff.exponential(initialDelay = Long.MAX_VALUE / 2, maximum = Long.MAX_VALUE)
        val maxInitialDelay = Backoff.exponential(initialDelay = Long.MAX_VALUE, maximum = Long.MAX_VALUE)
        val hugeMultiplier = Backoff.exponential(initialDelay = 2, multiplier = Double.MAX_VALUE, maximum = 150_000)
        val infiniteMultiplier = Backoff.exponential(
            initialDelay = 2, multiplier = Double.POSITIVE_INFINITY, maximum = Long.MAX_VALUE
        )

        assertEquals(Long.MAX_VALUE / 2, hugeInitialDelay.delay(0))
        for (retryCount in listOf(1, 2, 30, 64, 1000, Int.MAX_VALUE)) {
            assertEquals("retryCount $retryCount", Long.MAX_VALUE, hugeInitialDelay.delay(retryCount))
            assertEquals("retryCount $retryCount", Long.MAX_VALUE, maxInitialDelay.delay(retryCount))
            assertEquals("retryCount $retryCount", 150_000L, hugeMultiplier.delay(retryCount))
            assertEquals("retryCount $retryCount", Long.MAX_VALUE, infiniteMultiplier.delay(retryCount))
        }
    }

    @Test
    fun exponential_Returns_Initial_Delay_Exactly_When_First_Retry() {
        val initialDelay = Long.MAX_VALUE - 1

        val backoff = Backoff.exponential(initialDelay = initialDelay, maximum = Long.MAX_VALUE)

        assertEquals(initialDelay, backoff.delay(0))
    }

    @Test
    fun exponential_Returns_Zero_When_Maximum_Is_Not_Positive() {
        val negativeMaximum = Backoff.exponential(initialDelay = 100, maximum = -10)
        val zeroMaximum = Backoff.exponential(initialDelay = 100, maximum = 0)

        assertEquals(0L, negativeMaximum.delay(0))
        assertEquals(0L, negativeMaximum.delay(5))
        assertEquals(0L, zeroMaximum.delay(0))
        assertEquals(0L, zeroMaximum.delay(5))
    }

    @Test
    fun exponential_Returns_Zero_When_Initial_Delay_Is_Not_Positive() {
        val negativeInitialDelay = Backoff.exponential(initialDelay = -100, maximum = 20_000)
        val zeroInitialDelay = Backoff.exponential(initialDelay = 0, maximum = 20_000)

        assertEquals(0L, negativeInitialDelay.delay(0))
        assertEquals(0L, negativeInitialDelay.delay(5))
        assertEquals(0L, zeroInitialDelay.delay(0))
        assertEquals(0L, zeroInitialDelay.delay(5))
    }

    @Test
    fun exponential_Returns_Zero_When_Multiplier_Is_Nan() {
        val backoff = Backoff.exponential(initialDelay = 100, multiplier = Double.NaN, maximum = 20_000)

        assertEquals(0L, backoff.delay(1))
    }

    @Test
    fun exponential_Never_Returns_Negative_When_Multiplier_Is_Negative() {
        val backoff = Backoff.exponential(initialDelay = 100, multiplier = -2.0, maximum = 20_000)

        for (retryCount in 0..10) {
            val delay = backoff.delay(retryCount)
            assertTrue("retryCount $retryCount returned $delay", delay in 0..20_000)
        }
    }

    // endregion

    // region jitteredExponential

    @Test
    fun jitteredExponential_Passes_Exponential_Upper_Bound_To_Random_When_Calculating() {
        val backoff = recordingJitteredBackoff(initialDelay = 100, maximum = 20_000)

        (0..3).forEach { backoff.delay(it) }

        assertEquals(listOf(100L, 200L, 400L, 800L), receivedBounds)
    }

    @Test
    fun jitteredExponential_Returns_Random_Value_When_Calculating() {
        val backoff = Backoff.jitteredExponential(initialDelay = 1_000, maximum = 20_000) { bound ->
            bound / 2
        }

        assertEquals(2_000L, backoff.delay(2))
    }

    @Test
    fun jitteredExponential_Treats_Retry_Count_As_First_Retry_When_Negative() {
        val backoff = recordingJitteredBackoff(initialDelay = 1_000, maximum = 20_000)

        backoff.delay(0)
        backoff.delay(-1)
        backoff.delay(-3)

        assertEquals(listOf(1_000L, 1_000L, 1_000L), receivedBounds)
    }

    @Test
    fun jitteredExponential_Caps_Upper_Bound_At_Maximum_When_Delay_Exceeds_Maximum() {
        val backoff = recordingJitteredBackoff(initialDelay = 1_000, maximum = 20_000)

        backoff.delay(4)
        backoff.delay(5)
        assertEquals(20_000L, backoff.delay(1000))
        assertEquals(20_000L, backoff.delay(Int.MAX_VALUE))

        assertEquals(listOf(16_000L, 20_000L, 20_000L, 20_000L), receivedBounds)
    }

    @Test
    fun jitteredExponential_Does_Not_Call_Random_When_Upper_Bound_Is_Zero() {
        val zeroInitialDelay = recordingJitteredBackoff(initialDelay = 0, maximum = 20_000)
        val negativeValues = recordingJitteredBackoff(initialDelay = -1_000, maximum = -5_000)

        assertEquals(0L, zeroInitialDelay.delay(0))
        assertEquals(0L, negativeValues.delay(1))

        assertEquals(emptyList<Long>(), receivedBounds)
    }

    @Test
    fun jitteredExponential_Stays_Within_Bounds_When_Using_Default_Random() {
        val backoff = Backoff.jitteredExponential(initialDelay = 1_000, maximum = 20_000)

        repeat(100) {
            for (retryCount in 0..9) {
                val upperBound = minOf(20_000L, 1_000L shl retryCount)
                val delay = backoff.delay(retryCount)
                assertTrue("retryCount $retryCount returned $delay", delay in 0..upperBound)
            }
        }
    }

    @Test
    fun jitteredExponential_Stays_Within_Bounds_When_Using_Default_Random_With_Saturated_Bound() {
        val backoff = Backoff.jitteredExponential(initialDelay = Long.MAX_VALUE, maximum = Long.MAX_VALUE)

        repeat(100) {
            assertTrue(backoff.delay(5) >= 0)
        }
    }

    // endregion

    // region map

    @Test
    fun map_Returns_Same_Delays_When_Transform_Is_Identity() {
        val backoff = Backoff.exponential(initialDelay = 2_000, maximum = 150_000)

        val mapped = backoff.map { it }

        for (retryCount in 0..10) {
            assertEquals(backoff.delay(retryCount), mapped.delay(retryCount))
        }
    }

    @Test
    fun map_Applies_Transforms_In_Order_When_Chained() {
        val backoff = Backoff.exponential(initialDelay = 2_000, maximum = 150_000)
        val double: (Long) -> Long = { it * 2 }
        val addOne: (Long) -> Long = { it + 1 }

        val composed = backoff.map(double).map(addOne)
        val combined = backoff.map { addOne(double(it)) }

        for (retryCount in 0..10) {
            assertEquals(combined.delay(retryCount), composed.delay(retryCount))
        }
    }

    @Test
    fun map_Applies_Transform_To_Delay_When_Calculating() {
        val backoff = Backoff { retryCount -> retryCount.toLong() }

        val mapped = backoff.map { it * 10 }

        for (retryCount in 0..10) {
            assertEquals(retryCount * 10L, mapped.delay(retryCount))
        }
    }

    // endregion

    private fun recordingJitteredBackoff(initialDelay: Long, maximum: Long): Backoff =
        Backoff.jitteredExponential(initialDelay = initialDelay, maximum = maximum) { bound ->
            receivedBounds.add(bound)
            bound
        }
}
