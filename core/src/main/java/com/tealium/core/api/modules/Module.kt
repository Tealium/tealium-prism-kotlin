package com.tealium.core.api.modules

import com.tealium.core.api.Tealium
import com.tealium.core.api.data.DataObject

/**
 * The [Module] is the basis for extending functionality of the [Tealium] instance.
 *
 * The [id] should match the [ModuleFactory.id] that created it.
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
     * Called whenever an updated set of settings has been made available for this [Module].
     *
     * Implementors should return `null` if the [Module] can no longer operate correctly with the
     * updated settings. Otherwise it should return itself.
     *
     * The default behavior will return `this` and it can be assumed that when this method is being
     * called, that the [Module] is still considered enabled.
     *
     * @param moduleSettings The latest set of [Module] Settings relevant to this module
     * @return `this` if the module should remain enabled; null if the module should be disabled
     */
    fun updateSettings(moduleSettings: DataObject): Module? {
        return this
    }

    /**
     * Called when this [Module] has been determined to be shutdown. This could happen for the following
     * reasons:
     *  - Updated settings have been received with `enabled` set to false
     *  - The [Module] itself has returned `null` from its `updateSettings` method, thereby implying
     *  that the [Module] cannot operate with the latest set of settings.
     *  - The [Tealium] instance that this [Module] is running in, has been shutdown.
     *
     * [Module]s should handle any required cleanup; disposing of any internal subscriptions or
     * unsubscribing from any services etc.
     */
    fun onShutdown() {}
}
