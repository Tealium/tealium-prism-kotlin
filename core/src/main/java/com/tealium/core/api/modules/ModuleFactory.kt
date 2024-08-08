package com.tealium.core.api.modules

import com.tealium.core.api.Tealium
import com.tealium.core.api.settings.ModuleSettings

/**
 * A [ModuleFactory] is responsible for creating [Module] implementations. This will occur during
 * initialization of the [Tealium] instance, but also when new remote settings have been retrieved,
 * if in use, and the module has since been enabled.
 *
 * The [name] value should match its respective [Module.name]
 *
 * @see Module
 */
interface ModuleFactory {

    /**
     * The unique name identifying this [ModuleFactory]
     */
    val name: String

    /**
     * Called when a new instance of the [Module] is required to be created.
     *
     * @param context The [TealiumContext] containing access to many shared dependencies that may
     * be required by the [Module] implementation being created.
     * @param settings The current set of [ModuleSettings] for this module; this could be from a local,
     * cached or even remote source
     * @return The new [Module] instance; or null if the Module is either disabled as configured in
     * the settings, or missing required dependencies.
     */
    fun create(context: TealiumContext, settings: ModuleSettings): Module?
}

