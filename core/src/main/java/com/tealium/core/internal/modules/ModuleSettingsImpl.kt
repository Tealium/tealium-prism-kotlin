package com.tealium.core.internal.modules

import com.tealium.core.api.ModuleSettings

class ModuleSettingsImpl(
    override val enabled: Boolean = true,
    override val settings: Map<String, Any> = emptyMap()
) : ModuleSettings