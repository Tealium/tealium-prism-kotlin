package com.tealium.core.internal.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class TimeFrameTests {

    @Test
    fun compareTo_Returns_Zero_When_Values_And_Units_Are_The_Same() {
        val tenSeconds = TimeFrame(10, TimeUnit.SECONDS)
        val tenMoreSeconds = TimeFrame(10, TimeUnit.SECONDS)

        assertEquals(0, tenSeconds.compareTo(tenMoreSeconds))
        assertTrue(tenSeconds >= tenMoreSeconds)
        assertTrue(tenSeconds <= tenMoreSeconds)

        assertFalse(tenSeconds < tenMoreSeconds)
        assertFalse(tenSeconds > tenMoreSeconds)
    }

    @Test
    fun compareTo_Returns_Positive_When_Lhs_Is_Greater_Than_Rhs() {
        val tenSeconds = TimeFrame(10, TimeUnit.SECONDS)
        val elevenSeconds = TimeFrame(11, TimeUnit.SECONDS)

        assertTrue(elevenSeconds > tenSeconds)
    }

    @Test
    fun compareTo_Returns_Negative_When_Lhs_Is_Less_Than_Rhs() {
        val tenSeconds = TimeFrame(10, TimeUnit.SECONDS)
        val elevenSeconds = TimeFrame(11, TimeUnit.SECONDS)

        assertFalse(tenSeconds > elevenSeconds)
    }

    @Test
    fun compareTo_Returns_Zero_When_Equal_And_Units_Dont_Match() {
        val sixtySeconds = TimeFrame(60, TimeUnit.SECONDS)
        val oneMinute = TimeFrame(1, TimeUnit.MINUTES)

        assertTrue(sixtySeconds >= oneMinute)

        assertFalse(sixtySeconds > oneMinute)
        assertFalse(sixtySeconds < oneMinute)
    }

    @Test
    fun compareTo_Returns_Positive_When_Lhs_Is_Greater_than_Rhs_And_Units_Dont_Match() {
        val sixtyOneSeconds = TimeFrame(61, TimeUnit.SECONDS)
        val oneMinute = TimeFrame(1, TimeUnit.MINUTES)

        assertTrue(sixtyOneSeconds > oneMinute)

        assertFalse(sixtyOneSeconds <= oneMinute)
        assertFalse(sixtyOneSeconds < oneMinute)
    }

    @Test
    fun compareTo_Returns_Negative_When_Lhs_Is_Smaller_than_Rhs_And_Units_Dont_Match() {
        val fiftyNineSeconds = TimeFrame(59, TimeUnit.SECONDS)
        val oneMinute = TimeFrame(1, TimeUnit.MINUTES)

        assertTrue(fiftyNineSeconds < oneMinute)

        assertFalse(fiftyNineSeconds >= oneMinute)
        assertFalse(fiftyNineSeconds > oneMinute)
    }

    @Test
    fun compareTo_Returns_Zero_When_Units_Dont_Match_And_Unsafe_Conversion() {
        // 8,640,000,000,000,000,000L
        val oneHundredThousandDays = TimeFrame(100_000, TimeUnit.DAYS)
        // 86,400,000,000,000,000,000L // overflow to Long.MAX_VALUE
        val oneMillionDays = TimeFrame(1_000_000, TimeUnit.DAYS)
        // 9,223,372,036,854,775,807L
        val maxNanos = TimeFrame(Long.MAX_VALUE, TimeUnit.NANOSECONDS)

        assertTrue(oneHundredThousandDays < maxNanos)
        assertTrue(oneMillionDays.compareTo(maxNanos) == 0) // overflow error

        assertTrue(maxNanos > oneHundredThousandDays )
        assertTrue(maxNanos.compareTo(oneMillionDays) == 0) // overflow error
    }
}