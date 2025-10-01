package com.tealium.prism.lifecycle.lifecycleService

import com.tealium.prism.lifecycle.LifecycleEvent
import com.tealium.prism.lifecycle.internal.StateKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date

class LaunchSleepLaunchInAMonthTests : LifecycleServiceBaseTests() {
    private val calendar: Calendar = Calendar.getInstance()

    private val sleepTimestamp
        get() = launchTimestamp + millisecondsPerDay

    private val sleepDateString
        get() = formatDate("yyyy-MM-dd'T'HH:mm:ss'Z'", sleepTimestamp)

    private val secondLaunchTimestamp
        get() = sleepTimestamp + (30 * millisecondsPerDay) + millisecondsPerHour

    private val secondLaunchDateString
        get() = formatDate("yyyy-MM-dd'T'HH:mm:ss'Z'", secondLaunchTimestamp)

    private val customEventTimestamp
        get() = secondLaunchTimestamp + millisecondsPerHour

    @Before
    override fun setUp() {
        super.setUp()
        lifecycleService.registerLaunch(launchTimestamp)
        lifecycleService.registerSleep(sleepTimestamp)
        lifecycleEventState = lifecycleService.registerLaunch(secondLaunchTimestamp)
        customEventState = lifecycleService.getCurrentState(customEventTimestamp)
    }

    @Test
    fun didDetectCrash_isCorrect() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_DIDDETECT_CRASH))
        assertNull(customEventState.get(StateKey.LIFECYCLE_DIDDETECT_CRASH))
    }

    @Test
    fun dayOfWeekLocal_isCorrectInt() {
        calendar.time = Date(secondLaunchTimestamp)
        assertEquals(calendar.get(Calendar.DAY_OF_WEEK), lifecycleEventState.getInt(StateKey.LIFECYCLE_DAYOFWEEK_LOCAL))
        calendar.time = Date(customEventTimestamp)
        assertEquals(calendar.get(Calendar.DAY_OF_WEEK), customEventState.getInt(StateKey.LIFECYCLE_DAYOFWEEK_LOCAL))
    }

    @Test
    fun daysSinceLaunch_isCorrect() {
        assertEquals(31, lifecycleEventState.getInt(StateKey.LIFECYCLE_DAYSSINCEFIRSTLAUNCH))
        assertEquals(31, customEventState.getInt(StateKey.LIFECYCLE_DAYSSINCEFIRSTLAUNCH))
    }

    @Test
    fun daysSinceUpdate_isNull() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_DAYSSINCEUPDATE))
        assertNull(customEventState.get(StateKey.LIFECYCLE_DAYSSINCEUPDATE))
    }

    @Test
    fun daysSinceLastWake_isCorrect() {
        assertEquals(31, lifecycleEventState.getInt(StateKey.LIFECYCLE_DAYSSINCELASTWAKE))
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_DAYSSINCELASTWAKE))
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
        calendar.time = Date(secondLaunchTimestamp)
        assertEquals(calendar.get(Calendar.HOUR_OF_DAY), lifecycleEventState.getInt(StateKey.LIFECYCLE_HOUROFDAY_LOCAL))
        calendar.time = Date(customEventTimestamp)
        assertEquals(calendar.get(Calendar.HOUR_OF_DAY), customEventState.getInt(StateKey.LIFECYCLE_HOUROFDAY_LOCAL))
    }

    @Test
    fun isFirstLaunch_isNull() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_ISFIRSTLAUNCH))
        assertNull(customEventState.get(StateKey.LIFECYCLE_ISFIRSTLAUNCH))
    }

    @Test
    fun isFirstLaunchUpdate_isNull() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_ISFIRSTLAUNCHUPDATE))
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
        assertEquals(2, lifecycleEventState.getInt(StateKey.LIFECYCLE_LAUNCHCOUNT))
        assertEquals(2, customEventState.getInt(StateKey.LIFECYCLE_LAUNCHCOUNT))
    }

    @Test
    fun priorSecondsAwake_isCorrect() {
        assertEquals(secondsPerDay, lifecycleEventState.getLong(StateKey.LIFECYCLE_PRIORSECONDSAWAKE))
        assertNull(customEventState.get(StateKey.LIFECYCLE_PRIORSECONDSAWAKE))
    }

    @Test
    fun secondsAwake_isCorrect() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_SECONDSAWAKE))
        assertEquals(secondsPerHour, customEventState.getLong(StateKey.LIFECYCLE_SECONDSAWAKE))
    }

    @Test
    fun sleepCount_isCorrect() {
        assertEquals(1, lifecycleEventState.getInt(StateKey.LIFECYCLE_SLEEPCOUNT))
        assertEquals(1, customEventState.getInt(StateKey.LIFECYCLE_SLEEPCOUNT))
    }

    @Test
    fun totalCrashCount_isZero() {
        assertEquals(0, lifecycleEventState.getInt(StateKey.LIFECYCLE_TOTALCRASHCOUNT))
        assertEquals(0, customEventState.getInt(StateKey.LIFECYCLE_TOTALCRASHCOUNT))
    }

    @Test
    fun totalLaunchCount_isCorrect() {
        assertEquals(2, lifecycleEventState.getInt(StateKey.LIFECYCLE_TOTALLAUNCHCOUNT))
        assertEquals(2, customEventState.getInt(StateKey.LIFECYCLE_TOTALLAUNCHCOUNT))
    }

    @Test
    fun totalSecondsAwake_isCorrect() {
        assertEquals(secondsPerDay, lifecycleEventState.getLong(StateKey.LIFECYCLE_TOTALSECONDSAWAKE))
        assertEquals(secondsPerDay + secondsPerHour, customEventState.getLong(StateKey.LIFECYCLE_TOTALSECONDSAWAKE))
    }

    @Test
    fun totalSleepCount_isCorrect() {
        assertEquals(1, lifecycleEventState.getInt(StateKey.LIFECYCLE_TOTALSLEEPCOUNT))
        assertEquals(1, customEventState.getInt(StateKey.LIFECYCLE_TOTALSLEEPCOUNT))
    }

    @Test
    fun totalWakeCount_isCorrect() {
        assertEquals(2, lifecycleEventState.getInt(StateKey.LIFECYCLE_TOTALWAKECOUNT))
        assertEquals(2, customEventState.getInt(StateKey.LIFECYCLE_TOTALWAKECOUNT))
    }

    @Test
    fun updateLaunchDate_isNull() {
        assertNull(lifecycleEventState.get(StateKey.LIFECYCLE_UPDATELAUNCHDATE))
        assertNull(customEventState.get(StateKey.LIFECYCLE_UPDATELAUNCHDATE))
    }

    @Test
    fun lifecycleType_isCorrect() {
        assertEquals(LifecycleEvent.Launch.event, lifecycleEventState.getString(StateKey.LIFECYCLE_TYPE))
        assertNull(customEventState.get(StateKey.LIFECYCLE_TYPE))
    }

    @Test
    fun wakeCount_isCorrect() {
        assertEquals(2, lifecycleEventState.getInt(StateKey.LIFECYCLE_WAKECOUNT))
        assertEquals(2, customEventState.getInt(StateKey.LIFECYCLE_WAKECOUNT))
    }

    @Test
    fun lastLaunchDate_isCorrect() {
        assertEquals(launchDateString, lifecycleEventState.getString(StateKey.LIFECYCLE_LASTLAUNCHDATE))
        assertEquals(secondLaunchDateString, customEventState.getString(StateKey.LIFECYCLE_LASTLAUNCHDATE))
    }

    @Test
    fun lastSleepDate_isCorrect() {
        assertEquals(sleepDateString, lifecycleEventState.getString(StateKey.LIFECYCLE_LASTSLEEPDATE))
        assertEquals(sleepDateString, customEventState.getString(StateKey.LIFECYCLE_LASTSLEEPDATE))
    }

    @Test
    fun lastWakeDate_isCorrect() {
        assertEquals(launchDateString, lifecycleEventState.getString(StateKey.LIFECYCLE_LASTWAKEDATE))
        assertEquals(secondLaunchDateString, customEventState.getString(StateKey.LIFECYCLE_LASTWAKEDATE))
    }
}