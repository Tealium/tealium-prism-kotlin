package com.tealium.lifecycle

import com.tealium.core.api.data.DataList
import com.tealium.core.api.settings.CollectorSettingsBuilder
import com.tealium.lifecycle.internal.LifecycleConfiguration

/**
 * Enables the configuration, at runtime, of Lifecycle Settings.
 *
 * Note. Any configuration set here will override any configuration provided by the local or remote
 * settings files and will no longer be overridable remotely.
 */
class LifecycleSettingsBuilder : CollectorSettingsBuilder<LifecycleSettingsBuilder>() {

    /**
     * Sets Lifecycle session timeout in minutes. Set to -1 for an infinite session duration.
     * Any value less than or equal to -1 will also be treated as an infinite session.
     *
     * The default session timeout is 24 * 60 (1 day)
     */
    fun setSessionTimeoutInMinutes(timeout: Int) = apply {
        configuration.put(LifecycleConfiguration.KEY_SESSION_TIMEOUT, timeout)
    }

    /**
     * Sets optional automatic tracking of lifecycle events. If disabled, lifecycles calls must be
     * made manually.
     *
     * Lifecycles autotracking is set to true by default.
     */
    fun setAutoTrackingEnabled(enabled: Boolean) = apply {
        configuration.put(LifecycleConfiguration.KEY_AUTOTRACKING_ENABLED, enabled)
    }

    /**
     * Sets specific lifecycle events to be sent by the Lifecycle Module.
     *
     * All events will be sent by default.
     */
    fun setTrackedLifecycleEvents(events: List<LifecycleEvent>) = apply {
        configuration.put(LifecycleConfiguration.KEY_TRACKED_EVENTS, DataList.fromCollection(events))
    }

    /**
     * Sets the target events for which to add lifecycle data to.
     *
     * [LifecycleDataTarget.LifecycleEventsOnly] is the default, and will only send lifecycle data
     * on lifecycle events.
     */
    fun setDataTarget(target: LifecycleDataTarget) = apply {
        configuration.put(LifecycleConfiguration.KEY_DATA_TARGET, target)
    }
}