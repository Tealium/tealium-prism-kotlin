package com.tealium.core.api.settings

import com.tealium.core.api.barriers.ScopedBarrier
import com.tealium.core.api.logger.LogLevel
import com.tealium.core.api.misc.TimeFrame
import com.tealium.core.api.modules.Dispatcher

/**
 * Describes all the available configurable settings that control behavior of core SDK functionality.
 *
 * All settings available on this object are able to be set from remote, local and programmatic sources.
 */
interface CoreSettings {

    /**
     * The minimum [LogLevel] for Tealium log messages.
     */
    val logLevel: LogLevel

    /**
     * An identifier to identify this unique data source.
     */
    val dataSource: String?

    /**
     * The number of events to send at a time
     */
    val batchSize: Int

    /**
     * How many events can be queued at any given time. Events will be removed on an oldest-first basis
     * when the limit is reached.
     *
     * Negative values will indicate an infinite queue length.
     */
    val maxQueueSize: Int

    /**
     * How long an event is considered valid for.
     *
     * If events are not able to be processed by all registered [Dispatcher]s, then events will remain
     * persisted until either this expiration time has elapsed, or they are eventually successfully processed
     * by all registered [Dispatcher]s.
     */
    val expiration: TimeFrame

    /**
     * Whether or not events should be sent when the device has low battery
     */
    val batterySaver: Boolean

    /**
     * Whether or not events should only be sent on WiFi connections
     */
    val wifiOnly: Boolean

    /**
     * How regularly the Sdk should check for updated Sdk settings
     */
    val refreshInterval: TimeFrame

    /**
     * Whether or not Deep Link Tracking is enabled.
     */
    val deepLinkTrackingEnabled: Boolean

    /**
     * Whether the entire Sdk is disabled or not
     */
    val disableLibrary: Boolean

    /**
     * The key to look for in the data layer when identifying a user.
     *
     * This setting is used to automatically control when the Tealium Visitor Id is updated.
     */
    val visitorIdentityKey: String?

    /**
     * Scopes the given barriers to the given extension points.
     *
     * @see ScopedBarrier
     */
    val barriers: Set<ScopedBarrier>
}