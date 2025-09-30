package com.tealium.core.api.settings.modules

import com.tealium.core.api.Modules
import com.tealium.core.api.modules.Module

/**
 * A [ModuleSettingsBuilder] implementation to configure settings relevant to the AppData [Module]
 */
class AppDataSettingsBuilder :
    CollectorSettingsBuilder<AppDataSettingsBuilder>(Modules.Types.APP_DATA)