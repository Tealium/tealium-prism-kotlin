package com.tealium.core.api.modules

import com.tealium.core.api.Tealium
import com.tealium.core.api.data.DataObject

/**
 * A [ModuleFactory] is responsible for creating [Module] implementations. This will occur during
 * initialization of the [Tealium] instance, but also when new remote settings have been retrieved,
 * if in use, and the module has since been enabled.
 *
 * The [id] value should match its respective [Module.id]
 *
 * @see Module
 */
interface ModuleFactory {

    /**
     * The unique name identifying this [ModuleFactory]
     */
    val id: String

    /**
     * Specifies whether or not the [Module] produced by this [ModuleFactory] can be disabled by settings
     * from local and remote sources.
     *
     * The default is `true`, therefore enabling remote enabling/disabling of [Module]s by default
     * and it is only recommended for this be set to `false` for core modules in the SDK that are
     * required for correct operation.
     *
     * @return true if the module can be disabled by settings updates, otherwise false
     */
    fun canBeDisabled(): Boolean = true

    /**
     * Returns any pre-configured settings for this module that will be enforced throughout the
     * lifetime of the Tealium instance that it is loaded in.
     *
     * Settings provided by this method will be merged into existing settings from local and remote
     * sources, however these will take precedence.
     *
     */
    fun getEnforcedSettings(): DataObject? = null

    /**
     * Called when a new instance of the [Module] is required to be created.
     *
     * @param context The [TealiumContext] containing access to many shared dependencies that may
     * be required by the [Module] implementation being created.
     * @param configuration The current configuration for this module; this could be from a local,
     * cached or even remote source.
     * @return The new [Module] instance; or null if the Module is either disabled, missing required
     * properties in the [configuration] object, or missing required dependencies.
     */
    fun create(context: TealiumContext, configuration: DataObject): Module?
}
