package com.tealium.core.internal.settings

import com.tealium.core.api.data.TealiumBundle
import com.tealium.core.api.settings.ModuleSettings

/**
 * Default implementation of [ModuleSettings]
 */
class ModuleSettingsImpl(
    override var enabled: Boolean = true,
    override val bundle: TealiumBundle = TealiumBundle.EMPTY_BUNDLE,
    override val dependencies: List<Any> = emptyList()
) : ModuleSettings

