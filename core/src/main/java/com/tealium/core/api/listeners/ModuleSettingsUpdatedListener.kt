package com.tealium.core.api.listeners

import com.tealium.core.api.CoreSettings
import com.tealium.core.api.ModuleSettings

interface ModuleSettingsUpdatedListener : Listener {
    fun onModuleSettingsUpdated(coreSettings: CoreSettings, moduleSettings: ModuleSettings)
}