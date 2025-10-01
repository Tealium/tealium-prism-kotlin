package com.tealium.prism.lifecycle.internal

import com.tealium.prism.core.api.persistence.DataStore
import com.tealium.prism.core.api.persistence.Expiry
import com.tealium.prism.lifecycle.LifecycleEvent

interface LifecycleStorage {
    val currentAppVersion: String?
    val priorSecondsAwake: Long
    val timestampUpdate: Long?
    val timestampFirstLaunch: Long?
    val timestampLastLaunch: Long?
    val timestampLastSleep: Long?
    val timestampLastWake: Long?
    val totalSecondsAwake: Long
    val countLaunch: Int
    val countSleep: Int
    val countWake: Int
    val countTotalLaunch: Int
    val countTotalSleep: Int
    val countTotalWake: Int
    val countTotalCrash: Int
    val lastLifecycleEvent: LifecycleEvent?

    fun registerLaunch(timestamp: Long)
    fun registerWake(timestamp: Long)
    fun registerSleep(timestamp: Long, secondsAwake: Int)

    fun setFirstLaunchTimestamp(timestamp: Long)
    fun getLastEventTimestamp(event: String): Long?
    fun setCurrentAppVersion(newVersion: String)
    fun resetCountsAfterAppUpdate(timestamp: Long, newVersion: String)
    fun resetPriorSecondsAwake()
    fun incrementCrash()
}

class LifecycleStorageImpl(private val dataStore: DataStore) : LifecycleStorage {
    override val currentAppVersion: String?
        get() = dataStore.getString(LifecycleStorageKey.APP_VERSION)

    override val priorSecondsAwake: Long
        get() = dataStore.getLong(LifecycleStorageKey.PRIOR_SECONDS_AWAKE) ?: 0

    override val timestampUpdate: Long?
        get() = dataStore.getLong(LifecycleStorageKey.TIMESTAMP_UPDATE)

    override val timestampFirstLaunch: Long?
        get() = dataStore.getLong(LifecycleStorageKey.TIMESTAMP_FIRST_LAUNCH)

    override val timestampLastLaunch: Long?
        get() = dataStore.getLong(LifecycleStorageKey.TIMESTAMP_LAST_LAUNCH)

    override val timestampLastSleep: Long?
        get() = dataStore.getLong(LifecycleStorageKey.TIMESTAMP_LAST_SLEEP)

    override val timestampLastWake: Long?
        get() = dataStore.getLong(LifecycleStorageKey.TIMESTAMP_LAST_WAKE)

    override val totalSecondsAwake: Long
        get() = dataStore.getLong(LifecycleStorageKey.TOTAL_SECONDS_AWAKE) ?: 0

    override val countLaunch: Int
        get() = dataStore.getInt(LifecycleStorageKey.COUNT_LAUNCH) ?: 0

    override val countSleep: Int
        get() = dataStore.getInt(LifecycleStorageKey.COUNT_SLEEP) ?: 0

    override val countWake: Int
        get() = dataStore.getInt(LifecycleStorageKey.COUNT_WAKE) ?: 0

    override val countTotalLaunch: Int
        get() = dataStore.getInt(LifecycleStorageKey.COUNT_TOTAL_LAUNCH) ?: 0

    override val countTotalSleep: Int
        get() = dataStore.getInt(LifecycleStorageKey.COUNT_TOTAL_SLEEP) ?: 0

    override val countTotalWake: Int
        get() = dataStore.getInt(LifecycleStorageKey.COUNT_TOTAL_WAKE) ?: 0

    override val countTotalCrash: Int
        get() = dataStore.getInt(LifecycleStorageKey.COUNT_TOTAL_CRASH) ?: 0

    override val lastLifecycleEvent: LifecycleEvent?
        get() = dataStore.get(LifecycleStorageKey.LAST_EVENT, Converters.LifecycleEventConverter)

    override fun registerLaunch(timestamp: Long) {
        dataStore.edit()
            .setLastWake(timestamp)
            .setLastLifecycleEvent(LifecycleEvent.Launch)
            .incrementLaunch()
            .incrementWake()
            .setLastLaunch(timestamp)
            .commit()
    }

    override fun registerWake(timestamp: Long) {
        dataStore.edit()
            .setLastWake(timestamp)
            .setLastLifecycleEvent(LifecycleEvent.Wake)
            .incrementWake()
            .commit()
    }

    override fun registerSleep(timestamp: Long, secondsAwake: Int) {
        dataStore.edit()
            .setLastLifecycleEvent(LifecycleEvent.Sleep)
            .incrementSleep()
            .updateSecondsAwake(secondsAwake)
            .setLastSleep(timestamp)
            .commit()
    }

    override fun setFirstLaunchTimestamp(timestamp: Long) {
        dataStore.edit()
            .setFirstLaunchTimestamp(timestamp)
            .commit()
    }

    override fun setCurrentAppVersion(newVersion: String) {
        dataStore.edit()
            .setCurrentAppVersion(newVersion)
            .commit()
    }

    override fun resetCountsAfterAppUpdate(timestamp: Long, newVersion: String) {
        dataStore.edit()
            .setCurrentAppVersion(newVersion)
            .setTimestampUpdate(timestamp)
            .resetCounts()
            .commit()
    }

    override fun resetPriorSecondsAwake() {
        dataStore.edit()
            .resetPriorSecondsAwake()
            .commit()
    }

    override fun incrementCrash() {
        dataStore.edit()
            .incrementCrash()
            .commit()
    }

    override fun getLastEventTimestamp(event: String): Long? {
        return dataStore.getLong(event)
    }

    private fun DataStore.Editor.incrementLaunch() = apply {
        put(LifecycleStorageKey.COUNT_LAUNCH, countLaunch.inc(), Expiry.FOREVER)
        put(LifecycleStorageKey.COUNT_TOTAL_LAUNCH, countTotalLaunch.inc(), Expiry.FOREVER)
    }

    private fun DataStore.Editor.incrementWake() = apply {
        put(LifecycleStorageKey.COUNT_WAKE, countWake.inc(), Expiry.FOREVER)
        put(LifecycleStorageKey.COUNT_TOTAL_WAKE, countTotalWake.inc(), Expiry.FOREVER)
    }

    private fun DataStore.Editor.incrementSleep() = apply {
        put(LifecycleStorageKey.COUNT_SLEEP, countSleep.inc(), Expiry.FOREVER)
        put(LifecycleStorageKey.COUNT_TOTAL_SLEEP, countTotalSleep.inc(), Expiry.FOREVER)
    }

    private fun DataStore.Editor.incrementCrash() = apply {
        put(LifecycleStorageKey.COUNT_TOTAL_CRASH, countTotalCrash.inc(), Expiry.FOREVER)
    }

    private fun DataStore.Editor.setFirstLaunchTimestamp(timestamp: Long) = apply {
        put(LifecycleStorageKey.TIMESTAMP_FIRST_LAUNCH, timestamp, Expiry.FOREVER)
    }

    private fun DataStore.Editor.setLastLaunch(timestamp: Long) = apply {
        put(LifecycleStorageKey.TIMESTAMP_LAST_LAUNCH, timestamp, Expiry.FOREVER)
    }

    private fun DataStore.Editor.setLastSleep(timestamp: Long) = apply {
        put(LifecycleStorageKey.TIMESTAMP_LAST_SLEEP, timestamp, Expiry.FOREVER)
    }

    private fun DataStore.Editor.setLastWake(timestamp: Long) = apply {
        put(LifecycleStorageKey.TIMESTAMP_LAST_WAKE, timestamp, Expiry.FOREVER)
    }

    private fun DataStore.Editor.setLastLifecycleEvent(event: LifecycleEvent) = apply {
        put(LifecycleStorageKey.LAST_EVENT, event, Expiry.FOREVER)
    }

    private fun DataStore.Editor.updateSecondsAwake(seconds: Int) = apply {
        put(LifecycleStorageKey.TOTAL_SECONDS_AWAKE, totalSecondsAwake.plus(seconds), Expiry.FOREVER)
        put(LifecycleStorageKey.PRIOR_SECONDS_AWAKE, priorSecondsAwake.plus(seconds), Expiry.FOREVER)
    }

    private fun DataStore.Editor.resetPriorSecondsAwake() = apply {
        remove(LifecycleStorageKey.PRIOR_SECONDS_AWAKE)
    }

    private fun DataStore.Editor.setCurrentAppVersion(version: String) = apply {
        put(LifecycleStorageKey.APP_VERSION, version, Expiry.FOREVER)
    }

    private fun DataStore.Editor.setTimestampUpdate(timestamp: Long) = apply {
        put(LifecycleStorageKey.TIMESTAMP_UPDATE, timestamp, Expiry.FOREVER)
    }

    private fun DataStore.Editor.resetCounts() = apply {
        remove(LifecycleStorageKey.COUNT_LAUNCH)
        remove(LifecycleStorageKey.COUNT_WAKE)
        remove(LifecycleStorageKey.COUNT_SLEEP)
    }
}