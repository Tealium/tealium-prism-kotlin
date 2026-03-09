package com.tealium.prism.core.api.settings

import com.tealium.prism.core.api.data.DataObject
import com.tealium.prism.core.api.logger.LogLevel
import com.tealium.prism.core.api.misc.TimeFrame
import com.tealium.prism.core.api.misc.TimeFrameUtils.inSeconds
import com.tealium.prism.core.api.modules.DataLayer
import com.tealium.prism.core.internal.settings.CoreSettingsImpl

/**
 * Builder class for configuring core settings throughout the SDK.
 *
 * Settings configured here will override similar values that have come from other settings
 * sources - e.g. local or remote JSON settings - and will therefore not be overrideable
 * remotely.
 */
class CoreSettingsBuilder {
    private val builder = DataObject.Builder()

    /**
     * Sets the minimum [LogLevel] that a log entry need to be before it gets written to the
     * [android.util.Log].
     *
     * Default: [LogLevel.ERROR]
     *
     * @param logLevel The required minimum [LogLevel]
     */
    fun setLogLevel(logLevel: LogLevel) = apply {
        builder.put(CoreSettingsImpl.KEY_LOG_LEVEL, logLevel)
    }

    /**
     * Sets the maximum queue size to maintain when the SDK is unable to fully process events.
     * e.g. where connectivity may be unavailable for extended periods, events will be queued until
     * they can be processed safely.
     *
     * After the [queueSize] is reached, queued events will be evicted on an oldest-first basis.
     *
     * Default: 100
     *
     * @param queueSize The maximum number of events to hold the queue
     */
    fun setMaxQueueSize(queueSize: Int) = apply {
        builder.put(CoreSettingsImpl.KEY_MAX_QUEUE_SIZE, queueSize)
    }

    /**
     * Sets the maximum amount of time that a queued event remains valid for.
     *
     * After an event has been queued for longer than the given [expiration], then it will be removed
     * from the queue and no further attempts to process it will be made.
     *
     * Default: 1 day
     *
     * @param expiration The maximum amount of time that an event can remain valid for
     */
    fun setExpiration(expiration: TimeFrame) = apply {
        builder.put(CoreSettingsImpl.KEY_EXPIRATION, expiration.inSeconds())
    }

    /**
     * Sets the minimum amount of time before remote SDK settings should be refreshed.
     *
     * Default: 15 minutes
     *
     * @param refreshInterval the minimum amount of time required to pass before fetching updated settings
     */
    fun setRefreshInterval(refreshInterval: TimeFrame) = apply {
        builder.put(CoreSettingsImpl.KEY_REFRESH_INTERVAL, refreshInterval.inSeconds())
    }

    /**
     * Sets the visitor identity key to monitor from the [DataLayer]. Changes to the value found at
     * this [key] signify that the visitor has changed and will trigger visitor id switching.
     *
     * There is no default for this value
     *
     * @param key the [key] to monitor for changes in the [DataLayer]
     */
    fun setVisitorIdentityKey(key: String) = apply {
        builder.put(CoreSettingsImpl.KEY_VISITOR_IDENTITY_KEY, key)
    }

    /**
     * Sets the session length. Sessions are extended upon each tracked event, so this [sessionTimeout]
     * is the time of inactivity that needs to pass before a session is considered ended.
     *
     * This value will be coerced to be between 5 seconds and 30 minutes.
     *
     * Default: 5 minutes
     *
     * @param sessionTimeout The length of time of inactivity before a session is ended.
     */
    fun setSessionTimeout(sessionTimeout: TimeFrame) = apply {
        builder.put(CoreSettingsImpl.KEY_SESSION_TIMEOUT, sessionTimeout.inSeconds())
    }

    fun build(): DataObject {
        return builder.build()
    }
}