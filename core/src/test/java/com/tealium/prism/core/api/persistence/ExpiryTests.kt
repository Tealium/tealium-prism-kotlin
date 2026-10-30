package com.tealium.prism.core.api.persistence

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class ExpiryTests {

    @Test
    fun testExpiryValues() {

        assertEquals(-2, Expiry.SESSION.expiryTime())
        assertEquals(-1, Expiry.FOREVER.expiryTime())

        val timeInSeconds = System.currentTimeMillis() / 1000
        val secondsInTheFuture = 100L

        // timeUnit
        assertEquals(timeInSeconds + secondsInTheFuture,
            Expiry.afterTimeUnit(secondsInTheFuture, TimeUnit.SECONDS, timeInSeconds).expiryTime())
        assertEquals(timeInSeconds + secondsInTheFuture * 60 * 60 * 24,
            Expiry.afterTimeUnit(secondsInTheFuture, TimeUnit.DAYS, timeInSeconds).expiryTime())

        // date
        assertEquals(timeInSeconds + secondsInTheFuture,
            Expiry.afterDate(0, 0, 0, secondsInTheFuture.toInt(), timeInSeconds).expiryTime())
        assertEquals(timeInSeconds + secondsInTheFuture * 60 * 60 * 24,
            Expiry.afterDate(secondsInTheFuture.toInt(), 0, 0, 0, timeInSeconds).expiryTime())
        assertEquals(timeInSeconds + (1 * 60 * 60 * 24) + (1 * 60 * 60) + (1 * 60) + 1,
            Expiry.afterDate(1, 1, 1, 1, timeInSeconds).expiryTime())

        // epochTime
        assertEquals(timeInSeconds + secondsInTheFuture,
            Expiry.afterEpochTime(timeInSeconds + secondsInTheFuture).expiryTime())
    }

    @Test
    fun testFromLongValue() {
        val timeInSeconds = System.currentTimeMillis() / 1000
        val secondsInTheFuture = 100L

        assertEquals(timeInSeconds + secondsInTheFuture,
            Expiry.fromLongValue(timeInSeconds + secondsInTheFuture).expiryTime())
        assertEquals(
            Expiry.SESSION,
            Expiry.fromLongValue(-2))
        assertEquals(
            Expiry.FOREVER,
            Expiry.fromLongValue(-1))

        val expiry = Expiry.fromLongValue(timeInSeconds + secondsInTheFuture)
        assertEquals(timeInSeconds + secondsInTheFuture, expiry.expiryTime())
        assertTrue(expiry.timeRemaining() <= secondsInTheFuture)
    }

    @Test
    fun testEquality() {
        val timeInSeconds = System.currentTimeMillis() / 1000

        val session = Expiry.SESSION
        val session2 = Expiry.SESSION

        assertTrue(session == session2)
        assertEquals(session.hashCode(), session2.hashCode())

        val forever = Expiry.FOREVER
        val forever2 = Expiry.FOREVER

        assertTrue(forever == forever2)
        assertEquals(forever.hashCode(), forever2.hashCode())

        val after1 = Expiry.afterEpochTime(timeInSeconds)
        val after2 = Expiry.afterEpochTime(timeInSeconds)
        val after3 = Expiry.afterEpochTime(timeInSeconds + 100)
        assertTrue(after1 == after2)
        assertTrue(after1 != after3)
        assertTrue(after2 != after3)
        assertEquals(after1.hashCode(), after2.hashCode())
        assertNotEquals(after1.hashCode(), after3.hashCode())
        assertNotEquals(after2.hashCode(), after3.hashCode())


        assertEquals(after1.hashCode(), after1.hashCode())
        assertEquals(after2.hashCode(), after2.hashCode())
        assertEquals(after3.hashCode(), after3.hashCode())

        val after4 = Expiry.afterEpochTime(-2)
        assertNotEquals(after4.hashCode(), Expiry.SESSION.hashCode())

    }

    @Test
    fun isExpired_Returns_True_When_Expiry_Time_Equals_Now() {
        // Captured before building the Expiry: if the clock ticks over to the next second between
        // here and the assertion, the expiry is in the past and still classed as expired.
        val now = currentTimestampSeconds()

        assertTrue(Expiry.isExpired(Expiry.afterEpochTime(now)))
    }

    @Test
    fun isExpired_Returns_False_When_Expiry_Time_Is_In_The_Future() {
        val now = currentTimestampSeconds()

        assertFalse(Expiry.isExpired(Expiry.afterEpochTime(now + 60)))
    }

    @Test
    fun isExpired_Returns_False_For_Special_Expiry_Values() {
        assertFalse(Expiry.isExpired(null))
        assertFalse(Expiry.isExpired(Expiry.FOREVER))
        assertFalse(Expiry.isExpired(Expiry.SESSION))
        assertFalse(Expiry.isExpired(Expiry.UNTIL_RESTART))
    }

    @Test
    fun isExpired_Returns_True_When_Expiry_Time_Is_Before_Epoch() {
        assertTrue(Expiry.isExpired(Expiry.afterEpochTime(-1L)))
    }

    private fun currentTimestampSeconds(): Long = System.currentTimeMillis() / 1000
}
