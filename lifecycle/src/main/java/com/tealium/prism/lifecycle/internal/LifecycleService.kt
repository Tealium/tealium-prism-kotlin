package com.tealium.prism.lifecycle.internal

import android.content.Context
import android.content.pm.PackageInfo
import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.modules.TealiumContext
import com.tealium.prism.lifecycle.LifecycleEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * The [LifecycleService] manages lifecycle events and provides methods for registering
 * launch, wake, and sleep events, as well as retrieving the current lifecycle state.
 */
interface LifecycleService {
    val lastLifecycleEvent: LifecycleEvent?

    /**
     * Registers a launch event.
     *
     * @param timestamp The timestamp of the launch event.
     * @return A DataObject containing information of the launch event.
     */
    fun registerLaunch(timestamp: Long): DataObject

    /**
     * Registers a wake event.
     *
     * @param timestamp The timestamp of the wake event.
     * @return A DataObject containing information of the wake event.
     */
    fun registerWake(timestamp: Long): DataObject

    /**
     * Registers a sleep event.
     *
     * @param timestamp The timestamp of the sleep event.
     * @return A DataObject containing information of the sleep event.
     */
    fun registerSleep(timestamp: Long): DataObject

    /**
     * Retrieves current lifecycle state.
     *
     * @param timestamp The current timestamp.
     * @return A DataObject containing information of the current lifecycle state.
     */
    fun getCurrentState(timestamp: Long, lifecycleEvent: LifecycleEvent? = null): DataObject
}

/**
 * Implementation of the Lifecycle Service. This class provides the logic for managing
 * lifecycle events and storing lifecycle data.
 *
 */
class LifecycleServiceImpl(
    private val context: Context,
    private val lifecycleStorage: LifecycleStorage
) : LifecycleService {

    constructor(tealiumContext: TealiumContext, lifecycleStorage: LifecycleStorage) : this(
        tealiumContext.context,
        lifecycleStorage
    )

    internal var updateLaunchDate: String? = null
    internal var firstLaunchString: String? = null
    internal var firstLaunchMmDdYyyy: String? = null
    internal var lastLaunchString: String? = null
    internal var lastSleepString: String? = null
    internal var lastWakeString: String? = null

    private val calendar: Calendar = Calendar.getInstance()
    override val lastLifecycleEvent: LifecycleEvent?
        get() = lifecycleStorage.lastLifecycleEvent

    override fun registerLaunch(timestamp: Long): DataObject {
        val isFirstLaunch = lifecycleStorage.timestampFirstLaunch == null
        if (isFirstLaunch) {
            lifecycleStorage.setFirstLaunchTimestamp(timestamp)
        }
        val currentVersion = getPackageContext().versionName?.toString() ?: ""
        val didUpdate = updateAppVersion(timestamp, currentVersion)

        val lastWake = lifecycleStorage.timestampLastWake

        val state = DataObject.create {
            addWakeState(lastWake, timestamp)

            if (didDetectCrash()) {
                put(StateKey.LIFECYCLE_DIDDETECT_CRASH, true)
                lifecycleStorage.incrementCrash()
            }

            val daysSinceLastWake =
                daysSince(startEventMs = lifecycleStorage.timestampLastWake, endEventMs = timestamp)

            val previousLaunchString =
                lastLaunchString ?: setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_LAUNCH)

            val previousWakeString =
                lastWakeString ?: setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_WAKE)

            lifecycleStorage.registerLaunch(timestamp)
            lastLaunchString = formatTimestamp(timestamp)
            lastWakeString = formatTimestamp(timestamp)

            put(StateKey.LIFECYCLE_TYPE, LifecycleEvent.Launch)
            put(StateKey.LIFECYCLE_PRIORSECONDSAWAKE, lifecycleStorage.priorSecondsAwake)
            lifecycleStorage.resetPriorSecondsAwake()

            if (isFirstLaunch) {
                put(StateKey.LIFECYCLE_ISFIRSTLAUNCH, true)
            }

            if (didUpdate) {
                put(StateKey.LIFECYCLE_ISFIRSTLAUNCHUPDATE, true)
            }

            putAll(getCurrentState(timestamp, LifecycleEvent.Launch))

            if (daysSinceLastWake != null) {
                put(StateKey.LIFECYCLE_DAYSSINCELASTWAKE, daysSinceLastWake)
            } else {
                remove(StateKey.LIFECYCLE_DAYSSINCELASTWAKE)
            }

            if (previousLaunchString != null) {
                put(StateKey.LIFECYCLE_LASTLAUNCHDATE, previousLaunchString)
            } else {
                remove(StateKey.LIFECYCLE_LASTLAUNCHDATE)
            }

            if (previousWakeString != null) {
                put(StateKey.LIFECYCLE_LASTWAKEDATE, previousWakeString)
            } else {
                remove(StateKey.LIFECYCLE_LASTWAKEDATE)
            }
        }

        return state
    }

    override fun registerWake(timestamp: Long): DataObject {
        val lastWake = lifecycleStorage.timestampLastWake
        val state = DataObject.create {
            addWakeState(lastWake, timestamp)

            val daysSinceLastWake =
                daysSince(startEventMs = lifecycleStorage.timestampLastWake, endEventMs = timestamp)

            val previousWakeString =
                lastWakeString ?: setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_WAKE)

            lifecycleStorage.registerWake(timestamp)
            lastWakeString = formatTimestamp(timestamp)
            put(StateKey.LIFECYCLE_TYPE, LifecycleEvent.Wake)
            putAll(getCurrentState(timestamp, LifecycleEvent.Wake))

            if (daysSinceLastWake != null) {
                put(StateKey.LIFECYCLE_DAYSSINCELASTWAKE, daysSinceLastWake)
            } else {
                remove(StateKey.LIFECYCLE_DAYSSINCELASTWAKE)
            }

            if (previousWakeString != null) {
                put(StateKey.LIFECYCLE_LASTWAKEDATE, previousWakeString)
            } else {
                remove(StateKey.LIFECYCLE_LASTWAKEDATE)
            }
        }

        return state
    }

    override fun registerSleep(timestamp: Long): DataObject {
        val foregroundStart: Long = lifecycleStorage.timestampLastWake ?: timestamp
        val secondsAwakeDelta: Int = ((timestamp - foregroundStart) / 1000).toInt()

        val previousSleepString =
            lastSleepString ?: setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_SLEEP)

        lifecycleStorage.registerSleep(timestamp, secondsAwakeDelta)
        lastSleepString = formatTimestamp(timestamp)

        val state = DataObject.create {
            put(StateKey.LIFECYCLE_TYPE, LifecycleEvent.Sleep)
            putAll(getCurrentState(timestamp, LifecycleEvent.Sleep))

            if (previousSleepString != null) {
                put(StateKey.LIFECYCLE_LASTSLEEPDATE, previousSleepString)
            } else {
                remove(StateKey.LIFECYCLE_LASTSLEEPDATE)
            }
        }

        return state
    }

    override fun getCurrentState(timestamp: Long, lifecycleEvent: LifecycleEvent?): DataObject {
        val dataObject = DataObject.create {
            updateDaysSince(timestamp, this)

            put(StateKey.LIFECYCLE_DAYOFWEEK_LOCAL, getDayOfWeekLocal(timestamp))
            put(StateKey.LIFECYCLE_HOUROFDAY_LOCAL, getHourOfDayLocal(timestamp))
            put(StateKey.LIFECYCLE_LAUNCHCOUNT, lifecycleStorage.countLaunch)
            put(StateKey.LIFECYCLE_SLEEPCOUNT, lifecycleStorage.countSleep)
            put(StateKey.LIFECYCLE_WAKECOUNT, lifecycleStorage.countWake)
            put(StateKey.LIFECYCLE_TOTALCRASHCOUNT, lifecycleStorage.countTotalCrash)
            put(StateKey.LIFECYCLE_TOTALLAUNCHCOUNT, lifecycleStorage.countTotalLaunch)
            put(StateKey.LIFECYCLE_TOTALSLEEPCOUNT, lifecycleStorage.countTotalSleep)
            put(StateKey.LIFECYCLE_TOTALWAKECOUNT, lifecycleStorage.countTotalWake)

            val secondsAwakeDelta = calculateSecondsAwakeDelta(timestamp, lifecycleEvent)
            if (lifecycleEvent != LifecycleEvent.Launch) {
                put(StateKey.LIFECYCLE_SECONDSAWAKE, lifecycleStorage.priorSecondsAwake + secondsAwakeDelta)
            }

            put(
                StateKey.LIFECYCLE_TOTALSECONDSAWAKE,
                lifecycleStorage.totalSecondsAwake + secondsAwakeDelta
            )

            val firstLaunchString = firstLaunchString ?: setFormattedFirstLaunch(timestamp)
            firstLaunchString?.let {
                put(StateKey.LIFECYCLE_FIRSTLAUNCHDATE, it)
            }

            val firstLaunchMmDdYyyyString = firstLaunchMmDdYyyy ?: setFirstLaunchMmDdYyyy(timestamp)
            firstLaunchMmDdYyyyString?.let {
                put(StateKey.LIFECYCLE_FIRSTLAUNCHDATE_MMDDYYYY, it)
            }

            val lastLaunch =
                lastLaunchString ?: setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_LAUNCH)
            lastLaunch?.let {
                put(StateKey.LIFECYCLE_LASTLAUNCHDATE, lastLaunch)
            }

            val lastWake =
                lastWakeString ?: setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_WAKE)
            lastWake?.let {
                put(StateKey.LIFECYCLE_LASTWAKEDATE, lastWake)
            }

            val lastSleep =
                lastSleepString ?: setFormattedEvent(LifecycleStorageKey.TIMESTAMP_LAST_SLEEP)
            lastSleep?.let {
                put(StateKey.LIFECYCLE_LASTSLEEPDATE, lastSleep)
            }

            if (lifecycleStorage.timestampUpdate != null) {
                val lastUpdate = updateLaunchDate ?: setUpdateLaunchDate()
                if (lastUpdate != null) {
                    put(StateKey.LIFECYCLE_UPDATELAUNCHDATE, lastUpdate)
                }
            }
        }

        return dataObject
    }

    private fun updateDaysSince(timestamp: Long, dataObject: DataObject.Builder) {
        daysSince(
            startEventMs = lifecycleStorage.timestampFirstLaunch,
            endEventMs = timestamp
        )?.also { daysSinceFirstLaunch ->
            dataObject.put(
                StateKey.LIFECYCLE_DAYSSINCEFIRSTLAUNCH,
                daysSinceFirstLaunch
            )
        }

        daysSince(
            startEventMs = lifecycleStorage.timestampLastWake,
            endEventMs = timestamp
        )?.also { daysSinceLastWake ->
            dataObject.put(
                StateKey.LIFECYCLE_DAYSSINCELASTWAKE,
                daysSinceLastWake
            )
        }

        daysSince(
            startEventMs = lifecycleStorage.timestampUpdate,
            endEventMs = timestamp
        )?.also { daysSinceUpdate ->
            dataObject.put(
                StateKey.LIFECYCLE_DAYSSINCEUPDATE,
                daysSinceUpdate
            )
        }
    }

    internal fun calculateSecondsAwakeDelta(timestamp: Long, lifecycleEvent: LifecycleEvent?): Int {
        if (lifecycleEvent != null || lastLifecycleEvent == LifecycleEvent.Sleep) {
            return 0
        }

        return ((timestamp - (lifecycleStorage.timestampLastWake ?: timestamp)) / 1000).toInt()
    }

    private fun getPackageContext(): PackageInfo {
        val packageName = context.packageName
        return context.packageManager.getPackageInfo(packageName, 0)
    }

    private fun DataObject.Builder.addWakeState(
        lastWake: Long?,
        timestamp: Long
    ) {
        if (lastWake == null) {
            put(StateKey.LIFECYCLE_ISFIRSTWAKEMONTH, true)
            put(StateKey.LIFECYCLE_ISFIRSTWAKETODAY, true)
        } else {
            val isFirstWakeResult = FirstWakeType.fromTimestamps(lastWake, timestamp)
            if (isFirstWakeResult.isFirstWakeMonth) {
                put(StateKey.LIFECYCLE_ISFIRSTWAKEMONTH, true)
                put(StateKey.LIFECYCLE_ISFIRSTWAKETODAY, true)
            } else if (isFirstWakeResult.isFirstWakeToday) {
                put(StateKey.LIFECYCLE_ISFIRSTWAKETODAY, true)
            }
        }
    }

    internal fun didDetectCrash(): Boolean {
        val lastEvent = lifecycleStorage.lastLifecycleEvent ?: return false

        return LifecycleEvent.Launch == lastEvent || LifecycleEvent.Wake == lastEvent
    }

    internal fun updateAppVersion(timestamp: Long, initializedCurrentVersion: String): Boolean {
        val cachedVersion = lifecycleStorage.currentAppVersion

        if (cachedVersion == null) {
            lifecycleStorage.setCurrentAppVersion(initializedCurrentVersion)
        } else if (initializedCurrentVersion != cachedVersion) {
            lifecycleStorage.resetCountsAfterAppUpdate(timestamp, initializedCurrentVersion)
            return true
        }
        return false
    }

    private fun getDayOfWeekLocal(currentTime: Long): Int {
        if (calendar.timeInMillis != currentTime) {
            calendar.timeInMillis = currentTime
        }

        return calendar.get(Calendar.DAY_OF_WEEK)
    }

    private fun getHourOfDayLocal(currentTime: Long): Int {
        if (calendar.timeInMillis != currentTime) {
            calendar.timeInMillis = currentTime
        }

        return calendar.get(Calendar.HOUR_OF_DAY)
    }

    internal fun setFormattedFirstLaunch(fallbackTimestamp: Long = System.currentTimeMillis()): String? {
        firstLaunchString = formatTimestamp(
            lifecycleStorage.timestampFirstLaunch ?: fallbackTimestamp
        )

        return firstLaunchString
    }

    internal fun setFormattedEvent(eventKey: String): String? {
        val timestamp = lifecycleStorage.getLastEventTimestamp(eventKey)
        val formattedTimestamp = timestamp?.let { formatTimestamp(it) }

        when (eventKey) {
            LifecycleStorageKey.TIMESTAMP_LAST_LAUNCH -> lastLaunchString = formattedTimestamp
            LifecycleStorageKey.TIMESTAMP_LAST_WAKE -> lastWakeString = formattedTimestamp
            LifecycleStorageKey.TIMESTAMP_LAST_SLEEP -> lastSleepString = formattedTimestamp
        }

        return formattedTimestamp
    }

    internal fun setUpdateLaunchDate(): String? {
        updateLaunchDate = lifecycleStorage.timestampUpdate?.let {
            formatTimestamp(it)
        }

        return updateLaunchDate
    }

    internal fun setFirstLaunchMmDdYyyy(fallbackTimestamp: Long = System.currentTimeMillis()): String? {
        val formatMmDdYyyy = SimpleDateFormat("MM/dd/yyy", Locale.ROOT)
        formatMmDdYyyy.timeZone = TimeZone.getTimeZone("UTC")
        val date = Date(lifecycleStorage.timestampFirstLaunch ?: fallbackTimestamp)
        firstLaunchMmDdYyyy = formatMmDdYyyy.format(date)

        return firstLaunchMmDdYyyy
    }

    companion object {
        private val formatIso8601 = SimpleDateFormat(LifecycleDefault.FORMAT_ISO_8601, Locale.ROOT)

        init {
            formatIso8601.timeZone = TimeZone.getTimeZone("UTC")
        }

        fun daysSince(startEventMs: Long?, endEventMs: Long): Long? {
            val daysInMs = TimeUnit.DAYS.toMillis(1)
            return if (startEventMs != null && startEventMs >= 0 && endEventMs >= startEventMs) {
                val deltaMs = endEventMs - startEventMs
                deltaMs / daysInMs
            } else {
                return null
            }
        }

        fun formatTimestamp(timestamp: Long): String {
            return formatIso8601.format(Date(timestamp))
        }
    }
}