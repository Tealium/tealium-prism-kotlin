package com.tealium.prism.core.api.modules

import com.tealium.prism.core.api.Tealium
import com.tealium.prism.core.api.data.DataObject

/**
 * The [Module] is the basis for extending functionality of the [Tealium] instance.
 *
 * The [id] should be unique for all modules.
 */
interface Module {
    /**
     * The unique id identifying this [Module]
     */
    val id: String

    /**
     * A string describing the version of this [Module].
     */
    val version: String

    /**
     * Called whenever updated configuration has been made available for this specific [Module].
     *
     * Return `this` to keep this instance with the new configuration. Return `null` if the
     * configuration is invalid, or if the [Module] can no longer operate correctly with it, in
     * which case the [Module] is disabled and shut down. Returning any other instance is not
     * supported.
     *
     * It can be assumed that when this method is being called, the [Module] is still considered
     * enabled.
     *
     * @param configuration The latest configuration specifically relevant to this [Module].
     * @return `this`, or `null` to disable the [Module]
     */
    fun updateConfiguration(configuration: DataObject): Module? {
        return this
    }

    /**
     * Called when this [Module] has been determined to be shutdown. This could happen for the following
     * reasons:
     *  - Updated settings have been received with `enabled` set to false
     *  - The [Module] itself has returned `null` from its [updateConfiguration] method, thereby implying
     *  that the [Module] cannot operate with the latest configuration.
     *  - Updated settings no longer contain an entry for this [Module] at all.
     *  - The [Tealium] instance that this [Module] is running in, has been shutdown.
     *
     * [Module]s should handle any required cleanup; disposing of any internal subscriptions or
     * unsubscribing from any services etc.
     */
    fun onShutdown() {}
}
