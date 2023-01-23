package com.tealium.core.api.listeners

import com.tealium.core.api.CoreSettings
import com.tealium.core.api.ModuleSettings

interface SettingsUpdatedListener : Listener {
    fun onSettingsUpdated(coreSettings: CoreSettings, moduleSettings: Map<String, ModuleSettings>)
}
