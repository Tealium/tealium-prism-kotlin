package com.tealium.core.api

import com.tealium.core.api.settings.ModuleSettings
import com.tealium.core.Tealium

/**
 * The [Module] is the basis for extending functionality of the [Tealium] instance.
 *
 * The [name] should match the [ModuleFactory.name] that created it.
 */
interface Module {
    /**
     * The unique name identifying this [Module]
     */
    val name: String

    /**
     * A string describing the version of this [Module].
     */
    val version: String

    /**
     * Called whenever an updated set of [ModuleSettings] has been made available for this [Module]
     *
     * @param moduleSettings The latest set of [ModuleSettings] relevant to this module
     * @return `this` if the module should remain enabled; null if the module should be disabled
     */
    fun updateSettings(moduleSettings: ModuleSettings) : Module? {
        return this
    }
}
