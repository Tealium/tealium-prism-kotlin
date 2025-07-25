package com.tealium.core.api.settings

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
     * How regularly the Sdk should check for updated Sdk settings
     */
    val refreshInterval: TimeFrame

    /**
     * The key to look for in the data layer when identifying a user.
     *
     * This setting is used to automatically control when the Tealium Visitor Id is updated.
     */
    val visitorIdentityKey: String?
}