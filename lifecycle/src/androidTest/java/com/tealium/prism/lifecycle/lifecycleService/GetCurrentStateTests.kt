package com.tealium.prism.lifecycle.lifecycleService

import com.tealium.prism.lifecycle.internal.StateKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date

class GetCurrentStateTests : LifecycleServiceBaseTests() {
    private val calendar: Calendar = Calendar.getInstance()

    @Before
    override fun setUp() {
        super.setUp()
        calendar.time = Date(launchTimestamp)
        customEventState = lifecycleService.getCurrentState(timestamp = launchTimestamp)
    }

    @Test
    fun didDetectCrash_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_DIDDETECT_CRASH))
    }

    @Test
    fun dayOfWeekLocal_isCorrectInt() {
        assertEquals(calendar.get(Calendar.DAY_OF_WEEK), customEventState.getInt(StateKey.LIFECYCLE_DAYOFWEEK_LOCAL))
    }

    @Test
    fun daysSinceLaunch_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_DAYSSINCEFIRSTLAUNCH))
    }

    @Test
    fun daysSinceUpdate_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_DAYSSINCEUPDATE))
    }

    @Test
    fun daysSinceLastWake_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_DAYSSINCELASTWAKE))
    }

    @Test
    fun firstLaunchDate_isCorrectString() {
        assertEquals(launchDateString, customEventState.getString(StateKey.LIFECYCLE_FIRSTLAUNCHDATE))
    }

    @Test
    fun firstLaunchDateMmDdYyyy_isCorrectString() {
        assertEquals(launchMmDdYyyyString, customEventState.getString(StateKey.LIFECYCLE_FIRSTLAUNCHDATE_MMDDYYYY))
    }

    @Test
    fun hourOfDayLocal_isCorrectInt() {
        assertEquals(calendar.get(Calendar.HOUR_OF_DAY), customEventState.getInt(StateKey.LIFECYCLE_HOUROFDAY_LOCAL))
    }

    @Test
    fun isFirstLaunch_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_ISFIRSTLAUNCH))
    }

    @Test
    fun isFirstLaunchUpdate_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_ISFIRSTLAUNCHUPDATE))
    }

    @Test
    fun isFirstWakeMonth_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_ISFIRSTWAKEMONTH))
    }

    @Test
    fun isFirstWakeToday_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_ISFIRSTWAKETODAY))
    }

    @Test
    fun launchCount_isZero() {
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_LAUNCHCOUNT))
    }

    @Test
    fun priorSecondsAwake_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_PRIORSECONDSAWAKE))
    }

    @Test
    fun secondsAwake_isZero() {
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_SECONDSAWAKE))
    }

    @Test
    fun sleepCount_isZero() {
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_SLEEPCOUNT))
    }

    @Test
    fun totalCrashCount_isZero() {
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_TOTALCRASHCOUNT))
    }

    @Test
    fun totalLaunchCount_isZero() {
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_TOTALLAUNCHCOUNT))
    }

    @Test
    fun totalSecondsAwake_isZero() {
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_TOTALSECONDSAWAKE))
    }

    @Test
    fun totalSleepCount_isZero() {
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_TOTALSLEEPCOUNT))
    }

    @Test
    fun totalWakeCount_isZero() {
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_TOTALWAKECOUNT))
    }

    @Test
    fun updateLaunchDate_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_UPDATELAUNCHDATE))
    }

    @Test
    fun lifecycleType_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_TYPE))
    }

    @Test
    fun wakeCount_isZero() {
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_WAKECOUNT))
    }

    @Test
    fun lastLaunchDate_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_LASTLAUNCHDATE))
    }

    @Test
    fun lastSleepDate_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_LASTSLEEPDATE))
    }

    @Test
    fun lastWakeDate_isNull() {
        assertNull(customEventState.get(StateKey.LIFECYCLE_LASTWAKEDATE))
    }
}