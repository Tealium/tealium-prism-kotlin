package com.tealium.lifecycle

import android.content.Context
import com.tealium.lifecycle.internal.LifecycleServiceImpl
import com.tealium.lifecycle.internal.LifecycleStorage
import com.tealium.lifecycle.internal.LifecycleStorageKey
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class LifecycleServiceTests {

    @MockK
    private lateinit var mockContext: Context

    @RelaxedMockK
    private lateinit var mockLifecycleStorage: LifecycleStorage

    private lateinit var lifecycleService: LifecycleServiceImpl

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        lifecycleService = LifecycleServiceImpl(mockContext, mockLifecycleStorage)
    }

    @Test
    fun didDetectCrash_ShouldBeFalse() {
        every { mockLifecycleStorage.lastLifecycleEvent } returns null
        assertFalse(lifecycleService.didDetectCrash())

        every { mockLifecycleStorage.lastLifecycleEvent } returns LifecycleEvent.Sleep
        assertFalse(lifecycleService.didDetectCrash())
    }

    @Test
    fun didDetectCrash_ShouldBeTrue() {
        every { mockLifecycleStorage.lastLifecycleEvent } returns LifecycleEvent.Launch
        assertTrue(lifecycleService.didDetectCrash())

        every { mockLifecycleStorage.lastLifecycleEvent } returns LifecycleEvent.Wake
        assertTrue(lifecycleService.didDetectCrash())
    }

    @Test
    fun updateAppVersion_ShouldBeFalse() {
        every { mockLifecycleStorage.currentAppVersion } returns "10"
        assertFalse(lifecycleService.updateAppVersion(1L, "10"))

        every { mockLifecycleStorage.currentAppVersion } returns null
        assertFalse(lifecycleService.updateAppVersion(1L, "10"))

        verify {
            mockLifecycleStorage.setCurrentAppVersion("10")
        }
    }

    @Test
    fun updateAppVersion_ShouldBeTrue() {
        every { mockLifecycleStorage.currentAppVersion } returns "10"
        assertTrue(lifecycleService.updateAppVersion(1L, "11"))

        verify {
            mockLifecycleStorage.resetCountsAfterAppUpdate(1L, "11")
        }
    }

    @Test
    fun daysSince_ValidStartAndEndDates_IsZeroOrPositive() {
        val currentTimeMs = getCurrentTime()
        var futureTimeMs = addDays(currentTimeMs, 3)

        var daysSince = LifecycleServiceImpl.daysSince(currentTimeMs, futureTimeMs)
        assertEquals(3L, daysSince)

        futureTimeMs = addDays(currentTimeMs, 10)
        daysSince = LifecycleServiceImpl.daysSince(currentTimeMs, futureTimeMs)
        assertEquals(10L, daysSince)

        daysSince = LifecycleServiceImpl.daysSince(currentTimeMs, currentTimeMs)
        assertEquals(0L, daysSince)
    }

    @Test
    fun daysSince_NegativeStartAndEndDate_ReturnsNull() {
        val currentTimeMs = getCurrentTime()
        var futureTimeMs = Long.MIN_VALUE

        var daysSince = LifecycleServiceImpl.daysSince(currentTimeMs, futureTimeMs)
        assertEquals(null, daysSince)

        futureTimeMs = -1L
        daysSince = LifecycleServiceImpl.daysSince(currentTimeMs, futureTimeMs)
        assertEquals(null, daysSince)

        daysSince = LifecycleServiceImpl.daysSince(Long.MIN_VALUE, 0L)
        assertEquals(null, daysSince)
    }

    @Test
    fun setFormattedEvent_lastLaunch_SavesValue() {
        val expectedFormattedDate = "2023-03-01T00:00:00Z"
        every { mockLifecycleStorage.getLastEventTimestamp(LifecycleStorageKey.TIMESTAMP_LAST_LAUNCH) } returns 1677628800000L

        lifecycleService.setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_LAUNCH)

        assertEquals(expectedFormattedDate, lifecycleService.lastLaunchString)
    }

    @Test
    fun setFormattedEvent_lastWake_SavesValue() {
        val expectedFormattedDate = "2023-03-01T00:00:00Z"
        every { mockLifecycleStorage.getLastEventTimestamp(LifecycleStorageKey.TIMESTAMP_LAST_WAKE) } returns 1677628800000L

        lifecycleService.setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_WAKE)

        assertEquals(expectedFormattedDate, lifecycleService.lastWakeString)
    }

    @Test
    fun setFormattedEvent_LastSleep_SavesValue() {
        val expectedFormattedDate = "2023-03-01T00:00:00Z"
        every { mockLifecycleStorage.getLastEventTimestamp(LifecycleStorageKey.TIMESTAMP_LAST_SLEEP) } returns 1677628800000L

        lifecycleService.setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_SLEEP)

        assertEquals(expectedFormattedDate, lifecycleService.lastSleepString)
    }

    @Test
    fun setFormatted_FirstLaunch_SavesValue() {
        every { mockLifecycleStorage.timestampFirstLaunch } returns null
        val fallbackTimestamp = 1677628800000L

        val expectedFormattedDate = "2023-03-01T00:00:00Z"
        val result = lifecycleService.setFormattedFirstLaunch(fallbackTimestamp)

        assertEquals(expectedFormattedDate, result)
        assertEquals(expectedFormattedDate, lifecycleService.firstLaunchString)
    }

    @Test
    fun setUpdateLaunchDate_SavesValues() {
        every { mockLifecycleStorage.timestampUpdate } returns 1677628800000L
        val expectedFormattedDate = "2023-03-01T00:00:00Z"

        lifecycleService.setUpdateLaunchDate()

        assertEquals(expectedFormattedDate, lifecycleService.updateLaunchDate)
    }

    @Test
    fun setFirstLaunchMmDdYyyy_ReturnsCurrentDate_WhenTimestampFirstLaunch_IsNull() {
        every { mockLifecycleStorage.timestampFirstLaunch } returns null

        val currentDate = Date(System.currentTimeMillis())
        val currentDateFormatMmDdYyyy = SimpleDateFormat("MM/dd/yyy", Locale.ROOT)
        currentDateFormatMmDdYyyy.timeZone = TimeZone.getTimeZone("UTC")

        val firstLaunchDateMmDdYyyy = lifecycleService.setFirstLaunchMmDdYyyy()

        assertEquals(currentDateFormatMmDdYyyy.format(currentDate), firstLaunchDateMmDdYyyy!!)
    }

    @Test
    fun calculateSecondsAwakeDelta_WhenIncomingEventIsNull_ReturnsCorrectDelta() {
        val timestamp = 1678886400000L

        every { mockLifecycleStorage.lastLifecycleEvent } returns LifecycleEvent.Wake
        every { mockLifecycleStorage.timestampLastWake } returns 1678883200000L

        val secondsAwakeDelta = lifecycleService.calculateSecondsAwakeDelta(timestamp, null)

        assertEquals(3200, secondsAwakeDelta)
    }

    @Test
    fun calculateSecondsAwakeDelta_WhenIncomingEventIsNotNull_ReturnsZero() {
        val timestamp = 1678886400000L

        every { mockLifecycleStorage.lastLifecycleEvent } returns LifecycleEvent.Wake
        every { mockLifecycleStorage.timestampLastWake } returns 1677628800000L

        val secondsAwakeDelta =
            lifecycleService.calculateSecondsAwakeDelta(timestamp, LifecycleEvent.Launch)

        assertEquals(0, secondsAwakeDelta)
    }

    @Test
    fun calculateSecondsAwakeDelta_WhenIncomingEventIsNull_AndLastEventIsSleep_ReturnsZero() {
        val timestamp = 1678886400000L

        every { mockLifecycleStorage.lastLifecycleEvent } returns LifecycleEvent.Sleep
        every { mockLifecycleStorage.timestampLastWake } returns 1677628800000L

        val secondsAwakeDelta =
            lifecycleService.calculateSecondsAwakeDelta(timestamp, null)

        assertEquals(0, secondsAwakeDelta)
    }

    private fun getCurrentTime(): Long {
        return System.currentTimeMillis()
    }

    private fun addDays(timestampMs: Long, days: Long): Long {
        return timestampMs + TimeUnit.DAYS.toMillis(days)
    }
}