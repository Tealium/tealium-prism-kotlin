package com.tealium.core.api.settings

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.Module
import com.tealium.core.TealiumConfig

/**
 * The [ModuleSettings] represent the current configuration for a given [Module]. They are calculated
 * from merging multiple settings inputs, in the following order of precedence.
 *  - Local settings file                               (lowest precedence)
 *  - Remote settings file (including cached)
 *  - Programmatic configuration from [TealiumConfig]   (highest precedence)
 *
 * Higher preference settings will only override the keys specified, leaving any present in lower
 * preference settings untouched.
 *
 * Any keys that are omitted in all files will have a reasonable default at the discretion of the
 * relevant [Module].
 */
interface ModuleSettings {

    /**
     * Whether or not this module is marked as enabled or not.
     */
    var enabled: Boolean

    /**
     * The [TealiumBundle] representing all the merged settings
     */
    val bundle: TealiumBundle

    /**
     * Any settings that cannot be represented in a [TealiumBundle], e.g. delegates/listeners etc
     */
    val dependencies: List<Any>
}
