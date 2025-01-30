package com.tealium.lifecycle.internal

import com.tealium.core.api.data.DataObject
import com.tealium.lifecycle.LifecycleDataTarget
import com.tealium.lifecycle.LifecycleEvent

data class LifecycleSettings(
    val sessionTimeoutInMinutes: Int = DEFAULT_SESSION_TIMEOUT,
    val autoTrackingEnabled: Boolean = DEFAULT_AUTOTRACKING_ENABLED,
    val trackedLifecycleEvents: List<LifecycleEvent> = DEFAULT_TRACKED_EVENTS,
    val dataTarget: LifecycleDataTarget = DEFAULT_DATA_TARGET
) {

    companion object {
        const val MODULE_ID = "Lifecycle"
        const val DEFAULT_SESSION_TIMEOUT = 24 * 60
        const val DEFAULT_AUTOTRACKING_ENABLED = true
        val DEFAULT_TRACKED_EVENTS = LifecycleEvent.values().toList()
        val DEFAULT_DATA_TARGET = LifecycleDataTarget.LifecycleEventsOnly

        const val KEY_SESSION_TIMEOUT = "session_timeout"
        const val KEY_AUTOTRACKING_ENABLED = "autotracking_enabled"
        const val KEY_TRACKED_EVENTS = "tracked_lifecycle_events"
        const val KEY_DATA_TARGET = "data_target"

        fun fromDataObject(settings: DataObject): LifecycleSettings {
            val sessionTimeout = settings.getInt(KEY_SESSION_TIMEOUT)
                ?: DEFAULT_SESSION_TIMEOUT
            val autoTracking = settings.getBoolean(KEY_AUTOTRACKING_ENABLED)
                ?: DEFAULT_AUTOTRACKING_ENABLED
            val events = settings.getDataList(KEY_TRACKED_EVENTS)
                ?.mapNotNull(Converters.LifecycleEventConverter::convert)
                ?: DEFAULT_TRACKED_EVENTS
            val addData = settings.get(
                KEY_DATA_TARGET,
                Converters.LifecycleDataTargetConverter
            ) ?: DEFAULT_DATA_TARGET

            return LifecycleSettings(
                sessionTimeoutInMinutes = sessionTimeout,
                autoTrackingEnabled = autoTracking,
                trackedLifecycleEvents = events,
                dataTarget = addData,
            )
        }
    }
}
