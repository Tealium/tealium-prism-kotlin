package com.tealium.prism.lifecycle.lifecycleService

import com.tealium.prism.lifecycle.LifecycleEvent
import com.tealium.prism.lifecycle.internal.StateKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date

class UpdateLaunchTests : LifecycleServiceBaseTests() {
    private val calendar: Calendar = Calendar.getInstance()

    private val customEventTimestamp
        get() = launchTimestamp + millisecondsPerDay + millisecondsPerHour

    @Before
    override fun setUp() {
        super.setUp()
        lifecycleStorage.setCurrentAppVersion("0.9")
        lifecycleEventState = lifecycleService.registerLaunch(launchTimestamp)
        customEventState = lifecycleService.getCurrentState(customEventTimestamp)
    }

    @Test
    fun didDetectCrash_isCorrect() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_DIDDETECT_CRASH))
        assertNull(customEventState.get(StateKey.LIFECYCLE_DIDDETECT_CRASH))
    }

    @Test
    fun dayOfWeekLocal_isCorrectInt() {
        calendar.time = Date(launchTimestamp)
        assertEquals(calendar.get(Calendar.DAY_OF_WEEK), lifecycleEventState.getInt(StateKey.LIFECYCLE_DAYOFWEEK_LOCAL))
        calendar.time = Date(customEventTimestamp)
        assertEquals(calendar.get(Calendar.DAY_OF_WEEK), customEventState.getInt(StateKey.LIFECYCLE_DAYOFWEEK_LOCAL))
    }

    @Test
    fun daysSinceLaunch_isCorrect() {
        assertEquals(0, lifecycleEventState.getInt(StateKey.LIFECYCLE_DAYSSINCEFIRSTLAUNCH))
        assertEquals(1, customEventState.getInt(StateKey.LIFECYCLE_DAYSSINCEFIRSTLAUNCH))
    }

    @Test
    fun daysSinceUpdate_isCorrect() {
        assertEquals(0, lifecycleEventState.getInt(StateKey.LIFECYCLE_DAYSSINCEUPDATE))
        assertEquals(1, customEventState.getInt(StateKey.LIFECYCLE_DAYSSINCEUPDATE))
    }

    @Test
    fun daysSinceLastWake_isCorrect() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_DAYSSINCELASTWAKE))
        assertEquals(1, customEventState.getInt(StateKey.LIFECYCLE_DAYSSINCELASTWAKE))
    }

    @Test
    fun firstLaunchDate_isCorrectString() {
        assertEquals(launchDateString, lifecycleEventState.getString(StateKey.LIFECYCLE_FIRSTLAUNCHDATE))
        assertEquals(launchDateString, customEventState.getString(StateKey.LIFECYCLE_FIRSTLAUNCHDATE))
    }

    @Test
    fun firstLaunchDateMmDdYyyy_isCorrectString() {
        assertEquals(launchMmDdYyyyString, lifecycleEventState.getString(StateKey.LIFECYCLE_FIRSTLAUNCHDATE_MMDDYYYY))
        assertEquals(launchMmDdYyyyString, customEventState.getString(StateKey.LIFECYCLE_FIRSTLAUNCHDATE_MMDDYYYY))
    }

    @Test
    fun hourOfDayLocal_isCorrectInt() {
        calendar.time = Date(launchTimestamp)
        assertEquals(calendar.get(Calendar.HOUR_OF_DAY), lifecycleEventState.getInt(StateKey.LIFECYCLE_HOUROFDAY_LOCAL))
        calendar.time = Date(customEventTimestamp)
        assertEquals(calendar.get(Calendar.HOUR_OF_DAY), customEventState.getInt(StateKey.LIFECYCLE_HOUROFDAY_LOCAL))
    }

    @Test
    fun isFirstLaunch_isCorrect() {
        assertEquals(true, lifecycleEventState.getBoolean(StateKey.LIFECYCLE_ISFIRSTLAUNCH))
        assertNull(customEventState.get(StateKey.LIFECYCLE_ISFIRSTLAUNCH))
    }

    @Test
    fun isFirstLaunchUpdate_isCorrect() {
        assertEquals(true, lifecycleEventState.getBoolean(StateKey.LIFECYCLE_ISFIRSTLAUNCHUPDATE))
        assertNull(customEventState.get(StateKey.LIFECYCLE_ISFIRSTLAUNCHUPDATE))
    }

    @Test
    fun isFirstWakeMonth_isCorrect() {
        assertEquals(true, lifecycleEventState.getBoolean(StateKey.LIFECYCLE_ISFIRSTWAKEMONTH))
        assertNull(customEventState.get(StateKey.LIFECYCLE_ISFIRSTWAKEMONTH))
    }

    @Test
    fun isFirstWakeToday_isCorrect() {
        assertEquals(true, lifecycleEventState.getBoolean(StateKey.LIFECYCLE_ISFIRSTWAKETODAY))
        assertNull(customEventState.get(StateKey.LIFECYCLE_ISFIRSTWAKETODAY))
    }

    @Test
    fun launchCount_isCorrect() {
        assertEquals(1, lifecycleEventState.getInt(StateKey.LIFECYCLE_LAUNCHCOUNT))
        assertEquals(1, customEventState.getInt(StateKey.LIFECYCLE_LAUNCHCOUNT))
    }

    @Test
    fun priorSecondsAwake_isCorrect() {
        assertEquals(0L, lifecycleEventState.getLong(StateKey.LIFECYCLE_PRIORSECONDSAWAKE))
        assertNull(customEventState.get(StateKey.LIFECYCLE_PRIORSECONDSAWAKE))
    }

    @Test
    fun secondsAwake_isCorrect() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_SECONDSAWAKE))
        assertEquals(secondsPerDay + secondsPerHour, customEventState.getLong(StateKey.LIFECYCLE_SECONDSAWAKE))
    }

    @Test
    fun sleepCount_isCorrect() {
        assertEquals(0, lifecycleEventState.getInt(StateKey.LIFECYCLE_SLEEPCOUNT))
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_SLEEPCOUNT))
    }

    @Test
    fun totalCrashCount_isZero() {
        assertEquals(0, lifecycleEventState.getInt(StateKey.LIFECYCLE_TOTALCRASHCOUNT))
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_TOTALCRASHCOUNT))
    }

    @Test
    fun totalLaunchCount_isCorrect() {
        assertEquals(1, lifecycleEventState.getInt(StateKey.LIFECYCLE_TOTALLAUNCHCOUNT))
        assertEquals(1, customEventState.getInt(StateKey.LIFECYCLE_TOTALLAUNCHCOUNT))
    }

    @Test
    fun totalSecondsAwake_isCorrect() {
        assertEquals(0L, lifecycleEventState.getLong(StateKey.LIFECYCLE_TOTALSECONDSAWAKE))
        assertEquals(secondsPerDay + secondsPerHour, customEventState.getLong(StateKey.LIFECYCLE_TOTALSECONDSAWAKE))
    }

    @Test
    fun totalSleepCount_isCorrect() {
        assertEquals(0, lifecycleEventState.getInt(StateKey.LIFECYCLE_TOTALSLEEPCOUNT))
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_TOTALSLEEPCOUNT))
    }

    @Test
    fun totalWakeCount_isCorrect() {
        assertEquals(1, lifecycleEventState.getInt(StateKey.LIFECYCLE_TOTALWAKECOUNT))
        assertEquals(1, customEventState.getInt(StateKey.LIFECYCLE_TOTALWAKECOUNT))
    }

    @Test
    fun updateLaunchDate_isCorrect() {
        assertEquals(launchDateString, lifecycleEventState.getString(StateKey.LIFECYCLE_UPDATELAUNCHDATE))
        assertEquals(launchDateString, customEventState.getString(StateKey.LIFECYCLE_UPDATELAUNCHDATE))
    }

    @Test
    fun lifecycleType_isCorrect() {
        assertEquals(LifecycleEvent.Launch.event, lifecycleEventState.getString(StateKey.LIFECYCLE_TYPE))
        assertNull(customEventState.get(StateKey.LIFECYCLE_TYPE))
    }

    @Test
    fun wakeCount_isCorrect() {
        assertEquals(1, lifecycleEventState.getInt(StateKey.LIFECYCLE_WAKECOUNT))
        assertEquals(1, customEventState.getInt(StateKey.LIFECYCLE_WAKECOUNT))
    }

    @Test
    fun lastLaunchDate_isCorrect() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_LASTLAUNCHDATE))
        assertEquals(launchDateString, customEventState.getString(StateKey.LIFECYCLE_LASTLAUNCHDATE))
    }

    @Test
    fun lastSleepDate_isCorrect() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_LASTSLEEPDATE))
        assertNull(customEventState.get(StateKey.LIFECYCLE_LASTSLEEPDATE))
    }

    @Test
    fun lastWakeDate_isCorrect() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_LASTWAKEDATE))
        assertEquals(launchDateString, customEventState.getString(StateKey.LIFECYCLE_LASTWAKEDATE))
    }
}